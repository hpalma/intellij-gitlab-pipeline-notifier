package org.hugopalma.pipelinenotifier.settings;

import org.hugopalma.pipelinenotifier.github.GitHubProvider;
import org.hugopalma.pipelinenotifier.gitlab.GitLabProvider;
import org.hugopalma.pipelinenotifier.provider.CiProvider;
import org.hugopalma.pipelinenotifier.provider.CiProviders;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** Per-provider connection lookup and defaults. */
public class SettingsConnectionsTest {

    private static final CiProvider GITLAB = CiProviders.find(GitLabProvider.ID);
    private static final CiProvider GITHUB = CiProviders.find(GitHubProvider.ID);

    @Test
    public void unsavedProvidersGetTheirDefaults() {
        Settings.State state = new Settings.State();

        assertEquals("https://gitlab.com", state.connectionFor(GITLAB).host);
        assertEquals("https://github.com", state.connectionFor(GITHUB).host);
        assertEquals(List.of(), state.connectionFor(GITHUB).extraProjectPaths);
    }

    @Test
    public void savedConnectionWinsOverDefault() {
        Settings.State state = new Settings.State();
        state.connections.add(new Settings.Connection(GitLabProvider.ID, "https://git.example.com", List.of("g/p")));

        assertEquals("https://git.example.com", state.connectionFor(GITLAB).host);
        assertEquals(List.of("g/p"), state.connectionFor(GITLAB).extraProjectPaths);
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
    public void newRulesDefaultToGitlab() {
        assertEquals(GitLabProvider.ID, new NotificationRule().provider);
    }
}
