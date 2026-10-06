package org.hugopalma.pipelinenotifier.settings;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.SettingsCategory;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import com.intellij.util.xmlb.annotations.XCollection;
import org.hugopalma.pipelinenotifier.gitlab.GitLabProvider;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * User-visible configuration. Roams between installations.
 *
 * <p>Access tokens are deliberately <em>not</em> stored here - see {@link TokenStore}.
 */
@Service
@State(
        name = "org.hugopalma.pipelinenotifier.settings.Settings",
        storages = @Storage(value = "GitLabPipelineNotifier.xml", roamingType = RoamingType.DEFAULT),
        category = SettingsCategory.PLUGINS
)
public final class Settings implements PersistentStateComponent<Settings.State> {

    public static final String DEFAULT_HOST = "https://gitlab.com";
    public static final int DEFAULT_POLL_SECONDS = 60;
    public static final int MIN_POLL_SECONDS = 15;

    private final State state = new State();

    /** The server a provider is used against, plus the projects to watch there regardless of git remotes. */
    public static class Connection {
        /** {@link CiProvider#id()}. */
        public String provider;

        /** Base URL of the server, without a trailing slash or API path. */
        public String host;

        /** Extra project paths to watch, e.g. {@code group/subgroup/project}. */
        @XCollection(elementName = "path")
        public List<String> extraProjectPaths = new ArrayList<>();

        public Connection() {
        }

        public Connection(String provider, String host, List<String> extraProjectPaths) {
            this.provider = provider;
            this.host = host;
            this.extraProjectPaths = new ArrayList<>(extraProjectPaths);
        }

        public Connection(Connection other) {
            this(other.provider, other.host, other.extraProjectPaths);
        }
    }

    public static class State {
        /**
         * One entry per provider the user has opened settings for. Empty until then; read it through
         * {@link #connections()}, which also covers settings saved before there were other providers.
         */
        @XCollection(elementName = "connection")
        public List<Connection> connections = new ArrayList<>();

        /** @deprecated GitLab-only setting from before {@link #connections}; only read as a fallback. */
        @Deprecated
        public String gitlabHost = DEFAULT_HOST;

        /** @deprecated see {@link #gitlabHost}. */
        @Deprecated
        @XCollection(elementName = "path")
        public List<String> extraProjectPaths = new ArrayList<>();

        /** How often to poll, in seconds. Clamped to {@link #MIN_POLL_SECONDS} when applied. */
        public int pollIntervalSeconds = DEFAULT_POLL_SECONDS;

        /** Watch the GitLab projects matching the git remotes of open IDE projects. */
        public boolean watchGitRemotes = true;

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

        /**
         * The connection configured for every known provider, in registry order. A provider with
         * nothing saved yet gets its default host - or, for GitLab, whatever the pre-connections
         * settings held, so an upgrade keeps working without the user touching anything.
         */
        public List<Connection> connections() {
            List<Connection> result = new ArrayList<>();
            for (CiProvider provider : CiProviders.all()) {
                result.add(connectionFor(provider));
            }
            return result;
        }

        public Connection connectionFor(CiProvider provider) {
            for (Connection connection : connections) {
                if (provider.id().equals(connection.provider)) {
                    return connection;
                }
            }
            if (connections.isEmpty() && GitLabProvider.ID.equals(provider.id())) {
                return new Connection(provider.id(), gitlabHost, extraProjectPaths);
            }
            return new Connection(provider.id(), provider.defaultHost(), List.of());
        }
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
