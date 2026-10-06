package org.hugopalma.pipelinenotifier.provider;

import org.hugopalma.pipelinenotifier.github.GitHubProvider;
import org.hugopalma.pipelinenotifier.gitlab.GitLabProvider;

import java.util.List;

/** The supported CI services. Adding one is a one-line change here. */
public final class CiProviders {

    private static final List<CiProvider> ALL = List.of(new GitLabProvider(), new GitHubProvider());

    private CiProviders() {
    }

    public static List<CiProvider> all() {
        return ALL;
    }

    /** {@code null} for an id no longer (or not yet) known, e.g. settings written by a newer version. */
    public static CiProvider find(String id) {
        for (CiProvider provider : ALL) {
            if (provider.id().equals(id)) {
                return provider;
            }
        }
        return null;
    }

    /** Display name for {@code id}, falling back to the id itself so UI text never goes blank. */
    public static String displayName(String id) {
        CiProvider provider = find(id);
        return provider == null ? String.valueOf(id) : provider.displayName();
    }
}
