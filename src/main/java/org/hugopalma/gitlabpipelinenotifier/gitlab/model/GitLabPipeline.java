package org.hugopalma.gitlabpipelinenotifier.gitlab.model;

import com.google.gson.annotations.SerializedName;

import java.time.Instant;
import java.time.OffsetDateTime;

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

    /**
     * GitLab emits both {@code ...Z} and {@code ...+00:00} offsets depending on the endpoint and
     * version, so neither parser alone is sufficient.
     */
    public static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException first) {
            try {
                return OffsetDateTime.parse(value).toInstant();
            } catch (RuntimeException second) {
                return null;
            }
        }
    }
}
