package io.marketplace.sdk.config;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;

public class AdapterRegistry {
    public MarketplaceAdapter createAdapter(MarketplaceType type) {
        String className = switch (type) {
            case TRENDYOL    -> "io.marketplace.sdk.adapters.TrendyolAdapter";
            case HEPSIBURADA -> "io.marketplace.sdk.adapters.HepsiburadaAdapter";
            case N11         -> "io.marketplace.sdk.adapters.N11Adapter";
            case AMAZON_TR   -> "io.marketplace.sdk.adapters.AmazonAdapter";
            default          -> null;
        };

        if (className == null) return null;

        try {
            return (MarketplaceAdapter) Class.forName(className).getDeclaredConstructor().newInstance();
        } catch (ClassNotFoundException e) {
            // Enterprise adapters may not be present in open-source core
            return null;
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate adapter for " + type + ": " + className, e);
        }
    }
}
