package io.marketplace.sdk.core.exception;

public class OperationNotSupportedException extends MarketplaceException {
    public OperationNotSupportedException(String message) {
        super("OPERATION_NOT_SUPPORTED", message);
    }
}
