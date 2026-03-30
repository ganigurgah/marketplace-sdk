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

public class ConfigEngine {

    private static final Logger log = LoggerFactory.getLogger(ConfigEngine.class);

    private final Path configDir;
    private final OperationRouter router;
    private final YamlConfigLoader loader;
    private final AdapterRegistry adapterRegistry;
    private final Map<MarketplaceType, AdapterConfig> loadedConfigs = new ConcurrentHashMap<>();
    private HotReloadWatcher watcher;

    public ConfigEngine(Path configDir, OperationRouter router, AdapterRegistry adapterRegistry) {
        this.configDir = configDir;
        this.router = router;
        this.loader = new YamlConfigLoader();
        this.adapterRegistry = adapterRegistry;
    }

    public void loadAll() {
        if (!Files.exists(configDir)) return;
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
            MarketplaceType type = MarketplaceType.fromString(config.getExtra("marketplace"));

            MarketplaceAdapter adapter = adapterRegistry.createAdapter(type);
            adapter.initialize(config);
            router.register(adapter);
            loadedConfigs.put(type, config);

            log.info("Loaded config for {} from {}", type, yamlFile.getFileName());
        } catch (Exception e) {
            log.error("Failed to load config: {}", yamlFile, e);
        }
    }

    public void reload(MarketplaceType type) {
        Path yamlFile = configDir.resolve(type.name().toLowerCase().replace("_", "-") + ".yaml");
        if (!Files.exists(yamlFile)) {
            throw new ConfigNotFoundException(type.name());
        }
        loadSingle(yamlFile);
        log.info("Hot-reloaded config for {}", type);
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
        Path yamlFile = configDir.resolve(type.name().toLowerCase().replace("_", "-") + ".yaml");
        try {
            return Files.readString(yamlFile);
        } catch (Exception e) {
            log.error("Failed to read raw config for {}: {}", type, yamlFile, e);
            throw new RuntimeException("Config read failed", e);
        }
    }

    public void saveRawConfig(MarketplaceType type, String content) {
        Path yamlFile = configDir.resolve(type.name().toLowerCase().replace("_", "-") + ".yaml");
        try {
            Files.writeString(yamlFile, content);
            reload(type); // Hemen yeniden yükle
            log.info("Saved and reloaded raw config for {}", type);
        } catch (Exception e) {
            log.error("Failed to save raw config for {}: {}", type, yamlFile, e);
            throw new RuntimeException("Config save failed", e);
        }
    }
}
