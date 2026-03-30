package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;
import io.marketplace.sdk.core.http.HttpRequest;

import java.util.Map;
import java.util.Set;

public class AmazonAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.AMAZON_TR;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(Operation.GET_ORDERS);
    }

    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        AmazonTokenProvider provider = new AmazonTokenProvider();
        provider.initialize(config);
        return provider;
    }

    @Override
    protected HttpRequest createHttpRequest(OperationRequest request) {
        HttpRequest.Builder builder = HttpRequest.builder("GET", config.getBaseUrl() + "/orders/v0/orders");
        tokenProvider.getAuthHeaders().forEach(builder::header);
        return builder.build();
    }
}
