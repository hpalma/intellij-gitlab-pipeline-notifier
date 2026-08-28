package org.hugopalma.gitlabpipelinenotifier.watch;

import org.hugopalma.gitlabpipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.gitlabpipelinenotifier.settings.NotificationRule;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public final class RuleMatcher {

    private RuleMatcher() {
    }

    /**
     * Groups rules into the smallest set of API queries that can serve them.
     *
     * <p>Without this, N rules would mean N requests per project per poll; in practice most rules
     * share a username (or leave it blank), so this usually collapses to one or two.
     */
    public static List<PollQuery> planQueries(List<NotificationRule> rules) {
        Map<String, List<NotificationRule>> byUsername = new LinkedHashMap<>();
        for (NotificationRule rule : rules) {
            if (!rule.enabled) {
                continue;
            }
            String username = (rule.username == null || rule.username.isBlank()) ? null : rule.username.trim();
            byUsername.computeIfAbsent(username, k -> new ArrayList<>()).add(rule);
        }

        List<PollQuery> queries = new ArrayList<>();
        for (Map.Entry<String, List<NotificationRule>> entry : byUsername.entrySet()) {
            queries.add(new PollQuery(entry.getKey(), entry.getValue()));
        }
        return queries;
    }

    /**
     * Channels to fire for {@code pipeline}, or {@link AlertChannels#NONE} if no rule matches.
     *
     * <p>The username criterion is assumed to have been applied server-side by the query that
     * produced {@code pipeline}; only the ref glob and sources are re-checked here.
     */
    public static AlertChannels match(GitLabPipeline pipeline, List<NotificationRule> rules) {
        AlertChannels result = AlertChannels.NONE;
        for (NotificationRule rule : rules) {
            if (rule.enabled && matches(pipeline, rule)) {
                result = result.merge(AlertChannels.of(rule));
            }
        }
        return result;
    }

    private static boolean matches(GitLabPipeline pipeline, NotificationRule rule) {
        String glob = rule.refGlob == null ? "" : rule.refGlob.trim();
        if (!glob.isEmpty()) {
            String ref = pipeline.ref() == null ? "" : pipeline.ref();
            if (!globToRegex(glob).matcher(ref).matches()) {
                return false;
            }
        }

        List<String> sources = rule.sources.stream().filter(s -> s != null && !s.isBlank()).toList();
        return sources.isEmpty() || sources.contains(pipeline.source());
    }

    /**
     * Converts a git-ref glob to a regex.
     *
     * <p>A single star stops at a slash so {@code release/x} patterns do not swallow deeper paths;
     * a double star crosses separators. Everything else is quoted, so a ref pattern containing
     * regex metacharacters - a dot is common in tags like {@code v1.2.3} - is treated literally.
     */
    public static Pattern globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        int i = 0;
        while (i < glob.length()) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                sb.append(Pattern.quote(String.valueOf(c)));
            }
            i++;
        }
        sb.append('$');
        return Pattern.compile(sb.toString());
    }
}
