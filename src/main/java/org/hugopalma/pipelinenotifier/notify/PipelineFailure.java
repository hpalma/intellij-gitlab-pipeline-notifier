package org.hugopalma.pipelinenotifier.notify;

import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;

import java.util.List;

/** Everything the alert channels need, resolved once so each channel does not re-query. */
public record PipelineFailure(
        RemoteProject remote,
        PipelineRun pipeline,
        List<String> failedJobs,
        String triggeredBy,
        boolean own) {

    /** "GitLab", "GitHub", ... - for text that names where the failure happened. */
    public String providerName() {
        return CiProviders.displayName(remote.provider());
    }

    public String title() {
        String path = sanitize(remote.path(), MAX_PATH);
        return own ? "Your pipeline failed: " + path : "Pipeline failed: " + path;
    }

    /** Single-line summary for the system notification, which renders no markup. */
    public String plainSummary() {
        StringBuilder sb = new StringBuilder(sanitize(remote.path(), MAX_PATH));
        sb.append(" · ").append(sanitize(pipeline.ref(), MAX_NAME));
        if (triggeredBy != null) {
            sb.append(" · ").append(sanitize(triggeredBy, MAX_NAME));
        }
        if (!failedJobs.isEmpty()) {
            sb.append(" · ").append(jobSummary());
        }
        return sb.toString();
    }

    /** Notification balloons render a restricted subset of HTML. */
    public String htmlBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(escape(sanitize(pipeline.ref(), MAX_NAME))).append("</b>");
        sb.append(" · #").append(pipeline.id());
        sb.append(" · ").append(escape(pipeline.shortSha()));
        if (triggeredBy != null) {
            sb.append("<br/>Triggered by ").append(escape(sanitize(triggeredBy, MAX_NAME)));
        }
        if (pipeline.source() != null) {
            sb.append(" (").append(escape(sanitize(pipeline.source(), MAX_NAME))).append(")");
        }
        if (!failedJobs.isEmpty()) {
            sb.append("<br/>Failed: ").append(escape(jobSummary()));
        }
        return sb.toString();
    }

    /** Job names, each cut short and the list capped: a workflow author controls them. */
    private String jobSummary() {
        List<String> shown = failedJobs.stream().limit(MAX_JOBS).map(job -> sanitize(job, MAX_NAME)).toList();
        String joined = String.join(", ", shown);
        return failedJobs.size() > MAX_JOBS ? joined + " and " + (failedJobs.size() - MAX_JOBS) + " more" : joined;
    }

    private static final int MAX_PATH = 120;
    private static final int MAX_NAME = 80;
    private static final int MAX_JOBS = 5;

    /**
     * Makes text from the server safe to show in an alert: no angle brackets (some notification
     * daemons render markup), no control or bidirectional-formatting characters (which can disguise
     * text), and a length cap so a hostile name cannot fill the alert with its own message.
     */
    static String sanitize(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(value.length(), maxLength + 1));
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean bidi = (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069')
                    || c == '\u200E' || c == '\u200F' || c == '\u061C';
            if (c == '<' || c == '>' || Character.isISOControl(c) || bidi) {
                continue;
            }
            sb.append(c);
        }
        String clean = sb.toString().strip();
        return clean.length() <= maxLength ? clean : clean.substring(0, maxLength) + "…";
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
