package org.hugopalma.pipelinenotifier.provider;

import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
        if (status == 429 || (status == 403 && isRateLimited(response))) {
            Duration retryAfter = retryAfter(response);
            throw new CiRateLimitException(status, service + " rate limit exceeded for " + path, retryAfter);
        }
        if (status == 401) {
            throw new CiAuthException(service + " rejected the token (HTTP 401). " + authFailure);
        }
        if (status == 403) {
            // Not necessarily the token: it may only lack access to this one project. Whether that
            // adds up to a bad token is for the caller to judge across all of its projects.
            throw new CiHttpException(status, service + " denied access (HTTP 403) to " + path + ". " + authFailure);
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

    /** A 403 is a throttle rather than a refusal when the server says how long to wait or that the quota is spent. */
    private static boolean isRateLimited(HttpResponse<String> response) {
        return response.headers().firstValue("retry-after").isPresent()
                || "0".equals(response.headers().firstValue("x-ratelimit-remaining").orElse(""));
    }

    /** From {@code Retry-After} (seconds) or, failing that, the quota reset time; {@code null} if neither is usable. */
    static Duration retryAfter(HttpResponse<String> response) {
        Long seconds = parseLong(response.headers().firstValue("retry-after").orElse(null));
        if (seconds != null) {
            return Duration.ofSeconds(Math.max(seconds, 0));
        }
        Long resetEpoch = parseLong(response.headers().firstValue("x-ratelimit-reset").orElse(null));
        if (resetEpoch != null) {
            return Duration.between(Instant.now(), Instant.ofEpochSecond(resetEpoch)).abs();
        }
        return null;
    }

    private static Long parseLong(String value) {
        try {
            return value == null ? null : Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
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
