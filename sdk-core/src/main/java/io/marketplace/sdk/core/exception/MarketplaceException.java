package io.marketplace.sdk.core.exception;

public class MarketplaceException extends RuntimeException {
    private final String errorCode;

    public MarketplaceException(String message) {
        super(message);
        this.errorCode = "UNKNOWN";
    }

    public MarketplaceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MarketplaceException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "UNKNOWN";
    }

    public MarketplaceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
