package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.hugopalma.gitlabpipelinenotifier.watch.PipelinePoller;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

public class SettingsConfigurable implements Configurable {

    private SettingsComponent component;

    /**
     * The token lives in the password safe, not in {@link Settings}, so it is loaded and saved
     * separately from the rest of the form. Cached here so {@link #isModified()} can compare against
     * what was actually stored without hitting the keychain on every keystroke.
     */
    private String loadedToken = "";
    private String loadedTokenHost = "";

    @Override
    public @Nls String getDisplayName() {
        return "GitLab Pipeline Notifier";
    }

    @Override
    public @Nullable JComponent getPreferredFocusedComponent() {
        return component == null ? null : component.getPreferredFocusedComponent();
    }

    @Override
    public @Nullable JComponent createComponent() {
        component = new SettingsComponent();
        return component.getPanel();
    }

    @Override
    public boolean isModified() {
        Settings.State state = Settings.getInstance().getState();
        return !Objects.equals(component.getGitlabHost(), nullToEmpty(state.gitlabHost))
                || !Objects.equals(component.getToken(), loadedToken)
                || component.getPollIntervalSeconds() != state.pollIntervalSeconds
                || component.isWatchGitRemotes() != state.watchGitRemotes
                || !Objects.equals(component.getExtraProjectPaths(), state.extraProjectPaths)
                || component.isNotifyOwnFailures() != state.notifyOwnFailures
                || component.isOwnStickyBalloon() != state.ownStickyBalloon
                || component.isOwnSystemNotification() != state.ownSystemNotification
                || component.isOwnModalDialog() != state.ownModalDialog
                || component.isAlertOnRetries() != state.alertOnRetries
                || rulesModified(state.rules);
    }

    private boolean rulesModified(List<NotificationRule> stored) {
        List<NotificationRule> current = component.getRules();
        if (current.size() != stored.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            NotificationRule a = current.get(i);
            NotificationRule b = stored.get(i);
            if (a.enabled != b.enabled
                    || !Objects.equals(a.username, b.username)
                    || !Objects.equals(a.refGlob, b.refGlob)
                    || !Objects.equals(a.sources, b.sources)
                    || a.stickyBalloon != b.stickyBalloon
                    || a.systemNotification != b.systemNotification
                    || a.modalDialog != b.modalDialog) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset() {
        Settings.State state = Settings.getInstance().getState();
        component.setGitlabHost(state.gitlabHost);
        component.setPollIntervalSeconds(state.pollIntervalSeconds);
        component.setWatchGitRemotes(state.watchGitRemotes);
        component.setExtraProjectPaths(state.extraProjectPaths);
        component.setNotifyOwnFailures(state.notifyOwnFailures);
        component.setOwnStickyBalloon(state.ownStickyBalloon);
        component.setOwnSystemNotification(state.ownSystemNotification);
        component.setOwnModalDialog(state.ownModalDialog);
        component.setAlertOnRetries(state.alertOnRetries);
        component.setRules(state.rules);

        loadToken(nullToEmpty(state.gitlabHost));
    }

    /** Keychain reads block, so they happen off the EDT and the field is filled in afterwards. */
    private void loadToken(String host) {
        loadedTokenHost = host;
        loadedToken = "";
        component.setToken("");
        if (host.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String stored = TokenStore.get(host);
            String token = stored == null ? "" : stored;
            SwingUtilities.invokeLater(() -> {
                // The dialog may have been closed and re-opened while we were reading.
                if (component != null && host.equals(loadedTokenHost)) {
                    loadedToken = token;
                    component.setToken(token);
                }
            });
        });
    }

    @Override
    public void apply() {
        Settings.State state = Settings.getInstance().getState();

        String previousHost = nullToEmpty(state.gitlabHost);
        String newHost = component.getGitlabHost();

        state.gitlabHost = newHost;
        state.pollIntervalSeconds = component.getPollIntervalSeconds();
        state.watchGitRemotes = component.isWatchGitRemotes();
        state.extraProjectPaths = component.getExtraProjectPaths();
        state.notifyOwnFailures = component.isNotifyOwnFailures();
        state.ownStickyBalloon = component.isOwnStickyBalloon();
        state.ownSystemNotification = component.isOwnSystemNotification();
        state.ownModalDialog = component.isOwnModalDialog();
        state.alertOnRetries = component.isAlertOnRetries();
        state.rules = component.getRules();

        String token = component.getToken();
        loadedToken = token;
        loadedTokenHost = newHost;
        ApplicationManager.getApplication().executeOnPooledThread(() -> TokenStore.set(newHost, token));

        // Watermarks and the cached username are keyed to the old instance and its user; keeping
        // them across a host change would silently suppress the first alerts from the new one.
        if (!previousHost.equals(newHost)) {
            NotifierState.getInstance().reset();
        }

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                PipelinePoller.getInstance(project).restart();
            }
        }
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
