package io.marketplace.sdk.core.spi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class OperationConfig {

    /** cache: { ttlSeconds: 86400, maxSize: 10 } */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CacheConfig {
        private long ttlSeconds = 300;
        private int  maxSize    = 100;

        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
        public int  getMaxSize()    { return maxSize; }
        public void setMaxSize(int maxSize)         { this.maxSize = maxSize; }
    }

    private String method;
    private String path;
    private List<Map<String, Object>> queryParams;
    private Map<String, String> responseMapping;
    private Map<String, String> requestMapping;
    private String requestTemplate;
    private String contentType;
    private CacheConfig cache;

    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public List<Map<String, Object>> getQueryParams() { return queryParams; }
    public void setQueryParams(List<Map<String, Object>> queryParams) { this.queryParams = queryParams; }
    public Map<String, String> getResponseMapping() { return responseMapping; }
    public void setResponseMapping(Map<String, String> responseMapping) { this.responseMapping = responseMapping; }
    public Map<String, String> getRequestMapping() { return requestMapping; }
    public void setRequestMapping(Map<String, String> requestMapping) { this.requestMapping = requestMapping; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getContentType() { return contentType != null ? contentType : "application/json"; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public CacheConfig getCache() { return cache; }
    public void setCache(CacheConfig cache) { this.cache = cache; }
    public boolean isCacheable() { return cache != null; }
}
