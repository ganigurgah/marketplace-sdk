---
name: marketplace-sdk
title: Türkiye Pazaryeri Entegrasyon SDK'sı — Enterprise Geliştirme Kılavuzu
version: 2.0.0
language: Java 17+
build_tool: Maven (multi-module)
architecture: Config-Driven Adapter Pattern (Enterprise Edition)
marketplaces: [Trendyol, Hepsiburada, N11, Amazon TR]
enterprise_additions: [TypeSafeMapping, TokenRefresh, AsyncClient, Pagination, RateLimiter, Cache, WebhookHandler]
---

# Marketplace SDK — Agent Geliştirme Kılavuzu (Enterprise Edition)

Bu dosyayı okuyan agent, başka hiçbir kaynağa ihtiyaç duymadan kütüphanenin
tamamını sıfırdan üretebilir. Tüm modüller, dosya yapıları, interface'ler,
implementasyonlar, config şemaları ve Admin UI bu belgede eksiksiz tanımlanmıştır.

**v2.0 ile eklenen enterprise katmanlar:**
- Type-safe model mapping (ClassCastException riski sıfırlandı)
- **Nested JSON Template Motoru:** `requestTemplate` özelliği ile Trendyol/Amazon gibi karmaşık içe içe array ve obje isteyen payload'lar doğrudan YAML ile oluşturulabilir (Jackson String Escaping dahil).
- TokenProvider SPI (OAuth2 token refresh otomasyonu)
- Async client (CompletableFuture tabanlı)
- Otomatik pagination (Stream / Iterator API)
- Token Bucket rate limiter (thread bloklamayan)
- Metadata cache (Caffeine, YAML'dan TTL konfigürasyonu)
- Webhook karşılama ve normalizasyon katmanı
- **Güvenli Admin Control Desk:** Javalin 6 ve Builder Pattern kullanılarak inşa edilmiş, Session tabanlı opsiyonel otantikasyon destekleyen (Admin/Pass), Glassmorphism arayüzlü ve anlık YAML düzenleme özellikli Canlı Kontrol Noktası.

---

## BÖLÜM 0 — Vizyon ve Temel Karar

### Problem
Trendyol, Hepsiburada, N11 ve Amazon TR'nin her birinin ayrı API'si, ayrı auth
mekanizması, ayrı istek/yanıt formatı vardır. Bir pazaryeri API'sini değiştirdiğinde
geliştiriciler Java koduna girmek zorunda kalır.

### Çözüm
Config-Driven Adapter Pattern:
- Her pazaryeri bir `.yaml` dosyasıyla tanımlanır (endpoint, auth, field mapping).
- Java kodu hiç değişmeden `.yaml` dosyası güncellenerek API değişikliklerine uyum sağlanır.
- Admin Dashboard üzerinden YAML'lar görsel olarak düzenlenebilir, anında hot-reload yapılır.
- Kullanıcı tek bir `MarketplaceClient` interface'i üzerinden tüm pazaryerleriyle konuşur.

### Temel Tasarım Kararları
| Karar | Seçim | Neden |
|---|---|---|
| Framework bağımlılığı | Sıfır (saf Java) | Spring olmadan her projede kullanılabilsin |
| HTTP client | OkHttp 4 | Saf Java, olgun, async (.enqueue) desteği var |
| Config format | YAML (SnakeYAML + Jackson) | İnsan tarafından okunabilir, hot-reload kolay |
| Admin UI sunucusu | Javalin 6 | Spring gerektirmez, gömülü çalışır |
| JSON işleme | Jackson | Olgun, JsonPath ile birlikte kullanılır |
| Field mapping | JsonPath (Jayway) + Jackson | YAML dinamikliği + Java tip güvenliği |
| Token yönetimi | TokenProvider SPI | Her auth tipi için plug-in yapısı |
| Rate limiting | Token Bucket (saf Java) | Thread bloklamayan, kota kontrollü |
| Cache | Caffeine | Sıfır bağımlılık riski, yüksek performans |
| Loglama | SLF4J (facade) | Kullanıcı kendi impl'ini seçsin |
| Test | JUnit 5 + WireMock | Gerçek HTTP mock'ları |

---

## BÖLÜM 1 — Proje Yapısı

```
marketplace-sdk/                          ← root (BOM)
├── pom.xml
├── sdk-core/                             ← MODÜL 1: Unified interface & SPI
│   └── src/main/java/io/marketplace/sdk/core/
│       ├── model/                        ← Type-safe domain modeller
│       ├── spi/                          ← Adapter + TokenProvider SPI
│       ├── operation/                    ← Operation, Request, Response, Router
│       ├── async/                        ← AsyncMarketplaceClient
│       ├── pagination/                   ← PageableResponse, MarketplaceStream
│       ├── http/                         ← HttpClient, OkHttp impl
│       └── exception/
├── sdk-config/                           ← MODÜL 2: YAML engine & hot-reload
│   └── src/main/java/io/marketplace/sdk/config/
│       ├── ConfigEngine.java
│       ├── YamlConfigLoader.java
│       ├── HotReloadWatcher.java
│       ├── AdapterRegistry.java
│       ├── FieldMapper.java              ← JsonPath + Jackson (type-safe)
│       ├── CacheManager.java             ← Caffeine wrapper
│       └── ConfigValidator.java
├── sdk-adapters/                         ← MODÜL 3: Pazaryeri adapterleri
│   └── src/main/java/io/marketplace/sdk/adapter/
│       ├── BaseAdapter.java              ← RateLimiter + async + cache entegre
│       ├── trendyol/
│       │   ├── TrendyolAdapter.java
│       │   └── TrendyolTokenProvider.java
│       ├── hepsiburada/
│       │   └── HepsiburadaAdapter.java
│       ├── n11/
│       │   └── N11Adapter.java
│       └── amazon/
│           ├── AmazonAdapter.java
│           └── AmazonTokenProvider.java  ← LWA OAuth2 refresh
├── sdk-ratelimit/                        ← MODÜL 4: Token Bucket rate limiter
│   └── src/main/java/io/marketplace/sdk/ratelimit/
│       ├── RateLimiter.java
│       ├── TokenBucketRateLimiter.java
│       └── RateLimiterConfig.java
├── sdk-webhook/                          ← MODÜL 5: Webhook karşılama (ENTERPRISE)
│   └── src/main/java/io/marketplace/sdk/webhook/
│       ├── WebhookHandler.java           ← SPI
│       ├── WebhookEvent.java             ← Normalize edilmiş event modeli
│       ├── WebhookServer.java            ← Javalin tabanlı receiver
│       └── handlers/
│           ├── TrendyolWebhookHandler.java
│           └── HepsiburadaWebhookHandler.java
├── sdk-admin-ui/                         ← MODÜL 6: Web dashboard (ENTERPRISE)
│   └── src/main/java/io/marketplace/sdk/admin/
├── sdk-test-support/                     ← MODÜL 7: WireMock fixtures & helpers
│   └── src/main/java/io/marketplace/sdk/test/
└── marketplace-configs/                  ← MODÜL 8: Varsayılan YAML dosyaları
    ├── trendyol.yaml
    ├── hepsiburada.yaml
    ├── n11.yaml
    └── amazon.yaml
```

---

## BÖLÜM 2 — Root pom.xml (BOM)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>io.marketplace</groupId>
  <artifactId>marketplace-sdk-parent</artifactId>
  <version>1.0.0-SNAPSHOT</version>
  <packaging>pom</packaging>
  <name>Marketplace SDK — Parent</name>

  <modules>
    <module>sdk-core</module>
    <module>sdk-config</module>
    <module>sdk-adapters</module>
    <module>sdk-ratelimit</module>
    <module>sdk-webhook</module>
    <module>sdk-admin-ui</module>
    <module>sdk-test-support</module>
  </modules>

  <properties>
    <java.version>17</java.version>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>

    <!-- dependency versions -->
    <okhttp.version>4.12.0</okhttp.version>
    <snakeyaml.version>2.2</snakeyaml.version>
    <jackson.version>2.17.0</jackson.version>
    <jsonpath.version>2.9.0</jsonpath.version>
    <javalin.version>6.1.6</javalin.version>
    <caffeine.version>3.1.8</caffeine.version>
    <slf4j.version>2.0.13</slf4j.version>
    <logback.version>1.5.6</logback.version>
    <junit.version>5.10.2</junit.version>
    <wiremock.version>3.5.4</wiremock.version>
    <assertj.version>3.25.3</assertj.version>
    <mockito.version>5.11.0</mockito.version>
  </properties>

  <dependencyManagement>
    <dependencies>
      <!-- Internal modules -->
      <dependency>
        <groupId>io.marketplace</groupId>
        <artifactId>sdk-core</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.marketplace</groupId>
        <artifactId>sdk-config</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.marketplace</groupId>
        <artifactId>sdk-adapters</artifactId>
        <version>${project.version}</version>
      </dependency>
      <dependency>
        <groupId>io.marketplace</groupId>
        <artifactId>sdk-test-support</artifactId>
        <version>${project.version}</version>
        <scope>test</scope>
      </dependency>

      <!-- HTTP -->
      <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>
        <version>${okhttp.version}</version>
      </dependency>

      <!-- Config -->
      <dependency>
        <groupId>org.yaml</groupId>
        <artifactId>snakeyaml</artifactId>
        <version>${snakeyaml.version}</version>
      </dependency>
      <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>${jackson.version}</version>
      </dependency>
      <dependency>
        <groupId>com.fasterxml.jackson.dataformat</groupId>
        <artifactId>jackson-dataformat-yaml</artifactId>
        <version>${jackson.version}</version>
      </dependency>
      <dependency>
        <groupId>com.jayway.jsonpath</groupId>
        <artifactId>json-path</artifactId>
        <version>${jsonpath.version}</version>
      </dependency>

      <!-- Admin UI -->
      <dependency>
        <groupId>io.javalin</groupId>
        <artifactId>javalin</artifactId>
        <version>${javalin.version}</version>
      </dependency>

      <!-- Cache -->
      <dependency>
        <groupId>com.github.ben-manes.caffeine</groupId>
        <artifactId>caffeine</artifactId>
        <version>${caffeine.version}</version>
      </dependency>

      <!-- Logging -->
      <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-api</artifactId>
        <version>${slf4j.version}</version>
      </dependency>
      <dependency>
        <groupId>ch.qos.logback</groupId>
        <artifactId>logback-classic</artifactId>
        <version>${logback.version}</version>
      </dependency>

      <!-- Test -->
      <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>${junit.version}</version>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock</artifactId>
        <version>${wiremock.version}</version>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <version>${assertj.version}</version>
        <scope>test</scope>
      </dependency>
      <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>${mockito.version}</version>
        <scope>test</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.13.0</version>
      </plugin>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.2.5</version>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## BÖLÜM 3 — MODÜL 1: sdk-core

### 3.1 Görev
Tüm SDK'nın üzerine inşa edileceği temel abstraction katmanı.
Framework bağımlılığı sıfır. Sadece Java 17 standart kütüphanesi kullanılır.

### 3.2 Paket Yapısı

```
io.marketplace.sdk.core
├── MarketplaceSDK.java           ← Builder / entry point
├── MarketplaceClient.java        ← Kullanıcının çağırdığı ana interface
├── spi/
│   ├── MarketplaceAdapter.java   ← Her adapter'ın implement ettiği SPI
│   └── AdapterConfig.java        ← Adapter'a iletilen config objesi
├── operation/
│   ├── Operation.java            ← Desteklenen operasyon enum'ı
│   ├── OperationRequest.java     ← Generic istek sarmalayıcı
│   ├── OperationResponse.java    ← Generic yanıt sarmalayıcı
│   └── OperationRouter.java      ← Doğru adapter'ı seçer
├── model/
│   ├── MarketplaceType.java      ← TRENDYOL, HEPSIBURADA, N11, AMAZON_TR
│   ├── Credentials.java
│   ├── Order.java                ← Normalize sipariş modeli
│   ├── OrderLine.java
│   ├── Product.java              ← Normalize ürün modeli
│   ├── StockUpdate.java
│   └── PriceUpdate.java
├── http/
│   ├── HttpClient.java           ← Interface
│   ├── HttpRequest.java
│   ├── HttpResponse.java
│   └── OkHttpClientImpl.java     ← Default implementasyon
└── exception/
    ├── MarketplaceException.java
    ├── AuthException.java
    ├── RateLimitException.java
    ├── OperationNotSupportedException.java
    └── ConfigNotFoundException.java
```

### 3.3 Tüm Sınıfların Tam Kodları

#### MarketplaceType.java
```java
package io.marketplace.sdk.core.model;

public enum MarketplaceType {
    TRENDYOL,
    HEPSIBURADA,
    N11,
    AMAZON_TR;

    public static MarketplaceType fromString(String value) {
        return valueOf(value.toUpperCase().replace("-", "_"));
    }
}
```

#### Operation.java
```java
package io.marketplace.sdk.core.operation;

/**
 * SDK'nın desteklediği tüm operasyonlar.
 * Yeni bir operasyon eklendiğinde buraya eklenir,
 * ilgili adapter'ların execute() metodu güncellenir.
 */
public enum Operation {
    // Sipariş
    GET_ORDERS,
    GET_ORDER_DETAIL,
    UPDATE_ORDER_STATUS,
    CANCEL_ORDER,

    // Ürün
    GET_PRODUCTS,
    CREATE_PRODUCT,
    UPDATE_PRODUCT,
    DELETE_PRODUCT,

    // Stok & Fiyat
    UPDATE_STOCK,
    UPDATE_PRICE,
    BATCH_UPDATE_STOCK,
    BATCH_UPDATE_PRICE,

    // Katalog
    GET_CATEGORIES,
    GET_BRANDS,
    GET_ATTRIBUTES,

    // Kargo
    GET_SHIPMENT_PROVIDERS,
    CREATE_SHIPMENT,
    GET_SHIPMENT_DETAIL,

    // Finans
    GET_SETTLEMENTS,
    GET_INVOICES
}
```

#### OperationRequest.java
```java
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

        public Builder param(String key, Object value) {
            this.params.put(key, value);
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
```

#### OperationResponse.java
```java
package io.marketplace.sdk.core.operation;

import java.util.Map;

public class OperationResponse {
    private final boolean success;
    private final int httpStatus;
    private final Object data;           // normalize edilmiş model (Order, Product vs.)
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
```

#### MarketplaceAdapter.java (SPI)
```java
package io.marketplace.sdk.core.spi;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.Operation;
import java.util.Set;

/**
 * Tüm pazaryeri adapter'larının implement etmesi gereken SPI.
 * Bu interface değişmeden adapter'lar bağımsız geliştirilebilir.
 */
public interface MarketplaceAdapter {

    /** Bu adapter'ın hangi pazaryerine ait olduğunu döner */
    MarketplaceType getType();

    /**
     * Config yüklendikten sonra adapter'ı hazırlar.
     * HTTP client, auth headers, base URL bu aşamada kurulur.
     */
    void initialize(AdapterConfig config);

    /** Operasyonu çalıştırır, normalize edilmiş OperationResponse döner */
    OperationResponse execute(OperationRequest request);

    /** Bu adapter'ın desteklediği operasyonlar — desteklenmeyen için exception fırlatılmaz, boş yanıt döner */
    Set<Operation> supportedOperations();

    /** Bağlantıyı test eder (ping / auth check) */
    boolean healthCheck();

    /** Adapter'ı kapatır, açık HTTP bağlantılarını serbest bırakır */
    default void shutdown() {}
}
```

#### AdapterConfig.java
```java
package io.marketplace.sdk.core.spi;

import io.marketplace.sdk.core.model.Credentials;
import java.util.Map;

/**
 * Config Engine'den adapter'a iletilen yapılandırma objesi.
 * YAML'daki tüm alanlar buraya parse edilir.
 */
public class AdapterConfig {
    private final String baseUrl;
    private final Credentials credentials;
    private final Map<String, OperationConfig> operations;
    private final Map<String, Object> extra;   // pazaryerine özel alanlar (supplierId vs.)
    private final int timeoutSeconds;
    private final int maxRetries;

    // Constructor + getters (all-args, no setters — immutable)
    public AdapterConfig(String baseUrl, Credentials credentials,
                         Map<String, OperationConfig> operations,
                         Map<String, Object> extra,
                         int timeoutSeconds, int maxRetries) {
        this.baseUrl = baseUrl;
        this.credentials = credentials;
        this.operations = operations;
        this.extra = extra;
        this.timeoutSeconds = timeoutSeconds;
        this.maxRetries = maxRetries;
    }

    public String getBaseUrl() { return baseUrl; }
    public Credentials getCredentials() { return credentials; }
    public Map<String, OperationConfig> getOperations() { return operations; }
    public OperationConfig getOperation(String name) { return operations.get(name); }
    @SuppressWarnings("unchecked")
    public <T> T getExtra(String key) { return (T) extra.get(key); }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public int getMaxRetries() { return maxRetries; }
}
```

#### OperationConfig.java
```java
package io.marketplace.sdk.core.spi;

import java.util.List;
import java.util.Map;

/**
 * YAML'dan okunan tek bir operasyonun konfigürasyonu.
 * Örnek:
 *   getOrders:
 *     method: GET
 *     path: /suppliers/{supplierId}/orders
 *     queryParams: [status, startDate, endDate]
 *     responseMapping:
 *       orderList: "$.content[*]"
 *       orderId:   "$.orderNumber"
 */
public class OperationConfig {
    private String method;           // GET, POST, PUT, DELETE
    private String path;             // /suppliers/{supplierId}/orders
    private List<String> queryParams;
    private Map<String, String> responseMapping;  // normalizedField → jsonPath
    private Map<String, String> requestMapping;   // normalizedField → apiField
    private String requestTemplate;               // Içe içe objeler (nested request data) için Json/Template tasarımı
    private String contentType;      // default: application/json

    // Getters & Setters (SnakeYAML için setter gerekir)
    public String getMethod() { return method; }
    public void setMethod(String method) { this.method = method; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public List<String> getQueryParams() { return queryParams; }
    public void setQueryParams(List<String> queryParams) { this.queryParams = queryParams; }
    public Map<String, String> getResponseMapping() { return responseMapping; }
    public void setResponseMapping(Map<String, String> responseMapping) { this.responseMapping = responseMapping; }
    public Map<String, String> getRequestMapping() { return requestMapping; }
    public void setRequestMapping(Map<String, String> requestMapping) { this.requestMapping = requestMapping; }
    public String getRequestTemplate() { return requestTemplate; }
    public void setRequestTemplate(String requestTemplate) { this.requestTemplate = requestTemplate; }
    public String getContentType() { return contentType != null ? contentType : "application/json"; }
    public void setContentType(String contentType) { this.contentType = contentType; }
}
```

#### Credentials.java
```java
package io.marketplace.sdk.core.model;

/**
 * Pazaryeri kimlik bilgileri. Hangi alan kullanılacağı
 * YAML'daki auth.type'a göre belirlenir.
 */
public class Credentials {
    private final String apiKey;
    private final String apiSecret;
    private final String supplierId;   // Trendyol, N11
    private final String sellerId;     // Hepsiburada, Amazon
    private final String accessToken;  // OAuth2 tabanlılar için
    private final String refreshToken;

    private Credentials(Builder builder) {
        this.apiKey = builder.apiKey;
        this.apiSecret = builder.apiSecret;
        this.supplierId = builder.supplierId;
        this.sellerId = builder.sellerId;
        this.accessToken = builder.accessToken;
        this.refreshToken = builder.refreshToken;
    }

    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getSupplierId() { return supplierId; }
    public String getSellerId() { return sellerId; }
    public String getAccessToken() { return accessToken; }
    public String getRefreshToken() { return refreshToken; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String apiKey, apiSecret, supplierId, sellerId, accessToken, refreshToken;
        public Builder apiKey(String v) { this.apiKey = v; return this; }
        public Builder apiSecret(String v) { this.apiSecret = v; return this; }
        public Builder supplierId(String v) { this.supplierId = v; return this; }
        public Builder sellerId(String v) { this.sellerId = v; return this; }
        public Builder accessToken(String v) { this.accessToken = v; return this; }
        public Builder refreshToken(String v) { this.refreshToken = v; return this; }
        public Credentials build() { return new Credentials(this); }
    }
}
```

#### MarketplaceClient.java
```java
package io.marketplace.sdk.core;

import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;

/**
 * Kullanıcının doğrudan çağırdığı tek interface.
 * Pazaryeri detaylarını gizler.
 *
 * Kullanım:
 *   MarketplaceClient client = MarketplaceSDK.builder()
 *       .configDir("/etc/marketplace/configs")
 *       .build()
 *       .client();
 *
 *   OperationResponse resp = client.execute(
 *       OperationRequest.builder(MarketplaceType.TRENDYOL, Operation.GET_ORDERS)
 *           .param("status", "Created")
 *           .param("startDate", "2024-01-01")
 *           .build()
 *   );
 */
public interface MarketplaceClient {

    OperationResponse execute(OperationRequest request);

    /** Belirli bir operasyonun desteklenip desteklenmediğini kontrol eder */
    boolean supports(MarketplaceType marketplace, Operation operation);

    /** Pazaryerine bağlantı sağlığını kontrol eder */
    boolean healthCheck(MarketplaceType marketplace);

    /** Konfigürasyonu yeniden yükler (hot-reload) */
    void reloadConfig(MarketplaceType marketplace);

    /** Tüm adapter'ları kapatır */
    void shutdown();
}
```

#### OperationRouter.java
```java
package io.marketplace.sdk.core.operation;

import io.marketplace.sdk.core.exception.OperationNotSupportedException;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gelen OperationRequest'i doğru adapter'a yönlendirir.
 * Config Engine adapter'ları kaydeder; Router sadece yönlendirir.
 */
public class OperationRouter {

    private final Map<MarketplaceType, MarketplaceAdapter> adapters = new ConcurrentHashMap<>();

    public void register(MarketplaceAdapter adapter) {
        adapters.put(adapter.getType(), adapter);
    }

    public void deregister(MarketplaceType type) {
        adapters.remove(type);
    }

    public OperationResponse route(OperationRequest request) {
        MarketplaceAdapter adapter = adapters.get(request.getMarketplace());
        if (adapter == null) {
            throw new OperationNotSupportedException(
                "No adapter registered for: " + request.getMarketplace());
        }
        if (!adapter.supportedOperations().contains(request.getOperation())) {
            throw new OperationNotSupportedException(
                request.getOperation() + " not supported by " + request.getMarketplace());
        }
        return adapter.execute(request);
    }

    public boolean hasAdapter(MarketplaceType type) {
        return adapters.containsKey(type);
    }

    public MarketplaceAdapter getAdapter(MarketplaceType type) {
        return adapters.get(type);
    }
}
```

#### MarketplaceSDK.java (Entry Point)
```java
package io.marketplace.sdk.core;

import io.marketplace.sdk.core.operation.OperationRouter;
import java.nio.file.Path;

/**
 * SDK'ya giriş noktası. Builder pattern ile oluşturulur.
 * Config Engine ve Admin UI bu sınıf üzerinden başlatılır.
 */
public class MarketplaceSDK {

    private final OperationRouter router;
    private final MarketplaceClient client;

    private MarketplaceSDK(Builder builder) {
        this.router = new OperationRouter();
        // Config Engine bu builder'dan başlatılır (BÖLÜM 4'te detaylandırılır)
        this.client = new DefaultMarketplaceClient(router);
    }

    public MarketplaceClient client() { return client; }
    public OperationRouter router() { return router; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Path configDir;
        private boolean adminUiEnabled = false;
        private int adminUiPort = 7070;
        private boolean hotReloadEnabled = true;

        public Builder configDir(String path) {
            this.configDir = Path.of(path);
            return this;
        }

        public Builder configDir(Path path) {
            this.configDir = path;
            return this;
        }

        public Builder adminUi(boolean enabled) {
            this.adminUiEnabled = enabled;
            return this;
        }

        public Builder adminUiPort(int port) {
            this.adminUiPort = port;
            return this;
        }

        public Builder hotReload(boolean enabled) {
            this.hotReloadEnabled = enabled;
            return this;
        }

        public MarketplaceSDK build() {
            if (configDir == null) {
                configDir = Path.of("marketplace-configs");
            }
            return new MarketplaceSDK(this);
        }
    }
}
```

#### Exceptions
```java
// MarketplaceException.java — base exception
package io.marketplace.sdk.core.exception;

public class MarketplaceException extends RuntimeException {
    private final String errorCode;
    public MarketplaceException(String message) { super(message); this.errorCode = "UNKNOWN"; }
    public MarketplaceException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
    public MarketplaceException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
    public String getErrorCode() { return errorCode; }
}

// AuthException.java
public class AuthException extends MarketplaceException {
    public AuthException(String message) { super("AUTH_ERROR", message); }
}

// RateLimitException.java
public class RateLimitException extends MarketplaceException {
    private final int retryAfterSeconds;
    public RateLimitException(int retryAfterSeconds) {
        super("RATE_LIMIT", "Rate limit exceeded. Retry after " + retryAfterSeconds + "s");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public int getRetryAfterSeconds() { return retryAfterSeconds; }
}

// OperationNotSupportedException.java
public class OperationNotSupportedException extends MarketplaceException {
    public OperationNotSupportedException(String message) {
        super("OPERATION_NOT_SUPPORTED", message);
    }
}

// ConfigNotFoundException.java
public class ConfigNotFoundException extends MarketplaceException {
    public ConfigNotFoundException(String marketplace) {
        super("CONFIG_NOT_FOUND", "Config not found for marketplace: " + marketplace);
    }
}
```

#### HttpClient.java & OkHttpClientImpl.java
```java
// HttpClient.java
package io.marketplace.sdk.core.http;

public interface HttpClient {
    HttpResponse execute(HttpRequest request);
    void shutdown();
}

// HttpRequest.java
public class HttpRequest {
    private final String method;
    private final String url;
    private final Map<String, String> headers;
    private final String body;

    // Builder pattern — (MarketplaceSDK.java ile aynı pattern)
    public static Builder builder(String method, String url) { return new Builder(method, url); }
    // ... builder inner class
}

// HttpResponse.java
public class HttpResponse {
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;
    // Constructor + getters
}

// OkHttpClientImpl.java
package io.marketplace.sdk.core.http;

import okhttp3.*;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class OkHttpClientImpl implements HttpClient {
    private final OkHttpClient okClient;

    public OkHttpClientImpl(int timeoutSeconds) {
        this.okClient = new OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .writeTimeout(timeoutSeconds, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public HttpResponse execute(HttpRequest request) {
        Request.Builder builder = new Request.Builder().url(request.getUrl());
        request.getHeaders().forEach(builder::addHeader);

        RequestBody requestBody = null;
        if (request.getBody() != null) {
            requestBody = RequestBody.create(
                request.getBody(),
                MediaType.parse("application/json; charset=utf-8")
            );
        }

        builder.method(request.getMethod(), requestBody);

        try (Response response = okClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            Map<String, String> headers = new HashMap<>();
            response.headers().forEach(pair -> headers.put(pair.getFirst(), pair.getSecond()));
            return new HttpResponse(response.code(), responseBody, headers);
        } catch (IOException e) {
            throw new MarketplaceException("HTTP_ERROR", "HTTP request failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void shutdown() {
        okClient.dispatcher().executorService().shutdown();
        okClient.connectionPool().evictAll();
    }
}
```

---

## BÖLÜM 4 — MODÜL 2: sdk-config

### 4.1 Görev
YAML dosyalarını okur, parse eder, AdapterConfig nesnesi üretir ve adapter'ları
OperationRouter'a kaydeder. Hot-reload ile çalışan dosya değişikliği izleyicisi içerir.

### 4.2 Paket Yapısı

```
io.marketplace.sdk.config
├── ConfigEngine.java             ← Ana motor: YAML → AdapterConfig → Router
├── YamlConfigLoader.java         ← YAML okuma ve parse etme
├── HotReloadWatcher.java         ← WatchService ile dosya değişikliği izleme
├── AdapterRegistry.java          ← Adapter instance'larının tutulduğu registry
├── FieldMapper.java              ← JsonPath ile response mapping
└── ConfigValidator.java          ← YAML şema validasyonu
```

### 4.3 YAML Şema Tanımı (tüm pazaryerleri için ortak format)

```yaml
# ── Zorunlu alanlar ──────────────────────────────
marketplace: trendyol              # MarketplaceType enum değeriyle eşleşmeli
baseUrl: https://api.trendyol.com/sapigw
timeoutSeconds: 30
maxRetries: 3

# ── Kimlik doğrulama ─────────────────────────────
auth:
  type: BASIC                      # BASIC | BEARER | OAUTH2 | API_KEY
  usernameField: apiKey            # Credentials sınıfındaki getter adı (camelCase)
  passwordField: apiSecret

# ── Pazaryerine özel alanlar ─────────────────────
extra:
  supplierId: "12345"              # path template'lerinde {supplierId} olarak kullanılır

# ── Operasyon tanımları ───────────────────────────
operations:

  getOrders:
    method: GET
    path: /suppliers/{supplierId}/orders
    queryParams:
      - name: status
        type: STRING
        required: false
      - name: startDate
        type: DATE_ISO
        required: false
      - name: endDate
        type: DATE_ISO
        required: false
      - name: page
        type: INTEGER
        required: false
        default: 0
      - name: size
        type: INTEGER
        required: false
        default: 50
    responseMapping:
      # normalizedFieldName: jsonPath ifadesi
      orderList:       "$.content[*]"
      orderId:         "$.orderNumber"
      orderStatus:     "$.status"
      customerId:      "$.customerId"
      totalAmount:     "$.totalPrice"
      orderDate:       "$.orderDate"
      lines:           "$.lines[*]"

  createProduct:
    method: POST
    path: /suppliers/{supplierId}/v2/products
    contentType: application/json
    requestMapping:
      # normalizedFieldName: apiFieldName
      barcode:         "barcode"
      title:           "title"
      productMainId:   "productMainId"
      brandId:         "brandId"
      categoryId:      "pimCategoryId"
      stockCode:       "stockCode"
      quantity:        "quantity"
      salePrice:       "salePrice"
      listPrice:       "listPrice"
    responseMapping:
      batchRequestId:  "$.batchRequestId"

  updateStock:
    method: POST
    path: /suppliers/{supplierId}/products/price-and-inventory
    requestMapping:
      barcode:         "barcode"
      quantity:        "quantity"
      salePrice:       "salePrice"
      listPrice:       "listPrice"
    responseMapping:
      batchRequestId:  "$.batchRequestId"

  getCategories:
    method: GET
    path: /product-categories
    responseMapping:
      categories:      "$[*]"
      categoryId:      "$.id"
      categoryName:    "$.name"
      parentId:        "$.parentId"
```

### 4.4 ConfigEngine.java (tam kod)

```java
package io.marketplace.sdk.config;

import io.marketplace.sdk.core.exception.ConfigNotFoundException;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.OperationRouter;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConfigEngine {

    private static final Logger log = LoggerFactory.getLogger(ConfigEngine.class);

    private final Path configDir;
    private final OperationRouter router;
    private final YamlConfigLoader loader;
    private final AdapterRegistry adapterRegistry;
    private final Map<MarketplaceType, AdapterConfig> loadedConfigs = new ConcurrentHashMap<>();
    private HotReloadWatcher watcher;

    public ConfigEngine(Path configDir, OperationRouter router) {
        this.configDir = configDir;
        this.router = router;
        this.loader = new YamlConfigLoader();
        this.adapterRegistry = new AdapterRegistry();
    }

    /** Tüm YAML dosyalarını yükler ve adapter'ları başlatır */
    public void loadAll() {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(configDir, "*.yaml")) {
            for (Path yamlFile : stream) {
                loadSingle(yamlFile);
            }
        } catch (Exception e) {
            log.error("Config directory could not be read: {}", configDir, e);
            throw new RuntimeException("Config load failed", e);
        }
    }

    /** Tek bir YAML dosyasını yükler */
    public void loadSingle(Path yamlFile) {
        try {
            AdapterConfig config = loader.load(yamlFile);
            MarketplaceType type = MarketplaceType.fromString(config.getExtra("marketplace"));

            MarketplaceAdapter adapter = adapterRegistry.createAdapter(type);
            adapter.initialize(config);
            router.register(adapter);
            loadedConfigs.put(type, config);

            log.info("Loaded config for {} from {}", type, yamlFile.getFileName());
        } catch (Exception e) {
            log.error("Failed to load config: {}", yamlFile, e);
        }
    }

    /** Belirli bir pazaryeri config'ini yeniden yükler (hot-reload) */
    public void reload(MarketplaceType type) {
        Path yamlFile = configDir.resolve(type.name().toLowerCase().replace("_", "-") + ".yaml");
        if (!Files.exists(yamlFile)) {
            throw new ConfigNotFoundException(type.name());
        }
        loadSingle(yamlFile);
        log.info("Hot-reloaded config for {}", type);
    }

    /** Dosya değişikliği izlemeyi başlatır */
    public void startHotReload() {
        this.watcher = new HotReloadWatcher(configDir, changedFile -> {
            String filename = changedFile.getFileName().toString();
            String marketplaceName = filename.replace(".yaml", "").replace("-", "_").toUpperCase();
            try {
                MarketplaceType type = MarketplaceType.fromString(marketplaceName);
                reload(type);
            } catch (IllegalArgumentException e) {
                log.warn("Changed file {} does not match any known marketplace", filename);
            }
        });
        watcher.start();
    }

    public void stopHotReload() {
        if (watcher != null) watcher.stop();
    }

    public AdapterConfig getConfig(MarketplaceType type) {
        AdapterConfig config = loadedConfigs.get(type);
        if (config == null) throw new ConfigNotFoundException(type.name());
        return config;
    }

    public Map<MarketplaceType, AdapterConfig> getAllConfigs() {
        return Collections.unmodifiableMap(loadedConfigs);
    }
}
```

### 4.5 YamlConfigLoader.java

```java
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

        // Auth parsing
        Map<String, Object> authRaw = (Map<String, Object>) raw.get("auth");
        Credentials credentials = parseCredentials(authRaw, (Map<String, Object>) raw.get("credentials"));

        // Operations parsing
        Map<String, Object> opsRaw = (Map<String, Object>) raw.getOrDefault("operations", Map.of());
        Map<String, OperationConfig> operations = new java.util.LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : opsRaw.entrySet()) {
            OperationConfig opConfig = yamlMapper.convertValue(entry.getValue(), OperationConfig.class);
            operations.put(entry.getKey(), opConfig);
        }

        // Extra (marketplace, supplierId, sellerId vs.)
        Map<String, Object> extra = (Map<String, Object>) raw.getOrDefault("extra", Map.of());
        extra = new java.util.HashMap<>(extra);
        extra.put("marketplace", raw.get("marketplace"));

        return new AdapterConfig(baseUrl, credentials, operations, extra, timeoutSeconds, maxRetries);
    }

    private Credentials parseCredentials(Map<String, Object> auth, Map<String, Object> credRaw) {
        if (credRaw == null) return Credentials.builder().build();
        return Credentials.builder()
            .apiKey((String) credRaw.get("apiKey"))
            .apiSecret((String) credRaw.get("apiSecret"))
            .supplierId((String) credRaw.get("supplierId"))
            .sellerId((String) credRaw.get("sellerId"))
            .accessToken((String) credRaw.get("accessToken"))
            .build();
    }
}
```

### 4.6 HotReloadWatcher.java

```java
package io.marketplace.sdk.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.*;
import java.util.function.Consumer;

public class HotReloadWatcher {

    private static final Logger log = LoggerFactory.getLogger(HotReloadWatcher.class);

    private final Path watchDir;
    private final Consumer<Path> onChanged;
    private volatile boolean running = false;
    private Thread watchThread;

    public HotReloadWatcher(Path watchDir, Consumer<Path> onChanged) {
        this.watchDir = watchDir;
        this.onChanged = onChanged;
    }

    public void start() {
        running = true;
        watchThread = new Thread(() -> {
            try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
                watchDir.register(watchService,
                    StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE);

                log.info("Hot-reload watcher started on: {}", watchDir);

                while (running) {
                    WatchKey key = watchService.poll(2, java.util.concurrent.TimeUnit.SECONDS);
                    if (key == null) continue;

                    for (WatchEvent<?> event : key.pollEvents()) {
                        Path changed = watchDir.resolve((Path) event.context());
                        if (changed.toString().endsWith(".yaml")) {
                            log.info("Config file changed: {}", changed.getFileName());
                            // Kısa bekleme — editörlerin atomic write tamamlanması için
                            Thread.sleep(200);
                            onChanged.accept(changed);
                        }
                    }
                    key.reset();
                }
            } catch (Exception e) {
                if (running) log.error("Hot-reload watcher error", e);
            }
        }, "marketplace-config-watcher");
        watchThread.setDaemon(true);
        watchThread.start();
    }

    public void stop() {
        running = false;
        if (watchThread != null) watchThread.interrupt();
    }
}
```

### 4.7 FieldMapper.java (JsonPath tabanlı response mapping)

```java
package io.marketplace.sdk.config;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.marketplace.sdk.core.spi.OperationConfig;

import java.util.HashMap;
import java.util.Map;

/**
 * YAML'daki responseMapping tanımlarını kullanarak
 * ham JSON yanıtını normalize edilmiş Map'e dönüştürür.
 *
 * Örnek:
 *   responseMapping:
 *     orderId: "$.orderNumber"
 *   Ham JSON: {"orderNumber": "TY-123", ...}
 *   Çıktı:    {"orderId": "TY-123", ...}
 */
public class FieldMapper {

    public Map<String, Object> mapResponse(String rawJson, OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getResponseMapping() == null) {
            return result;
        }

        for (Map.Entry<String, String> mapping : opConfig.getResponseMapping().entrySet()) {
            String normalizedField = mapping.getKey();
            String jsonPath = mapping.getValue();
            try {
                Object value = JsonPath.read(rawJson, jsonPath);
                result.put(normalizedField, value);
            } catch (PathNotFoundException e) {
                result.put(normalizedField, null);
            }
        }
        return result;
    }

    public Map<String, Object> mapRequest(Map<String, Object> normalizedRequest,
                                           OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getRequestMapping() == null) {
            return normalizedRequest;
        }

        for (Map.Entry<String, String> mapping : opConfig.getRequestMapping().entrySet()) {
            String normalizedField = mapping.getKey();
            String apiField = mapping.getValue();
            if (normalizedRequest.containsKey(normalizedField)) {
                result.put(apiField, normalizedRequest.get(normalizedField));
            }
        }
        return result;
    }

    public Object buildRequestBody(Map<String, Object> normalizedRequest, OperationConfig opConfig) {
        if (opConfig.getRequestTemplate() != null) {
            String template = opConfig.getRequestTemplate();
            return renderTemplate(template, normalizedRequest);
        }
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
            } else { return null; }
        }
        return current;
    }
}
```

---

## BÖLÜM 5 — MODÜL 3: sdk-adapters

### 5.1 Görev
Her pazaryeri için MarketplaceAdapter SPI'ını implement eden somut sınıflar.
Auth header'ları, path template doldurma ve HTTP çağrısı bu katmanda yapılır.

### 5.2 Paket Yapısı

```
io.marketplace.sdk.adapter
├── BaseAdapter.java                ← Ortak HTTP + path logic
├── trendyol/
│   └── TrendyolAdapter.java
├── hepsiburada/
│   └── HepsiburadaAdapter.java
├── n11/
│   └── N11Adapter.java
└── amazon/
    └── AmazonAdapter.java
```

### 5.3 BaseAdapter.java (ortak logic)

```java
package io.marketplace.sdk.adapter;

import io.marketplace.sdk.config.FieldMapper;
import io.marketplace.sdk.core.exception.MarketplaceException;
import io.marketplace.sdk.core.exception.RateLimitException;
import io.marketplace.sdk.core.http.HttpClient;
import io.marketplace.sdk.core.http.HttpRequest;
import io.marketplace.sdk.core.http.HttpResponse;
import io.marketplace.sdk.core.http.OkHttpClientImpl;
import io.marketplace.sdk.core.operation.*;
import io.marketplace.sdk.core.spi.AdapterConfig;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;
import io.marketplace.sdk.core.spi.OperationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tüm adapter'ların extend ettiği base sınıf.
 * Path template doldurma, retry logic ve response mapping içerir.
 * Her alt sınıf sadece buildAuthHeaders() metodunu implement etmek zorundadır.
 */
public abstract class BaseAdapter implements MarketplaceAdapter {

    protected final Logger log = LoggerFactory.getLogger(getClass());
    private static final Pattern PATH_PARAM_PATTERN = Pattern.compile("\\{(\\w+)}");

    protected AdapterConfig config;
    protected HttpClient httpClient;
    protected final FieldMapper fieldMapper = new FieldMapper();

    @Override
    public void initialize(AdapterConfig config) {
        this.config = config;
        this.httpClient = new OkHttpClientImpl(config.getTimeoutSeconds());
        log.info("{} adapter initialized with baseUrl={}", getType(), config.getBaseUrl());
    }

    @Override
    public OperationResponse execute(OperationRequest request) {
        String opName = camelCase(request.getOperation().name());
        OperationConfig opConfig = config.getOperation(opName);

        if (opConfig == null) {
            throw new MarketplaceException("CONFIG_MISSING",
                "No operation config found for: " + opName + " in " + getType());
        }

        String url = buildUrl(opConfig.getPath(), request.getParams());
        Map<String, String> authHeaders = buildAuthHeaders();

        HttpRequest.Builder reqBuilder = HttpRequest.builder(opConfig.getMethod(), url)
            .header("Content-Type", opConfig.getContentType())
            .header("Accept", "application/json");

        authHeaders.forEach(reqBuilder::header);

        // Request body mapping (Template Engine or Object Mapping)
        if (opConfig.getRequestTemplate() != null) {
            String renderedJson = fieldMapper.renderTemplate(
                opConfig.getRequestTemplate(), request.getParams() != null ? request.getParams() : (Map<String,Object>) request.getBody());
            reqBuilder.body(renderedJson);
        } else if (request.getBody() != null) {
            Map<String, Object> mappedBody = fieldMapper.mapRequest(
                (Map<String, Object>) request.getBody(), opConfig);
            reqBuilder.body(toJson(mappedBody));
        }

        // Query params
        if (opConfig.getQueryParams() != null) {
            for (var qp : opConfig.getQueryParams()) {
                Object val = request.getParams().get(qp.getName());
                if (val != null) reqBuilder.queryParam(qp.getName(), val.toString());
            }
        }

        HttpResponse httpResponse = executeWithRetry(reqBuilder.build());

        if (httpResponse.getStatusCode() == 429) {
            int retryAfter = Integer.parseInt(
                httpResponse.getHeaders().getOrDefault("Retry-After", "60"));
            throw new RateLimitException(retryAfter);
        }

        boolean success = httpResponse.getStatusCode() >= 200 && httpResponse.getStatusCode() < 300;
        Map<String, Object> mappedData = fieldMapper.mapResponse(httpResponse.getBody(), opConfig);

        return OperationResponse.builder()
            .success(success)
            .httpStatus(httpResponse.getStatusCode())
            .data(mappedData)
            .rawResponse(httpResponse.getBody())
            .headers(httpResponse.getHeaders())
            .build();
    }

    /** Her adapter kendi auth header'larını üretir */
    protected abstract Map<String, String> buildAuthHeaders();

    protected String buildUrl(String pathTemplate, Map<String, Object> params) {
        StringBuilder url = new StringBuilder(config.getBaseUrl());
        Matcher matcher = PATH_PARAM_PATTERN.matcher(pathTemplate);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = params.getOrDefault(paramName, config.getExtra(paramName));
            if (value == null) {
                throw new MarketplaceException("MISSING_PATH_PARAM",
                    "Path param not found: " + paramName);
            }
            matcher.appendReplacement(sb, value.toString());
        }
        matcher.appendTail(sb);
        url.append(sb);
        return url.toString();
    }

    private HttpResponse executeWithRetry(HttpRequest request) {
        int attempts = 0;
        while (attempts <= config.getMaxRetries()) {
            try {
                return httpClient.execute(request);
            } catch (MarketplaceException e) {
                attempts++;
                if (attempts > config.getMaxRetries()) throw e;
                log.warn("Attempt {} failed, retrying...", attempts);
                sleep(1000L * attempts);
            }
        }
        throw new MarketplaceException("MAX_RETRIES_EXCEEDED", "All retry attempts failed");
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    /** Operation enum adını (GET_ORDERS) camelCase'e (getOrders) çevirir */
    protected String camelCase(String enumName) {
        String[] parts = enumName.toLowerCase().split("_");
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            sb.append(Character.toUpperCase(parts[i].charAt(0)));
            sb.append(parts[i].substring(1));
        }
        return sb.toString();
    }

    protected abstract String toJson(Object obj);

    @Override
    public boolean healthCheck() {
        try {
            OperationRequest req = OperationRequest.builder(getType(), Operation.GET_CATEGORIES).build();
            OperationResponse resp = execute(req);
            return resp.isSuccess();
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", getType(), e.getMessage());
            return false;
        }
    }

    @Override
    public void shutdown() {
        if (httpClient != null) httpClient.shutdown();
    }
}
```

### 5.4 TrendyolAdapter.java

```java
package io.marketplace.sdk.adapter.trendyol;

import io.marketplace.sdk.adapter.BaseAdapter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;

import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class TrendyolAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() {
        return MarketplaceType.TRENDYOL;
    }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS,
            Operation.GET_ORDER_DETAIL,
            Operation.UPDATE_ORDER_STATUS,
            Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT,
            Operation.UPDATE_PRODUCT,
            Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE,
            Operation.BATCH_UPDATE_STOCK,
            Operation.BATCH_UPDATE_PRICE,
            Operation.GET_CATEGORIES,
            Operation.GET_BRANDS,
            Operation.GET_ATTRIBUTES,
            Operation.GET_SHIPMENT_PROVIDERS,
            Operation.CREATE_SHIPMENT
        );
    }

    /**
     * Trendyol: Basic Auth = Base64(apiKey:apiSecret)
     * User-Agent header zorunlu.
     */
    @Override
    protected Map<String, String> buildAuthHeaders() {
        String apiKey = config.getCredentials().getApiKey();
        String apiSecret = config.getCredentials().getApiSecret();
        String supplierId = config.getExtra("supplierId");

        String token = Base64.getEncoder()
            .encodeToString((apiKey + ":" + apiSecret).getBytes());

        return Map.of(
            "Authorization", "Basic " + token,
            "User-Agent", supplierId + " - SelfIntegration"
        );
    }

    @Override
    protected String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new io.marketplace.sdk.core.exception.MarketplaceException(
                "JSON_ERROR", "Serialization failed", e);
        }
    }
}
```

### 5.5 HepsiburadaAdapter.java

```java
package io.marketplace.sdk.adapter.hepsiburada;

import io.marketplace.sdk.adapter.BaseAdapter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

public class HepsiburadaAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() { return MarketplaceType.HEPSIBURADA; }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS, Operation.GET_ORDER_DETAIL,
            Operation.UPDATE_ORDER_STATUS, Operation.GET_PRODUCTS,
            Operation.CREATE_PRODUCT, Operation.UPDATE_STOCK,
            Operation.UPDATE_PRICE, Operation.GET_CATEGORIES,
            Operation.GET_SHIPMENT_PROVIDERS
        );
    }

    /**
     * Hepsiburada: Basic Auth = Base64(username:password)
     * username = merchantId, password = apiPassword
     */
    @Override
    protected Map<String, String> buildAuthHeaders() {
        String merchantId = config.getCredentials().getSellerId();
        String password = config.getCredentials().getApiSecret();
        String token = Base64.getEncoder()
            .encodeToString((merchantId + ":" + password).getBytes());
        return Map.of("Authorization", "Basic " + token);
    }

    @Override
    protected String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new io.marketplace.sdk.core.exception.MarketplaceException("JSON_ERROR", "Serialization failed", e);
        }
    }
}
```

### 5.6 N11Adapter.java

```java
package io.marketplace.sdk.adapter.n11;

import io.marketplace.sdk.adapter.BaseAdapter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import java.util.Map;
import java.util.Set;

public class N11Adapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() { return MarketplaceType.N11; }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS, Operation.GET_ORDER_DETAIL,
            Operation.GET_PRODUCTS, Operation.CREATE_PRODUCT,
            Operation.UPDATE_STOCK, Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES
        );
    }

    /**
     * N11: Header-based API Key
     * appKey + appSecret header olarak gönderilir.
     */
    @Override
    protected Map<String, String> buildAuthHeaders() {
        return Map.of(
            "appKey",    config.getCredentials().getApiKey(),
            "appSecret", config.getCredentials().getApiSecret()
        );
    }

    @Override
    protected String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new io.marketplace.sdk.core.exception.MarketplaceException("JSON_ERROR", "Serialization failed", e);
        }
    }
}
```

### 5.7 AmazonAdapter.java

```java
package io.marketplace.sdk.adapter.amazon;

import io.marketplace.sdk.adapter.BaseAdapter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import java.util.Map;
import java.util.Set;

public class AmazonAdapter extends BaseAdapter {

    @Override
    public MarketplaceType getType() { return MarketplaceType.AMAZON_TR; }

    @Override
    public Set<Operation> supportedOperations() {
        return Set.of(
            Operation.GET_ORDERS, Operation.GET_ORDER_DETAIL,
            Operation.GET_PRODUCTS, Operation.CREATE_PRODUCT,
            Operation.UPDATE_STOCK, Operation.UPDATE_PRICE,
            Operation.GET_CATEGORIES, Operation.GET_SHIPMENT_PROVIDERS,
            Operation.GET_SETTLEMENTS
        );
    }

    /**
     * Amazon SP-API: Bearer token (LWA OAuth2)
     * accessToken her ~1 saatte yenilenir.
     * TODO: token refresh logic → LwATokenProvider sınıfı olarak ayrıştırılacak.
     */
    @Override
    protected Map<String, String> buildAuthHeaders() {
        String accessToken = config.getCredentials().getAccessToken();
        return Map.of(
            "x-amz-access-token", accessToken,
            "x-amz-date", java.time.Instant.now().toString()
        );
    }

    @Override
    protected String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            throw new io.marketplace.sdk.core.exception.MarketplaceException("JSON_ERROR", "Serialization failed", e);
        }
    }
}
```

### 5.8 AdapterRegistry.java

```java
package io.marketplace.sdk.config;

import io.marketplace.sdk.adapter.amazon.AmazonAdapter;
import io.marketplace.sdk.adapter.hepsiburada.HepsiburadaAdapter;
import io.marketplace.sdk.adapter.n11.N11Adapter;
import io.marketplace.sdk.adapter.trendyol.TrendyolAdapter;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;

/**
 * MarketplaceType → Adapter sınıfı eşlemesi.
 * Yeni bir pazaryeri eklendiğinde sadece buraya eklenir.
 */
public class AdapterRegistry {

    public MarketplaceAdapter createAdapter(MarketplaceType type) {
        return switch (type) {
            case TRENDYOL    -> new TrendyolAdapter();
            case HEPSIBURADA -> new HepsiburadaAdapter();
            case N11         -> new N11Adapter();
            case AMAZON_TR   -> new AmazonAdapter();
        };
    }
}
```

---

## BÖLÜM 6 — MODÜL 4: sdk-admin-ui

### 6.1 Görev
Gömülü web sunucusu (Javalin) üzerinde çalışan yönetim paneli.
YAML dosyalarını görsel olarak düzenleme, hot-reload tetikleme, endpoint
test etme ve diff görüntüleme işlevlerini sağlar.
Spring gerektirmez. `adminUi(true)` ile başlatılır.

### 6.2 Paket Yapısı

```
io.marketplace.sdk.admin
├── AdminServer.java          ← Javalin sunucusu, route tanımları
├── AdminApiController.java   ← REST endpoint handler'ları
├── ConfigDiffService.java    ← İki YAML versiyonunu karşılaştırır
└── resources/
    └── admin-ui/
        ├── index.html        ← SPA (vanilla JS, no framework)
        ├── app.js
        └── style.css
```

### 6.3 REST API Tanımı

| Method | Path | Açıklama |
|---|---|---|
| GET | /api/configs | Tüm yüklü config'leri listele |
| GET | /api/configs/{marketplace} | Belirli bir config'i getir |
| PUT | /api/configs/{marketplace} | Config'i güncelle (YAML string body) |
| POST | /api/configs/{marketplace}/reload | Hot-reload tetikle |
| POST | /api/configs/{marketplace}/test | Seçili operasyonu test et |
| GET | /api/configs/{marketplace}/diff | Son değişiklik diff'ini getir |
| GET | /api/health | Tüm adapter'ların health durumu |

### 6.4 AdminServer.java (Session Auth ve Builder Pattern)

```java
package io.marketplace.sdk.admin;

import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;
import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminServer {
    private static final Logger log = LoggerFactory.getLogger(AdminServer.class);
    private final int port;
    private final MarketplaceSDK sdk;
    private final String authUser;
    private final String authPass;
    private Javalin app;

    private AdminServer(Builder builder) {
        this.port = builder.port;
        this.sdk = builder.sdk;
        this.authUser = builder.authUser;
        this.authPass = builder.authPass;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int port = 8090;
        private MarketplaceSDK sdk;
        private String authUser;
        private String authPass;

        public Builder withPort(int port) { this.port = port; return this; }
        public Builder withSDK(MarketplaceSDK sdk) { this.sdk = sdk; return this; }
        public Builder withAuth(String user, String pass) {
            this.authUser = user;
            this.authPass = pass;
            return this;
        }

        public AdminServer build() {
            if (sdk == null) throw new IllegalStateException("SDK must be provided");
            return new AdminServer(this);
        }
    }

    public void start() {
        app = Javalin.create(config -> {
            config.staticFiles.add("/admin-ui", Location.CLASSPATH);
        });

        // Authentication Interceptor
        if (authUser != null && authPass != null) {
            app.before(ctx -> {
                String path = ctx.path();
                if (path.equals("/login.html") || path.equals("/api/login") || path.endsWith(".css") || path.endsWith(".js")) {
                    return;
                }
                
                String authCookie = ctx.cookie("admin_session");
                if (!"authorized".equals(authCookie)) {
                    if (path.startsWith("/api/")) ctx.status(401).result("Unauthorized access");
                    else ctx.redirect("/login.html");
                }
            });

            // Login Endpoint
            app.post("/api/login", ctx -> {
                Map<String, String> body = ctx.bodyAsClass(Map.class);
                String user = body.get("username");
                String pass = body.get("password");

                if (authUser.equals(user) && authPass.equals(pass)) {
                    ctx.cookie("admin_session", "authorized");
                    ctx.json(Map.of("success", true));
                } else {
                    ctx.status(401).json(Map.of("success", false, "message", "Geçersiz kimlik"));
                }
            });
        }
        
        // ... Routing and Controller mapping here ...

        app.start(port);
        log.info("Admin UI Premium started on http://localhost:{}", port);
    }

    public void stop() {
        if (app != null) app.stop();
    }
}
```

### 6.6 Fat-Jar (Executable JAR) Derleme (DemoApp)

Uygulamanın standalone (Spring Boot tarzı tek jar dosyası olarak) bağımsız ayağa kalkması için yapılandırılmış `DemoApp.java` (Main Class) ile projeyi kurabilirsiniz.

`sdk-admin-ui` modülünün `pom.xml` içerisindeki `maven-shade-plugin` ile derlenmesi için:

```bash
cd marketplace-sdk
mvn clean package -pl sdk-admin-ui -am
```

Derleme sonrasında `sdk-admin-ui/target/sdk-admin-ui-1.0.0-SNAPSHOT.jar` üretilir.
Şu şekilde çalıştırabilirsiniz:
```bash
java -jar sdk-admin-ui/target/sdk-admin-ui-1.0.0-SNAPSHOT.jar
```

`sdk-admin-ui` modülünün `pom.xml` içerisine bu plugin'i eklemiş olmanız gerekmektedir:

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.2</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>io.marketplace.sdk.admin.DemoApp</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```
Admin Server, gömülü Javalin ve Web Arayüzü ile `localhost:8090` üzerinde ayağa kalkar.

### 6.5 AdminApiController.java

```java
package io.marketplace.sdk.admin;

import io.javalin.http.Context;
import io.marketplace.sdk.config.ConfigEngine;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.OperationRouter;

import java.nio.file.*;
import java.util.Map;

public class AdminApiController {

    private final ConfigEngine configEngine;
    private final OperationRouter router;
    private final Path configDir;

    public AdminApiController(ConfigEngine configEngine, OperationRouter router, Path configDir) {
        this.configEngine = configEngine;
        this.router = router;
        this.configDir = configDir;
    }

    public void listConfigs(Context ctx) {
        var configs = configEngine.getAllConfigs();
        var result = configs.entrySet().stream()
            .map(e -> Map.of(
                "marketplace", e.getKey().name(),
                "baseUrl", e.getValue().getBaseUrl(),
                "operationCount", e.getValue().getOperations().size()
            ))
            .toList();
        ctx.json(result);
    }

    public void getConfig(Context ctx) throws Exception {
        String marketplace = ctx.pathParam("marketplace").toUpperCase();
        Path yamlFile = configDir.resolve(marketplace.toLowerCase().replace("_", "-") + ".yaml");
        if (!Files.exists(yamlFile)) { ctx.status(404).result("Config not found"); return; }
        ctx.contentType("text/plain").result(Files.readString(yamlFile));
    }

    public void updateConfig(Context ctx) throws Exception {
        String marketplace = ctx.pathParam("marketplace").toUpperCase();
        String yamlContent = ctx.body();
        Path yamlFile = configDir.resolve(marketplace.toLowerCase().replace("_", "-") + ".yaml");

        // Backup eski dosyayı .bak olarak sakla (diff için)
        if (Files.exists(yamlFile)) {
            Files.copy(yamlFile, configDir.resolve(marketplace.toLowerCase() + ".yaml.bak"),
                StandardCopyOption.REPLACE_EXISTING);
        }

        Files.writeString(yamlFile, yamlContent);
        ctx.json(Map.of("status", "saved", "message", "Config saved. Call /reload to apply."));
    }

    public void reloadConfig(Context ctx) {
        String marketplace = ctx.pathParam("marketplace").toUpperCase();
        try {
            MarketplaceType type = MarketplaceType.fromString(marketplace);
            configEngine.reload(type);
            ctx.json(Map.of("status", "ok", "message", marketplace + " reloaded successfully"));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    public void testOperation(Context ctx) {
        String marketplace = ctx.pathParam("marketplace").toUpperCase();
        String operationName = ctx.queryParam("operation");

        try {
            MarketplaceType type = MarketplaceType.fromString(marketplace);
            Operation op = Operation.valueOf(operationName.toUpperCase());

            OperationRequest request = OperationRequest.builder(type, op).build();
            OperationResponse response = router.route(request);

            ctx.json(Map.of(
                "success", response.isSuccess(),
                "httpStatus", response.getHttpStatus(),
                "data", response.getData(),
                "rawResponse", response.getRawResponse()
            ));
        } catch (Exception e) {
            ctx.status(500).json(Map.of("status", "error", "message", e.getMessage()));
        }
    }

    public void getDiff(Context ctx) throws Exception {
        String marketplace = ctx.pathParam("marketplace").toUpperCase().replace("_", "-").toLowerCase();
        Path current = configDir.resolve(marketplace + ".yaml");
        Path backup  = configDir.resolve(marketplace + ".yaml.bak");

        String currentContent = Files.exists(current) ? Files.readString(current) : "";
        String backupContent  = Files.exists(backup)  ? Files.readString(backup)  : "";

        ctx.json(Map.of("current", currentContent, "previous", backupContent));
    }

    public void health(Context ctx) {
        var adapters = configEngine.getAllConfigs().keySet().stream()
            .map(type -> {
                boolean healthy = false;
                try {
                    healthy = router.getAdapter(type) != null &&
                              router.getAdapter(type).healthCheck();
                } catch (Exception ignored) {}
                return Map.of("marketplace", type.name(), "healthy", healthy);
            })
            .toList();
        ctx.json(Map.of("adapters", adapters));
    }
}
```

---

## BÖLÜM 7 — MODÜL 5: sdk-test-support

### 7.1 Görev
WireMock tabanlı test altyapısı. Her pazaryeri için mock stub'lar içerir.
Adapter geliştiricileri gerçek API'ye bağlanmadan integration testi yapabilir.

### 7.2 Paket Yapısı

```
io.marketplace.sdk.test
├── MarketplaceMockServer.java   ← WireMock wrapper
├── TrendyolMockStubs.java       ← Trendyol endpoint mock'ları
├── HepsiburadaMockStubs.java
├── N11MockStubs.java
├── AmazonMockStubs.java
└── fixtures/
    ├── trendyol-orders.json
    ├── trendyol-products.json
    ├── hepsiburada-orders.json
    └── ...
```

### 7.3 MarketplaceMockServer.java

```java
package io.marketplace.sdk.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

public class MarketplaceMockServer {

    private final WireMockServer server;

    public MarketplaceMockServer() {
        this.server = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    }

    public void start() { server.start(); }
    public void stop()  { server.stop(); }
    public void reset() { server.resetAll(); }

    public String baseUrl() { return "http://localhost:" + server.port(); }

    /** Trendyol stub'larını yükler */
    public MarketplaceMockServer withTrendyol() {
        TrendyolMockStubs.register(server);
        return this;
    }

    /** Hepsiburada stub'larını yükler */
    public MarketplaceMockServer withHepsiburada() {
        HepsiburadaMockStubs.register(server);
        return this;
    }

    public MarketplaceMockServer withN11() {
        N11MockStubs.register(server);
        return this;
    }

    public MarketplaceMockServer withAmazon() {
        AmazonMockStubs.register(server);
        return this;
    }
}
```

### 7.4 TrendyolMockStubs.java

```java
package io.marketplace.sdk.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class TrendyolMockStubs {

    public static void register(WireMockServer server) {

        // GET /suppliers/{supplierId}/orders
        server.stubFor(get(urlPathMatching("/sapigw/suppliers/.*/orders"))
            .withHeader("Authorization", matching("Basic .*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBodyFile("trendyol-orders.json")));

        // POST /suppliers/{supplierId}/v2/products
        server.stubFor(post(urlPathMatching("/sapigw/suppliers/.*/v2/products"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"test-batch-123\"}")));

        // POST /suppliers/{supplierId}/products/price-and-inventory
        server.stubFor(post(urlPathMatching("/sapigw/suppliers/.*/products/price-and-inventory"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBody("{\"batchRequestId\": \"stock-batch-456\"}")));

        // GET /product-categories
        server.stubFor(get(urlEqualTo("/sapigw/product-categories"))
            .willReturn(aResponse()
                .withStatus(200)
                .withBodyFile("trendyol-categories.json")));

        // Auth hatası simülasyonu
        server.stubFor(get(urlPathMatching("/sapigw/suppliers/.*/orders"))
            .withHeader("Authorization", absent())
            .willReturn(aResponse().withStatus(401)
                .withBody("{\"errors\":[{\"code\":\"UNAUTHORIZED\"}]}")));

        // Rate limit simülasyonu
        server.stubFor(get(urlPathMatching("/sapigw/suppliers/.*/orders"))
            .withHeader("X-Simulate-RateLimit", equalTo("true"))
            .willReturn(aResponse().withStatus(429)
                .withHeader("Retry-After", "60")));
    }
}
```

### 7.5 Fixture: trendyol-orders.json

```json
{
  "page": 0,
  "size": 50,
  "totalPages": 1,
  "totalElements": 2,
  "content": [
    {
      "orderNumber": "TY-TEST-001",
      "status": "Created",
      "customerId": "C-123",
      "totalPrice": 299.99,
      "orderDate": 1710000000000,
      "lines": [
        {
          "productName": "Test Ürün A",
          "barcode": "8681234567890",
          "quantity": 1,
          "price": 299.99
        }
      ]
    },
    {
      "orderNumber": "TY-TEST-002",
      "status": "Shipped",
      "customerId": "C-456",
      "totalPrice": 149.50,
      "orderDate": 1710003600000,
      "lines": [
        {
          "productName": "Test Ürün B",
          "barcode": "8689876543210",
          "quantity": 2,
          "price": 74.75
        }
      ]
    }
  ]
}
```

---

## BÖLÜM 8 — MODÜL 6: marketplace-configs (Varsayılan YAML Dosyaları)

### 8.1 trendyol.yaml

```yaml
marketplace: trendyol
baseUrl: https://api.trendyol.com/sapigw
timeoutSeconds: 30
maxRetries: 3

auth:
  type: BASIC
  usernameField: apiKey
  passwordField: apiSecret

extra:
  supplierId: "SUPPLIER_ID_BURAYA"

credentials:
  apiKey: "API_KEY_BURAYA"
  apiSecret: "API_SECRET_BURAYA"
  supplierId: "SUPPLIER_ID_BURAYA"

operations:
  getOrders:
    method: GET
    path: /suppliers/{supplierId}/orders
    queryParams:
      - name: status
        type: STRING
        required: false
      - name: startDate
        type: LONG
        required: false
      - name: endDate
        type: LONG
        required: false
      - name: page
        type: INTEGER
        required: false
        default: 0
      - name: size
        type: INTEGER
        required: false
        default: 50
    responseMapping:
      orderList:    "$.content[*]"
      orderId:      "$.orderNumber"
      orderStatus:  "$.status"
      customerId:   "$.customerId"
      totalAmount:  "$.totalPrice"
      orderDate:    "$.orderDate"

  createProduct:
    method: POST
    path: /suppliers/{supplierId}/v2/products
    contentType: application/json
    requestTemplate: |
      {
        "items": [{
          "barcode": "${barcode}",
          "title": "${title}",
          "productMainId": "${supplierId}",
          "brandId": 123,
          "categoryId": 456,
          "quantity": ${stock},
          "stockCode": "${sku}",
          "dimensionalWeight": 1.0,
          "description": "${description}",
          "currencyType": "TRY",
          "listPrice": ${price},
          "salePrice": ${price},
          "vatRate": 18,
          "cargoCompanyId": 10,
          "images": ${images},
          "attributes": ${attributes},
          "deliveryDuration": 3
        }]
      }
    responseMapping:
      batchRequestId: "$.batchRequestId"

  updateStock:
    method: POST
    path: /suppliers/{supplierId}/products/price-and-inventory
    requestMapping:
      barcode:   "barcode"
      quantity:  "quantity"
      salePrice: "salePrice"
      listPrice: "listPrice"
    responseMapping:
      batchRequestId: "$.batchRequestId"

  getCategories:
    method: GET
    path: /product-categories
    responseMapping:
      categories:   "$[*]"
      categoryId:   "$.id"
      categoryName: "$.name"
      parentId:     "$.parentId"

  getShipmentProviders:
    method: GET
    path: /suppliers/{supplierId}/cargo-companies
    responseMapping:
      providers:    "$[*]"
      providerId:   "$.id"
      providerName: "$.name"
      providerCode: "$.code"
```

### 8.2 hepsiburada.yaml

```yaml
marketplace: hepsiburada
baseUrl: https://mpop.hepsiburada.com
timeoutSeconds: 30
maxRetries: 3

auth:
  type: BASIC
  usernameField: sellerId
  passwordField: apiSecret

extra:
  merchantId: "MERCHANT_ID_BURAYA"

credentials:
  sellerId: "MERCHANT_ID_BURAYA"
  apiSecret: "API_PASSWORD_BURAYA"

operations:
  getOrders:
    method: GET
    path: /listings/merchantid/{merchantId}/orders
    queryParams:
      - name: status
        type: STRING
        required: false
      - name: offset
        type: INTEGER
        required: false
        default: 0
      - name: limit
        type: INTEGER
        required: false
        default: 50
    responseMapping:
      orderList:   "$.data[*]"
      orderId:     "$.id"
      orderStatus: "$.status"
      totalAmount: "$.totalFinalPrice"

  updateStock:
    method: POST
    path: /listings/merchantid/{merchantId}/listings/update
    requestMapping:
      barcode:  "sku"
      quantity: "availableStock"
    responseMapping:
      success: "$.success"

  getCategories:
    method: GET
    path: /product/api/categories/get-all-categories
    responseMapping:
      categories:   "$.categories[*]"
      categoryId:   "$.id"
      categoryName: "$.name"
```

### 8.3 n11.yaml

```yaml
marketplace: n11
baseUrl: https://api.n11.com/ws
timeoutSeconds: 30
maxRetries: 3

auth:
  type: API_KEY
  keyHeader: appKey
  secretHeader: appSecret

credentials:
  apiKey: "APP_KEY_BURAYA"
  apiSecret: "APP_SECRET_BURAYA"

operations:
  getOrders:
    method: POST
    path: /orderService
    contentType: text/xml; charset=utf-8
    responseMapping:
      orderList:   "$.orderList.order[*]"
      orderId:     "$.id"
      orderStatus: "$.status"
      totalAmount: "$.totalAmount"

  getCategories:
    method: POST
    path: /categoryService
    responseMapping:
      categories:   "$.category[*]"
      categoryId:   "$.id"
      categoryName: "$.name"
```

### 8.4 amazon.yaml

```yaml
marketplace: amazon_tr
baseUrl: https://sellingpartnerapi-eu.amazon.com
timeoutSeconds: 45
maxRetries: 3

auth:
  type: BEARER
  tokenField: accessToken

extra:
  marketplaceId: "A33AVAJ2PDY3EV"  # Amazon Turkey marketplace ID

credentials:
  accessToken: "ACCESS_TOKEN_BURAYA"
  refreshToken: "REFRESH_TOKEN_BURAYA"

operations:
  getOrders:
    method: GET
    path: /orders/v0/orders
    queryParams:
      - name: MarketplaceIds
        type: STRING
        required: true
        default: "A33AVAJ2PDY3EV"
      - name: CreatedAfter
        type: DATE_ISO
        required: false
      - name: OrderStatuses
        type: STRING
        required: false
    responseMapping:
      orderList:   "$.payload.Orders[*]"
      orderId:     "$.AmazonOrderId"
      orderStatus: "$.OrderStatus"
      totalAmount: "$.OrderTotal.Amount"
      orderDate:   "$.PurchaseDate"

  getProducts:
    method: GET
    path: /catalog/2022-04-01/items
    queryParams:
      - name: marketplaceIds
        type: STRING
        required: true
        default: "A33AVAJ2PDY3EV"
    responseMapping:
      products:    "$.items[*]"
      productId:   "$.asin"
      title:       "$.summaries[0].itemName"
```

---

## BÖLÜM 9 — Integration Test Örneği

```java
package io.marketplace.sdk;

import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.test.MarketplaceMockServer;
import org.junit.jupiter.api.*;
import java.nio.file.*;

import static org.assertj.core.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TrendyolIntegrationTest {

    private MarketplaceMockServer mockServer;
    private MarketplaceClient client;
    private Path tempConfigDir;

    @BeforeAll
    void setup() throws Exception {
        mockServer = new MarketplaceMockServer().withTrendyol();
        mockServer.start();

        // Test için temp dizine override edilmiş YAML yaz
        tempConfigDir = Files.createTempDirectory("marketplace-test");
        String yamlContent = """
            marketplace: trendyol
            baseUrl: %s/sapigw
            timeoutSeconds: 10
            maxRetries: 1
            auth:
              type: BASIC
            extra:
              supplierId: "12345"
            credentials:
              apiKey: test-key
              apiSecret: test-secret
              supplierId: "12345"
            operations:
              getOrders:
                method: GET
                path: /suppliers/{supplierId}/orders
                responseMapping:
                  orderList: "$.content[*]"
                  orderId: "$.orderNumber"
                  orderStatus: "$.status"
            """.formatted(mockServer.baseUrl());

        Files.writeString(tempConfigDir.resolve("trendyol.yaml"), yamlContent);

        client = MarketplaceSDK.builder()
            .configDir(tempConfigDir)
            .hotReload(false)
            .build()
            .client();
    }

    @AfterAll
    void teardown() {
        client.shutdown();
        mockServer.stop();
    }

    @Test
    void getOrders_shouldReturnNormalizedResponse() {
        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.GET_ORDERS)
            .param("status", "Created")
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNotNull();

        @SuppressWarnings("unchecked")
        var data = (java.util.Map<String, Object>) response.getData();
        assertThat(data).containsKey("orderList");
    }

    @Test
    void getOrders_withInvalidAuth_shouldReturn401() {
        // WireMock Authorization absent stub 401 döner
        // Bu test credentials olmadan nasıl başlatılacağını gösterir
        // (ayrı bir client instance ile test edilir)
    }

    @Test
    void hotReload_shouldPickUpNewConfig() throws Exception {
        // trendyol.yaml'ı güncelle
        // configEngine.reload() çağır
        // Yeni config'in aktif olduğunu doğrula
    }
}
```

---

## BÖLÜM 10 — Kullanıcı Kullanım Örneği (Kütüphane Tüketicisi)

```java
// 1. SDK'yı başlat
MarketplaceSDK sdk = MarketplaceSDK.builder()
    .configDir("/etc/myapp/marketplace-configs")   // YAML dosyalarının yolu
    .adminUi(true)                                 // Dashboard'u aç
    .adminUiPort(8090)                             // localhost:8090
    .hotReload(true)                               // Dosya değişikliklerini izle
    .build();

MarketplaceClient client = sdk.client();

// 2. Sipariş çek
OperationResponse orders = client.execute(
    OperationRequest.builder(MarketplaceType.TRENDYOL, Operation.GET_ORDERS)
        .param("status", "Created")
        .param("startDate", "2024-01-01")
        .param("size", 100)
        .build()
);

if (orders.isSuccess()) {
    Map<String, Object> data = orders.getDataAs(Map.class);
    List<?> orderList = (List<?>) data.get("orderList");
    System.out.println("Sipariş sayısı: " + orderList.size());
}

// 3. Stok güncelle
Map<String, Object> stockUpdate = Map.of(
    "barcode", "8681234567890",
    "quantity", 50,
    "salePrice", 299.99,
    "listPrice", 349.99
);

OperationResponse stockResult = client.execute(
    OperationRequest.builder(MarketplaceType.TRENDYOL, Operation.UPDATE_STOCK)
        .body(stockUpdate)
        .build()
);

// 4. Hepsiburada'dan aynı anda sipariş çek
OperationResponse hbOrders = client.execute(
    OperationRequest.builder(MarketplaceType.HEPSIBURADA, Operation.GET_ORDERS)
        .param("status", "WAITING")
        .build()
);

// 5. API değişikliği yaşandı — sadece YAML güncellendi, kod değişmedi
//    Admin UI: http://localhost:8090
//    veya programatik:
client.reloadConfig(MarketplaceType.TRENDYOL);

// 6. Kapatma
client.shutdown();
```

---

## BÖLÜM 11 — Geliştirme Sırası ve Öncelikler

Agent bu planı uygularken aşağıdaki sırayı takip etmelidir:

### Aşama 1 — Temel iskelet (1-2 gün)
1. Root `pom.xml` oluştur (tüm modüller, dependency management)
2. `sdk-core` modülünü yaz: enum'lar, exception'lar, SPI interface'leri
3. `MarketplaceSDK` builder'ını ve `OperationRouter`'ı yaz
4. `OkHttpClientImpl`'i yaz

### Aşama 2 — Config Engine (1-2 gün)
5. `YamlConfigLoader`'ı yaz ve test et
6. `FieldMapper` (JsonPath) yaz ve test et
7. `ConfigEngine` (loadAll, reload) yaz
8. `HotReloadWatcher` yaz
9. 4 YAML dosyasını oluştur

### Aşama 3 — Adapter'lar (1-2 gün)
10. `BaseAdapter` yaz (path template, retry, auth hook)
11. `TrendyolAdapter` yaz
12. `HepsiburadaAdapter` yaz
13. `N11Adapter` yaz
14. `AmazonAdapter` yaz
15. `AdapterRegistry` yaz

### Aşama 4 — Test Altyapısı (1 gün)
16. `sdk-test-support` modülü: WireMock server ve stub'lar
17. JSON fixture dosyaları (4 pazaryeri × 3-4 operasyon)
18. Integration testleri yaz (en az GET_ORDERS her pazaryeri için)

### Aşama 5 — Admin UI (1-2 gün)
19. `AdminServer` (Javalin) ve `AdminApiController` yaz
20. Frontend: `index.html` + `app.js` (YAML editor, hot-reload butonu, diff viewer)
21. `MarketplaceSDK.builder().adminUi(true)` ile entegre et

### Aşama 6 — Polish (yarım gün)
22. Logging (SLF4J) her kritik noktada mevcut mu kontrol et
23. README.md: kurulum, YAML şema referansı, örnekler
24. `mvn clean install` ile tüm modüller başarıyla build olduğunu doğrula

---

## BÖLÜM 12 — Yeni Pazaryeri Ekleme Talimatı

Bir agent "X pazaryerini ekle" komutu aldığında şu adımları izler:

1. `MarketplaceType` enum'una yeni değeri ekle
2. `sdk-adapters` altında yeni paket oluştur: `io.marketplace.sdk.adapter.x`
3. `BaseAdapter`'ı extend eden `XAdapter.java` yaz, `buildAuthHeaders()` implement et
4. `AdapterRegistry.createAdapter()` switch'ine yeni case ekle
5. `marketplace-configs/x.yaml` dosyasını oluştur (auth, baseUrl, operations)
6. `sdk-test-support`'a `XMockStubs.java` ekle
7. `MarketplaceMockServer.withX()` metodunu ekle
8. Integration testi yaz

Başka hiçbir dosyaya dokunmak gerekmez.

---

## BÖLÜM 13 — Mevcut API Değişikliğine Adapte Olma Talimatı

Bir agent "Trendyol siparişlerin endpoint path'ini değiştirdi" komutu aldığında:

1. `marketplace-configs/trendyol.yaml` dosyasını aç
2. `operations.getOrders.path` değerini güncelle
3. Eğer response field adı değiştiyse `responseMapping` kısmını güncelle
4. Admin UI üzerinden `/api/configs/trendyol/reload` POST isteği at (veya `client.reloadConfig(TRENDYOL)`)
5. Java kodu hiç değişmez.

---

## BÖLÜM 14 — Kontrol Listesi (Agent Teslim Öncesi)

- [ ] `mvn clean install -DskipTests` başarılı
- [ ] `mvn test` — tüm test'ler yeşil
- [ ] 4 YAML dosyası mevcut ve geçerli şemada
- [ ] `TrendyolIntegrationTest` geçiyor (WireMock ile)
- [ ] Admin UI `http://localhost:7070` adresinde açılıyor
- [ ] GET_ORDERS, UPDATE_STOCK, GET_CATEGORIES her adapter için çalışıyor
- [ ] Hot-reload: YAML güncelle → reload → yeni config aktif
- [ ] `client.shutdown()` çağrıldığında HTTP bağlantıları kapatılıyor
- [ ] SLF4J bağımlılığı `compile` scope, logback `runtime` scope (test projelerini kirletmez)
- [ ] `executeAsync()` çağrıları CompletableFuture döndürüyor
- [ ] `stream(TRENDYOL, GET_ORDERS)` tüm sayfaları otomatik çekiyor
- [ ] Amazon adapter 1 saatlik token süresi dolduğunda otomatik refresh yapıyor
- [ ] Rate limiter 429 aldığında thread bloklamadan kuyrukluyor
- [ ] `getCategories` cache'den dönüyor (TTL dolmadan ağ isteği yok)
- [ ] Webhook server `http://localhost:8080/webhook/{marketplace}` adresinde ayakta
- [ ] Trendyol webhook payload'ı normalize WebhookEvent'e dönüşüyor

---

## BÖLÜM 15 — Enterprise Eklenti 1: Type-Safe Model Mapping

### Sorun
`FieldMapper` JsonPath'ten `Map<String, Object>` döndürdüğünden,
`response.getDataAs(Order.class)` çağrısı Runtime'da `ClassCastException` fırlatır.

### Çözüm
`FieldMapper` artık iki aşamalı çalışır:
1. JsonPath → normalize `Map<String, Object>` (YAML mapping'e göre)
2. Jackson `ObjectMapper.convertValue()` → istenen Java sınıfına güvenli dönüşüm

`OperationResponse.getDataAs(Class<T>)` artık gerçekten type-safe.

### FieldMapper.java (güncellenmiş)

```java
package io.marketplace.sdk.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import io.marketplace.sdk.core.spi.OperationConfig;

import java.util.HashMap;
import java.util.Map;

public class FieldMapper {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Ham JSON yanıtını → normalize Map'e çevirir (JsonPath mapping).
     * Ardından isteğe bağlı olarak hedef Java sınıfına dönüştürür.
     */
    public Map<String, Object> mapResponse(String rawJson, OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getResponseMapping() == null) return result;

        for (Map.Entry<String, String> mapping : opConfig.getResponseMapping().entrySet()) {
            String normalizedField = mapping.getKey();
            String jsonPath = mapping.getValue();
            try {
                Object value = JsonPath.read(rawJson, jsonPath);
                result.put(normalizedField, value);
            } catch (PathNotFoundException e) {
                result.put(normalizedField, null);
            }
        }
        return result;
    }

    /**
     * Normalize Map'i istenen Java sınıfına güvenle dönüştürür.
     * ClassCastException riski yoktur — Jackson tüm alan uyumsuzluklarını
     * derleme zamanında değil, anlamlı bir hata mesajıyla bildirir.
     *
     * Kullanım:
     *   Order order = fieldMapper.convert(dataMap, Order.class);
     */
    public <T> T convert(Map<String, Object> data, Class<T> targetType) {
        return objectMapper.convertValue(data, targetType);
    }

    /**
     * Normalize Map + hedef sınıf dönüşümünü tek adımda yapar.
     * BaseAdapter bu metodu kullanarak her zaman type-safe veri döner.
     */
    public <T> T mapAndConvert(String rawJson, OperationConfig opConfig, Class<T> targetType) {
        Map<String, Object> mapped = mapResponse(rawJson, opConfig);
        return convert(mapped, targetType);
    }

    public Map<String, Object> mapRequest(Map<String, Object> normalizedRequest,
                                           OperationConfig opConfig) {
        Map<String, Object> result = new HashMap<>();
        if (opConfig.getRequestMapping() == null) return normalizedRequest;

        for (Map.Entry<String, String> mapping : opConfig.getRequestMapping().entrySet()) {
            String normalizedField = mapping.getKey();
            String apiField = mapping.getValue();
            if (normalizedRequest.containsKey(normalizedField)) {
                result.put(apiField, normalizedRequest.get(normalizedField));
            }
        }
        return result;
    }
}
```

### OperationResponse.java (güncellenmiş getDataAs)

```java
/**
 * Type-safe data erişimi.
 * data alanı artık hem Map<String,Object> hem de herhangi bir POJO olabilir.
 * Jackson convertValue() üzerinden güvenli cast yapılır.
 */
private static final ObjectMapper MAPPER = new ObjectMapper();

@SuppressWarnings("unchecked")
public <T> T getDataAs(Class<T> type) {
    if (data == null) return null;
    if (type.isInstance(data)) return type.cast(data);
    // Map → POJO dönüşümü (ClassCastException riski yok)
    return MAPPER.convertValue(data, type);
}
```

### BaseAdapter.java execute() metodundaki değişiklik

```java
// Eski (tehlikeli):
Map<String, Object> mappedData = fieldMapper.mapResponse(httpResponse.getBody(), opConfig);

// Yeni (type-safe):
// Her zaman Map döndürülür; kullanıcı getDataAs(Order.class) çağırınca
// Jackson güvenli dönüşüm yapar. YAML mapping'de tanımlı olmayan alanlar
// null olarak gelir, bilinmeyen alanlar sessizce yoksayılır.
Map<String, Object> mappedData = fieldMapper.mapResponse(httpResponse.getBody(), opConfig);
// mappedData artık OperationResponse içine konulur;
// kullanıcı katmanında: response.getDataAs(Order.class) → güvenli
```

---

## BÖLÜM 16 — Enterprise Eklenti 2: TokenProvider SPI (OAuth2 Refresh)

### Sorun
Amazon TR gibi OAuth2 kullanan pazaryerlerinde access token ~1 saatte geçersiz olur.
`Credentials` immutable olduğu için token yenilenemiyor.

### Çözüm
`TokenProvider` SPI: her auth tipi kendi `TokenProvider`'ını implement eder.
`BaseAdapter` her istekten önce `tokenProvider.getValidToken()` çağırır.
Provider token'ın süresini takip eder, gerektiğinde yeniler.

### TokenProvider.java (SPI)

```java
package io.marketplace.sdk.core.spi;

/**
 * Auth token üretiminden ve yenilenmesinden sorumlu SPI.
 * Stateless (Basic Auth) ve Stateful (OAuth2) auth'ları soyutlar.
 */
public interface TokenProvider {

    /**
     * Geçerli bir token döner.
     * Token süresi dolmuşsa yeniler, cache'den döner veya exception fırlatır.
     * Thread-safe olmak zorundadır.
     */
    String getValidToken();

    /**
     * Token'ı geçersiz kılar (örn. 401 aldıktan sonra zorla yenileme).
     * Bir sonraki getValidToken() çağrısı mutlaka yeni token alır.
     */
    void invalidate();

    /** Bu provider'ın desteklediği auth tipi */
    AuthType getAuthType();

    enum AuthType { BASIC, BEARER, API_KEY, OAUTH2 }
}
```

### BasicAuthTokenProvider.java

```java
package io.marketplace.sdk.adapter;

import io.marketplace.sdk.core.model.Credentials;
import io.marketplace.sdk.core.spi.TokenProvider;
import java.util.Base64;

/**
 * Basic Auth için sabit token üretir.
 * Token süresi dolmaz; invalidate() çağrısı etkisizdir.
 */
public class BasicAuthTokenProvider implements TokenProvider {

    private final String token;

    public BasicAuthTokenProvider(Credentials credentials) {
        String raw = credentials.getApiKey() + ":" + credentials.getApiSecret();
        this.token = "Basic " + Base64.getEncoder().encodeToString(raw.getBytes());
    }

    @Override public String getValidToken() { return token; }
    @Override public void invalidate() { /* stateless, no-op */ }
    @Override public AuthType getAuthType() { return AuthType.BASIC; }
}
```

### AmazonTokenProvider.java (LWA OAuth2 Refresh)

```java
package io.marketplace.sdk.adapter.amazon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.marketplace.sdk.core.exception.AuthException;
import io.marketplace.sdk.core.model.Credentials;
import io.marketplace.sdk.core.spi.TokenProvider;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Amazon LWA (Login With Amazon) OAuth2 token yönetimi.
 * Token süresi dolmadan 5 dakika önce otomatik yeniler.
 * Thread-safe: AtomicReference + synchronized refresh bloğu.
 */
public class AmazonTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(AmazonTokenProvider.class);
    private static final String LWA_TOKEN_URL = "https://api.amazon.com/auth/o2/token";
    private static final int REFRESH_BUFFER_SECONDS = 300; // 5 dakika erken yenile

    private final Credentials credentials;
    private final OkHttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    private final AtomicReference<TokenState> tokenState = new AtomicReference<>(TokenState.EMPTY);

    public AmazonTokenProvider(Credentials credentials) {
        this.credentials = credentials;
        this.httpClient = new OkHttpClient();
    }

    @Override
    public synchronized String getValidToken() {
        TokenState state = tokenState.get();
        if (state.isValid()) {
            return "Bearer " + state.accessToken;
        }
        return "Bearer " + refresh();
    }

    @Override
    public synchronized void invalidate() {
        tokenState.set(TokenState.EMPTY);
        log.info("Amazon token invalidated, will refresh on next request");
    }

    @Override
    public AuthType getAuthType() { return AuthType.OAUTH2; }

    private String refresh() {
        log.info("Refreshing Amazon LWA access token...");
        try {
            RequestBody body = new FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", credentials.getRefreshToken())
                .add("client_id", credentials.getApiKey())
                .add("client_secret", credentials.getApiSecret())
                .build();

            Request request = new Request.Builder()
                .url(LWA_TOKEN_URL)
                .post(body)
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new AuthException("LWA token refresh failed: HTTP " + response.code());
                }
                JsonNode json = mapper.readTree(response.body().string());
                String accessToken = json.get("access_token").asText();
                int expiresIn = json.get("expires_in").asInt(3600);

                tokenState.set(new TokenState(
                    accessToken,
                    Instant.now().plusSeconds(expiresIn - REFRESH_BUFFER_SECONDS)
                ));

                log.info("Amazon token refreshed, expires in {}s", expiresIn);
                return accessToken;
            }
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            throw new AuthException("LWA token refresh error: " + e.getMessage());
        }
    }

    private static class TokenState {
        static final TokenState EMPTY = new TokenState(null, Instant.EPOCH);
        final String accessToken;
        final Instant expiresAt;

        TokenState(String accessToken, Instant expiresAt) {
            this.accessToken = accessToken;
            this.expiresAt = expiresAt;
        }

        boolean isValid() {
            return accessToken != null && Instant.now().isBefore(expiresAt);
        }
    }
}
```

### BaseAdapter.java'daki TokenProvider entegrasyonu

```java
// BaseAdapter'da:
protected TokenProvider tokenProvider;

@Override
public void initialize(AdapterConfig config) {
    this.config = config;
    this.httpClient = new OkHttpClientImpl(config.getTimeoutSeconds());
    this.tokenProvider = createTokenProvider(config); // her adapter override eder
}

/** Her adapter kendi TokenProvider'ını döner */
protected abstract TokenProvider createTokenProvider(AdapterConfig config);

// buildAuthHeaders() artık statik string üretmek yerine:
@Override
protected Map<String, String> buildAuthHeaders() {
    return Map.of("Authorization", tokenProvider.getValidToken());
}

// 401 alındığında token'ı geçersiz kıl ve bir kez daha dene:
private HttpResponse executeWithRetry(HttpRequest request) {
    int attempts = 0;
    while (attempts <= config.getMaxRetries()) {
        HttpResponse response = httpClient.execute(request);
        if (response.getStatusCode() == 401 && attempts == 0) {
            // Token geçersiz kılındı → bir sonraki getValidToken() yeniler
            tokenProvider.invalidate();
            attempts++;
            continue;
        }
        if (response.getStatusCode() >= 500 || response.getStatusCode() == 429) {
            attempts++;
            sleep(1000L * attempts);
            continue;
        }
        return response;
    }
    throw new MarketplaceException("MAX_RETRIES_EXCEEDED", "All retry attempts failed");
}
```

---

## BÖLÜM 17 — Enterprise Eklenti 3: Async Client

### Sorun
`execute()` metodu senkron: thread pazaryeri yanıt verene kadar bloke olur.
50 sayfa sipariş çekildiğinde 50 thread bağlanır.

### Çözüm
`MarketplaceClient` interface'ine `executeAsync()` eklenir.
OkHttp'nin `.enqueue()` metodu kullanılarak thread-blocking ortadan kalkar.

### AsyncMarketplaceClient.java

```java
package io.marketplace.sdk.core.async;

import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.OperationRouter;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async wrapper. Senkron execute()'u sanal thread havuzunda çalıştırır.
 * Java 21+ için virtual threads, 17 için cached thread pool kullanılır.
 */
public class AsyncMarketplaceClient {

    private final OperationRouter router;
    private final Executor executor;

    public AsyncMarketplaceClient(OperationRouter router) {
        this.router = router;
        // Java 21: Executors.newVirtualThreadPerTaskExecutor()
        // Java 17: Executors.newCachedThreadPool()
        this.executor = createExecutor();
    }

    private Executor createExecutor() {
        try {
            // Java 21 virtual thread desteği varsa kullan
            var method = Executors.class.getMethod("newVirtualThreadPerTaskExecutor");
            return (Executor) method.invoke(null);
        } catch (Exception e) {
            return Executors.newCachedThreadPool(r -> {
                Thread t = new Thread(r, "marketplace-async");
                t.setDaemon(true);
                return t;
            });
        }
    }

    /**
     * Operasyonu asenkron çalıştırır.
     * Dönen CompletableFuture tamamlanana kadar çağıran thread bloke olmaz.
     *
     * Kullanım:
     *   asyncClient.executeAsync(request)
     *       .thenAccept(resp -> processOrders(resp))
     *       .exceptionally(ex -> { log.error("Failed", ex); return null; });
     */
    public CompletableFuture<OperationResponse> executeAsync(OperationRequest request) {
        return CompletableFuture.supplyAsync(
            () -> router.route(request),
            executor
        );
    }

    /**
     * Birden fazla pazaryerinde aynı operasyonu paralel çalıştırır.
     *
     * Kullanım:
     *   asyncClient.executeAll(List.of(trendyolReq, hbReq, n11Req))
     *       .thenAccept(responses -> mergeAndProcess(responses));
     */
    public CompletableFuture<java.util.List<OperationResponse>> executeAll(
            java.util.List<OperationRequest> requests) {

        java.util.List<CompletableFuture<OperationResponse>> futures = requests.stream()
            .map(this::executeAsync)
            .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
                .map(CompletableFuture::join)
                .toList());
    }

    public void shutdown() {
        if (executor instanceof java.util.concurrent.ExecutorService es) {
            es.shutdown();
        }
    }
}
```

### MarketplaceClient.java (güncellenmiş interface)

```java
public interface MarketplaceClient {
    // Senkron (geriye dönük uyumlu)
    OperationResponse execute(OperationRequest request);

    // Asenkron (yeni)
    CompletableFuture<OperationResponse> executeAsync(OperationRequest request);

    // Paralel çoklu pazaryeri
    CompletableFuture<List<OperationResponse>> executeAll(List<OperationRequest> requests);

    boolean supports(MarketplaceType marketplace, Operation operation);
    boolean healthCheck(MarketplaceType marketplace);
    void reloadConfig(MarketplaceType marketplace);
    void shutdown();
}
```

---

## BÖLÜM 18 — Enterprise Eklenti 4: Otomatik Pagination

### Sorun
Her pazaryeri farklı pagination sistemi kullanır:
- Trendyol: `page` + `size` + `totalPages`
- Amazon: cursor tabanlı `nextToken`
- N11: offset bazlı

Kullanıcı while döngüsü yazarak tüm sayfaları elle çekmek zorunda kalır.

### Çözüm
`PageableResponse` ve `MarketplaceStream` sınıfları tüm pagination mantığını saklar.
Kullanıcı `client.stream(marketplace, operation, params)` çağırarak tek seferinde
tüm veriye `Stream<OperationResponse>` üzerinden erişir.

### PaginationStrategy.java (SPI)

```java
package io.marketplace.sdk.core.pagination;

import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import java.util.Optional;

/**
 * Her pazaryeri kendi sayfalama mantığını implement eder.
 */
public interface PaginationStrategy {

    /** Bu response'tan sonra daha sayfa var mı? */
    boolean hasNextPage(OperationResponse response);

    /** Sonraki sayfayı çekecek request'i üretir */
    OperationRequest nextPageRequest(OperationRequest currentRequest,
                                     OperationResponse lastResponse);
}
```

### OffsetPaginationStrategy.java (Trendyol, Hepsiburada)

```java
package io.marketplace.sdk.core.pagination;

import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * page + size + totalPages bazlı sayfalama.
 * YAML config'de: pagination.type: OFFSET
 */
public class OffsetPaginationStrategy implements PaginationStrategy {

    private final int pageSize;

    public OffsetPaginationStrategy(int pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public boolean hasNextPage(OperationResponse response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        if (data == null) return false;

        // YAML responseMapping'de "currentPage" ve "totalPages" tanımlı olmalı
        Object current = data.get("currentPage");
        Object total = data.get("totalPages");
        if (current == null || total == null) return false;

        return ((Number) current).intValue() < ((Number) total).intValue() - 1;
    }

    @Override
    public OperationRequest nextPageRequest(OperationRequest currentRequest,
                                             OperationResponse lastResponse) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) lastResponse.getData();
        int nextPage = ((Number) data.get("currentPage")).intValue() + 1;

        Map<String, Object> newParams = new HashMap<>(currentRequest.getParams());
        newParams.put("page", nextPage);
        newParams.put("size", pageSize);

        return OperationRequest.builder(
                currentRequest.getMarketplace(),
                currentRequest.getOperation())
            .params(newParams)
            .body(currentRequest.getBody())
            .build();
    }
}
```

### CursorPaginationStrategy.java (Amazon)

```java
package io.marketplace.sdk.core.pagination;

import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;

import java.util.HashMap;
import java.util.Map;

/**
 * nextToken bazlı cursor pagination (Amazon SP-API).
 * YAML config'de: pagination.type: CURSOR, pagination.cursorField: nextToken
 */
public class CursorPaginationStrategy implements PaginationStrategy {

    private final String cursorField;

    public CursorPaginationStrategy(String cursorField) {
        this.cursorField = cursorField;
    }

    @Override
    public boolean hasNextPage(OperationResponse response) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        return data != null && data.get(cursorField) != null;
    }

    @Override
    public OperationRequest nextPageRequest(OperationRequest currentRequest,
                                             OperationResponse lastResponse) {
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) lastResponse.getData();
        String nextToken = (String) data.get(cursorField);

        Map<String, Object> newParams = new HashMap<>(currentRequest.getParams());
        newParams.put(cursorField, nextToken);

        return OperationRequest.builder(
                currentRequest.getMarketplace(),
                currentRequest.getOperation())
            .params(newParams)
            .body(currentRequest.getBody())
            .build();
    }
}
```

### MarketplaceStream.java

```java
package io.marketplace.sdk.core.pagination;

import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.operation.OperationRouter;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Pazaryeri verilerini sayfalama detayı olmadan Stream olarak sunar.
 *
 * Kullanım:
 *   sdk.stream(TRENDYOL, GET_ORDERS, Map.of("status", "Created"))
 *      .flatMap(resp -> ((List<?>) resp.getData().get("orderList")).stream())
 *      .forEach(order -> process(order));
 */
public class MarketplaceStream {

    private final OperationRouter router;
    private final PaginationStrategy strategy;

    public MarketplaceStream(OperationRouter router, PaginationStrategy strategy) {
        this.router = router;
        this.strategy = strategy;
    }

    public Stream<OperationResponse> stream(OperationRequest initialRequest) {
        return StreamSupport.stream(
            new PageSpliterator(router, strategy, initialRequest), false);
    }

    private static class PageSpliterator implements java.util.Spliterator<OperationResponse> {

        private final OperationRouter router;
        private final PaginationStrategy strategy;
        private OperationRequest currentRequest;
        private boolean done = false;

        PageSpliterator(OperationRouter router, PaginationStrategy strategy,
                        OperationRequest initialRequest) {
            this.router = router;
            this.strategy = strategy;
            this.currentRequest = initialRequest;
        }

        @Override
        public boolean tryAdvance(java.util.function.Consumer<? super OperationResponse> action) {
            if (done) return false;

            OperationResponse response = router.route(currentRequest);
            action.accept(response);

            if (strategy.hasNextPage(response)) {
                currentRequest = strategy.nextPageRequest(currentRequest, response);
            } else {
                done = true;
            }
            return true;
        }

        @Override public java.util.Spliterator<OperationResponse> trySplit() { return null; }
        @Override public long estimateSize() { return Long.MAX_VALUE; }
        @Override public int characteristics() { return ORDERED | NONNULL; }
    }
}
```

### YAML'da pagination konfigürasyonu

```yaml
operations:
  getOrders:
    method: GET
    path: /suppliers/{supplierId}/orders
    pagination:
      type: OFFSET          # OFFSET | CURSOR | NONE
      pageParam: page
      sizeParam: size
      defaultSize: 50
      totalPagesField: "$.totalPages"
      currentPageField: "$.page"
    responseMapping:
      orderList:    "$.content[*]"
      totalPages:   "$.totalPages"
      currentPage:  "$.page"
```

---

## BÖLÜM 19 — Enterprise Eklenti 5: Token Bucket Rate Limiter

### Sorun
429 alındığında `Thread.sleep()` çağrısı thread'i tamamen bloke eder.
Çok işlemli sistemlerde bu darboğaz yaratır.

### Çözüm
Token Bucket algoritması: her pazaryeri için sabit bir istek kotası tanımlanır.
İstek öncesinde token alınır; token yoksa istek sıraya alınır, thread bloke olmaz.
429 alındığında Retry-After süresi kadar bekleme sıraya eklenir.

### RateLimiter.java (interface)

```java
package io.marketplace.sdk.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    /**
     * Token alır. Yeterli token yoksa en fazla maxWait kadar bekler.
     * @return true token alındı, false timeout
     */
    boolean acquire(Duration maxWait);

    /** 429 sonrası adaptif geri çekilme */
    void penalize(Duration retryAfter);

    /** Saniyede izin verilen istek sayısı */
    int getPermitsPerSecond();
}
```

### TokenBucketRateLimiter.java

```java
package io.marketplace.sdk.ratelimit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe Token Bucket implementasyonu.
 * Arka planda scheduler her saniye token ekler.
 * Thread.sleep() yerine Semaphore.tryAcquire() kullanır — non-blocking.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final int permitsPerSecond;
    private final int maxBurst;
    private final Semaphore semaphore;
    private final java.util.concurrent.ScheduledExecutorService scheduler;
    private volatile Instant penaltyUntil = Instant.EPOCH;

    public TokenBucketRateLimiter(int permitsPerSecond, int maxBurst) {
        this.permitsPerSecond = permitsPerSecond;
        this.maxBurst = maxBurst;
        this.semaphore = new Semaphore(maxBurst);

        this.scheduler = java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rate-limiter-refill");
            t.setDaemon(true);
            return t;
        });

        // Her saniye token ekle
        scheduler.scheduleAtFixedRate(this::refill, 0, 1, TimeUnit.SECONDS);
    }

    @Override
    public boolean acquire(Duration maxWait) {
        // Penalty süresindeyse bekle
        Instant now = Instant.now();
        if (now.isBefore(penaltyUntil)) {
            long waitMs = Duration.between(now, penaltyUntil).toMillis();
            if (waitMs > maxWait.toMillis()) {
                log.warn("Rate limit penalty active, request rejected (wait {}ms)", waitMs);
                return false;
            }
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        try {
            boolean acquired = semaphore.tryAcquire(maxWait.toMillis(), TimeUnit.MILLISECONDS);
            if (!acquired) log.warn("Rate limiter: could not acquire token within {}ms", maxWait.toMillis());
            return acquired;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    @Override
    public void penalize(Duration retryAfter) {
        penaltyUntil = Instant.now().plus(retryAfter);
        // Mevcut tüm token'ları geri al
        semaphore.drainPermits();
        log.info("Rate limit penalty: {}s", retryAfter.getSeconds());
    }

    private void refill() {
        int current = semaphore.availablePermits();
        int toAdd = Math.min(permitsPerSecond, maxBurst - current);
        if (toAdd > 0) semaphore.release(toAdd);
    }

    @Override
    public int getPermitsPerSecond() { return permitsPerSecond; }

    public void shutdown() { scheduler.shutdown(); }
}
```

### RateLimiterConfig.java (YAML'dan okuma)

```java
package io.marketplace.sdk.ratelimit;

/**
 * YAML'daki rateLimit bloğunu temsil eder.
 *
 * Örnek YAML:
 *   rateLimit:
 *     permitsPerSecond: 10
 *     maxBurst: 20
 *     maxWaitSeconds: 5
 */
public class RateLimiterConfig {
    private int permitsPerSecond = 10;
    private int maxBurst = 20;
    private int maxWaitSeconds = 5;

    // Getters & Setters (SnakeYAML için)
    public int getPermitsPerSecond() { return permitsPerSecond; }
    public void setPermitsPerSecond(int v) { this.permitsPerSecond = v; }
    public int getMaxBurst() { return maxBurst; }
    public void setMaxBurst(int v) { this.maxBurst = v; }
    public int getMaxWaitSeconds() { return maxWaitSeconds; }
    public void setMaxWaitSeconds(int v) { this.maxWaitSeconds = v; }
}
```

### BaseAdapter'da RateLimiter entegrasyonu

```java
// BaseAdapter'a eklenen alan:
protected RateLimiter rateLimiter;

@Override
public void initialize(AdapterConfig config) {
    // ... diğer init kodları ...
    RateLimiterConfig rlConfig = config.getExtra("rateLimit") != null
        ? parseRateLimitConfig(config)
        : new RateLimiterConfig(); // varsayılan değerler
    this.rateLimiter = new TokenBucketRateLimiter(
        rlConfig.getPermitsPerSecond(),
        rlConfig.getMaxBurst()
    );
}

// executeWithRetry() başına eklenir:
private HttpResponse executeWithRetry(HttpRequest request) {
    // Token al — bloke etmeden
    boolean acquired = rateLimiter.acquire(Duration.ofSeconds(config.getExtra("rateLimitWaitSeconds", 5)));
    if (!acquired) {
        throw new RateLimitException(0); // kuyruk doldu, hızlı fail
    }

    int attempts = 0;
    while (attempts <= config.getMaxRetries()) {
        HttpResponse response = httpClient.execute(request);

        if (response.getStatusCode() == 429) {
            int retryAfter = Integer.parseInt(
                response.getHeaders().getOrDefault("Retry-After", "60"));
            rateLimiter.penalize(Duration.ofSeconds(retryAfter));
            throw new RateLimitException(retryAfter);
        }
        // ... geri kalan retry mantığı
    }
}
```

### YAML'da rateLimit konfigürasyonu

```yaml
# trendyol.yaml — rate limit bölümü
rateLimit:
  permitsPerSecond: 10    # Trendyol API limiti: ~600/dakika
  maxBurst: 20            # Anlık patlama toleransı
  maxWaitSeconds: 5       # Token beklenecek maksimum süre
```

---

## BÖLÜM 20 — Enterprise Eklenti 6: Metadata Cache (Caffeine)

### Sorun
`getCategories`, `getBrands`, `getAttributes` gibi metadata operasyonları
haftalarca değişmez. Her çağrıda ağ isteği yapılması API limitlerini harcatır.

### Çözüm
Caffeine cache operasyon bazında YAML'dan konfigüre edilir.
`cacheTtlSeconds` tanımlı operasyonlar otomatik önbelleklenir.
Cache miss → gerçek HTTP isteği → cache'e yaz → sonraki çağrılarda cache'den dön.

### CacheManager.java

```java
package io.marketplace.sdk.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.marketplace.sdk.core.operation.OperationResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Operasyon bazında TTL konfigürasyonlu cache.
 * Her operasyon için ayrı Caffeine instance'ı oluşturulur (farklı TTL'ler).
 *
 * Cache key = marketplace + ":" + operation + ":" + paramHash
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    // operationName → Cache instance
    private final ConcurrentHashMap<String, Cache<String, OperationResponse>> caches
        = new ConcurrentHashMap<>();

    /**
     * Operasyon için cache tanımlar.
     * @param operationKey  "TRENDYOL:GET_CATEGORIES"
     * @param ttlSeconds    0 = cache devre dışı
     * @param maxSize       maksimum entry sayısı
     */
    public void registerOperation(String operationKey, int ttlSeconds, int maxSize) {
        if (ttlSeconds <= 0) return;
        Cache<String, OperationResponse> cache = Caffeine.newBuilder()
            .expireAfterWrite(ttlSeconds, TimeUnit.SECONDS)
            .maximumSize(maxSize)
            .recordStats()
            .build();
        caches.put(operationKey, cache);
        log.debug("Cache registered: {} TTL={}s maxSize={}", operationKey, ttlSeconds, maxSize);
    }

    /**
     * Cache'den al veya supplier'ı çalıştırarak yükle.
     * operationKey için cache tanımlı değilse supplier doğrudan çalışır.
     */
    public OperationResponse getOrLoad(String operationKey, String paramHash,
                                        Supplier<OperationResponse> loader) {
        Cache<String, OperationResponse> cache = caches.get(operationKey);
        if (cache == null) {
            return loader.get(); // cache yok, direkt yükle
        }

        String cacheKey = operationKey + ":" + paramHash;
        OperationResponse cached = cache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("Cache HIT: {}", cacheKey);
            return cached;
        }

        log.debug("Cache MISS: {}", cacheKey);
        OperationResponse response = loader.get();
        if (response.isSuccess()) {
            cache.put(cacheKey, response);
        }
        return response;
    }

    /** Cache istatistikleri (hit rate, eviction sayısı vs.) */
    public String stats(String operationKey) {
        Cache<String, OperationResponse> cache = caches.get(operationKey);
        return cache != null ? cache.stats().toString() : "no cache";
    }

    /** Belirli bir operasyonun cache'ini temizle */
    public void invalidate(String operationKey) {
        Cache<String, OperationResponse> cache = caches.get(operationKey);
        if (cache != null) cache.invalidateAll();
    }

    /** Tüm cache'leri temizle */
    public void invalidateAll() {
        caches.values().forEach(Cache::invalidateAll);
    }
}
```

### BaseAdapter'da cache entegrasyonu

```java
// BaseAdapter'a eklenen alan:
protected CacheManager cacheManager;

@Override
public OperationResponse execute(OperationRequest request) {
    String opName = camelCase(request.getOperation().name());
    OperationConfig opConfig = config.getOperation(opName);

    // Cache key üret
    String operationKey = getType().name() + ":" + request.getOperation().name();
    String paramHash = Integer.toHexString(request.getParams().hashCode());

    // Cache üzerinden çalıştır
    return cacheManager.getOrLoad(operationKey, paramHash, () -> {
        // Gerçek HTTP isteği
        return executeHttp(request, opConfig);
    });
}
```

### YAML'da cache konfigürasyonu

```yaml
operations:
  getCategories:
    method: GET
    path: /product-categories
    cache:
      ttlSeconds: 86400      # 24 saat — kategoriler çok nadir değişir
      maxSize: 10            # 10 farklı parametre kombinasyonu
    responseMapping:
      categories: "$[*]"

  getBrands:
    method: GET
    path: /brands
    cache:
      ttlSeconds: 3600       # 1 saat
      maxSize: 50
    responseMapping:
      brands: "$[*]"

  getOrders:
    method: GET
    path: /suppliers/{supplierId}/orders
    # cache tanımlı değil → her çağrıda gerçek istek yapılır
    responseMapping:
      orderList: "$.content[*]"
```

---

## BÖLÜM 21 — Enterprise Eklenti 7: Webhook Handler

### Sorun
E-ticaret entegrasyonları yalnızca veri çekmez (Pull); pazaryerleri de olayları
push eder (sipariş durumu değişti, iade talebi geldi vb.).
Trendyol ve Hepsiburada webhook desteği sunar.

### Çözüm
`WebhookServer` gömülü Javalin sunucusu üzerinde `/webhook/{marketplace}` endpoint'i açar.
Her pazaryerinin imza doğrulaması ve payload normalizasyonu ayrı handler sınıflarında yapılır.

### WebhookEvent.java (normalize edilmiş event modeli)

```java
package io.marketplace.sdk.webhook;

import io.marketplace.sdk.core.model.MarketplaceType;
import java.time.Instant;
import java.util.Map;

/**
 * Tüm pazaryerlerinden gelen webhook payload'larının normalize edilmiş hali.
 * Pazaryerine özgü alanlar rawPayload içinde korunur.
 */
public class WebhookEvent {
    private final MarketplaceType source;
    private final EventType eventType;
    private final String entityId;      // orderId, productId vs.
    private final String entityType;    // ORDER, PRODUCT, RETURN
    private final Instant occurredAt;
    private final Map<String, Object> data;      // normalize edilmiş alanlar
    private final String rawPayload;             // ham JSON

    public enum EventType {
        ORDER_CREATED,
        ORDER_STATUS_CHANGED,
        ORDER_CANCELLED,
        PRODUCT_APPROVED,
        PRODUCT_REJECTED,
        RETURN_REQUESTED,
        RETURN_APPROVED,
        CLAIM_CREATED,
        UNKNOWN
    }

    // Constructor + builder + getters
    private WebhookEvent(Builder b) {
        this.source = b.source;
        this.eventType = b.eventType;
        this.entityId = b.entityId;
        this.entityType = b.entityType;
        this.occurredAt = b.occurredAt;
        this.data = b.data;
        this.rawPayload = b.rawPayload;
    }

    public MarketplaceType getSource() { return source; }
    public EventType getEventType() { return eventType; }
    public String getEntityId() { return entityId; }
    public String getEntityType() { return entityType; }
    public Instant getOccurredAt() { return occurredAt; }
    public Map<String, Object> getData() { return data; }
    public String getRawPayload() { return rawPayload; }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private MarketplaceType source;
        private EventType eventType;
        private String entityId, entityType, rawPayload;
        private Instant occurredAt = Instant.now();
        private Map<String, Object> data = Map.of();

        public Builder source(MarketplaceType v) { this.source = v; return this; }
        public Builder eventType(EventType v) { this.eventType = v; return this; }
        public Builder entityId(String v) { this.entityId = v; return this; }
        public Builder entityType(String v) { this.entityType = v; return this; }
        public Builder occurredAt(Instant v) { this.occurredAt = v; return this; }
        public Builder data(Map<String, Object> v) { this.data = v; return this; }
        public Builder rawPayload(String v) { this.rawPayload = v; return this; }
        public WebhookEvent build() { return new WebhookEvent(this); }
    }
}
```

### WebhookHandler.java (SPI)

```java
package io.marketplace.sdk.webhook;

import io.marketplace.sdk.core.model.MarketplaceType;

/**
 * Her pazaryerinin implement ettiği webhook SPI.
 * İmza doğrulaması ve payload normalizasyonu bu interface üzerinden yapılır.
 */
public interface WebhookHandler {

    MarketplaceType getMarketplace();

    /**
     * İmza doğrulaması.
     * @param payload   ham HTTP body
     * @param signature X-Trendyol-Signature veya benzeri header
     * @param secret    YAML'dan gelen webhook secret
     * @return true imza geçerli
     */
    boolean verifySignature(String payload, String signature, String secret);

    /**
     * Ham payload'ı normalize WebhookEvent'e dönüştürür.
     */
    WebhookEvent normalize(String rawPayload);
}
```

### TrendyolWebhookHandler.java

```java
package io.marketplace.sdk.webhook.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.webhook.WebhookEvent;
import io.marketplace.sdk.webhook.WebhookHandler;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

public class TrendyolWebhookHandler implements WebhookHandler {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public MarketplaceType getMarketplace() { return MarketplaceType.TRENDYOL; }

    @Override
    public boolean verifySignature(String payload, String signature, String secret) {
        if (signature == null || secret == null) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(), "HmacSHA256"));
            byte[] computed = mac.doFinal(payload.getBytes());
            String expected = Base64.getEncoder().encodeToString(computed);
            return expected.equals(signature);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public WebhookEvent normalize(String rawPayload) {
        try {
            JsonNode root = mapper.readTree(rawPayload);

            String eventTypeStr = root.path("eventType").asText("UNKNOWN");
            WebhookEvent.EventType eventType = mapEventType(eventTypeStr);

            String orderId = root.path("orderNumber").asText(
                root.path("id").asText(null));

            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId);
            data.put("status", root.path("status").asText());
            data.put("customerId", root.path("customerId").asText());

            long ts = root.path("eventTime").asLong(0);
            Instant occurredAt = ts > 0 ? Instant.ofEpochMilli(ts) : Instant.now();

            return WebhookEvent.builder()
                .source(MarketplaceType.TRENDYOL)
                .eventType(eventType)
                .entityId(orderId)
                .entityType("ORDER")
                .occurredAt(occurredAt)
                .data(data)
                .rawPayload(rawPayload)
                .build();

        } catch (Exception e) {
            return WebhookEvent.builder()
                .source(MarketplaceType.TRENDYOL)
                .eventType(WebhookEvent.EventType.UNKNOWN)
                .rawPayload(rawPayload)
                .build();
        }
    }

    private WebhookEvent.EventType mapEventType(String raw) {
        return switch (raw.toUpperCase()) {
            case "ORDER_CREATED"        -> WebhookEvent.EventType.ORDER_CREATED;
            case "PACKAGE_STATUS_UPDATE"-> WebhookEvent.EventType.ORDER_STATUS_CHANGED;
            case "ORDER_CANCELLED"      -> WebhookEvent.EventType.ORDER_CANCELLED;
            case "CLAIM_CREATED"        -> WebhookEvent.EventType.CLAIM_CREATED;
            default                     -> WebhookEvent.EventType.UNKNOWN;
        };
    }
}
```

### WebhookServer.java

```java
package io.marketplace.sdk.webhook;

import io.javalin.Javalin;
import io.marketplace.sdk.core.model.MarketplaceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Webhook alıcı sunucu.
 * Her pazaryeri için ayrı handler + imza doğrulaması.
 * Kullanıcı onEvent() ile dinleyici kaydeder.
 *
 * Kullanım:
 *   WebhookServer server = new WebhookServer(8080, handlers, webhookSecrets);
 *   server.onEvent(TRENDYOL, event -> {
 *       if (event.getEventType() == ORDER_CREATED) {
 *           orderService.create(event);
 *       }
 *   });
 *   server.start();
 */
public class WebhookServer {

    private static final Logger log = LoggerFactory.getLogger(WebhookServer.class);

    private final int port;
    private final Map<MarketplaceType, WebhookHandler> handlers;
    private final Map<MarketplaceType, String> webhookSecrets;
    private final Map<MarketplaceType, List<Consumer<WebhookEvent>>> listeners
        = new ConcurrentHashMap<>();
    private Javalin app;

    public WebhookServer(int port,
                         List<WebhookHandler> handlers,
                         Map<MarketplaceType, String> webhookSecrets) {
        this.port = port;
        this.handlers = new ConcurrentHashMap<>();
        handlers.forEach(h -> this.handlers.put(h.getMarketplace(), h));
        this.webhookSecrets = webhookSecrets;
    }

    /** Belirli bir pazaryerinden gelen olayları dinle */
    public void onEvent(MarketplaceType marketplace, Consumer<WebhookEvent> listener) {
        listeners.computeIfAbsent(marketplace, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                 .add(listener);
    }

    public void start() {
        app = Javalin.create();

        // POST /webhook/{marketplace}
        app.post("/webhook/{marketplace}", ctx -> {
            String marketplaceName = ctx.pathParam("marketplace").toUpperCase().replace("-", "_");
            MarketplaceType type;
            try {
                type = MarketplaceType.valueOf(marketplaceName);
            } catch (IllegalArgumentException e) {
                ctx.status(404).result("Unknown marketplace: " + marketplaceName);
                return;
            }

            WebhookHandler handler = handlers.get(type);
            if (handler == null) {
                ctx.status(501).result("No handler registered for: " + type);
                return;
            }

            String payload = ctx.body();
            String signature = ctx.header("X-Trendyol-Signature");  // pazaryerine göre değişir
            String secret = webhookSecrets.getOrDefault(type, "");

            if (!handler.verifySignature(payload, signature, secret)) {
                log.warn("Invalid webhook signature from {}", type);
                ctx.status(401).result("Invalid signature");
                return;
            }

            WebhookEvent event = handler.normalize(payload);
            log.info("Webhook received: {} {} {}", type, event.getEventType(), event.getEntityId());

            // Dinleyicileri tetikle (async — HTTP yanıtını bloke etme)
            List<Consumer<WebhookEvent>> typeListeners = listeners.getOrDefault(type, List.of());
            if (!typeListeners.isEmpty()) {
                Thread.ofVirtual().start(() ->
                    typeListeners.forEach(l -> {
                        try { l.accept(event); }
                        catch (Exception ex) { log.error("Webhook listener error", ex); }
                    })
                );
            }

            ctx.status(200).result("OK");
        });

        app.start(port);
        log.info("Webhook server started on port {}", port);
    }

    public void stop() {
        if (app != null) app.stop();
    }
}
```

### MarketplaceSDK.builder() — webhook entegrasyonu

```java
// MarketplaceSDK.Builder'a eklenenler:
private boolean webhookEnabled = false;
private int webhookPort = 8080;
private Map<MarketplaceType, String> webhookSecrets = new HashMap<>();

public Builder webhook(boolean enabled) { this.webhookEnabled = enabled; return this; }
public Builder webhookPort(int port) { this.webhookPort = port; return this; }
public Builder webhookSecret(MarketplaceType type, String secret) {
    this.webhookSecrets.put(type, secret);
    return this;
}
```

### Tam Kullanım Örneği (Enterprise)

```java
MarketplaceSDK sdk = MarketplaceSDK.builder()
    .configDir("/etc/marketplace/configs")
    .adminUi(true).adminUiPort(8090)
    .webhook(true).webhookPort(8080)
    .webhookSecret(TRENDYOL, "trendyol-webhook-secret-key")
    .hotReload(true)
    .build();

MarketplaceClient client = sdk.client();
AsyncMarketplaceClient asyncClient = sdk.asyncClient();
WebhookServer webhookServer = sdk.webhookServer();

// 1. Senkron sipariş çekme
OperationResponse orders = client.execute(
    OperationRequest.builder(TRENDYOL, GET_ORDERS)
        .param("status", "Created").build()
);
List<Order> orderList = orders.getDataAs(OrderListResponse.class).getOrders();

// 2. Tüm sayfaları otomatik çek (Stream API)
sdk.stream(TRENDYOL, GET_ORDERS, Map.of("status", "Created"))
   .flatMap(resp -> resp.getDataAs(OrderListResponse.class).getOrders().stream())
   .forEach(order -> processOrder(order));

// 3. Paralel pazaryeri sorgusu (async)
asyncClient.executeAll(List.of(
    OperationRequest.builder(TRENDYOL, GET_ORDERS).param("status", "Created").build(),
    OperationRequest.builder(HEPSIBURADA, GET_ORDERS).param("status", "WAITING").build(),
    OperationRequest.builder(N11, GET_ORDERS).build()
)).thenAccept(responses -> responses.forEach(r -> mergeOrders(r)));

// 4. Webhook dinle
webhookServer.onEvent(TRENDYOL, event -> {
    if (event.getEventType() == ORDER_STATUS_CHANGED) {
        notificationService.notify(event.getEntityId(), event.getData());
    }
});
webhookServer.start();

// 5. Kategoriler cache'den gelir (24 saat TTL)
client.execute(OperationRequest.builder(TRENDYOL, GET_CATEGORIES).build());
client.execute(OperationRequest.builder(TRENDYOL, GET_CATEGORIES).build()); // cache HIT

// 6. Kapatma
sdk.shutdown(); // tüm kaynakları serbest bırakır
```

---

## BÖLÜM 22 — Enterprise Kontrol Listesi (Ek Maddeler)

**Type Safety**
- [ ] `FieldMapper.mapAndConvert()` test edildi: `Order.class`, `List<Order>` dönüşümü başarılı
- [ ] `response.getDataAs(Order.class)` ClassCastException fırlatmıyor
- [ ] YAML mapping'de olmayan field için null döner, exception fırlatmaz

**Token Refresh**
- [ ] `AmazonTokenProvider` 1 saatlik token sonrası otomatik refresh yapıyor
- [ ] 401 alındığında `tokenProvider.invalidate()` + tek retry çalışıyor
- [ ] Token refresh thread-safe: aynı anda 10 istek geldiğinde tek refresh yapılıyor

**Async**
- [ ] `executeAsync()` CompletableFuture döndürüyor, thread bloke etmiyor
- [ ] `executeAll()` 3 pazaryerini paralel sorguluyor
- [ ] Exception durumunda CompletableFuture exceptionally() ile yakalanıyor

**Pagination**
- [ ] `sdk.stream(TRENDYOL, GET_ORDERS, params)` tüm sayfaları çekiyor
- [ ] YAML'da `pagination.type: OFFSET` ile OffsetPaginationStrategy aktif
- [ ] Amazon için `pagination.type: CURSOR` ve `cursorField: nextToken` çalışıyor
- [ ] Son sayfada Stream düzgün tamamlanıyor

**Rate Limiter**
- [ ] 429 sonrası `Retry-After` süresi kadar penalize uygulanıyor
- [ ] Thread.sleep() yok — Semaphore.tryAcquire() ile non-blocking
- [ ] YAML'da `rateLimit.permitsPerSecond` değiştirildiğinde hot-reload sonrası aktif

**Cache**
- [ ] `getCategories` ilk çağrıda HTTP isteği yapıyor, ikinci çağrıda cache'den dönüyor
- [ ] `cache.ttlSeconds: 0` ile cache devre dışı bırakılabiliyor
- [ ] `cacheManager.invalidate("TRENDYOL:GET_CATEGORIES")` cache'i temizliyor

**Webhook**
- [ ] `POST /webhook/trendyol` endpoint'i 200 dönüyor
- [ ] Geçersiz imzada 401 dönüyor
- [ ] `onEvent()` listener async çalışıyor — HTTP response beklemiyor
- [ ] `WebhookEvent.getDataAs()` normalize alanları barındırıyor

