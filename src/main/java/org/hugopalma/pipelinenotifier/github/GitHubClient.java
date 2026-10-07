package org.hugopalma.pipelinenotifier.github;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.hugopalma.pipelinenotifier.github.model.GitHubJob;
import org.hugopalma.pipelinenotifier.github.model.GitHubRepo;
import org.hugopalma.pipelinenotifier.github.model.GitHubUser;
import org.hugopalma.pipelinenotifier.github.model.GitHubWorkflowRun;
import org.hugopalma.pipelinenotifier.provider.CiClient;
import org.hugopalma.pipelinenotifier.provider.HttpSupport;
import org.hugopalma.pipelinenotifier.provider.Page;
import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;
import org.hugopalma.pipelinenotifier.watch.RemoteUrlParser;
import org.hugopalma.pipelinenotifier.watch.UrlSafety;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Minimal read-only GitHub Actions client over the REST API. The repository is addressed by its
 * {@code owner/name} path, so unlike GitLab there is no project-id lookup.
 *
 * <p>Every call blocks and must be made off the EDT.
 */
public class GitHubClient implements CiClient {

    private static final Logger LOG = Logger.getInstance(GitHubClient.class);
    private static final Type REPO_LIST = new TypeToken<List<GitHubRepo>>() { }.getType();

    /**
     * GitHub can only filter runs by <em>creation</em> time, but a run is alertable when it
     * <em>finishes</em> failing, which may be long after it was created: a slow workflow, or a
     * "re-run failed jobs" of an old run. The query therefore reaches this far back before the
     * watermark and the exact {@code updated_at} cut-off is applied client-side.
     */
    static final Duration CREATED_LOOKBACK = Duration.ofHours(12);

    private final Gson gson = new GsonBuilder().create();
    private final String apiBase;
    private final String token;
    private final HttpClient httpClient;

    public GitHubClient(String host, String token) {
        this(host, token, HttpSupport.defaultHttpClient());
    }

    public GitHubClient(String host, String token, HttpClient httpClient) {
        this.apiBase = apiBaseFor(host);
        this.token = token;
        this.httpClient = httpClient;
    }

    /** github.com serves its API from a separate host; GitHub Enterprise Server from {@code /api/v3}. */
    static String apiBaseFor(String host) {
        String base = UrlSafety.normalizeBaseUrl(host);
        if ("github.com".equals(RemoteUrlParser.hostOf(base))) {
            return "https://api.github.com";
        }
        return base + "/api/v3";
    }

    @Override
    public String currentUser() throws IOException, InterruptedException {
        GitHubUser user = gson.fromJson(request("/user"), GitHubUser.class);
        return user == null ? null : user.login();
    }

    @Override
    public Page<PipelineRun> failedRuns(RemoteProject target, Instant since, String username, int perPage, int page)
            throws IOException, InterruptedException {
        Instant createdFrom = since.minus(CREATED_LOOKBACK);
        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Map.entry("status", "failure"));
        params.add(Map.entry("created", ">=" + DateTimeFormatter.ISO_INSTANT.format(createdFrom)));
        params.add(Map.entry("per_page", String.valueOf(perPage)));
        params.add(Map.entry("page", String.valueOf(page)));
        if (username != null && !username.isBlank()) {
            params.add(Map.entry("actor", username));
        }

        GitHubWorkflowRun.Runs body = gson.fromJson(
                request(repoPath(target) + "/actions/runs" + HttpSupport.query(params)), GitHubWorkflowRun.Runs.class);
        List<GitHubWorkflowRun> raw = body == null || body.workflowRuns() == null ? List.of() : body.workflowRuns();

        List<PipelineRun> runs = new ArrayList<>(raw.size());
        for (GitHubWorkflowRun run : raw) {
            PipelineRun converted = toRun(run);
            // See CREATED_LOOKBACK: the server could only narrow by creation time.
            if (converted.updatedAt() == null || !converted.updatedAt().isBefore(since)) {
                runs.add(converted);
            }
        }
        return Page.of(runs, perPage, raw.size());
    }

    /** The listing already carries the actor, so there is nothing to add. */
    @Override
    public PipelineRun detail(RemoteProject target, PipelineRun run) {
        return run;
    }

    @Override
    public List<String> failedJobs(RemoteProject target, PipelineRun run) throws IOException, InterruptedException {
        String path = repoPath(target) + "/actions/runs/" + run.id() + "/jobs"
                + HttpSupport.query(List.of(Map.entry("filter", "latest"), Map.entry("per_page", "100")));
        GitHubJob.Jobs body = gson.fromJson(request(path), GitHubJob.Jobs.class);

        List<String> names = new ArrayList<>();
        if (body != null && body.jobs() != null) {
            for (GitHubJob job : body.jobs()) {
                if (job.failed()) {
                    names.add(job.name());
                }
            }
        }
        return names;
    }

    /**
     * The API has no repository search over the user's own repositories, so {@code search} is
     * applied here. A page can therefore come back short without being the last one, which is why
     * the result carries its own {@code hasMore}.
     */
    @Override
    public Page<String> listProjects(String search, int perPage, int page) throws IOException, InterruptedException {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Map.entry("affiliation", "owner,collaborator,organization_member"));
        params.add(Map.entry("sort", "full_name"));
        params.add(Map.entry("per_page", String.valueOf(perPage)));
        params.add(Map.entry("page", String.valueOf(page)));

        List<GitHubRepo> raw = gson.fromJson(request("/user/repos" + HttpSupport.query(params)), REPO_LIST);
        if (raw == null) {
            raw = List.of();
        }

        String needle = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<String> paths = new ArrayList<>();
        for (GitHubRepo repo : raw) {
            if (repo.fullName() != null && repo.fullName().toLowerCase(Locale.ROOT).contains(needle)) {
                paths.add(repo.fullName());
            }
        }
        return Page.of(paths, perPage, raw.size());
    }

    private static PipelineRun toRun(GitHubWorkflowRun run) {
        String name = run.displayTitle() != null && !run.displayTitle().isBlank() ? run.displayTitle() : run.name();
        return new PipelineRun(
                run.id(),
                run.headBranch(),
                run.headSha(),
                run.event(),
                name,
                run.htmlUrl(),
                PipelineRun.parseTimestamp(run.updatedAt()),
                run.triggeredBy());
    }

    /**
     * {@code path} is {@code owner/repo}. It originates in a git remote or in settings, so each
     * segment is encoded: a crafted one must not be able to reach another endpoint.
     */
    private static String repoPath(RemoteProject target) {
        StringBuilder sb = new StringBuilder("/repos");
        for (String segment : target.path().split("/")) {
            sb.append('/').append(HttpSupport.encode(segment));
        }
        return sb.toString();
    }

    private String request(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 400) {
            LOG.debug("GitHub " + status + " for " + path + ": " + HttpSupport.truncate(response.body()));
        }
        return HttpSupport.requireSuccess(response, path, "GitHub",
                "Check that it is valid and can read Actions on the watched repositories.");
    }
}
