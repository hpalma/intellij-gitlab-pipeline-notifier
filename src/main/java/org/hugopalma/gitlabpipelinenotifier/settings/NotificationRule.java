package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.util.xmlb.annotations.XCollection;

import java.util.ArrayList;
import java.util.List;

/**
 * One "notify me about other failures too" rule. A null/blank criterion means "any".
 *
 * <p>{@link #username} is applied server-side via the {@code username} query parameter, because the
 * pipeline <em>list</em> response does not carry the triggering user. {@link #refGlob} and
 * {@link #sources} are matched client-side.
 *
 * <p>Public mutable fields are deliberate: this is serialised by the platform's XML serializer.
 */
public class NotificationRule {

    public boolean enabled = true;
    public String username;
    public String refGlob;

    @XCollection(elementName = "source")
    public List<String> sources = new ArrayList<>();

    public boolean stickyBalloon = true;
    public boolean systemNotification = true;
    public boolean modalDialog = false;

    public NotificationRule() {
    }

    public NotificationRule(NotificationRule other) {
        this.enabled = other.enabled;
        this.username = other.username;
        this.refGlob = other.refGlob;
        this.sources = new ArrayList<>(other.sources);
        this.stickyBalloon = other.stickyBalloon;
        this.systemNotification = other.systemNotification;
        this.modalDialog = other.modalDialog;
    }

    /** Short human-readable summary used in the settings table. */
    public String describe() {
        List<String> parts = new ArrayList<>();
        if (username != null && !username.isBlank()) {
            parts.add("by " + username);
        }
        if (refGlob != null && !refGlob.isBlank()) {
            parts.add("on " + refGlob);
        }
        if (!sources.isEmpty()) {
            parts.add("from " + String.join("/", sources));
        }
        return parts.isEmpty() ? "any failed pipeline" : String.join(" ", parts);
    }

    /** Human-readable list of the enabled alert channels, for the settings table. */
    public String describeChannels() {
        List<String> parts = new ArrayList<>();
        if (stickyBalloon) {
            parts.add("Balloon");
        }
        if (systemNotification) {
            parts.add("System");
        }
        if (modalDialog) {
            parts.add("Dialog");
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }
}
