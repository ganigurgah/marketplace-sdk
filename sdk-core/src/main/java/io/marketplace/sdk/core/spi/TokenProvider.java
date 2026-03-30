package io.marketplace.sdk.core.spi;

import java.util.Map;

/**
 * Token yönetimi ve auth mekanizmaları için SPI
 */
public interface TokenProvider {
    void initialize(AdapterConfig config);
    Map<String, String> getAuthHeaders();
    void invalidateToken();
}
