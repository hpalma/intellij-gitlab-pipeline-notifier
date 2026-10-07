package org.hugopalma.pipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.hugopalma.pipelinenotifier.watch.PipelinePoller;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;
import org.hugopalma.pipelinenotifier.watch.RemoteUrlParser;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

public class SettingsConfigurable implements Configurable {

    private SettingsComponent component;

    /**
     * Tokens live in the password safe, not in {@link Settings}, so they are loaded and saved
     * separately from the rest of the form. Cached here, by provider id, so {@link #isModified()}
     * can compare against what was actually stored without hitting the keychain on every keystroke.
     */
    private final Map<String, String> loadedTokens = new HashMap<>();
    private final Map<String, String> loadedTokenHosts = new HashMap<>();

    @Override
    public @Nls String getDisplayName() {
        return "Pipeline Notifier";
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
        return connectionsModified(state)
                || component.getPollIntervalSeconds() != state.pollIntervalSeconds
                || component.isWatchGitRemotes() != state.watchGitRemotes
                || component.isNotifyOwnFailures() != state.notifyOwnFailures
                || component.isOwnStickyBalloon() != state.ownStickyBalloon
                || component.isOwnSystemNotification() != state.ownSystemNotification
                || component.isOwnModalDialog() != state.ownModalDialog
                || component.isAlertOnRetries() != state.alertOnRetries
                || rulesModified(state.rules);
    }

    private boolean connectionsModified(Settings.State state) {
        for (CiProvider provider : CiProviders.all()) {
            ConnectionPanel panel = component.connection(provider.id());
            Settings.Connection stored = state.connectionFor(provider);
            if (!Objects.equals(panel.getHost(), nullToEmpty(stored.host))
                    || !Objects.equals(panel.getToken(), loadedTokens.getOrDefault(provider.id(), ""))
                    || !Objects.equals(panel.getExtraProjectPaths(), stored.extraProjectPaths)) {
                return true;
            }
        }
        return false;
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
                    || !Objects.equals(a.provider, b.provider)
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
        for (CiProvider provider : CiProviders.all()) {
            Settings.Connection connection = state.connectionFor(provider);
            ConnectionPanel panel = component.connection(provider.id());
            panel.setHost(connection.host);
            panel.setExtraProjectPaths(connection.extraProjectPaths);
            loadToken(provider, nullToEmpty(connection.host));
        }
        component.setPollIntervalSeconds(state.pollIntervalSeconds);
        component.setWatchGitRemotes(state.watchGitRemotes);
        component.setNotifyOwnFailures(state.notifyOwnFailures);
        component.setOwnStickyBalloon(state.ownStickyBalloon);
        component.setOwnSystemNotification(state.ownSystemNotification);
        component.setOwnModalDialog(state.ownModalDialog);
        component.setAlertOnRetries(state.alertOnRetries);
        component.setRules(state.rules);
    }

    /** Keychain reads block, so they happen off the EDT and the field is filled in afterwards. */
    private void loadToken(CiProvider provider, String host) {
        String id = provider.id();
        loadedTokenHosts.put(id, host);
        loadedTokens.put(id, "");
        component.connection(id).setToken("");
        if (host.isEmpty()) {
            return;
        }

        ApplicationManager.getApplication().executeOnPooledThread(() -> {
            String stored = TokenStore.get(id, host);
            String token = stored == null ? "" : stored;
            SwingUtilities.invokeLater(() -> {
                // The dialog may have been closed and re-opened while we were reading.
                if (component != null && host.equals(loadedTokenHosts.get(id))) {
                    loadedTokens.put(id, token);
                    component.connection(id).setToken(token);
                }
            });
        });
    }

    @Override
    public void apply() {
        Settings.State state = Settings.getInstance().getState();

        List<Settings.Connection> updated = new ArrayList<>();
        for (CiProvider provider : CiProviders.all()) {
            updated.add(applyConnection(state, provider));
        }
        state.connections = updated;

        state.pollIntervalSeconds = component.getPollIntervalSeconds();
        state.watchGitRemotes = component.isWatchGitRemotes();
        state.notifyOwnFailures = component.isNotifyOwnFailures();
        state.ownStickyBalloon = component.isOwnStickyBalloon();
        state.ownSystemNotification = component.isOwnSystemNotification();
        state.ownModalDialog = component.isOwnModalDialog();
        state.alertOnRetries = component.isAlertOnRetries();
        state.rules = component.getRules();

        for (Project project : ProjectManager.getInstance().getOpenProjects()) {
            if (!project.isDisposed()) {
                PipelinePoller.getInstance(project).restart();
            }
        }
    }

    /** Saves one provider's token and invalidates whatever bookkeeping its changes make stale. */
    private Settings.Connection applyConnection(Settings.State state, CiProvider provider) {
        String id = provider.id();
        ConnectionPanel panel = component.connection(id);

        String previousHost = nullToEmpty(state.connectionFor(provider).host);
        String newHost = panel.getHost();
        Settings.Connection connection = new Settings.Connection(id, newHost, panel.getExtraProjectPaths());

        String token = panel.getToken();
        boolean tokenChanged = !token.equals(loadedTokens.getOrDefault(id, ""));
        loadedTokens.put(id, token);
        loadedTokenHosts.put(id, newHost);
        // Only write what the user actually changed. The field is filled from the keychain
        // asynchronously, so a fast Apply can see it still empty: writing it back unconditionally
        // would wipe the stored token.
        boolean hostChanged = !previousHost.equals(newHost);
        if (tokenChanged || (hostChanged && !token.isEmpty())) {
            ApplicationManager.getApplication().executeOnPooledThread(() -> TokenStore.set(id, newHost, token));
        }

        String previousKey = connectionKey(id, previousHost);
        if (hostChanged) {
            // Watermarks and the cached username are keyed to the old server and its user; keeping
            // them across a host change would silently suppress the first alerts from the new one.
            if (previousKey != null) {
                NotifierState.getInstance().reset(previousKey);
            }
        } else if (tokenChanged && previousKey != null) {
            // A different token may belong to a different user; the cached username would make
            // "my failures" silently track the previous account.
            NotifierState.getInstance().setResolvedUsername(previousKey, null);
        }
        return connection;
    }

    private static String connectionKey(String providerId, String host) {
        String bare = RemoteUrlParser.hostOf(host);
        return bare == null ? null : RemoteProject.connectionKey(providerId, bare);
    }

    @Override
    public void disposeUIResources() {
        component = null;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
