package org.hugopalma.gitlabpipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User-visible configuration. Roams between installations.
 *
 * <p>The GitLab personal access token is deliberately <em>not</em> stored here - see
 * {@link TokenStore}.
 */
@Service
@State(
        name = "org.hugopalma.gitlabpipelinenotifier.settings.Settings",
        storages = @Storage(value = "GitLabPipelineNotifier.xml", roamingType = RoamingType.DEFAULT),
        category = SettingsCategory.PLUGINS
)
public final class Settings implements PersistentStateComponent<Settings.State> {

    public static final String DEFAULT_HOST = "https://gitlab.com";
    public static final int DEFAULT_POLL_SECONDS = 60;
    public static final int MIN_POLL_SECONDS = 15;

    private final State state = new State();

    public static class State {
        /** Base URL of the GitLab instance, without a trailing slash or {@code /api/v4}. */
        public String gitlabHost = DEFAULT_HOST;

        /** How often to poll, in seconds. Clamped to {@link #MIN_POLL_SECONDS} when applied. */
        public int pollIntervalSeconds = DEFAULT_POLL_SECONDS;

        /** Watch the GitLab projects matching the git remotes of open IDE projects. */
        public boolean watchGitRemotes = true;

        /** Extra project paths to watch, e.g. {@code group/subgroup/project}. */
        @XCollection(elementName = "path")
        public List<String> extraProjectPaths = new ArrayList<>();

        /** Alert on pipelines triggered by the token's own user. */
        public boolean notifyOwnFailures = true;
        public boolean ownStickyBalloon = true;
        public boolean ownSystemNotification = true;
        public boolean ownModalDialog = false;

        /** Additional rules for other people's failures. */
        @XCollection(elementName = "rule")
        public List<NotificationRule> rules = new ArrayList<>();

        /**
         * Alert again when a pipeline that already failed is retried and fails again.
         *
         * <p>Off by default: the usual case is one alert per pipeline, however many times you poke
         * at it. Turning it on trades that quiet for knowing every time a retry does not help.
         */
        public boolean alertOnRetries = false;
    }

    @Override
    public @NotNull State getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull State state) {
        XmlSerializerUtil.copyBean(state, this.state);
    }

    public static Settings getInstance() {
        return ApplicationManager.getApplication().getService(Settings.class);
    }
}
