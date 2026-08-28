package org.hugopalma.gitlabpipelinenotifier.startup;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.hugopalma.gitlabpipelinenotifier.watch.PipelinePoller;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Starts polling when a project opens.
 */
public class PipelineNotifierStartup implements ProjectActivity {

    @Override
    public @Nullable Object execute(@NonNull Project project, @NonNull Continuation<? super Unit> continuation) {
        PipelinePoller.getInstance(project).start();
        return null;
    }
}
