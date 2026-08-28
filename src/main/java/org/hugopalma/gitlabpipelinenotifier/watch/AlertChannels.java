package org.hugopalma.gitlabpipelinenotifier.watch;

import org.hugopalma.gitlabpipelinenotifier.settings.NotificationRule;

/** Which of the three alert channels a matched pipeline should fire. */
public record AlertChannels(boolean stickyBalloon, boolean systemNotification, boolean modalDialog) {

    public static final AlertChannels NONE = new AlertChannels(false, false, false);

    public static AlertChannels of(NotificationRule rule) {
        return new AlertChannels(rule.stickyBalloon, rule.systemNotification, rule.modalDialog);
    }

    public boolean any() {
        return stickyBalloon || systemNotification || modalDialog;
    }

    /** Union, so a pipeline matching two rules gets the loudest treatment either rule asked for. */
    public AlertChannels merge(AlertChannels other) {
        return new AlertChannels(
                stickyBalloon || other.stickyBalloon,
                systemNotification || other.systemNotification,
                modalDialog || other.modalDialog);
    }
}
