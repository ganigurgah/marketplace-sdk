package io.marketplace.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.marketplace.sdk.core.spi.OperationConfig;
import java.util.HashMap;
import java.util.Map;

public class FieldMapper {
    private final ObjectMapper mapper = new ObjectMapper();

    public Map<String, Object> mapResponse(String rawJson, OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getResponseMapping() == null) return result;

        for (Map.Entry<String, String> mapping : opConfig.getResponseMapping().entrySet()) {
            try {
                Object value = JsonPath.read(rawJson, mapping.getValue());
                result.put(mapping.getKey(), value);
            } catch (PathNotFoundException e) {
                result.put(mapping.getKey(), null);
            }
        }
        return result;
    }

    public <T> T mapAndConvert(String rawJson, OperationConfig opConfig, Class<T> targetType) {
        Map<String, Object> mapped = mapResponse(rawJson, opConfig);
        return mapper.convertValue(mapped, targetType);
    }

    public Map<String, Object> mapRequest(Map<String, Object> normalizedRequest, OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getRequestMapping() == null) return normalizedRequest;

        for (Map.Entry<String, String> mapping : opConfig.getRequestMapping().entrySet()) {
            if (normalizedRequest.containsKey(mapping.getKey())) {
                result.put(mapping.getValue(), normalizedRequest.get(mapping.getKey()));
            }
        }
        return result;
    }

    public Object buildRequestBody(Map<String, Object> normalizedRequest, OperationConfig opConfig) {
        if (opConfig.getRequestTemplate() != null) {
            String template = opConfig.getRequestTemplate();
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\$\\{([a-zA-Z0-9_\\.]+)\\}").matcher(template);
            StringBuilder sb = new StringBuilder();
            
            while (m.find()) {
                String key = m.group(1);
                Object value = getValueByDotPath(normalizedRequest, key);
                String replacement = "null"; // Default fallback
                
                if (value != null) {
                    try {
                        if (value instanceof String) {
                            // Sadece escape ediyoruz, dış JSON içinde tırnakları kullanıcının kendi şablonuna bıraktık
                            String serialized = mapper.writeValueAsString(value);
                            replacement = serialized.substring(1, serialized.length() - 1);
                        } else if (value instanceof Number || value instanceof Boolean) {
                            replacement = value.toString();
                        } else {
                            // Nested array veya object -> doğrudan stringified JSON formatında inject edilecek
                            replacement = mapper.writeValueAsString(value);
                        }
                    } catch (Exception e) {
                        replacement = value.toString().replace("\"", "\\\""); // error fallback
                    }
                }
                m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            return sb.toString();
        }

        // Eğer şablon (requestTemplate) yoksa eski 'requestMapping' ile map moduna fallback yapılır
        return mapRequest(normalizedRequest, opConfig);
    }

    public String renderTemplate(String template, Map<String, Object> params) {
        String result = template;
        if (params == null) return result;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (entry.getValue() == null) continue;
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() instanceof String
                ? "\"" + entry.getValue().toString().replace("\"", "\\\"") + "\""
                : String.valueOf(entry.getValue());
            result = result.replace(placeholder, value);
        }
        return result;
    }

    private Object getValueByDotPath(Map<String, Object> map, String path) {
        if (map == null) return null;
        if (!path.contains(".")) return map.get(path);

        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<?, ?>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }
}
