package io.marketplace.sdk.core.model;

public enum MarketplaceType {
    TRENDYOL,
    HEPSIBURADA,
    N11,
    AMAZON_TR;

    public static MarketplaceType fromString(String value) {
        return valueOf(value.toUpperCase().replace("-", "_"));
    }
}
