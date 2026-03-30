package io.marketplace.sdk.core;

import io.marketplace.sdk.core.async.AsyncMarketplaceClient;

/**
 * Kullanıcının projeye giriş noktası (Entry Point).
 */
public interface MarketplaceSDK {

    MarketplaceClient client();
    AsyncMarketplaceClient asyncClient();
    java.util.List<io.marketplace.sdk.core.model.LogEntry> getRecentLogs();
    java.util.Map<io.marketplace.sdk.core.model.MarketplaceType, io.marketplace.sdk.core.spi.AdapterConfig> getConfigurations();
    String getRawConfig(io.marketplace.sdk.core.model.MarketplaceType type);
    void updateConfig(io.marketplace.sdk.core.model.MarketplaceType type, String content);
    void shutdown();

    static Builder builder() {
        try {
            Class<?> builderClass = Class.forName("io.marketplace.sdk.config.MarketplaceSDKBuilder");
            return (Builder) builderClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Could not find SDK Builder. Make sure sdk-config module is in the classpath.", e);
        }
    }

    interface Builder {
        Builder configDir(String dir);
        Builder adminUi(boolean enable);
        Builder adminUiPort(int port);
        Builder webhook(boolean enable);
        Builder webhookPort(int port);
        Builder hotReload(boolean enable);
        Builder webhookSecret(io.marketplace.sdk.core.model.MarketplaceType type, String secret);
        MarketplaceSDK build();
    }
}
