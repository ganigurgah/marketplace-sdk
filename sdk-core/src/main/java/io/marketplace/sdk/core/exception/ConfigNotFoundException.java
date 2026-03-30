package io.marketplace.sdk.core.exception;

public class ConfigNotFoundException extends MarketplaceException {
    public ConfigNotFoundException(String message) {
        super(message);
    }

    public ConfigNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
