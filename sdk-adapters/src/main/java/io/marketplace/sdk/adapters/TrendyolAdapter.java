package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;
import io.marketplace.sdk.core.http.HttpRequest;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class TrendyolAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.TRENDYOL;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(Operation.GET_ORDERS, Operation.GET_CATEGORIES, Operation.CREATE_PRODUCT);
    }

    @Override
    protected TokenProvider initializeTokenProvider(AdapterConfig config) {
        return new TokenProvider() {
            @Override
            public void initialize(AdapterConfig c) {}
            @Override
            public Map<String, String> getAuthHeaders() {
                String authString = config.getCredentials().getApiKey() + ":" + config.getCredentials().getApiSecret();
                String encodedAuth = Base64.getEncoder().encodeToString(authString.getBytes());
                return Map.of("Authorization", "Basic " + encodedAuth);
            }
            @Override
            public void invalidateToken() {}
        };
    }

    @Override
    protected HttpRequest createHttpRequest(OperationRequest request) {
        if (request.getOperation() == Operation.CREATE_PRODUCT) {
            String url = config.getBaseUrl() + "/integration/product/sellers/" + config.getCredentials().getSupplierId() + "/products";
            HttpRequest.Builder builder = HttpRequest.builder("POST", url);
            tokenProvider.getAuthHeaders().forEach(builder::header);
            builder.header("Content-Type", "application/json");

            try {
                java.util.Map<String, Object> item = new java.util.HashMap<>();
                item.put("barcode", request.getParams().get("barcode"));
                item.put("stockCode", request.getParams().get("sku"));
                item.put("title", request.getParams().get("title"));
                item.put("description", request.getParams().get("description"));
                item.put("attributes", request.getParams().get("attributes"));
                item.put("listPrice", request.getParams().get("price"));
                item.put("salePrice", request.getParams().get("price"));
                item.put("quantity", request.getParams().get("stock"));
                item.put("images", request.getParams().get("images"));
                
                String body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                   java.util.Map.of("items", java.util.List.of(item))
                );
                builder.body(body);
            } catch (Exception e) {
                // Ignore parse errors
            }
            return builder.build();
        }

        String url = config.getBaseUrl() + "/suppliers/" + config.getCredentials().getSupplierId() + "/orders";
        HttpRequest.Builder builder = HttpRequest.builder("GET", url);
        tokenProvider.getAuthHeaders().forEach(builder::header);
        return builder.build();
    }
}
