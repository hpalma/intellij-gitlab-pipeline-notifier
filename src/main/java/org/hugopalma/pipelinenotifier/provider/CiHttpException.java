package org.hugopalma.pipelinenotifier.provider;

/** Any non-2xx response that is not an authentication failure. */
public class CiHttpException extends RuntimeException {

    private final int status;

    public CiHttpException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
