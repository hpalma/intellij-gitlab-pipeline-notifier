package org.hugopalma.pipelinenotifier.github.model;

import com.google.gson.annotations.SerializedName;

import java.util.List;

public record GitHubJob(long id, String name, String status, String conclusion) {

    /** The list response wraps the jobs in an object. */
    public record Jobs(@SerializedName("total_count") int totalCount, List<GitHubJob> jobs) {
    }

    public boolean failed() {
        return "failure".equals(conclusion) || "timed_out".equals(conclusion);
    }
}
