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
        return Set.of(Operation.GET_ORDERS, Operation.GET_CATEGORIES);
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
        String url = config.getBaseUrl() + "/suppliers/" + config.getCredentials().getSupplierId() + "/orders";
        HttpRequest.Builder builder = HttpRequest.builder("GET", url);
        tokenProvider.getAuthHeaders().forEach(builder::header);
        return builder.build();
    }
}
