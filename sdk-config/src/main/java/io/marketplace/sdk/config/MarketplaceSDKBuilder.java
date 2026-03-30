package io.marketplace.sdk.config;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRouter;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class MarketplaceSDKBuilder implements MarketplaceSDK.Builder {
    private String configDir;
    private boolean adminUi;
    private int adminUiPort = 8090;
    private boolean webhook;
    private int webhookPort = 8080;
    private boolean hotReload;
    private final Map<MarketplaceType, String> webhookSecrets = new HashMap<>();

    @Override
    public MarketplaceSDK.Builder configDir(String dir) { this.configDir = dir; return this; }
    @Override
    public MarketplaceSDK.Builder adminUi(boolean enable) { this.adminUi = enable; return this; }
    @Override
    public MarketplaceSDK.Builder adminUiPort(int port) { this.adminUiPort = port; return this; }
    @Override
    public MarketplaceSDK.Builder webhook(boolean enable) { this.webhook = enable; return this; }
    @Override
    public MarketplaceSDK.Builder webhookPort(int port) { this.webhookPort = port; return this; }
    @Override
    public MarketplaceSDK.Builder hotReload(boolean enable) { this.hotReload = enable; return this; }
    @Override
    public MarketplaceSDK.Builder webhookSecret(MarketplaceType type, String secret) { 
        this.webhookSecrets.put(type, secret); 
        return this; 
    }

    @Override
    public MarketplaceSDK build() {
        OperationRouter router = new OperationRouter();
        AdapterRegistry registry = new AdapterRegistry();
        ConfigEngine configEngine = new ConfigEngine(Path.of(configDir != null ? configDir : "."), router, registry);

        configEngine.loadAll();
        if (hotReload) {
            configEngine.startHotReload();
        }

        return new MarketplaceSDKImpl(configEngine, router);
    }
}
