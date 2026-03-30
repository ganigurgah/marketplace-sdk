package io.marketplace.sdk.config;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRouter;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MarketplaceSDKBuilder implements MarketplaceSDK.Builder {

    /** Her config dizini bir instanceKey ile eşleştirilir. */
    private record ConfigEntry(String dir, String instanceKey) {}

    private final List<ConfigEntry> configEntries = new ArrayList<>();
    private boolean adminUi;
    private int adminUiPort = 8090;
    private boolean webhook;
    private int webhookPort = 8080;
    private boolean hotReload;
    private final Map<MarketplaceType, String> webhookSecrets = new HashMap<>();

    // -------------------------------------------------------------------------
    // Builder metotları
    // -------------------------------------------------------------------------

    @Override
    public MarketplaceSDK.Builder configDir(String dir) {
        return configDir(dir, OperationRouter.DEFAULT_INSTANCE);
    }

    @Override
    public MarketplaceSDK.Builder configDir(String dir, String instanceKey) {
        configEntries.add(new ConfigEntry(dir, instanceKey));
        return this;
    }

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

    // -------------------------------------------------------------------------
    // Build
    // -------------------------------------------------------------------------

    @Override
    public MarketplaceSDK build() {
        OperationRouter router = new OperationRouter();
        AdapterRegistry registry = new AdapterRegistry();

        if (configEntries.isEmpty()) {
            throw new IllegalStateException(
                "No config directory specified. Call .configDir(\"path\") before .build()."
            );
        }

        // Her configEntry için ayrı bir ConfigEngine yükle
        // (her biri kendi instanceKey'iyle router'a kaydeder)
        ConfigEngine primaryEngine = null;

        for (ConfigEntry entry : configEntries) {
            ConfigEngine engine = new ConfigEngine(
                Path.of(entry.dir()),
                router,
                registry,
                entry.instanceKey()   // ← instanceKey geçiyor
            );
            engine.loadAll();

            if (hotReload) {
                engine.startHotReload();
            }

            if (primaryEngine == null) {
                primaryEngine = engine; // İlk engine primary (getRawConfig vs. için)
            }
        }

        return new MarketplaceSDKImpl(primaryEngine, router);
    }
}
