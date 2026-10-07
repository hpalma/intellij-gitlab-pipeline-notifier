package org.hugopalma.pipelinenotifier.gitlab;

import org.hugopalma.pipelinenotifier.gitlab.model.GitLabJob;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabPipeline;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabProject;
import org.hugopalma.pipelinenotifier.gitlab.model.GitLabUser;
import org.hugopalma.pipelinenotifier.provider.CiClient;
import org.hugopalma.pipelinenotifier.provider.Page;
import org.hugopalma.pipelinenotifier.provider.PipelineRun;
import org.hugopalma.pipelinenotifier.watch.RemoteProject;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Adapts the GitLab REST client to the provider-neutral {@link CiClient}. */
final class GitLabCiClient implements CiClient {

    private final GitLabClient client;

    /** GitLab addresses projects by numeric id; a lookup is stable for the lifetime of this client. */
    private final Map<String, Long> projectIds = new HashMap<>();

    GitLabCiClient(GitLabClient client) {
        this.client = client;
    }

    @Override
    public String currentUser() throws IOException, InterruptedException {
        GitLabUser user = client.currentUser();
        return user == null ? null : user.username();
    }

    @Override
    public Page<PipelineRun> failedRuns(RemoteProject target, Instant since, String username, int perPage, int page)
            throws IOException, InterruptedException {
        List<GitLabPipeline> raw = client.failedPipelines(projectId(target), since, username, perPage, page);
        List<PipelineRun> runs = new ArrayList<>(raw.size());
        for (GitLabPipeline pipeline : raw) {
            runs.add(toRun(pipeline));
        }
        return Page.of(runs, perPage, raw.size());
    }

    @Override
    public PipelineRun detail(RemoteProject target, PipelineRun run) throws IOException, InterruptedException {
        GitLabPipeline detail = client.pipeline(projectId(target), run.id());
        return detail == null ? run : toRun(detail);
    }

    @Override
    public List<String> failedJobs(RemoteProject target, PipelineRun run) throws IOException, InterruptedException {
        List<String> names = new ArrayList<>();
        for (GitLabJob job : client.failedJobs(projectId(target), run.id())) {
            // A job allowed to fail did not fail the pipeline.
            if (!job.allowFailure()) {
                names.add(job.name());
            }
        }
        return names;
    }

    @Override
    public Page<String> listProjects(String search, int perPage, int page) throws IOException, InterruptedException {
        List<GitLabProject> raw = client.listProjects(search, perPage, page);
        List<String> paths = new ArrayList<>(raw.size());
        for (GitLabProject project : raw) {
            paths.add(project.pathWithNamespace());
        }
        return Page.of(paths, perPage, raw.size());
    }

    private synchronized long projectId(RemoteProject target) throws IOException, InterruptedException {
        Long cached = projectIds.get(target.key());
        if (cached != null) {
            return cached;
        }
        long id = client.findProject(target.path()).id();
        projectIds.put(target.key(), id);
        return id;
    }

    private static PipelineRun toRun(GitLabPipeline pipeline) {
        return new PipelineRun(
                pipeline.id(),
                pipeline.ref(),
                pipeline.sha(),
                pipeline.source(),
                pipeline.name(),
                pipeline.webUrl(),
                pipeline.updatedAtInstant(),
                pipeline.user() == null ? null : pipeline.user().username());
    }
}
