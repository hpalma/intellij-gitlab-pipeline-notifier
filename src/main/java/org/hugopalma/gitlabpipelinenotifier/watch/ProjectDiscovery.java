package org.hugopalma.gitlabpipelinenotifier.watch;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.ide.productMode.IdeProductMode;
import org.hugopalma.gitlabpipelinenotifier.settings.Settings;

import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Works out which GitLab projects to poll for a given IDE project: the ones its git remotes point
 * at, plus anything the user listed explicitly in settings.
 */
public final class ProjectDiscovery {

    private static final Logger LOG = Logger.getInstance(ProjectDiscovery.class);

    /**
     * Under JetBrains Remote Development (Coder, Gateway, Code With Me), this plugin's project-level
     * services run inside the JetBrains Client frontend process - not the backend that actually has
     * the checked-out repository. {@link ProjectRootManager}'s content roots there resolve to the
     * frontend's own local filesystem, which has no {@code .git} at all, so git-remote discovery can
     * never find anything and silently produces zero targets. Detected once per session so the
     * warning below is not repeated on every poll tick.
     */
    private static final AtomicBoolean warnedAboutFrontendProcess = new AtomicBoolean();

    private ProjectDiscovery() {
    }

    /**
     * Whether {@link #discover} can see the real repository checkout. False under a Remote
     * Development frontend process; {@link org.hugopalma.gitlabpipelinenotifier.watch.PipelinePoller}
     * uses this to surface a one-time, actionable notice rather than leaving the user to wonder why
     * auto-discovered projects never show up.
     */
    public static boolean isGitRemoteDiscoverySupported() {
        return !IdeProductMode.isFrontend();
    }

    public static Set<RemoteProject> discover(Project project, Settings.State settings) {
        Set<RemoteProject> result = new LinkedHashSet<>();

        String host = RemoteUrlParser.hostOf(settings.gitlabHost);
        if (host == null) {
            LOG.debug("discover(" + project.getName() + "): no usable host in configured GitLab URL '"
                    + settings.gitlabHost + "', nothing to watch");
            return result;
        }

        if (settings.watchGitRemotes && !isGitRemoteDiscoverySupported()) {
            if (warnedAboutFrontendProcess.compareAndSet(false, true)) {
                LOG.warn("Running inside a JetBrains Client / Remote Development frontend process; "
                        + "git-remote discovery cannot see the backend's checkout and is disabled for this "
                        + "session. List projects explicitly under 'Also watch these projects' instead.");
            }
        } else if (settings.watchGitRemotes) {
            Set<RemoteProject> fromRemotes = fromGitRemotes(project, host);
            LOG.debug("discover(" + project.getName() + "): git-remote discovery against host '" + host
                    + "' found " + fromRemotes.size() + " project(s): " + fromRemotes);
            result.addAll(fromRemotes);
        } else {
            LOG.debug("discover(" + project.getName() + "): watchGitRemotes is off, skipping git-remote discovery");
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

        LOG.debug("discover(" + project.getName() + "): watching " + result.size() + " project(s) total: " + result);
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
            Set<String> urls = readRemoteUrls(gitDir);
            LOG.debug("fromGitRemotes: " + gitDir.getPath() + " has remote URL(s): " + urls);
            for (String url : urls) {
                RemoteProject parsed = RemoteUrlParser.parse(url);
                if (parsed == null) {
                    LOG.debug("fromGitRemotes: could not parse remote URL '" + url + "' as a project path");
                    continue;
                }
                // Remotes pointing at GitHub, a mirror, or a second GitLab instance are not ours.
                if (!parsed.host().equals(host)) {
                    LOG.debug("fromGitRemotes: remote '" + url + "' resolved to host '" + parsed.host()
                            + "', which does not match the configured host '" + host + "' - skipped");
                    continue;
                }
                result.add(parsed);
            }
        }
        return result;
    }

    /** One entry per distinct git directory found under any content root of the project. */
    private static Set<VirtualFile> findGitDirs(Project project) {
        VirtualFile[] contentRoots = ProjectRootManager.getInstance(project).getContentRootsFromAllModules();
        LOG.debug("findGitDirs(" + project.getName() + "): " + contentRoots.length + " content root(s): "
                + Arrays.stream(contentRoots).map(VirtualFile::getPath).toList());

        Set<VirtualFile> result = new LinkedHashSet<>();
        for (VirtualFile root : contentRoots) {
            VirtualFile gitDir = resolveGitDir(root);
            if (gitDir == null) {
                LOG.debug("findGitDirs: no .git found under content root " + root.getPath());
            } else {
                LOG.debug("findGitDirs: content root " + root.getPath() + " -> git dir " + gitDir.getPath());
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
                VirtualFile resolved =
                        VfsUtilCore.findRelativeFile(content.substring("gitdir:".length()).trim(), contentRoot);
                if (resolved == null) {
                    LOG.debug("resolveGitDir: " + dotGit.getPath() + " points at '" + content
                            + "', which does not resolve to an existing file");
                }
                return resolved;
            }
            LOG.debug("resolveGitDir: " + dotGit.getPath() + " is a file but not a 'gitdir:' pointer: " + content);
        } catch (IOException e) {
            LOG.debug("Could not read " + dotGit.getPath(), e);
        }
        return null;
    }

    /** Minimal parse of the {@code [remote "name"]} sections of a git config file - just the URLs. */
    private static Set<String> readRemoteUrls(VirtualFile gitDir) {
        VirtualFile config = gitDir.findChild("config");
        if (config == null) {
            LOG.debug("readRemoteUrls: no 'config' file under " + gitDir.getPath());
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
