package org.hugopalma.pipelinenotifier.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabJob;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabProject;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabUser;
import org.hugopalma.pipelinenotifier.provider.HttpSupport;
import org.hugopalma.pipelinenotifier.watch.UrlSafety;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Minimal read-only GitLab REST client.
 *
 * <p>Deliberately hand-rolled over {@link HttpClient} rather than pulling in a dependency: the
 * plugin needs five endpoints, and shipping an HTTP stack inside a plugin classloader invites
 * conflicts with the platform's own.
 *
 * <p>Every call blocks and must be made off the EDT.
 */
public class GitLabClient {

    private static final Logger LOG = Logger.getInstance(GitLabClient.class);
    private static final Type PIPELINE_LIST = new TypeToken<List<GitLabPipeline>>() { }.getType();
    private static final Type JOB_LIST = new TypeToken<List<GitLabJob>>() { }.getType();
    private static final Type PROJECT_LIST = new TypeToken<List<GitLabProject>>() { }.getType();

    private final Gson gson = new GsonBuilder().create();
    private final String apiBase;
    private final String token;
    private final HttpClient httpClient;

    public GitLabClient(String host, String token) {
        this(host, token, defaultHttpClient());
    }

    public GitLabClient(String host, String token, HttpClient httpClient) {
        this.apiBase = UrlSafety.normalizeBaseUrl(host) + "/api/v4";
        this.token = token;
        this.httpClient = httpClient;
    }

    public static HttpClient defaultHttpClient() {
        return HttpSupport.defaultHttpClient();
    }

    /** The user the token belongs to. Also the cheapest way to validate a token. */
    public GitLabUser currentUser() throws IOException, InterruptedException {
        return gson.fromJson(request("/user"), GitLabUser.class);
    }

    public GitLabProject findProject(String pathWithNamespace) throws IOException, InterruptedException {
        return gson.fromJson(request("/projects/" + encode(pathWithNamespace)), GitLabProject.class);
    }

    /**
     * Failed pipelines updated since {@code updatedAfter}, one page of results at a time.
     *
     * <p>{@code username} is applied server-side because the list response carries no user field.
     * Glob refs cannot be expressed here, so the caller filters those client-side. {@code page} is
     * 1-based; the caller is expected to keep requesting subsequent pages while a full page comes
     * back, since results are sorted newest-first and a partial fetch would otherwise silently
     * drop the oldest matches in a burst larger than one page.
     */
    public List<GitLabPipeline> failedPipelines(long projectId,
                                                Instant updatedAfter,
                                                String username,
                                                int perPage,
                                                int page) throws IOException, InterruptedException {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Map.entry("status", "failed"));
        params.add(Map.entry("updated_after", DateTimeFormatter.ISO_INSTANT.format(updatedAfter)));
        params.add(Map.entry("order_by", "updated_at"));
        params.add(Map.entry("sort", "desc"));
        params.add(Map.entry("per_page", String.valueOf(perPage)));
        params.add(Map.entry("page", String.valueOf(page)));
        if (username != null && !username.isBlank()) {
            params.add(Map.entry("username", username));
        }

        String body = request("/projects/" + projectId + "/pipelines" + query(params));
        List<GitLabPipeline> pipelines = gson.fromJson(body, PIPELINE_LIST);
        return pipelines == null ? List.of() : pipelines;
    }

    /**
     * Projects the token's own user is a member of, for populating the "watch" picker.
     * Membership-scoped rather than every project the instance hosts, since an unfiltered search on
     * a large self-hosted or gitlab.com instance would return results with no relevance to this user.
     */
    public List<GitLabProject> listProjects(String search, int perPage, int page)
            throws IOException, InterruptedException {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Map.entry("membership", "true"));
        params.add(Map.entry("simple", "true"));
        params.add(Map.entry("order_by", "path"));
        params.add(Map.entry("sort", "asc"));
        params.add(Map.entry("per_page", String.valueOf(perPage)));
        params.add(Map.entry("page", String.valueOf(page)));
        if (search != null && !search.isBlank()) {
            params.add(Map.entry("search", search));
        }

        String body = request("/projects" + query(params));
        List<GitLabProject> projects = gson.fromJson(body, PROJECT_LIST);
        return projects == null ? List.of() : projects;
    }

    /** Full pipeline, which unlike a list entry includes the triggering user. */
    public GitLabPipeline pipeline(long projectId, long pipelineId) throws IOException, InterruptedException {
        return gson.fromJson(request("/projects/" + projectId + "/pipelines/" + pipelineId), GitLabPipeline.class);
    }

    /** Failed jobs, so the alert can name what actually broke. */
    public List<GitLabJob> failedJobs(long projectId, long pipelineId) throws IOException, InterruptedException {
        String path = "/projects/" + projectId + "/pipelines/" + pipelineId + "/jobs"
                + query(List.of(Map.entry("scope[]", "failed"), Map.entry("per_page", "20")));
        List<GitLabJob> jobs = gson.fromJson(request(path), JOB_LIST);
        return jobs == null ? List.of() : jobs;
    }

    private String request(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiBase + path))
                .header("PRIVATE-TOKEN", token)
                .header("Accept", "application/json")
                .timeout(HttpSupport.REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            LOG.debug("GitLab " + response.statusCode() + " for " + path + ": " + HttpSupport.truncate(response.body()));
        }
        return HttpSupport.requireSuccess(response, path, "GitLab", "Check that it is valid and has the read_api scope.");
    }

    private static String query(List<Map.Entry<String, String>> params) {
        return HttpSupport.query(params);
    }

    private static String encode(String value) {
        return HttpSupport.encode(value);
    }
}
