package org.hugopalma.pipelinenotifier.watch;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extracts the server host and {@code group/subgroup/project} path from a git remote URL.
 *
 * <p>Handles the three shapes git remotes actually come in:
 * <ul>
 *   <li>{@code https://gitlab.com/group/sub/proj.git}</li>
 *   <li>{@code ssh://git@gitlab.com:2222/group/sub/proj.git}</li>
 *   <li>{@code git@gitlab.com:group/sub/proj.git} (scp-like, which is not a valid URI)</li>
 * </ul>
 *
 * <p>Returns {@code null} for anything it cannot make sense of; callers filter by host anyway.
 */
public final class RemoteUrlParser {

    private static final Pattern SCP_LIKE = Pattern.compile("^(?:([^@/]+)@)?([^:/]+):(.+)$");

    private RemoteUrlParser() {
    }

    public static RemoteLocation parse(String remoteUrl) {
        if (remoteUrl == null) {
            return null;
        }
        String url = remoteUrl.trim();
        if (url.isEmpty()) {
            return null;
        }

        String host;
        String rawPath;
        if (url.contains("://")) {
            URI uri;
            try {
                uri = new URI(url);
            } catch (Exception e) {
                return null;
            }
            host = uri.getHost();
            rawPath = uri.getPath();
            if (host == null || rawPath == null) {
                return null;
            }
        } else {
            // scp-like syntax has no scheme and uses ':' to separate host from path
            Matcher matcher = SCP_LIKE.matcher(url);
            if (!matcher.matches()) {
                return null;
            }
            host = matcher.group(2);
            rawPath = matcher.group(3);
        }

        String path = trimSlashes(rawPath);
        if (path.endsWith(".git")) {
            path = path.substring(0, path.length() - 4);
        }
        path = trimSlashes(path);

        if (host.isBlank() || !isValidProjectPath(path)) {
            return null;
        }
        return new RemoteLocation(host.toLowerCase(Locale.ROOT), path);
    }

    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z0-9_.-]+");

    /**
     * Whether {@code path} is a plausible {@code group/.../project}: at least two segments of the
     * characters GitLab and GitHub allow in project paths. The path ends up in API request URLs and
     * comes from a git remote (which a cloned repository controls) or from settings, so anything
     * else - {@code ?}, {@code #}, {@code %}, {@code ..} - is refused rather than passed on.
     */
    public static boolean isValidProjectPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String[] segments = path.split("/", -1);
        if (segments.length < 2) {
            return false;
        }
        for (String segment : segments) {
            if (segment.equals(".") || segment.equals("..") || !PATH_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }

    /** Normalises a configured server base URL to a bare host, for comparison against remotes. */
    public static String hostOf(String serverUrl) {
        if (serverUrl == null) {
            return null;
        }
        String trimmed = serverUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        if (trimmed.isEmpty()) {
            return null;
        }
        String withScheme = trimmed.contains("://") ? trimmed : "https://" + trimmed;
        try {
            String host = new URI(withScheme).getHost();
            return host == null ? null : host.toLowerCase(Locale.ROOT);
        } catch (Exception e) {
            return null;
        }
    }

    private static String trimSlashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '/') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '/') {
            end--;
        }
        return value.substring(start, end);
    }
}
