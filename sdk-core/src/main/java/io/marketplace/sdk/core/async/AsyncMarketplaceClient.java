package io.marketplace.sdk.core.async;

import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public interface AsyncMarketplaceClient extends MarketplaceClient {
    CompletableFuture<OperationResponse> executeAsync(OperationRequest request);
    CompletableFuture<List<OperationResponse>> executeAll(List<OperationRequest> requests);
}
