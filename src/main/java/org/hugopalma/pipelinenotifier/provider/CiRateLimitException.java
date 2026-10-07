package org.hugopalma.pipelinenotifier.provider;

import java.time.Duration;

/**
 * The server is throttling us. Transient: not a bad token, and not a reason to keep asking.
 * {@link #getRetryAfter()} is when the server said to come back, or {@code null} if it did not.
 */
public class CiRateLimitException extends CiHttpException {

    private final Duration retryAfter;

    public CiRateLimitException(int status, String message, Duration retryAfter) {
        super(status, message);
        this.retryAfter = retryAfter;
    }

    public Duration getRetryAfter() {
        return retryAfter;
    }
}
