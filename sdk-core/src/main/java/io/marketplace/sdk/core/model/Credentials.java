package io.marketplace.sdk.core.model;

public class Credentials {
    private final String apiKey;
    private final String apiSecret;
    private final String supplierId;
    private final String sellerId;
    private final String accessToken;
    private final String refreshToken;

    private Credentials(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiSecret = builder.apiSecret;
        this.supplierId = builder.supplierId;
        this.sellerId = builder.sellerId;
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
    }

    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getSupplierId() { return supplierId; }
    public String getSellerId() { return sellerId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String apiKey, apiSecret, supplierId, sellerId, accessToken, refreshToken;
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder apiSecret(String v) { this.apiSecret = v; return this; }
        public Builder supplierId(String v) { this.supplierId = v; return this; }
        public Builder sellerId(String v) { this.sellerId = v; return this; }
        public Builder accessToken(String v) { this.accessToken = v; return this; }
        public Builder refreshToken(String v) { this.refreshToken = v; return this; }
        public Credentials build() { return new Credentials(this); }
    }
}
