package io.marketplace.sdk.core.operation;

import io.marketplace.sdk.core.exception.OperationNotSupportedException;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gelen OperationRequest'i doğru adapter'a yönlendirir.
 */
public class OperationRouter {
    private final Map<MarketplaceType, MarketplaceAdapter> adapters = new ConcurrentHashMap<>();

    public void register(MarketplaceAdapter adapter) {
        adapters.put(adapter.getType(), adapter);
    }

    public void deregister(MarketplaceType type) {
        adapters.remove(type);
    }

    public OperationResponse route(OperationRequest request) {
        MarketplaceAdapter adapter = adapters.get(request.getMarketplace());
        if (adapter == null) {
            throw new OperationNotSupportedException(
                "No adapter registered for: " + request.getMarketplace());
        }
        if (!adapter.supportedOperations().contains(request.getOperation())) {
            throw new OperationNotSupportedException(
                request.getOperation() + " not supported by " + request.getMarketplace());
        }
        return adapter.execute(request);
    }

    public boolean hasAdapter(MarketplaceType type) {
        return adapters.containsKey(type);
    }

    public MarketplaceAdapter getAdapter(MarketplaceType type) {
        return adapters.get(type);
    }
}
