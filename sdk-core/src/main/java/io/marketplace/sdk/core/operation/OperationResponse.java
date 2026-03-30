package io.marketplace.sdk.core.operation;

import java.util.Map;

public class OperationResponse {
    private final boolean success;
    private final int httpStatus;
    private final Object data;           // normalize edilmiş model
    private final String rawResponse;   // pazaryerinin ham JSON yanıtı
    private final String errorCode;
    private final String errorMessage;
    private final Map<String, String> headers;

    private OperationResponse(Builder builder) {
        this.success = builder.success;
        this.httpStatus = builder.httpStatus;
        this.data = builder.data;
        this.rawResponse = builder.rawResponse;
        this.errorCode = builder.errorCode;
        this.errorMessage = builder.errorMessage;
        this.headers = builder.headers;
    }

    public boolean isSuccess() { return success; }
    public int getHttpStatus() { return httpStatus; }
    public Object getData() { return data; }
    public String getRawResponse() { return rawResponse; }
    public String getErrorCode() { return errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public Map<String, String> getHeaders() { return headers; }

    @SuppressWarnings("unchecked")
    public <T> T getDataAs(Class<T> type) {
        return (T) data;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean success;
        private int httpStatus;
        private Object data;
        private String rawResponse;
        private String errorCode;
        private String errorMessage;
        private Map<String, String> headers = Map.of();

        public Builder success(boolean success) { this.success = success; return this; }
        public Builder httpStatus(int status) { this.httpStatus = status; return this; }
        public Builder data(Object data) { this.data = data; return this; }
        public Builder rawResponse(String raw) { this.rawResponse = raw; return this; }
        public Builder errorCode(String code) { this.errorCode = code; return this; }
        public Builder errorMessage(String msg) { this.errorMessage = msg; return this; }
        public Builder headers(Map<String, String> headers) { this.headers = headers; return this; }

        public OperationResponse build() { return new OperationResponse(this); }
    }
}
