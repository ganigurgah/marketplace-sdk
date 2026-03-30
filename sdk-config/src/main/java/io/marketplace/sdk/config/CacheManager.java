package io.marketplace.sdk.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.concurrent.TimeUnit;

public class CacheManager {
    private final Cache<String, Object> cache;

    public CacheManager(long duration, TimeUnit unit) {
        this.cache = Caffeine.newBuilder()
            .expireAfterWrite(duration, unit)
            .maximumSize(1000)
            .build();
    }

    public Object get(String key) {
        return cache.getIfPresent(key);
    }

    public void put(String key, Object value) {
        cache.put(key, value);
    }

    public void invalidate(String key) {
        cache.invalidate(key);
    }

    public void clear() {
        cache.invalidateAll();
    }
}
