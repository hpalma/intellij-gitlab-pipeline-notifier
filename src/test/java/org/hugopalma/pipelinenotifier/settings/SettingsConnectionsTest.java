package org.hugopalma.pipelinenotifier.settings;

import org.hugopalma.pipelinenotifier.github.GitHubProvider;
import org.hugopalma.pipelinenotifier.gitlab.GitLabProvider;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Upgrade behaviour: settings written before connections existed must keep working. */
public class SettingsConnectionsTest {

    private static final CiProvider GITLAB = CiProviders.find(GitLabProvider.ID);
    private static final CiProvider GITHUB = CiProviders.find(GitHubProvider.ID);

    @Test
    @SuppressWarnings("deprecation")
    public void legacyGitlabSettingsSeedTheGitlabConnection() {
        Settings.State state = new Settings.State();
        state.gitlabHost = "https://git.example.com";
        state.extraProjectPaths = List.of("g/p");

        Settings.Connection gitlab = state.connectionFor(GITLAB);

        assertEquals("https://git.example.com", gitlab.host);
        assertEquals(List.of("g/p"), gitlab.extraProjectPaths);
        // Other providers start from their own defaults, never from GitLab's settings.
        assertEquals("https://github.com", state.connectionFor(GITHUB).host);
        assertEquals(List.of(), state.connectionFor(GITHUB).extraProjectPaths);
    }

    @Test
    @SuppressWarnings("deprecation")
    public void savedConnectionsWinOverLegacyFields() {
        Settings.State state = new Settings.State();
        state.gitlabHost = "https://old.example.com";
        state.connections.add(new Settings.Connection(GitLabProvider.ID, "https://new.example.com", List.of()));

        assertEquals("https://new.example.com", state.connectionFor(GITLAB).host);
        // GitLab was saved, but GitHub never was: it must not inherit GitLab's legacy host either.
        assertEquals("https://github.com", state.connectionFor(GITHUB).host);
    }

    @Test
    public void connectionsListsEveryRegisteredProviderInOrder() {
        List<Settings.Connection> all = new Settings.State().connections();

        assertEquals(CiProviders.all().size(), all.size());
        assertEquals(GitLabProvider.ID, all.get(0).provider);
        assertEquals(GitHubProvider.ID, all.get(1).provider);
    }

    @Test
    public void ruleSavedBeforeProvidersExistedBelongsToGitlab() {
        assertEquals(GitLabProvider.ID, new NotificationRule().provider);
    }
}
