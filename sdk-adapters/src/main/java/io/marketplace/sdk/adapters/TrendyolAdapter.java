package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Trendyol pazaryeri adaptörü.
 *
 * Auth     : Basic Auth — Authorization: Basic Base64(apiKey:apiSecret)
 * User-Agent: {sellerId} - SelfIntegration  (Trendyol zorunlu tutuyor)
 *
 * Path parametresi {sellerId} → credentials.sellerId değerinden otomatik doldurulur.
 * Endpoint URL veya sellerId değiştiğinde sadece trendyol.yaml güncellenir;
 * bu dosyaya dokunulmaz.
 *
 * Multi-product desteği:
 *   Trendyol API'si "items" array ile tek veya çok ürün kabul eder.
 *   createProduct operasyonu hem tek hem çok ürünü destekler.
 *   Kullanım:
 *     .param("items", List.of(product1, product2))   // çok ürün
 *     .param("items", List.of(product1))              // tek ürün
 */
public class TrendyolAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.TRENDYOL;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS,
            Operation.GET_ORDER_DETAIL,
            Operation.UPDATE_ORDER_STATUS,
            Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT,
            Operation.UPDATE_PRODUCT,
            Operation.DELETE_PRODUCT,
            Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES,
            Operation.GET_BRANDS,
            Operation.GET_ATTRIBUTES,
            Operation.GET_SHIPMENT_PROVIDERS,
            Operation.GET_BATCH_REQUEST_RESULT
        );
    }

    /**
     * Trendyol Basic Auth:
     *   Authorization: Basic Base64(apiKey:apiSecret)
     *   User-Agent:    {sellerId} - SelfIntegration
     *
     * sellerId önce credentials'dan, yoksa extra'dan alınır.
     */
    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        return new TokenProvider() {
            @Override public void initialize(AdapterConfig c) { }

            @Override
            public Map<String, String> getAuthHeaders() {
                String apiKey    = config.getCredentials().getApiKey();
                String apiSecret = config.getCredentials().getApiSecret();

                // sellerId: credentials > extra
                String sellerId = config.getCredentials().getSellerId();
                if (sellerId == null) sellerId = config.getExtra("sellerId");
                if (sellerId == null) sellerId = "unknown";

                String encoded = Base64.getEncoder()
                    .encodeToString((apiKey + ":" + apiSecret).getBytes());

                return Map.of(
                    "Authorization", "Basic " + encoded,
                    "User-Agent",    sellerId + " - SelfIntegration"
                );
            }

            @Override public void invalidateToken() { /* Basic Auth — no-op */ }
        };
    }

    /**
     * {sellerId} path parametresi için ek çözümleme.
     * credentials.sellerId → extra.sellerId sırasıyla kontrol edilir.
     */
    @Override
    protected Object resolveCredential(String paramName) {
        if ("sellerid".equals(paramName.toLowerCase())) {
            String fromCreds = config.getCredentials().getSellerId();
            if (fromCreds != null) return fromCreds;
            return config.getExtra("sellerId");
        }
        return super.resolveCredential(paramName);
    }
}
