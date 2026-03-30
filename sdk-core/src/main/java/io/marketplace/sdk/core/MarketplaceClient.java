package io.marketplace.sdk.core;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;

/**
 * Kullanıcının doğrudan çağırdığı tek interface.
 */
public interface MarketplaceClient {
    OperationResponse execute(OperationRequest request);
    boolean supports(MarketplaceType marketplace, Operation operation);
    boolean healthCheck(MarketplaceType marketplace);
    void reloadConfig(MarketplaceType marketplace);
    void shutdown();
}
