package io.marketplace.sdk.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.marketplace.sdk.config.FieldMapper;
import io.marketplace.sdk.core.exception.MarketplaceException;
import io.marketplace.sdk.core.exception.OperationNotSupportedException;
import io.marketplace.sdk.core.http.HttpClient;
import io.marketplace.sdk.core.http.HttpRequest;
import io.marketplace.sdk.core.http.HttpResponse;
import io.marketplace.sdk.core.http.OkHttpClientImpl;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import io.marketplace.sdk.core.spi.OperationConfig;
import io.marketplace.sdk.core.spi.TokenProvider;
import io.marketplace.sdk.ratelimit.TokenBucketRateLimiter;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tüm adapter'ların extend ettiği YAML-driven temel sınıf.
 *
 * Çalışma prensibi:
 * 1. execute() → operation adı camelCase'e çevrilir (GET_ORDERS → getOrders)
 * 2. YAML'dan o operasyonun konfigürasyonu okunur
 * 3. Path template doldurulur (/suppliers/{supplierId}/orders → gerçek URL)
 * 4. requestTemplate veya requestMapping ile body üretilir
 * 5. Auth headers TokenProvider'dan alınır
 * 6. HTTP isteği atılır, yanıt responseMapping ile normalize edilir
 *
 * Alt sınıflar SADECE initializeTokenProvider() override eder.
 * createHttpRequest() artık yoktur — her şey YAML'dan otomatik üretilir.
 */
public abstract class BaseAdapter implements MarketplaceAdapter {

    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected AdapterConfig config;
    protected TokenBucketRateLimiter rateLimiter;
    protected TokenProvider tokenProvider;
    protected HttpClient httpClient;
    protected FieldMapper fieldMapper;

    // -------------------------------------------------------------------------
    // Başlatma
    // -------------------------------------------------------------------------

    @Override
    public void initialize(AdapterConfig config) {
        this.config = config;
        this.fieldMapper = new FieldMapper();

        Object permits = config.getExtra("permitsPerSecond");
        this.rateLimiter = new TokenBucketRateLimiter(
                permits instanceof Integer ? (Integer) permits : 10);
        this.tokenProvider = initializeTokenProvider(config);
        this.httpClient = new OkHttpClientImpl(config.getTimeoutSeconds());
    }

    /**
     * Her adapter kendi auth mekanizmasını döner.
     * Basic Auth → BasicAuthTokenProvider
     * OAuth2 → AmazonTokenProvider
     * API Key → ApiKeyTokenProvider
     * Başka hiçbir metot override edilmesi gerekmez.
     */
    protected abstract TokenProvider initializeTokenProvider(AdapterConfig config);

    // -------------------------------------------------------------------------
    // Ana çalışma döngüsü — YAML-driven, kod değişikliği gerektirmez
    // -------------------------------------------------------------------------

    @Override
    public final OperationResponse execute(OperationRequest request) {

        // 1. Rate limit token al
        try {
            rateLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MarketplaceException("RATE_LIMIT_INTERRUPTED", "Rate limit acquire interrupted");
        }

        // 2. GET_ORDERS → getOrders
        String opKey = toYamlKey(request.getOperation().name());

        // 3. YAML'dan operasyon config'ini oku
        OperationConfig opConfig = config.getOperation(opKey);
        if (opConfig == null) {
            throw new OperationNotSupportedException(
                    "Operation '" + opKey + "' is not defined in YAML for " + getType()
                            + ". Add it under 'operations:' in "
                            + getType().name().toLowerCase().replace("_", "-") + ".yaml");
        }

        // 4. YAML'dan HTTP isteği üret
        HttpRequest httpRequest = buildHttpRequest(request, opConfig);

        // 5. İsteği at (retry dahil)
        HttpResponse httpResponse = executeWithRetry(httpRequest);

        // 6. 429 → rate limiter cezası
        if (httpResponse.getStatusCode() == 429) {
            String retryAfter = httpResponse.getHeaders().get("Retry-After");
            int penalty = retryAfter != null ? Integer.parseInt(retryAfter) : 60;
            rateLimiter.penalize(penalty);
            throw new io.marketplace.sdk.core.exception.RateLimitException(penalty);
        }

        // 7. Response normalize
        Object data = parseResponse(httpResponse.getBody(), opConfig);

        return OperationResponse.builder()
                .success(httpResponse.getStatusCode() >= 200 && httpResponse.getStatusCode() < 300)
                .httpStatus(httpResponse.getStatusCode())
                .rawResponse(httpResponse.getBody())
                .data(data)
                .headers(httpResponse.getHeaders())
                .build();
    }

    // -------------------------------------------------------------------------
    // YAML'dan HTTP isteği üretimi
    // -------------------------------------------------------------------------

    /**
     * YAML operasyon konfigürasyonundan HttpRequest üretir.
     * Bu metot override edilmemelidir — YAML değişince davranış otomatik değişir.
     */
    private HttpRequest buildHttpRequest(OperationRequest request, OperationConfig opConfig) {

        // URL: baseUrl + path template doldur
        String url = buildUrl(opConfig.getPath(), request.getParams());

        HttpRequest.Builder builder = HttpRequest.builder(opConfig.getMethod(), url)
                .header("Content-Type", opConfig.getContentType())
                .header("Accept", "application/json");

        // Auth headers — TokenProvider'dan
        tokenProvider.getAuthHeaders().forEach(builder::header);

        // Query params — YAML'daki queryParams listesinden
        if (opConfig.getQueryParams() != null) {
            for (Map<String, Object> qp : opConfig.getQueryParams()) {
                String name = (String) qp.get("name");
                Object val = request.getParams() != null ? request.getParams().get(name) : null;
                if (val != null) {
                    builder.queryParam(name, val.toString());
                } else if (qp.get("defaultValue") != null) {
                    builder.queryParam(name, qp.get("defaultValue").toString());
                }
            }
        }

        // Request body — requestTemplate veya requestMapping tanımlıysa body gönderilir.
        // DELETE de dahildir: Trendyol deleteProduct gibi bazı pazaryerleri
        // DELETE isteğinde body bekler.
        String method = opConfig.getMethod().toUpperCase();
        boolean hasBodyDefinition = opConfig.getRequestTemplate() != null
                || (opConfig.getRequestMapping() != null && !opConfig.getRequestMapping().isEmpty());

        if (hasBodyDefinition &&
                (method.equals("POST") || method.equals("PUT")
                        || method.equals("PATCH") || method.equals("DELETE"))) {
            Map<String, Object> params = request.getParams() != null
                    ? request.getParams()
                    : Map.of();

            // fieldMapper: requestTemplate varsa template engine,
            // yoksa requestMapping ile field dönüşümü yapar
            Object body = fieldMapper.buildRequestBody(params, opConfig);
            if (body != null) {
                builder.body(body instanceof String ? (String) body : toJson(body));
            }
        }

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Yardımcı metodlar
    // -------------------------------------------------------------------------

    /**
     * Path template'deki {param} yerlerini doldurur.
     * Arama sırası: request params → YAML extra → credentials
     *
     * Örnek: /suppliers/{supplierId}/orders
     * → /suppliers/12345/orders
     *
     * Trendyol path'i değişirse sadece trendyol.yaml güncellenir.
     * Bu metoda dokunulmaz.
     */
    protected String buildUrl(String pathTemplate, Map<String, Object> params) {
        Matcher matcher = PATH_PARAM_PATTERN.matcher(pathTemplate);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);

            Object value = null;

            // 1. Request params'a bak
            if (params != null)
                value = params.get(paramName);

            // 2. YAML extra'ya bak (supplierId, sellerId, merchantId...)
            if (value == null)
                value = config.getExtra(paramName);

            // 3. Credentials'a bak
            if (value == null)
                value = resolveCredential(paramName);

            if (value == null) {
                throw new MarketplaceException("MISSING_PATH_PARAM",
                        "Path parameter '{" + paramName + "}' could not be resolved for " + getType()
                                + ". Add it to request params or under 'extra:' in YAML.");
            }

            matcher.appendReplacement(sb, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(sb);
        return config.getBaseUrl() + sb;
    }

    /**
     * Bilinen credential alanlarını isme göre çözer.
     * Adapter'lar kendi özel alanları için override edebilir.
     */
    protected Object resolveCredential(String paramName) {
        if (config.getCredentials() == null)
            return null;
        return switch (paramName.toLowerCase()) {
            case "supplierid" -> config.getCredentials().getSupplierId();
            case "sellerid" -> config.getCredentials().getSellerId();
            case "merchantid" -> config.getCredentials().getSellerId();
            case "apikey" -> config.getCredentials().getApiKey();
            default -> null;
        };
    }

    /**
     * Ham JSON yanıtını responseMapping ile normalize eder.
     * responseMapping tanımlı değilse ham Map döner.
     */
    private Object parseResponse(String rawBody, OperationConfig opConfig) {
        if (rawBody == null || rawBody.isBlank())
            return null;
        try {
            if (opConfig.getResponseMapping() != null && !opConfig.getResponseMapping().isEmpty()) {
                return fieldMapper.mapResponse(rawBody, opConfig);
            }
            return MAPPER.readValue(rawBody, Map.class);
        } catch (Exception e) {
            return rawBody; // XML veya parse edilemeyen yanıtlar için
        }
    }

    private HttpResponse executeWithRetry(HttpRequest request) {
        int maxRetries = config.getMaxRetries();
        int attempt = 0;
        while (true) {
            try {
                HttpResponse response = httpClient.execute(request);
                if (response.getStatusCode() >= 500 && attempt < maxRetries) {
                    attempt++;
                    sleep(1000L * attempt);
                    continue;
                }
                return response;
            } catch (Exception e) {
                if (attempt >= maxRetries) {
                    throw new MarketplaceException("HTTP_ERROR",
                            "Request failed after " + maxRetries + " attempts: " + e.getMessage(), e);
                }
                attempt++;
                sleep(1000L * attempt);
            }
        }
    }

    /** GET_ORDERS → getOrders */
    protected String toYamlKey(String enumName) {
        String[] parts = enumName.toLowerCase(Locale.US).split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    protected String toJson(Object obj) {
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            throw new MarketplaceException("JSON_SERIALIZE_ERROR", "Serialization failed", e);
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean healthCheck() {
        return true;
    }

    @Override
    public void shutdown() {
        if (rateLimiter != null)
            rateLimiter.shutdown();
        if (httpClient != null)
            httpClient.shutdown();
    }
}
