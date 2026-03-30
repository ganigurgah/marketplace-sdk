package io.marketplace.sdk.core.spi;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.Operation;
import java.util.Set;

/**
 * Tüm pazaryeri adapter'larının implement etmesi gereken SPI.
 * Bu interface değişmeden adapter'lar bağımsız geliştirilebilir.
 */
public interface MarketplaceAdapter {
    MarketplaceType getType();
    void initialize(AdapterConfig config);
    OperationResponse execute(OperationRequest request);
    Set<Operation> supportedOperations();
    boolean healthCheck();
    default void shutdown() {}
}
