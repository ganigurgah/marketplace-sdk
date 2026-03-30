package io.marketplace.sdk.core.operation;

import io.marketplace.sdk.core.model.MarketplaceType;

import java.util.HashMap;
import java.util.Map;

public class OperationRequest {
    private final MarketplaceType marketplace;
    private final Operation operation;
    private final Map<String, Object> params;
    private Object body;

    private OperationRequest(Builder builder) {
        this.marketplace = builder.marketplace;
        this.operation = builder.operation;
        this.params = builder.params;
        this.body = builder.body;
    }

    public MarketplaceType getMarketplace() { return marketplace; }
    public Operation getOperation() { return operation; }
    public Map<String, Object> getParams() { return params; }
    public Object getBody() { return body; }

    public static Builder builder(MarketplaceType marketplace, Operation operation) {
        return new Builder(marketplace, operation);
    }

    public static class Builder {
        private final MarketplaceType marketplace;
        private final Operation operation;
        private final Map<String, Object> params = new HashMap<>();
        private Object body;

        private Builder(MarketplaceType marketplace, Operation operation) {
            this.marketplace = marketplace;
            this.operation = operation;
        }

        /** Tekli parametre ekler. */
        public Builder param(String key, Object value) {
            this.params.put(key, value);
            return this;
        }

        /** Tüm parametreleri map olarak set eder (mevcut params'ı temizler). */
        public Builder params(Map<String, Object> params) {
            this.params.clear();
            if (params != null) this.params.putAll(params);
            return this;
        }

        /**
         * Multiple instance desteği: hangi adapter instance'ının kullanılacağını belirtir.
         * Belirtilmezse "default" instance kullanılır.
         *
         * Kullanım:
         *   .instanceKey("seller-A")
         */
        public Builder instanceKey(String instanceKey) {
            this.params.put("instanceKey", instanceKey);
            return this;
        }

        public Builder body(Object body) {
            this.body = body;
            return this;
        }

        public OperationRequest build() {
            return new OperationRequest(this);
        }
    }
}
