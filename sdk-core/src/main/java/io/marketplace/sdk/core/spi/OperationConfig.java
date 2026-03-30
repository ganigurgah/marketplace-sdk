package io.marketplace.sdk.core.spi;

import java.util.List;
import java.util.Map;

public class OperationConfig {
    private String method;
    private String path;
    // According to SKILL.md v2, yaml has name/type/required object format for queryParams
    private List<Map<String, Object>> queryParams;
    private Map<String, String> responseMapping;
    private Map<String, String> requestMapping;
    private String requestTemplate;
    private String contentType;

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
}
