package io.marketplace.sdk.core.http;

import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;
    private final Map<String, String> queryParams;

    private HttpRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.headers = builder.headers;
        this.body = builder.body;
        this.queryParams = builder.queryParams;
    }

    public String getMethod() { return method; }
    public String getUrl() {
        if (queryParams.isEmpty()) return url;
        StringBuilder sb = new StringBuilder(url);
        sb.append("?");
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            sb.append(entry.getKey()).append("=").append(entry.getValue()).append("&");
        }
        return sb.substring(0, sb.length() - 1);
    }
    public Map<String, String> getHeaders() { return headers; }
    public String getBody() { return body; }

    public static Builder builder(String method, String url) { return new Builder(method, url); }

    public static class Builder {
        private final String method;
        private final String url;
        private final Map<String, String> headers = new HashMap<>();
        private String body;
        private final Map<String, String> queryParams = new HashMap<>();

        public Builder(String method, String url) {
            this.method = method;
            this.url = url;
        }

        public Builder header(String name, String value) {
            this.headers.put(name, value);
            return this;
        }

        public Builder queryParam(String name, String value) {
            this.queryParams.put(name, value);
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public HttpRequest build() { return new HttpRequest(this); }
    }
}
