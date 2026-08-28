package org.hugopalma.gitlabpipelinenotifier.gitlab;

/** Any non-2xx response that is not an authentication failure. */
public class GitLabHttpException extends RuntimeException {

    private final int status;

    public GitLabHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
