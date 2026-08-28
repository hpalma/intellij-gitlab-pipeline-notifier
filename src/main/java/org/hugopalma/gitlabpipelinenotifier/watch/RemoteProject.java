package org.hugopalma.gitlabpipelinenotifier.watch;

/** A GitLab project identified by the instance host and its namespaced path. */
public record RemoteProject(String host, String path) {

    /** Stable key for watermark bookkeeping - survives project id changes and renames alike. */
    public String key() {
        return host + "|" + path;
    }
}
