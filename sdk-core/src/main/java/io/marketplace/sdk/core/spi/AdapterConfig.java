package io.marketplace.sdk.core.spi;

import io.marketplace.sdk.core.model.Credentials;
import java.util.Map;

/**
 * Config Engine'den adapter'a iletilen yapılandırma objesi.
 */
public class AdapterConfig {
    private final String baseUrl;
    private final Credentials credentials;
    private final Map<String, OperationConfig> operations;
    private final Map<String, Object> extra;
    private final int timeoutSeconds;
    private final int maxRetries;

    public AdapterConfig(String baseUrl, Credentials credentials,
                         Map<String, OperationConfig> operations,
                         Map<String, Object> extra,
                         int timeoutSeconds, int maxRetries) {
        this.baseUrl = baseUrl;
        this.credentials = credentials;
        this.operations = operations;
        this.extra = extra;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    public String getBaseUrl() { return baseUrl; }
    public Credentials getCredentials() { return credentials; }
    public Map<String, OperationConfig> getOperations() { return operations; }
    public OperationConfig getOperation(String name) { return operations.get(name); }
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key) { return (T) extra.get(key); }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
}
