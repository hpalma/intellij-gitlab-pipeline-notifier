package org.hugopalma.gitlabpipelinenotifier.gitlab;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabProject;
import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabUser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.Assert.*;

/**
 * Drives {@link GitLabClient} against a real loopback HTTP server, exercising the request/response
 * handling that {@link GitLabClientTest} (pure Gson parsing) does not: headers sent, path/query
 * encoding, and the HTTP status-to-exception mapping in {@code GitLabClient.request()}.
 */
public class GitLabClientHttpTest {

    private HttpServer server;
    private GitLabClient client;

    private volatile int nextStatus;
    private volatile String nextBody;
    private volatile String lastRawPath;
    private volatile String lastQuery;
    private volatile String lastTokenHeader;

    @Before
    public void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.setExecutor(null);
        server.start();
        client = new GitLabClient("http://127.0.0.1:" + server.getAddress().getPort(), "tok-123");
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
        lastTokenHeader = exchange.getRequestHeaders().getFirst("PRIVATE-TOKEN");

        byte[] bytes = nextBody.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(nextStatus, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    @Test
    public void sendsPrivateTokenHeaderAndAcceptsJson() throws Exception {
        nextStatus = 200;
        nextBody = "{\"id\":1,\"username\":\"hugo\",\"name\":\"Hugo\"}";

        GitLabUser user = client.currentUser();

        assertEquals("hugo", user.username());
        assertEquals("tok-123", lastTokenHeader);
        assertEquals("/api/v4/user", lastRawPath);
    }

    @Test
    public void mapsUnauthorizedToAuthException() {
        nextStatus = 401;
        nextBody = "{}";

        assertThrows(GitLabAuthException.class, client::currentUser);
    }

    @Test
    public void mapsForbiddenToAuthException() {
        nextStatus = 403;
        nextBody = "{}";

        assertThrows(GitLabAuthException.class, client::currentUser);
    }

    @Test
    public void mapsNotFoundToHttpExceptionWithStatus() {
        nextStatus = 404;
        nextBody = "";

        GitLabHttpException e = assertThrows(GitLabHttpException.class, client::currentUser);
        assertEquals(404, e.getStatus());
    }

    @Test
    public void mapsGenericServerErrorToHttpExceptionWithStatus() {
        nextStatus = 500;
        nextBody = "boom";

        GitLabHttpException e = assertThrows(GitLabHttpException.class, client::currentUser);
        assertEquals(500, e.getStatus());
    }

    @Test
    public void mapsTooManyRequestsToHttpExceptionWithStatus() {
        // No special-casing today, but a 429 must not be silently swallowed or misclassified
        // as an auth failure - it is transient, not a bad token.
        nextStatus = 429;
        nextBody = "";

        GitLabHttpException e = assertThrows(GitLabHttpException.class, client::currentUser);
        assertEquals(429, e.getStatus());
    }

    @Test
    public void findProjectPercentEncodesSlashesInPath() throws Exception {
        nextStatus = 200;
        nextBody = "{\"id\":99,\"path_with_namespace\":\"group/sub/proj\"}";

        GitLabProject project = client.findProject("group/sub/proj");

        assertEquals(99L, project.id());
        assertEquals("/api/v4/projects/group%2Fsub%2Fproj", lastRawPath);
    }

    @Test
    public void failedPipelinesSendsPaginationAndFilterParams() throws Exception {
        nextStatus = 200;
        nextBody = "[]";

        client.failedPipelines(42, Instant.parse("2026-01-01T00:00:00Z"), "hugo", 100, 3);

        assertEquals("/api/v4/projects/42/pipelines", lastRawPath);
        assertTrue(lastQuery.contains("status=failed"));
        assertTrue(lastQuery.contains("per_page=100"));
        assertTrue(lastQuery.contains("page=3"));
        assertTrue(lastQuery.contains("username=hugo"));
    }

    @Test
    public void failedPipelinesOmitsUsernameParamWhenBlank() throws Exception {
        nextStatus = 200;
        nextBody = "[]";

        client.failedPipelines(42, Instant.parse("2026-01-01T00:00:00Z"), null, 20, 1);

        assertTrue(lastQuery.contains("page=1"));
        assertFalse(lastQuery.contains("username"));
    }

    @Test
    public void emptyResponseBodyParsesToNullRatherThanThrowing() throws Exception {
        // Gson.fromJson("", ...) returns null for a blank body without throwing; callers of
        // currentUser()/findProject() must be prepared for that (see GitLabClient#currentUser).
        nextStatus = 200;
        nextBody = "";

        assertNull(client.currentUser());
    }
}
