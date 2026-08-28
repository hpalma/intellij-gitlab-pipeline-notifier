package org.hugopalma.gitlabpipelinenotifier.notify;

import com.intellij.ide.BrowserUtil;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.project.Project;
import com.intellij.ui.AppIcon;
import com.intellij.ui.SystemNotifications;
import org.hugopalma.gitlabpipelinenotifier.settings.SettingsConfigurable;
import org.hugopalma.gitlabpipelinenotifier.watch.AlertChannels;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fans a failure out to the alert channels a rule asked for.
 *
 * <p>The three channels are complementary rather than redundant:
 * <ul>
 *   <li>the balloon is what you see when you are looking at the IDE;</li>
 *   <li>{@link SystemNotifications} no-ops while the IDE is focused and fires when it is not, so it
 *       covers the case where you have tabbed away;</li>
 *   <li>the application-icon badge persists after both have gone, so a failure that arrived while
 *       you were at lunch is still visible when you come back.</li>
 * </ul>
 */
@Service(Service.Level.PROJECT)
public final class FailureAlerter {

    /** Must match the {@code notificationGroup} id registered in plugin.xml. */
    public static final String NOTIFICATION_GROUP = "GitLab Pipeline Notifier";

    private final Project project;
    private final AtomicInteger outstanding = new AtomicInteger();
    private volatile PipelineFailureDialog openDialog;

    public FailureAlerter(Project project) {
        this.project = project;
    }

    public static FailureAlerter getInstance(Project project) {
        return project.getService(FailureAlerter.class);
    }

    /** Called from the polling thread. */
    public void alert(PipelineFailure failure, AlertChannels channels) {
        if (!channels.any()) {
            return;
        }

        if (channels.stickyBalloon()) {
            showBalloon(failure);
        }
        if (channels.systemNotification()) {
            showSystemNotification(failure);
        }

        // The badge and attention request accompany any visual channel - they are the part that
        // survives the balloon being dismissed or the system notification being swiped away.
        bumpAppIcon();

        if (channels.modalDialog()) {
            showDialog(failure);
        }
    }

    private void showBalloon(PipelineFailure failure) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(failure.title(), failure.htmlBody(), NotificationType.ERROR)
                .setImportant(true)
                .addAction(NotificationAction.createSimple("Open in GitLab", () -> {
                    BrowserUtil.browse(failure.pipeline().webUrl());
                    clearAppIcon();
                }))
                .addAction(NotificationAction.createSimple("Settings...", this::openSettings))
                .whenExpired(this::clearAppIcon)
                .notify(project);
    }

    private void showSystemNotification(PipelineFailure failure) {
        // Suppressed by the platform while the IDE window is focused, and honours the user's
        // "Enable system notifications" setting - so it is safe to call unconditionally.
        SystemNotifications.getInstance().notify(
                NOTIFICATION_GROUP,
                failure.title(),
                failure.plainSummary(),
                () -> {
                    BrowserUtil.browse(failure.pipeline().webUrl());
                    clearAppIcon();
                });
    }

    private void bumpAppIcon() {
        int count = outstanding.incrementAndGet();
        AppIcon appIcon = AppIcon.getInstance();
        appIcon.setErrorBadge(project, String.valueOf(count));
        appIcon.requestAttention(project, true);
    }

    private void clearAppIcon() {
        outstanding.set(0);
        AppIcon.getInstance().setErrorBadge(project, null);
    }

    private void showDialog(PipelineFailure failure) {
        ApplicationManager.getApplication().invokeLater(() -> {
            PipelineFailureDialog existing = openDialog;
            if (existing != null && existing.isShowing()) {
                // Several pipelines can fail within one poll; grow the open dialog rather than
                // stacking a separate modal window per failure.
                existing.add(failure);
                return;
            }

            PipelineFailureDialog dialog = new PipelineFailureDialog(project, failure);
            openDialog = dialog;
            try {
                dialog.show();
            } finally {
                openDialog = null;
                clearAppIcon();
            }
        }, project.getDisposed());
    }

    /** Notifies once that polling has stopped, rather than looping on a token the user must fix. */
    public void notifyPollingStopped(String reason) {
        NotificationGroupManager.getInstance()
                .getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification("GitLab pipeline notifier stopped", reason, NotificationType.ERROR)
                .setImportant(true)
                .addAction(NotificationAction.createSimple("Configure...", this::openSettings))
                .notify(project);
    }

    private void openSettings() {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, SettingsConfigurable.class);
    }
}
