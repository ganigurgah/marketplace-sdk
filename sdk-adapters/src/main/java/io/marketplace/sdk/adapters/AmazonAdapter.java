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
        return Set.of(Operation.GET_ORDERS, Operation.CREATE_PRODUCT);
    }

    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        AmazonTokenProvider provider = new AmazonTokenProvider();
        provider.initialize(config);
        return provider;
    }

    @Override
    protected HttpRequest createHttpRequest(OperationRequest request) {
        if (request.getOperation() == Operation.CREATE_PRODUCT) {
            String sku = (String) request.getParams().get("sku");
            String url = config.getBaseUrl() + "/listings/2021-08-01/items/A-SELLER/" + sku; 
            HttpRequest.Builder builder = HttpRequest.builder("PUT", url);
            tokenProvider.getAuthHeaders().forEach(builder::header);
            builder.header("Content-Type", "application/json");

            try {
                java.util.Map<String, Object> attr = java.util.Map.of("item_name", java.util.List.of(java.util.Map.of("value", request.getParams().get("title"))));
                java.util.Map<String, Object> bodyMap = java.util.Map.of("productType", "SHIRT", "attributes", attr);
                String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(bodyMap);
                builder.body(body);
            } catch (Exception e) {
                // Ignore parse errors
            }
            return builder.build();
        }

        HttpRequest.Builder builder = HttpRequest.builder("GET", config.getBaseUrl() + "/orders/v0/orders");
        tokenProvider.getAuthHeaders().forEach(builder::header);
        return builder.build();
    }
}
