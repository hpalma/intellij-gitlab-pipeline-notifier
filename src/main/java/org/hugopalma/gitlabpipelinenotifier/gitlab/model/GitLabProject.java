package org.hugopalma.gitlabpipelinenotifier.gitlab.model;

import com.google.gson.annotations.SerializedName;

public record GitLabProject(
        long id,
        @SerializedName("path_with_namespace") String pathWithNamespace,
        @SerializedName("web_url") String webUrl) {
}
