package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.spi.TokenProvider;
import io.marketplace.sdk.core.spi.AdapterConfig;
import java.util.Map;

public class AmazonTokenProvider implements TokenProvider {
    private String currentToken = "dummy-amazon-token";

    @Override
    public void initialize(AdapterConfig config) {
        // Amazon SP-API LWA OAuth2 flow
    }

    @Override
    public Map<String, String> getAuthHeaders() {
        return Map.of("x-amz-access-token", currentToken);
    }

    @Override
    public void invalidateToken() {
        this.currentToken = "new-refresh-token";
    }
}
