package io.marketplace.sdk.core.exception;

public class RateLimitException extends MarketplaceException {
    private final int retryAfterSeconds;

    public RateLimitException(int retryAfterSeconds) {
        super("RATE_LIMIT", "Rate limit exceeded. Retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
