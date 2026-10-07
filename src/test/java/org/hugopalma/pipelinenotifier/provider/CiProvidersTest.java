package org.hugopalma.pipelinenotifier.provider;

import org.hugopalma.pipelinenotifier.github.GitHubProvider;
import org.hugopalma.pipelinenotifier.gitlab.GitLabProvider;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class CiProvidersTest {

    @Test
    public void findsRegisteredProvidersById() {
        assertEquals("GitLab", CiProviders.find(GitLabProvider.ID).displayName());
        assertEquals("GitHub", CiProviders.find(GitHubProvider.ID).displayName());
        assertNull(CiProviders.find("bitbucket"));
        assertNull(CiProviders.find(null));
    }

    @Test
    public void idsAreUniqueAndEveryProviderDescribesItself() {
        Set<String> ids = new HashSet<>();
        for (CiProvider provider : CiProviders.all()) {
            assertTrue("duplicate id " + provider.id(), ids.add(provider.id()));
            assertFalse(provider.displayName().isBlank());
            assertTrue(provider.defaultHost().startsWith("https://"));
            assertFalse(provider.sourceChoices().isEmpty());
        }
    }

    @Test
    public void displayNameFallsBackToTheIdForUnknownProviders() {
        assertEquals("bitbucket", CiProviders.displayName("bitbucket"));
    }

    @Test
    public void persistedIdsAreStable() {
        // These ids are written to users' settings; changing one orphans their configuration.
        assertEquals("gitlab", GitLabProvider.ID);
        assertEquals("github", GitHubProvider.ID);
    }
}
