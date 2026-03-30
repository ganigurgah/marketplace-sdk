package io.marketplace.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.marketplace.sdk.core.model.Credentials;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.OperationConfig;
import java.nio.file.Path;
import java.util.Map;

public class YamlConfigLoader {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @SuppressWarnings("unchecked")
    public AdapterConfig load(Path yamlFile) throws Exception {
        Map<String, Object> raw = yamlMapper.readValue(yamlFile.toFile(), Map.class);

        String baseUrl = (String) raw.get("baseUrl");
        int timeoutSeconds = (int) raw.getOrDefault("timeoutSeconds", 30);
        int maxRetries = (int) raw.getOrDefault("maxRetries", 3);

        Map<String, Object> authRaw = (Map<String, Object>) raw.get("auth");
        Credentials credentials = parseCredentials(authRaw, (Map<String, Object>) raw.get("credentials"));

        Map<String, Object> opsRaw = (Map<String, Object>) raw.getOrDefault("operations", Map.of());
        Map<String, OperationConfig> operations = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : opsRaw.entrySet()) {
            OperationConfig opConfig = yamlMapper.convertValue(entry.getValue(), OperationConfig.class);
            operations.put(entry.getKey(), opConfig);
        }

        Map<String, Object> extra = (Map<String, Object>) raw.getOrDefault("extra", Map.of());
        extra = new java.util.HashMap<>(extra);
        extra.put("marketplace", raw.get("marketplace"));

        // sellerId'yi extra'ya da ekle; bu sayede {sellerId} path parametresi
        // credentials'dan otomatik çözümlenir (extra her zaman önce kontrol edilir)
        if (credentials.getSellerId() != null && !extra.containsKey("sellerId")) {
            extra.put("sellerId", credentials.getSellerId());
        }

        return new AdapterConfig(baseUrl, credentials, operations, extra, timeoutSeconds, maxRetries);
    }

    private Credentials parseCredentials(Map<String, Object> auth, Map<String, Object> credRaw) {
        if (credRaw == null) return Credentials.builder().build();
        // sellerId önce credentials bloğundan okunur; yoksa supplierId'ye bakılır (geriye dönük uyum)
        String sellerId = (String) credRaw.get("sellerId");
        if (sellerId == null) sellerId = (String) credRaw.get("supplierId");
        return Credentials.builder()
            .apiKey((String) credRaw.get("apiKey"))
            .apiSecret((String) credRaw.get("apiSecret"))
            .supplierId((String) credRaw.get("supplierId"))
            .sellerId(sellerId)
            .accessToken((String) credRaw.get("accessToken"))
            .refreshToken((String) credRaw.get("refreshToken"))
            .build();
    }
}
