package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Hepsiburada pazaryeri adaptörü.
 *
 * Auth: Basic Auth (Base64 merchantId:apiPassword)
 * Tüm endpoint tanımları hepsiburada.yaml'da bulunur.
 */
public class HepsiburadaAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.HEPSIBURADA;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS,
            Operation.GET_ORDER_DETAIL,
            Operation.UPDATE_ORDER_STATUS,
            Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT,
            Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES,
            Operation.GET_SHIPMENT_PROVIDERS
        );
    }

    /**
     * Hepsiburada Basic Auth: Authorization: Basic Base64(merchantId:password)
     * sellerId alanı merchantId olarak kullanılır.
     */
    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        return new TokenProvider() {
            @Override
            public void initialize(AdapterConfig c) { }

            @Override
            public Map<String, String> getAuthHeaders() {
                String raw = config.getCredentials().getSellerId()
                           + ":" + config.getCredentials().getApiSecret();
                String encoded = Base64.getEncoder().encodeToString(raw.getBytes());
                return Map.of("Authorization", "Basic " + encoded);
            }

            @Override
            public void invalidateToken() { }
        };
    }
}
