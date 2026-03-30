package io.marketplace.sdk.config;

import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.async.AsyncMarketplaceClient;
import io.marketplace.sdk.core.operation.OperationRouter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MarketplaceSDKImpl implements MarketplaceSDK {
    private final ConfigEngine configEngine;
    private final OperationRouter router;
    private final java.util.concurrent.ConcurrentLinkedDeque<io.marketplace.sdk.core.model.LogEntry> logs = new java.util.concurrent.ConcurrentLinkedDeque<>();

    public MarketplaceSDKImpl(ConfigEngine configEngine, OperationRouter router) {
        this.configEngine = configEngine;
        this.router = router;
    }

    @Override
    public MarketplaceClient client() {
        return new MarketplaceClient() {
            @Override
            public OperationResponse execute(OperationRequest request) {
                long start = System.currentTimeMillis();
                OperationResponse response = router.route(request);
                long duration = System.currentTimeMillis() - start;

                // Log ekle (maksimum 50 adet)
                logs.addFirst(new io.marketplace.sdk.core.model.LogEntry(
                    java.time.LocalDateTime.now().toString(),
                    request.getMarketplace(),
                    request.getOperation().name(),
                    response.getHttpStatus(),
                    duration
                ));
                if (logs.size() > 50) logs.removeLast();

                return response;
            }
            @Override
            public boolean supports(MarketplaceType marketplace, Operation operation) {
                if(!router.hasAdapter(marketplace)) return false;
                return router.getAdapter(marketplace).supportedOperations().contains(operation);
            }
            @Override
            public boolean healthCheck(MarketplaceType marketplace) {
                if(!router.hasAdapter(marketplace)) return false;
                return router.getAdapter(marketplace).healthCheck();
            }
            @Override
            public void reloadConfig(MarketplaceType marketplace) {
                configEngine.reload(marketplace);
            }
            @Override
            public void shutdown() {
                MarketplaceSDKImpl.this.shutdown();
            }
        };
    }

    @Override
    public AsyncMarketplaceClient asyncClient() {
        MarketplaceClient syncClient = client();
        return new AsyncMarketplaceClient() {
            // Asenkron implementasyonların stubları (gelecekte multi-threading uygulanacak)
            @Override
            public CompletableFuture<OperationResponse> executeAsync(OperationRequest request) {
                return CompletableFuture.supplyAsync(() -> syncClient.execute(request));
            }

            @Override
            public CompletableFuture<List<OperationResponse>> executeAll(List<OperationRequest> requests) {
                return CompletableFuture.completedFuture(
                    requests.stream().map(syncClient::execute).toList()
                );
            }

            @Override public OperationResponse execute(OperationRequest r) { return syncClient.execute(r); }
            @Override public boolean supports(MarketplaceType m, Operation o) { return syncClient.supports(m, o); }
            @Override public boolean healthCheck(MarketplaceType m) { return syncClient.healthCheck(m); }
            @Override public void reloadConfig(MarketplaceType m) { syncClient.reloadConfig(m); }
            @Override public void shutdown() { syncClient.shutdown(); }
        };
    }

    @Override
    public java.util.List<io.marketplace.sdk.core.model.LogEntry> getRecentLogs() {
        return new java.util.ArrayList<>(logs);
    }

    @Override
    public java.util.Map<io.marketplace.sdk.core.model.MarketplaceType, io.marketplace.sdk.core.spi.AdapterConfig> getConfigurations() {
        return configEngine.getAllConfigs();
    }

    @Override
    public String getRawConfig(io.marketplace.sdk.core.model.MarketplaceType type) {
        return configEngine.getRawConfig(type);
    }

    @Override
    public void updateConfig(io.marketplace.sdk.core.model.MarketplaceType type, String content) {
        configEngine.saveRawConfig(type, content);
    }

    @Override
    public void shutdown() {
        configEngine.stopHotReload();
        configEngine.getAllConfigs().keySet().forEach(type -> {
            if(router.hasAdapter(type)) {
                router.getAdapter(type).shutdown();
            }
        });
    }
}
