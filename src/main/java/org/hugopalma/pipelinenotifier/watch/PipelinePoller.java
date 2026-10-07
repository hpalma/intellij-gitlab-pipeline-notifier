package org.hugopalma.pipelinenotifier.watch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.hugopalma.pipelinenotifier.notify.FailureAlerter;
import org.hugopalma.pipelinenotifier.notify.PipelineFailure;
import org.hugopalma.pipelinenotifier.provider.CiAuthException;
import org.hugopalma.pipelinenotifier.provider.CiClient;
import org.hugopalma.pipelinenotifier.provider.CiHttpException;
import org.hugopalma.pipelinenotifier.provider.CiRateLimitException;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.hugopalma.pipelinenotifier.provider.Page;
import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.settings.NotificationRule;
import org.hugopalma.pipelinenotifier.settings.NotifierState;
import org.hugopalma.pipelinenotifier.settings.Settings;
import org.hugopalma.pipelinenotifier.settings.TokenStore;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls every configured CI service for failed pipelines and hands matches to {@link FailureAlerter}.
 * Nothing here knows which service it is talking to; that is all behind {@link CiClient}.
 *
 * <p>Polling is used rather than webhooks because an IDE has no reachable endpoint to receive them.
 * Request volume is kept deliberately flat: one request per watched project per distinct rule
 * username per tick, far below the services' authenticated rate limits.
 *
 * <p>Each tick reschedules the next one, so the interval can change - and back off on failure -
 * without tearing down a fixed-rate task.
 */
@Service(Service.Level.PROJECT)
public final class PipelinePoller implements Disposable {

    private static final Logger LOG = Logger.getInstance(PipelinePoller.class);

    /** Caps backoff at 2^4 = 16x the configured poll interval. */
    private static final int MAX_BACKOFF_SHIFT = 4;
    /** The largest page both services accept; keeps a large burst of failures to as few requests as possible. */
    private static final int PER_PAGE = 100;
    /** Safety valve on how many pages one query is allowed to page through in a single tick. */
    private static final int MAX_PAGES = 10;
    /** How far behind the newest run seen the watermark is kept; see {@link #pollTarget}. */
    private static final Duration WATERMARK_OVERLAP = Duration.ofSeconds(60);
    /** Longest a throttled connection is left alone, whatever the server asks for. */
    private static final Duration MAX_SNOOZE = Duration.ofHours(1);

    private final Project project;

    private record CachedClient(String host, String token, CiClient client) {
    }

    // Reused across ticks so the underlying HttpClient - and its connection pool and keep-alive -
    // survives from one poll to the next instead of being rebuilt (and its resources abandoned to
    // the GC) every tick. Per provider; rebuilt only when its host or token actually changes.
    private final Map<String, CachedClient> clients = new HashMap<>();

    /**
     * Connections whose token was rejected. A bad token will never fix itself, so they are skipped
     * until {@link #restart()} - which settings changes trigger - while the others keep polling.
     */
    private final Set<String> pausedConnections = new HashSet<>();

    /** Connections the server asked us to leave alone for a while, until the given instant. */
    private final Map<String, Instant> snoozedUntil = new HashMap<>();

    private ScheduledFuture<?> scheduled;
    private boolean stopped;
    private int backoffTicks;

    /**
     * Bumped on every {@link #stop()}. A tick that was already running when stop/restart happened
     * carries the old value, so it can neither re-arm itself (which would leave two polling chains
     * alive after a settings change) nor stop the freshly restarted poller on a stale auth failure.
     */
    private long generation;

    public PipelinePoller(Project project) {
        this.project = project;
    }

    public static PipelinePoller getInstance(Project project) {
        return project.getService(PipelinePoller.class);
    }

    public synchronized void start() {
        stopped = false;
        if (scheduled == null || scheduled.isDone()) {
            schedule(intervalSeconds());
        }
    }

    public synchronized void stop() {
        stopped = true;
        generation++;
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    /** Called when settings change: drop caches so a new host or token takes effect immediately. */
    public synchronized void restart() {
        stop();
        clients.clear();
        pausedConnections.clear();
        snoozedUntil.clear();
        backoffTicks = 0;
        start();
    }

    @Override
    public void dispose() {
        stop();
    }

    private synchronized void schedule(long delaySeconds) {
        schedule(delaySeconds, generation);
    }

    private synchronized void schedule(long delaySeconds, long expectedGeneration) {
        if (stopped || expectedGeneration != generation || project.isDisposed()) {
            return;
        }
        scheduled = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(() -> tick(expectedGeneration), delaySeconds, TimeUnit.SECONDS);
    }

    private void tick(long tickGeneration) {
        if (project.isDisposed()) {
            return;
        }

        long nextDelay;
        try {
            pollOnce(tickGeneration);
            synchronized (this) {
                backoffTicks = 0;
            }
            nextDelay = intervalSeconds();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Throwable e) {
            // Deliberately Throwable, not just Exception: tick() self-reschedules only by reaching
            // schedule(nextDelay) below, so any uncaught Error (e.g. NoClassDefFoundError from a
            // platform module unavailable in this process) would otherwise silently and permanently
            // stop the poller after a single tick, with no retry and no visible sign why.
            synchronized (this) {
                backoffTicks = Math.min(backoffTicks + 1, MAX_BACKOFF_SHIFT);
                nextDelay = intervalSeconds() << backoffTicks;
            }
            LOG.warn("Poll failed, retrying in " + nextDelay + "s", e);
        }

        schedule(nextDelay, tickGeneration);
    }

    private static long intervalSeconds() {
        return Math.max(Settings.getInstance().getState().pollIntervalSeconds, Settings.MIN_POLL_SECONDS);
    }

    private void pollOnce(long tickGeneration) throws Exception {
        Settings.State settings = Settings.getInstance().getState();

        Set<RemoteProject> targets = ProjectDiscovery.discover(project, settings);
        if (targets.isEmpty()) {
            return;
        }
        Map<String, List<RemoteProject>> targetsByConnection = new LinkedHashMap<>();
        for (RemoteProject target : targets) {
            targetsByConnection.computeIfAbsent(target.connectionKey(), k -> new ArrayList<>()).add(target);
        }

        // One connection failing (server down, bad token) must not silence the others.
        Exception firstFailure = null;
        for (Settings.Connection connection : settings.connections()) {
            CiProvider provider = CiProviders.find(connection.provider);
            String host = RemoteUrlParser.hostOf(connection.host);
            if (provider == null || host == null) {
                continue;
            }
            String connectionKey = RemoteProject.connectionKey(provider.id(), host);
            List<RemoteProject> own = targetsByConnection.get(connectionKey);
            if (own == null || isPaused(connectionKey)) {
                continue;
            }
            String token = TokenStore.get(provider.id(), connection.host.trim());
            if (token == null) {
                continue;
            }

            try {
                pollConnection(provider, connection.host.trim(), token, connectionKey, own, settings, tickGeneration);
            } catch (CiAuthException e) {
                pauseConnection(provider, connectionKey, e, tickGeneration);
            } catch (CiRateLimitException e) {
                snoozeConnection(provider, connectionKey, e);
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn(provider.displayName() + " poll failed", e);
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        // Surfaced after the loop so the poller still backs off, as it would for a single server.
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    private void pollConnection(CiProvider provider,
                                String host,
                                String token,
                                String connectionKey,
                                List<RemoteProject> connectionTargets,
                                Settings.State settings,
                                long tickGeneration) throws Exception {
        CiClient client = clientFor(provider, host, token);
        NotifierState notifierState = NotifierState.getInstance();

        String me = resolveUsername(client, notifierState, connectionKey);
        List<PollQuery> queries = buildQueries(settings, provider, me);
        if (queries.isEmpty()) {
            return;
        }

        int failed = 0;
        int forbidden = 0;
        Exception firstFailure = null;
        for (RemoteProject target : connectionTargets) {
            // A single misconfigured or renamed target (bad path, deleted project, a repo the token
            // cannot see) must not take down polling for every other watched project - so its
            // failure is contained here. A rejected token (401) and a throttled server affect every
            // target of the connection, so those propagate and stop it outright or for a while.
            try {
                pollTarget(client, notifierState, settings, queries, target, me, tickGeneration);
            } catch (CiAuthException | CiRateLimitException | InterruptedException e) {
                throw e;
            } catch (Exception e) {
                LOG.warn("Poll failed for " + target.key() + ", skipping this tick", e);
                failed++;
                if (e instanceof CiHttpException http && http.getStatus() == 403) {
                    forbidden++;
                }
                if (firstFailure == null) {
                    firstFailure = e;
                }
            }
        }

        // One forbidden project is a project problem; all of them is a token problem (missing scope).
        if (forbidden == connectionTargets.size()) {
            throw new CiAuthException(provider.displayName() + " denied access to every watched project (HTTP 403)."
                    + " Check the token's scopes or permissions.");
        }
        // Every project failing is a server problem: surface it so the poller backs off.
        if (failed == connectionTargets.size()) {
            throw firstFailure;
        }
    }

    private synchronized boolean isCurrent(long tickGeneration) {
        return tickGeneration == generation;
    }

    private synchronized boolean isPaused(String connectionKey) {
        if (pausedConnections.contains(connectionKey)) {
            return true;
        }
        Instant until = snoozedUntil.get(connectionKey);
        if (until != null && Instant.now().isBefore(until)) {
            return true;
        }
        snoozedUntil.remove(connectionKey);
        return false;
    }

    /**
     * Leaves a throttled server alone until it says it is ready, rather than asking again every tick
     * for every project. Other connections carry on, and no poller-wide backoff is needed.
     */
    private synchronized void snoozeConnection(CiProvider provider, String connectionKey, CiRateLimitException e) {
        Duration wait = e.getRetryAfter() == null ? Duration.ofSeconds(intervalSeconds() * 2) : e.getRetryAfter();
        wait = wait.compareTo(MAX_SNOOZE) > 0 ? MAX_SNOOZE : wait;
        wait = wait.compareTo(Duration.ofSeconds(intervalSeconds())) < 0 ? Duration.ofSeconds(intervalSeconds()) : wait;
        snoozedUntil.put(connectionKey, Instant.now().plus(wait));
        LOG.warn(provider.displayName() + " is rate limiting us; pausing it for " + wait.toSeconds() + "s");
    }

    private void pauseConnection(CiProvider provider, String connectionKey, CiAuthException e, long tickGeneration) {
        synchronized (this) {
            if (tickGeneration != generation) {
                return; // superseded by stop()/restart() while this tick was in flight
            }
            if (!pausedConnections.add(connectionKey)) {
                return;
            }
        }
        LOG.warn(provider.displayName() + " authentication failed, pausing it", e);
        FailureAlerter.getInstance(project).notifyPollingPaused(provider.displayName(),
                e.getMessage() + " Polling " + provider.displayName() + " is paused until you update the settings.");
    }

    private void pollTarget(CiClient client,
                            NotifierState notifierState,
                            Settings.State settings,
                            List<PollQuery> queries,
                            RemoteProject target,
                            String me,
                            long tickGeneration) throws Exception {
        Instant now = Instant.now();
        Instant since = notifierState.watermarkFor(target.key(), now);
        Instant newest = since;

        // Collect across every query before alerting: a run can come back from more than
        // one query (say the "my failures" query and a catch-all rule), and it should get the
        // union of what those rules asked for rather than whichever query happened to run first.
        Map<Long, PipelineRun> matched = new LinkedHashMap<>();
        Map<Long, AlertChannels> matchedChannels = new LinkedHashMap<>();

        // Whether every matching run in [since, now) was actually seen. Results come back
        // newest-first, so a page with more behind it means older matches may still be waiting;
        // if MAX_PAGES is exhausted before that runs dry, this target's window was not fully
        // covered and the watermark must not advance - otherwise the leftover, older runs would
        // fall before the new "since" and be silently skipped on every future tick.
        boolean fullyCovered = true;

        for (PollQuery query : queries) {
            for (int pageNumber = 1; pageNumber <= MAX_PAGES; pageNumber++) {
                Page<PipelineRun> page = client.failedRuns(target, since, query.username(), PER_PAGE, pageNumber);

                for (PipelineRun run : page.items()) {
                    Instant updated = run.updatedAt();
                    if (updated != null && updated.isAfter(newest)) {
                        newest = updated;
                    }

                    AlertChannels channels = RuleMatcher.match(run, query.rules());
                    if (!channels.any()) {
                        continue;
                    }

                    matched.putIfAbsent(run.id(), run);
                    matchedChannels.merge(run.id(), channels, AlertChannels::merge);
                }

                if (!page.hasMore()) {
                    break;
                }
                if (pageNumber == MAX_PAGES) {
                    LOG.warn("Pipeline backlog for " + target.key() + " exceeded " + (MAX_PAGES * PER_PAGE)
                            + " results in one poll; watermark will not advance until it drains");
                    fullyCovered = false;
                }
            }
        }

        // stop()/restart() may have run while this tick was fetching. It would alert and move
        // watermarks using the old client, token and settings, racing the tick chain that replaced it.
        if (!isCurrent(tickGeneration)) {
            return;
        }

        for (Map.Entry<Long, PipelineRun> entry : matched.entrySet()) {
            // Dedupe after matching, so a retried run that still fails is not re-announced just
            // because the server bumped its updated time - unless the user asked to hear about
            // retries, in which case that bump is exactly the signal we key on.
            if (!notifierState.markAlerted(target.key(), entry.getKey(),
                    retryRevision(settings, entry.getValue()))) {
                continue;
            }

            PipelineFailure failure = buildFailure(client, target, entry.getValue(), me);
            FailureAlerter.getInstance(project).alert(failure, matchedChannels.get(entry.getKey()));
        }

        if (fullyCovered) {
            // Not quite to the newest run seen: a run can show up in the listing a little after one
            // that finished later, and starting exactly at the newest would skip it for good. The
            // overlap re-reads a short tail every tick; markAlerted keeps it from alerting twice.
            notifierState.advanceWatermark(target.key(), newest.minus(WATERMARK_OVERLAP));
        }
    }

    /**
     * What distinguishes one failed run of a pipeline from the next, or {@code null} to treat every
     * run as the same alert.
     *
     * <p>Neither service exposes a portable retry counter, so the update time stands in: it moves
     * when a retried run finishes failing again, and is stable once it has. A run with no usable
     * timestamp falls back to id-only dedupe rather than risking an alert every tick.
     */
    private static String retryRevision(Settings.State settings, PipelineRun run) {
        if (!settings.alertOnRetries) {
            return null;
        }
        return run.updatedAt() == null ? null : run.updatedAt().toString();
    }

    private synchronized CiClient clientFor(CiProvider provider, String host, String token) {
        CachedClient cached = clients.get(provider.id());
        if (cached == null || !host.equals(cached.host()) || !token.equals(cached.token())) {
            cached = new CachedClient(host, token, provider.createClient(host, token));
            clients.put(provider.id(), cached);
        }
        return cached.client();
    }

    private static String resolveUsername(CiClient client, NotifierState state, String connectionKey) throws Exception {
        String cached = state.getResolvedUsername(connectionKey);
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String username = client.currentUser();
        if (username == null || username.isBlank()) {
            return null;
        }
        state.setResolvedUsername(connectionKey, username);
        return username;
    }

    /**
     * The built-in "my failures" toggle is expressed as an ordinary rule pinned to the current user,
     * so it goes through exactly the same matching and grouping as user-defined rules. Only rules
     * written for {@code provider} apply: their sources and usernames mean nothing elsewhere.
     */
    private static List<PollQuery> buildQueries(Settings.State settings, CiProvider provider, String me) {
        List<NotificationRule> rules = new ArrayList<>();

        if (settings.notifyOwnFailures && me != null) {
            NotificationRule own = new NotificationRule();
            own.enabled = true;
            own.provider = provider.id();
            own.username = me;
            own.stickyBalloon = settings.ownStickyBalloon;
            own.systemNotification = settings.ownSystemNotification;
            own.modalDialog = settings.ownModalDialog;
            rules.add(own);
        }
        for (NotificationRule rule : settings.rules) {
            if (rule.enabled && provider.id().equals(rule.provider)) {
                rules.add(rule);
            }
        }

        return RuleMatcher.planQueries(rules);
    }

    private static PipelineFailure buildFailure(CiClient client, RemoteProject target, PipelineRun run, String me) {
        // Only runs that already matched get enriched, so this costs nothing on a quiet poll.
        PipelineRun detail = run;
        try {
            detail = client.detail(target, run);
        } catch (Exception e) {
            LOG.debug("Could not load pipeline detail for " + run.id(), e);
        }

        List<String> failedJobs = List.of();
        try {
            failedJobs = client.failedJobs(target, run);
        } catch (Exception e) {
            LOG.debug("Could not load failed jobs for " + run.id(), e);
        }

        String triggeredBy = detail.triggeredBy();
        boolean own = triggeredBy != null && triggeredBy.equalsIgnoreCase(me);

        return new PipelineFailure(target, detail, failedJobs, triggeredBy, own);
    }
}
