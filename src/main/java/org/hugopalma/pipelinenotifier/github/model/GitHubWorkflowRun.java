package org.hugopalma.pipelinenotifier.github.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** A workflow run as returned by {@code GET /repos/:owner/:repo/actions/runs}. */
public record GitHubWorkflowRun(
        long id,
        String name,
        @SerializedName("display_title") String displayTitle,
        @SerializedName("head_branch") String headBranch,
        @SerializedName("head_sha") String headSha,
        String event,
        String status,
        String conclusion,
        @SerializedName("html_url") String htmlUrl,
        @SerializedName("updated_at") String updatedAt,
        @SerializedName("run_attempt") int runAttempt,
        Actor actor,
        @SerializedName("triggering_actor") Actor triggeringActor) {

    public record Actor(String login) {
    }

    /** The list response wraps the runs in an object, unlike GitLab's bare array. */
    public record Runs(@SerializedName("total_count") int totalCount,
                       @SerializedName("workflow_runs") List<GitHubWorkflowRun> workflowRuns) {
    }

    /** Whoever caused this attempt: a re-run is triggered by someone other than the original actor. */
    public String triggeredBy() {
        Actor who = triggeringActor != null ? triggeringActor : actor;
        return who == null ? null : who.login();
    }
}
