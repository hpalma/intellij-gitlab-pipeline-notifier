package org.hugopalma.gitlabpipelinenotifier.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.hugopalma.gitlabpipelinenotifier.watch.PipelinePoller;
import org.jetbrains.annotations.NotNull;

/**
 * Starts polling when a project opens.
 *
 * <p>Uses {@link StartupActivity} rather than the newer {@code ProjectActivity}: the latter is a
 * Kotlin suspending interface whose Java signature takes a {@code Continuation}, which is not worth
 * the noise here. {@code StartupActivity} is marked obsolete but is neither deprecated for removal
 * nor internal.
 */
@SuppressWarnings("UnstableApiUsage")
public class PipelineNotifierStartup implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        PipelinePoller.getInstance(project).start();
    }
}
