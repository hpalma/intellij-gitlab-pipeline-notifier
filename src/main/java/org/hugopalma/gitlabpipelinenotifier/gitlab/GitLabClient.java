package org.hugopalma.gitlabpipelinenotifier.gitlab;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.intellij.openapi.diagnostic.Logger;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabJob;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabProject;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabUser;

import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
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
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);
    private static final Type PIPELINE_LIST = new TypeToken<List<GitLabPipeline>>() { }.getType();
    private static final Type JOB_LIST = new TypeToken<List<GitLabJob>>() { }.getType();

    private final Gson gson = new GsonBuilder().create();
    private final String apiBase;
    private final String token;
    private final HttpClient httpClient;

    public GitLabClient(String host, String token) {
        this(host, token, defaultHttpClient());
    }

    public GitLabClient(String host, String token, HttpClient httpClient) {
        this.apiBase = stripTrailingSlash(host) + "/api/v4";
        this.token = token;
        this.httpClient = httpClient;
    }

    public static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** The user the token belongs to. Also the cheapest way to validate a token. */
    public GitLabUser currentUser() throws IOException, InterruptedException {
        return gson.fromJson(request("/user"), GitLabUser.class);
    }

    public GitLabProject findProject(String pathWithNamespace) throws IOException, InterruptedException {
        return gson.fromJson(request("/projects/" + encode(pathWithNamespace)), GitLabProject.class);
    }

    /**
     * Failed pipelines updated since {@code updatedAfter}.
     *
     * <p>{@code username} is applied server-side because the list response carries no user field.
     * Glob refs cannot be expressed here, so the caller filters those client-side.
     */
    public List<GitLabPipeline> failedPipelines(long projectId,
                                                Instant updatedAfter,
                                                String username,
                                                int perPage) throws IOException, InterruptedException {
        List<Map.Entry<String, String>> params = new ArrayList<>();
        params.add(Map.entry("status", "failed"));
        params.add(Map.entry("updated_after", DateTimeFormatter.ISO_INSTANT.format(updatedAfter)));
        params.add(Map.entry("order_by", "updated_at"));
        params.add(Map.entry("sort", "desc"));
        params.add(Map.entry("per_page", String.valueOf(perPage)));
        if (username != null && !username.isBlank()) {
            params.add(Map.entry("username", username));
        }

        String body = request("/projects/" + projectId + "/pipelines" + query(params));
        List<GitLabPipeline> pipelines = gson.fromJson(body, PIPELINE_LIST);
        return pipelines == null ? List.of() : pipelines;
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
                .timeout(REQUEST_TIMEOUT)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        if (status == 401 || status == 403) {
            throw new GitLabAuthException(
                    "GitLab rejected the token (HTTP " + status + "). Check that it is valid and has the read_api scope.");
        }
        if (status == 404) {
            throw new GitLabHttpException(status, "Not found: " + path);
        }
        LOG.debug("GitLab " + status + " for " + path + ": " + truncate(response.body()));
        throw new GitLabHttpException(status, "GitLab returned HTTP " + status + " for " + path);
    }

    private static String query(List<Map.Entry<String, String>> params) {
        StringBuilder sb = new StringBuilder("?");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(encode(params.get(i).getKey())).append('=').append(encode(params.get(i).getValue()));
        }
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}
