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
        return own
                ? "Your pipeline failed: " + remote.path()
                : "Pipeline failed: " + remote.path();
    }

    /** Single-line summary for the system notification, which renders no markup. */
    public String plainSummary() {
        StringBuilder sb = new StringBuilder(remote.path());
        sb.append(" · ").append(pipeline.ref());
        if (triggeredBy != null) {
            sb.append(" · ").append(triggeredBy);
        }
        if (!failedJobs.isEmpty()) {
            sb.append(" · ").append(String.join(", ", failedJobs));
        }
        return sb.toString();
    }

    /** Notification balloons render a restricted subset of HTML. */
    public String htmlBody() {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>").append(escape(pipeline.ref())).append("</b>");
        sb.append(" · #").append(pipeline.id());
        sb.append(" · ").append(escape(pipeline.shortSha()));
        if (triggeredBy != null) {
            sb.append("<br/>Triggered by ").append(escape(triggeredBy));
        }
        if (pipeline.source() != null) {
            sb.append(" (").append(escape(pipeline.source())).append(")");
        }
        if (!failedJobs.isEmpty()) {
            sb.append("<br/>Failed: ").append(escape(String.join(", ", failedJobs)));
        }
        return sb.toString();
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
