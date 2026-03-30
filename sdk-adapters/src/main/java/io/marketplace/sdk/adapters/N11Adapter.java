package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;

import java.util.Map;
import java.util.Set;

/**
 * N11 pazaryeri adaptörü.
 *
 * Auth: Header tabanlı API Key (appKey + appSecret)
 * Tüm endpoint tanımları n11.yaml'da bulunur.
 */
public class N11Adapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.N11;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS,
            Operation.GET_ORDER_DETAIL,
            Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT,
            Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES
        );
    }

    /**
     * N11 API Key Auth: appKey ve appSecret ayrı header olarak gönderilir.
     */
    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        return new TokenProvider() {
            @Override
            public void initialize(AdapterConfig c) { }

            @Override
            public Map<String, String> getAuthHeaders() {
                return Map.of(
                    "appKey",    config.getCredentials().getApiKey(),
                    "appSecret", config.getCredentials().getApiSecret()
                );
            }

            @Override
            public void invalidateToken() { }
        };
    }
}
