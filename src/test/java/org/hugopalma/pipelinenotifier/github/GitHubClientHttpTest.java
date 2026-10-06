package org.hugopalma.pipelinenotifier.github;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hugopalma.pipelinenotifier.provider.CiAuthException;
import org.hugopalma.pipelinenotifier.provider.CiHttpException;
import org.hugopalma.pipelinenotifier.provider.Page;
import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/** Drives {@link GitHubClient} against a loopback HTTP server, as {@code GitLabClientHttpTest} does for GitLab. */
public class GitHubClientHttpTest {

    private static final RemoteProject TARGET = new RemoteProject("github", "github.com", "octo/repo");

    private HttpServer server;
    private GitHubClient client;

    private volatile int nextStatus;
    private volatile String nextBody;
    private volatile String rateLimitRemaining;
    private volatile String lastRawPath;
    private volatile String lastQuery;
    private volatile String lastAuthHeader;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
        // A non-github.com host is treated as GitHub Enterprise Server, so the API lives under /api/v3.
        client = new GitHubClient("http://127.0.0.1:" + server.getAddress().getPort(), "tok-123");
    }

    @After
    public void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private void handle(HttpExchange exchange) throws IOException {
        lastRawPath = exchange.getRequestURI().getRawPath();
        lastQuery = exchange.getRequestURI().getRawQuery();
        lastAuthHeader = exchange.getRequestHeaders().getFirst("Authorization");

        byte[] bytes = nextBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (rateLimitRemaining != null) {
            exchange.getResponseHeaders().set("x-ratelimit-remaining", rateLimitRemaining);
        }
        exchange.sendResponseHeaders(nextStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    public void apiBaseIsSeparateHostForGithubComAndSubpathForEnterprise() {
        assertEquals("https://api.github.com", GitHubClient.apiBaseFor("https://github.com"));
        assertEquals("https://api.github.com", GitHubClient.apiBaseFor("github.com/"));
        assertEquals("https://ghe.example.com/api/v3", GitHubClient.apiBaseFor("https://ghe.example.com"));
    }

    @Test
    public void sendsBearerTokenAndResolvesCurrentUser() throws Exception {
        nextStatus = 200;
        nextBody = "{\"id\":1,\"login\":\"octocat\"}";

        assertEquals("octocat", client.currentUser());
        assertEquals("Bearer tok-123", lastAuthHeader);
        assertEquals("/api/v3/user", lastRawPath);
    }

    @Test
    public void unauthorizedIsAnAuthFailure() {
        nextStatus = 401;
        nextBody = "{}";

        assertThrows(CiAuthException.class, client::currentUser);
    }

    @Test
    public void forbiddenIsAnAuthFailure() {
        nextStatus = 403;
        nextBody = "{}";

        assertThrows(CiAuthException.class, client::currentUser);
    }

    @Test
    public void exhaustedRateLimitIsTransientNotAnAuthFailure() {
        // GitHub reports quota exhaustion as 403; pausing polling over it would be wrong.
        nextStatus = 403;
        nextBody = "{}";
        rateLimitRemaining = "0";

        CiHttpException e = assertThrows(CiHttpException.class, client::currentUser);
        assertEquals(403, e.getStatus());
    }

    @Test
    public void failedRunsSendsFilterAndWindowParamsAndMapsRuns() throws Exception {
        nextStatus = 200;
        nextBody = """
                {"total_count": 1, "workflow_runs": [{
                  "id": 555, "name": "CI", "display_title": "Fix the thing",
                  "head_branch": "feature/x", "head_sha": "0123456789abcdef",
                  "event": "pull_request", "status": "completed", "conclusion": "failure",
                  "html_url": "https://github.com/octo/repo/actions/runs/555",
                  "updated_at": "2026-08-21T10:05:00Z", "run_attempt": 1,
                  "actor": {"login": "original"}, "triggering_actor": {"login": "rerunner"}
                }]}
                """;

        Page<PipelineRun> page = client.failedRuns(TARGET, Instant.parse("2026-08-21T10:00:00Z"), "octocat", 100, 2);

        assertEquals("/api/v3/repos/octo/repo/actions/runs", lastRawPath);
        assertTrue(lastQuery.contains("status=failure"));
        assertTrue(lastQuery.contains("per_page=100"));
        assertTrue(lastQuery.contains("page=2"));
        assertTrue(lastQuery.contains("actor=octocat"));
        // The window is widened by the lookback, because the server can only filter by creation time.
        assertTrue(lastQuery.contains("created=%3E%3D2026-08-20T22%3A00%3A00Z"));

        assertEquals(1, page.items().size());
        PipelineRun run = page.items().getFirst();
        assertEquals(555L, run.id());
        assertEquals("feature/x", run.ref());
        assertEquals("pull_request", run.source());
        assertEquals("Fix the thing", run.name());
        assertEquals("rerunner", run.triggeredBy());
        assertEquals(Instant.parse("2026-08-21T10:05:00Z"), run.updatedAt());
        assertFalse(page.hasMore());
    }

    @Test
    public void failedRunsDropsRunsLastUpdatedBeforeTheWatermark() throws Exception {
        // Created inside the lookback but finished failing before 'since': already handled.
        nextStatus = 200;
        nextBody = """
                {"total_count": 2, "workflow_runs": [
                  {"id": 2, "head_branch": "main", "updated_at": "2026-08-21T10:30:00Z"},
                  {"id": 1, "head_branch": "main", "updated_at": "2026-08-21T09:00:00Z"}
                ]}
                """;

        Page<PipelineRun> page = client.failedRuns(TARGET, Instant.parse("2026-08-21T10:00:00Z"), null, 2, 1);

        assertEquals(List.of(2L), page.items().stream().map(PipelineRun::id).toList());
        // The raw page was full, so more may follow even though filtering shortened it.
        assertTrue(page.hasMore());
        assertFalse(lastQuery.contains("actor"));
    }

    @Test
    public void failedJobsKeepsOnlyFailedOnes() throws Exception {
        nextStatus = 200;
        nextBody = """
                {"total_count": 3, "jobs": [
                  {"id": 1, "name": "build", "status": "completed", "conclusion": "success"},
                  {"id": 2, "name": "test", "status": "completed", "conclusion": "failure"},
                  {"id": 3, "name": "e2e", "status": "completed", "conclusion": "timed_out"}
                ]}
                """;

        List<String> jobs = client.failedJobs(TARGET, new PipelineRun(555, "main", null, null, null, null, null, null));

        assertEquals(List.of("test", "e2e"), jobs);
        assertEquals("/api/v3/repos/octo/repo/actions/runs/555/jobs", lastRawPath);
    }

    @Test
    public void listProjectsFiltersBySearchClientSide() throws Exception {
        nextStatus = 200;
        nextBody = "[{\"id\":1,\"full_name\":\"octo/alpha\"},{\"id\":2,\"full_name\":\"octo/Beta\"}]";

        Page<String> page = client.listProjects("BET", 100, 1);

        assertEquals(List.of("octo/Beta"), page.items());
        assertFalse(page.hasMore());
        assertEquals("/api/v3/user/repos", lastRawPath);
    }

    @Test
    public void emptyBodyDoesNotThrow() throws Exception {
        nextStatus = 200;
        nextBody = "";

        assertNull(client.currentUser());
        assertTrue(client.failedRuns(TARGET, Instant.parse("2026-08-21T10:00:00Z"), null, 100, 1).items().isEmpty());
    }
}
