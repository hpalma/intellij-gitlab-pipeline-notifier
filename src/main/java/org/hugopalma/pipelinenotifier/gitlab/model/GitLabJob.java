package org.hugopalma.pipelinenotifier.gitlab.model;

import com.google.gson.annotations.SerializedName;

public record GitLabJob(
        long id,
        String name,
        String stage,
        String status,
        @SerializedName("allow_failure") boolean allowFailure,
        @SerializedName("web_url") String webUrl) {
}
