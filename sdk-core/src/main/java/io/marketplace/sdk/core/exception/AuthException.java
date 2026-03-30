package io.marketplace.sdk.core.exception;

public class AuthException extends MarketplaceException {
    public AuthException(String message) {
        super("AUTH_ERROR", message);
    }
}
