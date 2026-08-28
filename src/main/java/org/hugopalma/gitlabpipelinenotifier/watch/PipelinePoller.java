package org.hugopalma.gitlabpipelinenotifier.watch;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.hugopalma.gitlabpipelinenotifier.gitlab.GitLabAuthException;
import org.hugopalma.gitlabpipelinenotifier.gitlab.GitLabClient;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabJob;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.gitlabpipelinenotifier.notify.FailureAlerter;
import org.hugopalma.gitlabpipelinenotifier.notify.PipelineFailure;
import org.hugopalma.gitlabpipelinenotifier.settings.NotificationRule;
import org.hugopalma.gitlabpipelinenotifier.settings.NotifierState;
import org.hugopalma.gitlabpipelinenotifier.settings.Settings;
import org.hugopalma.gitlabpipelinenotifier.settings.TokenStore;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Polls GitLab for failed pipelines and hands matches to {@link FailureAlerter}.
 *
 * <p>Polling is used rather than webhooks because an IDE has no reachable endpoint to receive them.
 * Request volume is kept deliberately flat: one request per watched project per distinct rule
 * username per tick, far below GitLab's authenticated rate limit.
 *
 * <p>Each tick reschedules the next one, so the interval can change - and back off on failure -
 * without tearing down a fixed-rate task.
 */
@Service(Service.Level.PROJECT)
public final class PipelinePoller implements Disposable {

    private static final Logger LOG = Logger.getInstance(PipelinePoller.class);

    /** Caps backoff at 2^4 = 16x the configured poll interval. */
    private static final int MAX_BACKOFF_SHIFT = 4;
    private static final int PER_PAGE = 20;

    private final Project project;

    /** Project id lookups are stable for a session; a rename is rare enough to need a restart. */
    private final Map<String, Long> projectIdCache = new HashMap<>();

    private ScheduledFuture<?> scheduled;
    private boolean stopped;
    private int backoffTicks;

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
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

    /** Called when settings change: drop caches so a new host or token takes effect immediately. */
    public synchronized void restart() {
        stop();
        projectIdCache.clear();
        backoffTicks = 0;
        start();
    }

    @Override
    public void dispose() {
        stop();
    }

    private synchronized void schedule(long delaySeconds) {
        if (stopped || project.isDisposed()) {
            return;
        }
        scheduled = AppExecutorUtil.getAppScheduledExecutorService()
                .schedule(this::tick, delaySeconds, TimeUnit.SECONDS);
    }

    private void tick() {
        if (project.isDisposed()) {
            return;
        }

        long nextDelay;
        try {
            pollOnce();
            synchronized (this) {
                backoffTicks = 0;
            }
            nextDelay = intervalSeconds();
        } catch (GitLabAuthException e) {
            // A bad token will never fix itself; stop rather than hammering the instance.
            LOG.warn("GitLab authentication failed, stopping poller", e);
            stop();
            FailureAlerter.getInstance(project).notifyPollingStopped(
                    e.getMessage() + " Polling is paused until you update the settings.");
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception e) {
            synchronized (this) {
                backoffTicks = Math.min(backoffTicks + 1, MAX_BACKOFF_SHIFT);
                nextDelay = intervalSeconds() << backoffTicks;
            }
            LOG.warn("GitLab poll failed, retrying in " + nextDelay + "s", e);
        }

        schedule(nextDelay);
    }

    private static long intervalSeconds() {
        return Math.max(Settings.getInstance().getState().pollIntervalSeconds, Settings.MIN_POLL_SECONDS);
    }

    private void pollOnce() throws Exception {
        Settings.State settings = Settings.getInstance().getState();

        String host = settings.gitlabHost == null ? "" : settings.gitlabHost.trim();
        if (host.isEmpty()) {
            return;
        }

        String token = TokenStore.get(host);
        if (token == null) {
            return;
        }

        Set<RemoteProject> targets = ProjectDiscovery.discover(project, settings);
        if (targets.isEmpty()) {
            return;
        }

        GitLabClient client = new GitLabClient(host, token);
        NotifierState notifierState = NotifierState.getInstance();

        String me = resolveUsername(client, notifierState);
        List<PollQuery> queries = buildQueries(settings, me);
        if (queries.isEmpty()) {
            return;
        }

        for (RemoteProject target : targets) {
            long projectId = cachedProjectId(client, target);

            Instant now = Instant.now();
            Instant since = notifierState.watermarkFor(target.key(), now);
            Instant newest = since;

            // Collect across every query before alerting: a pipeline can come back from more than
            // one query (say the "my failures" query and a catch-all rule), and it should get the
            // union of what those rules asked for rather than whichever query happened to run first.
            Map<Long, GitLabPipeline> matched = new LinkedHashMap<>();
            Map<Long, AlertChannels> matchedChannels = new LinkedHashMap<>();

            for (PollQuery query : queries) {
                List<GitLabPipeline> pipelines =
                        client.failedPipelines(projectId, since, query.username(), PER_PAGE);

                for (GitLabPipeline pipeline : pipelines) {
                    Instant updated = pipeline.updatedAtInstant();
                    if (updated != null && updated.isAfter(newest)) {
                        newest = updated;
                    }

                    AlertChannels channels = RuleMatcher.match(pipeline, query.rules());
                    if (!channels.any()) {
                        continue;
                    }

                    matched.putIfAbsent(pipeline.id(), pipeline);
                    matchedChannels.merge(pipeline.id(), channels, AlertChannels::merge);
                }
            }

            for (Map.Entry<Long, GitLabPipeline> entry : matched.entrySet()) {
                // Dedupe after matching, so a retried pipeline that still fails is not
                // re-announced just because GitLab bumped its updated_at - unless the user asked
                // to hear about retries, in which case that bump is exactly the signal we key on.
                if (!notifierState.markAlerted(target.key(), entry.getKey(),
                        retryRevision(settings, entry.getValue()))) {
                    continue;
                }

                PipelineFailure failure = buildFailure(client, projectId, target, entry.getValue(), me);
                FailureAlerter.getInstance(project).alert(failure, matchedChannels.get(entry.getKey()));
            }

            notifierState.advanceWatermark(target.key(), newest);
        }
    }

    /**
     * What distinguishes one failed run of a pipeline from the next, or {@code null} to treat every
     * run as the same alert.
     *
     * <p>GitLab exposes no retry counter on a pipeline, so {@code updated_at} stands in: it moves
     * when a retried pipeline finishes failing again, and is stable once it has. A pipeline with no
     * usable timestamp falls back to id-only dedupe rather than risking an alert every tick.
     */
    private static String retryRevision(Settings.State settings, GitLabPipeline pipeline) {
        if (!settings.alertOnRetries) {
            return null;
        }
        Instant updated = pipeline.updatedAtInstant();
        return updated == null ? null : updated.toString();
    }

    private synchronized Long cachedProjectId(GitLabClient client, RemoteProject target) throws Exception {
        Long cached = projectIdCache.get(target.key());
        if (cached != null) {
            return cached;
        }
        long id = client.findProject(target.path()).id();
        projectIdCache.put(target.key(), id);
        return id;
    }

    private static String resolveUsername(GitLabClient client, NotifierState state) throws Exception {
        String cached = state.getResolvedUsername();
        if (cached != null && !cached.isBlank()) {
            return cached;
        }
        String username = client.currentUser().username();
        if (username == null || username.isBlank()) {
            return null;
        }
        state.setResolvedUsername(username);
        return username;
    }

    /**
     * The built-in "my failures" toggle is expressed as an ordinary rule pinned to the current user,
     * so it goes through exactly the same matching and grouping as user-defined rules.
     */
    private static List<PollQuery> buildQueries(Settings.State settings, String me) {
        List<NotificationRule> rules = new ArrayList<>();

        if (settings.notifyOwnFailures && me != null) {
            NotificationRule own = new NotificationRule();
            own.enabled = true;
            own.username = me;
            own.stickyBalloon = settings.ownStickyBalloon;
            own.systemNotification = settings.ownSystemNotification;
            own.modalDialog = settings.ownModalDialog;
            rules.add(own);
        }
        for (NotificationRule rule : settings.rules) {
            if (rule.enabled) {
                rules.add(rule);
            }
        }

        return RuleMatcher.planQueries(rules);
    }

    private static PipelineFailure buildFailure(GitLabClient client,
                                                long projectId,
                                                RemoteProject target,
                                                GitLabPipeline pipeline,
                                                String me) {
        // Only pipelines that already matched get enriched, so this costs nothing on a quiet poll.
        GitLabPipeline detail = null;
        try {
            detail = client.pipeline(projectId, pipeline.id());
        } catch (Exception e) {
            LOG.debug("Could not load pipeline detail for " + pipeline.id(), e);
        }

        List<String> failedJobs = new ArrayList<>();
        try {
            for (GitLabJob job : client.failedJobs(projectId, pipeline.id())) {
                if (!job.allowFailure()) {
                    failedJobs.add(job.name());
                }
            }
        } catch (Exception e) {
            LOG.debug("Could not load failed jobs for " + pipeline.id(), e);
        }

        String triggeredBy = (detail != null && detail.user() != null) ? detail.user().username() : null;
        boolean own = triggeredBy != null && triggeredBy.equals(me);

        return new PipelineFailure(
                target,
                detail != null ? detail : pipeline,
                failedJobs,
                triggeredBy,
                own);
    }
}
