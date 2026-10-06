package org.hugopalma.pipelinenotifier.provider;

/** The token is missing, expired, or lacks a required scope. Polling must stop, not retry. */
public class CiAuthException extends RuntimeException {
    public CiAuthException(String message) {
        super(message);
    }
}
