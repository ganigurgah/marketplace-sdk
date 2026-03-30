package io.marketplace.sdk.core.model;

public record LogEntry(
    String timestamp,
    MarketplaceType marketplace,
    String operation,
    int statusCode,
    long durationMs
) {}
