package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;

import java.util.Set;

/**
 * Amazon Turkey (SP-API) pazaryeri adaptörü.
 *
 * Auth: OAuth2 Bearer token (LWA — Login With Amazon)
 *       Token ~1 saatte bir yenilenir; AmazonTokenProvider bu işi yönetir.
 * Tüm endpoint tanımları amazon.yaml'da bulunur.
 *
 * 401 alındığında AmazonTokenProvider.invalidateToken() çağrılır,
 * bir sonraki istekte token otomatik yenilenir.
 */
public class AmazonAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.AMAZON_TR;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS,
            Operation.GET_ORDER_DETAIL,
            Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT,
            Operation.UPDATE_PRODUCT,
            Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES,
            Operation.GET_SETTLEMENTS
        );
    }

    /**
     * Amazon SP-API: x-amz-access-token header.
     * AmazonTokenProvider token süresini takip eder ve gerektiğinde LWA
     * üzerinden yeniler (Bölüm 16 — SKILL.md).
     */
    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        AmazonTokenProvider provider = new AmazonTokenProvider();
        provider.initialize(config);
        return provider;
    }
}
