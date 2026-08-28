package org.hugopalma.gitlabpipelinenotifier.watch;

import com.intellij.openapi.project.Project;
import git4idea.repo.GitRemote;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import org.hugopalma.gitlabpipelinenotifier.settings.Settings;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out which GitLab projects to poll for a given IDE project: the ones its git remotes point
 * at, plus anything the user listed explicitly in settings.
 */
public final class ProjectDiscovery {

    private ProjectDiscovery() {
    }

    public static Set<RemoteProject> discover(Project project, Settings.State settings) {
        Set<RemoteProject> result = new LinkedHashSet<>();

        String host = RemoteUrlParser.hostOf(settings.gitlabHost);
        if (host == null) {
            return result;
        }

        if (settings.watchGitRemotes) {
            result.addAll(fromGitRemotes(project, host));
        }

        for (String raw : settings.extraProjectPaths) {
            if (raw == null) {
                continue;
            }
            String path = raw.trim();
            while (path.startsWith("/")) {
                path = path.substring(1);
            }
            while (path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (!path.isEmpty()) {
                result.add(new RemoteProject(host, path));
            }
        }

        return result;
    }

    private static Set<RemoteProject> fromGitRemotes(Project project, String host) {
        Set<RemoteProject> result = new LinkedHashSet<>();
        for (GitRepository repository : GitRepositoryManager.getInstance(project).getRepositories()) {
            for (GitRemote remote : repository.getRemotes()) {
                for (String url : remote.getUrls()) {
                    RemoteProject parsed = RemoteUrlParser.parse(url);
                    // Remotes pointing at GitHub, a mirror, or a second GitLab instance are not ours.
                    if (parsed != null && parsed.host().equals(host)) {
                        result.add(parsed);
                    }
                }
            }
        }
        return result;
    }
}
