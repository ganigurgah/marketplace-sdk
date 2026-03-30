package io.marketplace.sdk.config;

import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.async.AsyncMarketplaceClient;
import io.marketplace.sdk.core.model.LogEntry;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.OperationRouter;
import io.marketplace.sdk.core.spi.AdapterConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;

public class MarketplaceSDKImpl implements MarketplaceSDK {

    private final ConfigEngine primaryEngine;
    private final OperationRouter router;
    private final ConcurrentLinkedDeque<LogEntry> logs = new ConcurrentLinkedDeque<>();

    public MarketplaceSDKImpl(ConfigEngine primaryEngine, OperationRouter router) {
        this.primaryEngine = primaryEngine;
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

                logs.addFirst(new LogEntry(
                    java.time.LocalDateTime.now().toString(),
                    request.getMarketplace(),
                    request.getOperation().name(),
                    response.getHttpStatus(),
                    duration
                ));
                if (logs.size() > 100) logs.removeLast();

                return response;
            }

            @Override
            public boolean supports(MarketplaceType marketplace, Operation operation) {
                if (!router.hasAdapter(marketplace)) return false;
                return router.getAdapter(marketplace).supportedOperations().contains(operation);
            }

            @Override
            public boolean healthCheck(MarketplaceType marketplace) {
                if (!router.hasAdapter(marketplace)) return false;
                return router.getAdapter(marketplace).healthCheck();
            }

            @Override
            public void reloadConfig(MarketplaceType marketplace) {
                primaryEngine.reload(marketplace);
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
            @Override
            public CompletableFuture<OperationResponse> executeAsync(OperationRequest request) {
                return CompletableFuture.supplyAsync(() -> syncClient.execute(request));
            }

            @Override
            public CompletableFuture<List<OperationResponse>> executeAll(List<OperationRequest> requests) {
                // Gerçek paralel yürütme — her istek ayrı thread'de
                List<CompletableFuture<OperationResponse>> futures = requests.stream()
                    .map(r -> CompletableFuture.supplyAsync(() -> syncClient.execute(r)))
                    .toList();

                return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .thenApply(v -> futures.stream()
                        .map(CompletableFuture::join)
                        .toList());
            }

            @Override public OperationResponse execute(OperationRequest r) { return syncClient.execute(r); }
            @Override public boolean supports(MarketplaceType m, Operation o) { return syncClient.supports(m, o); }
            @Override public boolean healthCheck(MarketplaceType m) { return syncClient.healthCheck(m); }
            @Override public void reloadConfig(MarketplaceType m) { syncClient.reloadConfig(m); }
            @Override public void shutdown() { syncClient.shutdown(); }
        };
    }

    @Override
    public List<LogEntry> getRecentLogs() {
        return List.copyOf(logs);
    }

    @Override
    public Map<MarketplaceType, AdapterConfig> getConfigurations() {
        return primaryEngine.getAllConfigs();
    }

    @Override
    public String getRawConfig(MarketplaceType type) {
        return primaryEngine.getRawConfig(type);
    }

    @Override
    public void updateConfig(MarketplaceType type, String content) {
        primaryEngine.saveRawConfig(type, content);
    }

    @Override
    public void shutdown() {
        primaryEngine.stopHotReload();
        primaryEngine.getAllConfigs().keySet().forEach(type -> {
            if (router.hasAdapter(type)) {
                router.getAdapter(type).shutdown();
            }
        });
    }
}
