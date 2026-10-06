package org.hugopalma.pipelinenotifier.provider;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Plumbing shared by the hand-rolled REST clients. Deliberately no HTTP dependency: shipping an
 * HTTP stack inside a plugin classloader invites conflicts with the platform's own.
 */
public final class HttpSupport {

    public static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(20);

    private HttpSupport() {
    }

    public static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                // The token header is re-sent on redirects, including cross-host ones, and none of
                // the supported APIs needs them.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /**
     * The body of a 2xx response; anything else becomes the matching exception.
     *
     * @param service     name used in messages, e.g. "GitLab"
     * @param authFailure message for a 401/403, which should say what the token needs
     */
    public static String requireSuccess(HttpResponse<String> response, String path, String service, String authFailure) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return response.body();
        }
        if (status == 401 || status == 403) {
            throw new CiAuthException(service + " rejected the token (HTTP " + status + "). " + authFailure);
        }
        if (status >= 300 && status < 400) {
            throw new CiHttpException(status, service + " redirected the request (HTTP " + status
                    + "). Check that the " + service + " URL in settings is correct and uses the right scheme.");
        }
        if (status == 404) {
            throw new CiHttpException(status, "Not found: " + path);
        }
        throw new CiHttpException(status, service + " returned HTTP " + status + " for " + path);
    }

    public static String query(List<Map.Entry<String, String>> params) {
        StringBuilder sb = new StringBuilder("?");
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                sb.append('&');
            }
            sb.append(encode(params.get(i).getKey())).append('=').append(encode(params.get(i).getValue()));
        }
        return sb.toString();
    }

    public static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public static String truncate(String body) {
        if (body == null) {
            return "";
        }
        return body.length() <= 500 ? body : body.substring(0, 500);
    }
}
