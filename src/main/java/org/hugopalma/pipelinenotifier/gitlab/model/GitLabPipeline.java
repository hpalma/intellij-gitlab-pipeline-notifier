package org.hugopalma.pipelinenotifier.gitlab.model;

import com.google.gson.annotations.SerializedName;

import org.hugopalma.pipelinenotifier.provider.PipelineRun;

import java.time.Instant;

/**
 * A pipeline as returned by the API.
 *
 * <p>Note that the <em>list</em> endpoint ({@code GET /projects/:id/pipelines}) does not populate
 * {@link #user()} - only the single-pipeline endpoint does. Filtering by triggering user therefore
 * has to go through the {@code username} query parameter rather than being done client-side.
 */
public record GitLabPipeline(
        long id,
        long iid,
        @SerializedName("project_id") long projectId,
        String status,
        String source,
        String ref,
        String sha,
        String name,
        @SerializedName("web_url") String webUrl,
        @SerializedName("created_at") String createdAt,
        @SerializedName("updated_at") String updatedAt,
        Integer duration,
        GitLabUser user) {

    public Instant updatedAtInstant() {
        return parseTimestamp(updatedAt);
    }

    public String shortSha() {
        return sha == null ? "" : sha.substring(0, Math.min(8, sha.length()));
    }

    public static Instant parseTimestamp(String value) {
        return PipelineRun.parseTimestamp(value);
    }
}
