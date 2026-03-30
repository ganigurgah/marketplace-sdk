package io.marketplace.sdk.config;

import io.marketplace.sdk.core.exception.ConfigNotFoundException;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRouter;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * YAML dosyalarını yükler, adapter'ları başlatır ve router'a kaydeder.
 *
 * instanceKey desteği ile aynı pazaryerinin birden fazla satıcı hesabı
 * ayrı ayrı yüklenebilir:
 *
 *   new ConfigEngine(Path.of("configs/seller-a"), router, registry, "seller-a")
 *   new ConfigEngine(Path.of("configs/seller-b"), router, registry, "seller-b")
 *
 * instanceKey = "default" ise eski davranış korunur (geriye dönük uyumlu).
 */
public class ConfigEngine {

    private static final Logger log = LoggerFactory.getLogger(ConfigEngine.class);

    private final Path configDir;
    private final OperationRouter router;
    private final YamlConfigLoader loader;
    private final AdapterRegistry adapterRegistry;
    private final String instanceKey;
    private final Map<MarketplaceType, AdapterConfig> loadedConfigs = new ConcurrentHashMap<>();
    private HotReloadWatcher watcher;

    /** Geriye dönük uyumlu constructor — instanceKey = "default". */
    public ConfigEngine(Path configDir, OperationRouter router, AdapterRegistry adapterRegistry) {
        this(configDir, router, adapterRegistry, OperationRouter.DEFAULT_INSTANCE);
    }

    /** Multiple instance constructor. */
    public ConfigEngine(Path configDir, OperationRouter router,
                        AdapterRegistry adapterRegistry, String instanceKey) {
        this.configDir = configDir;
        this.router = router;
        this.loader = new YamlConfigLoader();
        this.adapterRegistry = adapterRegistry;
        this.instanceKey = instanceKey != null ? instanceKey : OperationRouter.DEFAULT_INSTANCE;
    }

    public void loadAll() {
        if (!Files.exists(configDir)) {
            log.warn("Config directory does not exist: {}", configDir);
            return;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.yaml")) {
            for (Path yamlFile : stream) {
                loadSingle(yamlFile);
            }
        } catch (Exception e) {
            log.error("Config directory could not be read: {}", configDir, e);
            throw new RuntimeException("Config load failed", e);
        }
    }

    public void loadSingle(Path yamlFile) {
        try {
            AdapterConfig config = loader.load(yamlFile);
            String marketplaceRaw = config.getExtra("marketplace");
            if (marketplaceRaw == null) {
                log.warn("YAML file {} is missing 'marketplace' field, skipping.", yamlFile.getFileName());
                return;
            }

            MarketplaceType type = MarketplaceType.fromString(marketplaceRaw);
            MarketplaceAdapter adapter = adapterRegistry.createAdapter(type);

            if (adapter != null) {
                adapter.initialize(config);
                router.register(instanceKey, adapter);   // ← instanceKey ile kayıt
                loadedConfigs.put(type, config);
                log.info("Loaded config for {} (instance='{}') from {}",
                    type, instanceKey, yamlFile.getFileName());
            } else {
                log.warn("No adapter found for marketplace '{}', skipping {}",
                    type, yamlFile.getFileName());
            }
        } catch (Exception e) {
            log.error("Failed to load config from {}: {}", yamlFile, e.getMessage(), e);
        }
    }

    public void reload(MarketplaceType type) {
        String filename = type.name().toLowerCase().replace("_", "-") + ".yaml";
        Path yamlFile = configDir.resolve(filename);
        if (!Files.exists(yamlFile)) {
            throw new ConfigNotFoundException(type.name());
        }
        loadSingle(yamlFile);
        log.info("Hot-reloaded config for {} (instance='{}')", type, instanceKey);
    }

    public void startHotReload() {
        if (!Files.exists(configDir)) return;
        this.watcher = new HotReloadWatcher(configDir, changedFile -> {
            String filename = changedFile.getFileName().toString();
            String marketplaceName = filename.replace(".yaml", "").replace("-", "_").toUpperCase();
            try {
                MarketplaceType type = MarketplaceType.fromString(marketplaceName);
                reload(type);
            } catch (IllegalArgumentException e) {
                log.warn("Changed file {} does not match any known marketplace", filename);
            }
        });
        watcher.start();
    }

    public void stopHotReload() {
        if (watcher != null) watcher.stop();
    }

    public AdapterConfig getConfig(MarketplaceType type) {
        AdapterConfig config = loadedConfigs.get(type);
        if (config == null) throw new ConfigNotFoundException(type.name());
        return config;
    }

    public Map<MarketplaceType, AdapterConfig> getAllConfigs() {
        return Collections.unmodifiableMap(loadedConfigs);
    }

    public String getRawConfig(MarketplaceType type) {
        String filename = type.name().toLowerCase().replace("_", "-") + ".yaml";
        Path yamlFile = configDir.resolve(filename);
        try {
            return Files.readString(yamlFile);
        } catch (Exception e) {
            throw new RuntimeException("Config read failed for " + type + ": " + e.getMessage(), e);
        }
    }

    public void saveRawConfig(MarketplaceType type, String content) {
        String filename = type.name().toLowerCase().replace("_", "-") + ".yaml";
        Path yamlFile = configDir.resolve(filename);
        try {
            Files.writeString(yamlFile, content);
            reload(type);
            log.info("Saved and reloaded raw config for {} (instance='{}')", type, instanceKey);
        } catch (Exception e) {
            throw new RuntimeException("Config save failed for " + type + ": " + e.getMessage(), e);
        }
    }

    public String getInstanceKey() { return instanceKey; }
}
