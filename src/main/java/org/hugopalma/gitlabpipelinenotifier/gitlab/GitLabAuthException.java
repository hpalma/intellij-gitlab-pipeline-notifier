package org.hugopalma.gitlabpipelinenotifier.gitlab;

/** The token is missing, expired, or lacks the {@code read_api} scope. Polling must stop, not retry. */
public class GitLabAuthException extends RuntimeException {
    public GitLabAuthException(String message) {
        super(message);
    }
}
