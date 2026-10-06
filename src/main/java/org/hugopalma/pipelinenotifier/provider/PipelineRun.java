package org.hugopalma.pipelinenotifier.provider;

import java.time.Instant;
import java.time.OffsetDateTime;

/**
 * A CI pipeline run in provider-neutral terms: a GitLab pipeline, a GitHub Actions workflow run.
 * Everything downstream of the {@link CiClient} (matching, dedupe, alerting) works on this.
 *
 * @param source what started it, in the provider's own vocabulary (see {@link CiProvider#sourceChoices()})
 * @param triggeredBy login of whoever started it, or {@code null} if the listing did not say
 */
public record PipelineRun(
        long id,
        String ref,
        String sha,
        String source,
        String name,
        String webUrl,
        Instant updatedAt,
        String triggeredBy) {

    public String shortSha() {
        return sha == null ? "" : sha.substring(0, Math.min(8, sha.length()));
    }

    public PipelineRun withTriggeredBy(String login) {
        return new PipelineRun(id, ref, sha, source, name, webUrl, updatedAt, login);
    }

    /** Accepts both {@code ...Z} and {@code ...+00:00} offsets; {@code null} for anything else. */
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
