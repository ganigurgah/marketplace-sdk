package io.marketplace.sdk.core;

import io.marketplace.sdk.core.async.AsyncMarketplaceClient;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.AdapterConfig;

import java.util.List;
import java.util.Map;

/**
 * Kullanıcının SDK'ya giriş noktası.
 *
 * Tek satıcı (tek instance):
 *   MarketplaceSDK sdk = MarketplaceSDK.builder()
 *       .configDir("marketplace-configs")
 *       .build();
 *
 * Birden fazla satıcı / mağaza (multiple instances):
 *   MarketplaceSDK sdk = MarketplaceSDK.builder()
 *       .configDir("configs/seller-a", "seller-a")
 *       .configDir("configs/seller-b", "seller-b")
 *       .build();
 *
 *   // Hangi mağaza?
 *   client.execute(
 *       OperationRequest.builder(TRENDYOL, GET_ORDERS)
 *           .instanceKey("seller-a")
 *           .build()
 *   );
 */
public interface MarketplaceSDK {

    MarketplaceClient client();
    AsyncMarketplaceClient asyncClient();

    List<io.marketplace.sdk.core.model.LogEntry> getRecentLogs();
    Map<MarketplaceType, AdapterConfig> getConfigurations();
    String getRawConfig(MarketplaceType type);
    void updateConfig(MarketplaceType type, String content);
    void shutdown();

    static Builder builder() {
        try {
            Class<?> builderClass = Class.forName("io.marketplace.sdk.config.MarketplaceSDKBuilder");
            return (Builder) builderClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException(
                "Could not find SDK Builder. Make sure sdk-config module is in the classpath.", e);
        }
    }

    interface Builder {
        /** Tek config dizini — tüm adapter'lar "default" instance olarak yüklenir. */
        Builder configDir(String dir);

        /**
         * Belirli bir instanceKey ile config dizini ekler.
         * Birden fazla kez çağrılabilir (her çağrı ayrı bir instance oluşturur).
         *
         * Örnek:
         *   .configDir("configs/seller-a", "seller-a")
         *   .configDir("configs/seller-b", "seller-b")
         */
        Builder configDir(String dir, String instanceKey);

        Builder adminUi(boolean enable);
        Builder adminUiPort(int port);
        Builder webhook(boolean enable);
        Builder webhookPort(int port);
        Builder hotReload(boolean enable);
        Builder webhookSecret(MarketplaceType type, String secret);
        MarketplaceSDK build();
    }
}
