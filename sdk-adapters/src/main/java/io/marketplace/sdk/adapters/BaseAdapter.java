package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.TokenProvider;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.ratelimit.TokenBucketRateLimiter;
import io.marketplace.sdk.core.http.HttpClient;
import io.marketplace.sdk.core.http.HttpRequest;
import io.marketplace.sdk.core.http.HttpResponse;

public abstract class BaseAdapter implements MarketplaceAdapter {
    protected AdapterConfig config;
    protected TokenBucketRateLimiter rateLimiter;
    protected TokenProvider tokenProvider;
    protected HttpClient httpClient;

    @Override
    public void initialize(AdapterConfig config) {
        this.config = config;
        Object permits = config.getExtra("permitsPerSecond");
        this.rateLimiter = new TokenBucketRateLimiter(
            permits instanceof Integer ? (Integer) permits : 10
        );
        this.tokenProvider = initializeTokenProvider(config);
        this.httpClient = new io.marketplace.sdk.core.http.OkHttpClientImpl(config.getTimeoutSeconds());
    }

    protected abstract TokenProvider initializeTokenProvider(AdapterConfig config);

    @Override
    public OperationResponse execute(OperationRequest request) {
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Rate limit acquire interrupted", e);
        }

        HttpRequest httpRequest = createHttpRequest(request);
        HttpResponse httpResponse = httpClient.execute(httpRequest);

        if (httpResponse.getStatusCode() == 429) {
            String retryAfter = httpResponse.getHeaders().get("Retry-After");
            int penalty = retryAfter != null ? Integer.parseInt(retryAfter) : 5;
            rateLimiter.penalize(penalty);
        }

        return OperationResponse.builder()
            .success(httpResponse.getStatusCode() >= 200 && httpResponse.getStatusCode() < 300)
            .httpStatus(httpResponse.getStatusCode())
            .rawResponse(httpResponse.getBody())
            .build();
    }

    protected abstract HttpRequest createHttpRequest(OperationRequest request);

    @Override
    public boolean healthCheck() {
        return true; 
    }

    @Override
    public void shutdown() {
        if (rateLimiter != null) rateLimiter.shutdown();
        if (httpClient != null) httpClient.shutdown();
    }
}
