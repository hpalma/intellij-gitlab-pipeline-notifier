package org.hugopalma.gitlabpipelinenotifier.watch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import org.hugopalma.gitlabpipelinenotifier.settings.Settings;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Works out which GitLab projects to poll for a given IDE project: the ones its git remotes point
 * at, plus anything the user listed explicitly in settings.
 */
public final class ProjectDiscovery {

    private static final Logger LOG = Logger.getInstance(ProjectDiscovery.class);

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

    /**
     * Reads remote URLs straight out of each content root's {@code .git/config} rather than going
     * through Git4Idea's {@code GitRepositoryManager}: that class lives in a content module the
     * platform does not load in a JetBrains Client / Remote Development frontend process (only in
     * the real backend IDE), so depending on it broke this entirely there even though Git4Idea
     * itself shows as installed. Plain file reads through the platform VFS work identically in
     * every process, and this plugin only ever needed the remote URLs anyway.
     */
    private static Set<RemoteProject> fromGitRemotes(Project project, String host) {
        Set<RemoteProject> result = new LinkedHashSet<>();
        for (VirtualFile gitDir : findGitDirs(project)) {
            for (String url : readRemoteUrls(gitDir)) {
                RemoteProject parsed = RemoteUrlParser.parse(url);
                // Remotes pointing at GitHub, a mirror, or a second GitLab instance are not ours.
                if (parsed != null && parsed.host().equals(host)) {
                    result.add(parsed);
                }
            }
        }
        return result;
    }

    /** One entry per distinct git directory found under any content root of the project. */
    private static Set<VirtualFile> findGitDirs(Project project) {
        Set<VirtualFile> result = new LinkedHashSet<>();
        for (VirtualFile root : ProjectRootManager.getInstance(project).getContentRootsFromAllModules()) {
            VirtualFile gitDir = resolveGitDir(root);
            if (gitDir != null) {
                result.add(gitDir);
            }
        }
        return result;
    }

    /**
     * {@code .git} is a directory in a normal checkout, but a one-line file pointing elsewhere for a
     * submodule or worktree: {@code gitdir: <relative-path>}.
     */
    private static VirtualFile resolveGitDir(VirtualFile contentRoot) {
        VirtualFile dotGit = contentRoot.findChild(".git");
        if (dotGit == null) {
            return null;
        }
        if (dotGit.isDirectory()) {
            return dotGit;
        }
        try {
            String content = VfsUtilCore.loadText(dotGit).trim();
            if (content.startsWith("gitdir:")) {
                return VfsUtilCore.findRelativeFile(content.substring("gitdir:".length()).trim(), contentRoot);
            }
        } catch (IOException e) {
            LOG.debug("Could not read " + dotGit.getPath(), e);
        }
        return null;
    }

    /** Minimal parse of the {@code [remote "name"]} sections of a git config file - just the URLs. */
    private static Set<String> readRemoteUrls(VirtualFile gitDir) {
        VirtualFile config = gitDir.findChild("config");
        if (config == null) {
            return Set.of();
        }

        Set<String> urls = new LinkedHashSet<>();
        try {
            boolean inRemoteSection = false;
            for (String line : VfsUtilCore.loadText(config).split("\\R")) {
                String trimmed = line.trim();
                if (trimmed.startsWith("[")) {
                    inRemoteSection = trimmed.startsWith("[remote ");
                    continue;
                }
                if (inRemoteSection && trimmed.startsWith("url")) {
                    int eq = trimmed.indexOf('=');
                    if (eq > 0) {
                        urls.add(trimmed.substring(eq + 1).trim());
                    }
                }
            }
        } catch (IOException e) {
            LOG.debug("Could not read " + config.getPath(), e);
        }
        return urls;
    }
}
