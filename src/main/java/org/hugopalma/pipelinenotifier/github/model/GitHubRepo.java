package org.hugopalma.pipelinenotifier.github.model;

import com.google.gson.annotations.SerializedName;

public record GitHubRepo(long id, @SerializedName("full_name") String fullName) {
}
