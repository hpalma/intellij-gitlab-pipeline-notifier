package org.hugopalma.gitlabpipelinenotifier.watch;

import java.net.URI;
import java.util.Locale;

/** Small guards for URLs that come from outside the plugin: user settings, git config, the server. */
public final class UrlSafety {

    private UrlSafety() {
    }

    /**
     * Strips {@code user:password@} from every URL in {@code text}, so remotes such as
     * {@code https://user:glpat-xxxx@gitlab.com/g/p.git} are safe to write to idea.log.
     */
    public static String redact(String text) {
        return text == null ? null : text.replaceAll("(?<=://)[^/@\\s]+@", "");
    }

    /**
     * Turns what the user typed into a base URL with an explicit http(s) scheme, no userinfo, query
     * or fragment, and no trailing slash. A missing scheme defaults to https, matching
     * {@link RemoteUrlParser#hostOf}.
     *
     * @throws IllegalArgumentException if the value cannot be a GitLab base URL
     */
    public static String normalizeBaseUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("GitLab URL is empty");
        }
        URI uri;
        try {
            uri = new URI(trimmed.contains("://") ? trimmed : "https://" + trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("GitLab URL is not valid", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            throw new IllegalArgumentException("GitLab URL must use http or https");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("GitLab URL has no host");
        }
        if (uri.getRawUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("GitLab URL must not contain credentials, a query or a fragment");
        }
        return trimmed.contains("://") ? trimmed : "https://" + trimmed;
    }

    /**
     * Whether a server-supplied link is safe to hand to the system browser: http(s) only, and on the
     * configured GitLab host rather than wherever the response said to go.
     */
    public static boolean isSafeToBrowse(String url, String configuredGitlabUrl) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))) {
                return false;
            }
            String expected = RemoteUrlParser.hostOf(configuredGitlabUrl);
            return expected != null && uri.getHost() != null && expected.equalsIgnoreCase(uri.getHost());
        } catch (Exception e) {
            return false;
        }
    }
}
