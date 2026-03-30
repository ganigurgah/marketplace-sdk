package io.marketplace.sdk.test;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

/**
 * WireMock tabanlı entegrasyon testleri.
 *
 * Konfigürasyon:
 *   trendyol.yaml → baseUrl: http://localhost:8080
 *   trendyol.yaml → credentials.sellerId: "12345"
 *
 * Beklenen URL şablonu: /integration/product/sellers/{sellerId}/products
 * Çözümlenmiş URL    : /integration/product/sellers/12345/products
 *
 * sellerId sadece yaml'dan okunur. Test kodu sellerId bilmez.
 * Trendyol sellerId'si değiştiğinde yalnızca trendyol.yaml güncellenir.
 *
 * Multi-product:
 *   Tek satıcı (sellerId=12345), tek POST isteğinde N ürün gönderebilir.
 *   "items" array 1..N eleman içerebilir — YAML veya Java kodu değişmez.
 */
public class MarketplaceIntegrationTest {

    private static WireMockServer wireMockServer;
    private static MarketplaceClient client;

    @BeforeAll
    static void setupClass() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);

        // trendyol.yaml'daki baseUrl: http://localhost:8080
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        client = sdk.client();
    }

    @AfterAll
    static void teardownClass() {
        if (wireMockServer != null) wireMockServer.stop();
    }

    @BeforeEach
    void resetStubs() {
        WireMock.reset();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Sipariş Testleri
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Sipariş listesi — sellerId yaml'dan okunur")
    void testTrendyolGetOrders() {
        // sellerId=12345 → trendyol.yaml'daki credentials.sellerId değeri
        stubFor(get(urlPathEqualTo("/integration/order/sellers/12345/orders"))
            .withQueryParam("status", equalTo("Created"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "page": 0,
                      "totalPages": 2,
                      "content": [
                        {
                          "orderNumber": "ORD-001",
                          "status": "Created",
                          "totalPrice": 249.99,
                          "orderDate": 1711000000000
                        },
                        {
                          "orderNumber": "ORD-002",
                          "status": "Created",
                          "totalPrice": 149.99,
                          "orderDate": 1711100000000
                        }
                      ]
                    }
                    """)));

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.GET_ORDERS)
            .param("status", "Created")
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getHttpStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data).containsKey("orderList");
        assertThat(data).containsKey("totalPages");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ürün Oluşturma — Tek Ürün
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Tek ürün oluşturma — items[0] ile")
    void testTrendyolCreateSingleProduct() {
        stubFor(post(urlEqualTo("/integration/product/sellers/12345/products"))
            .withHeader("Content-Type", containing("application/json"))
            // items array'in ilk (ve tek) elemanı doğrulanır
            .withRequestBody(matchingJsonPath("$.items[0].barcode",
                equalTo("8681234567890")))
            .withRequestBody(matchingJsonPath("$.items[0].title",
                equalTo("Pamuklu Basic Tişört")))
            .withRequestBody(matchingJsonPath("$.items[0].categoryId",
                equalTo("411")))
            .withRequestBody(matchingJsonPath("$.items[0].brandId",
                equalTo("1791")))
            .withRequestBody(matchingJsonPath("$.items[0].quantity",
                equalTo("50")))
            .withRequestBody(matchingJsonPath("$.items[0].salePrice",
                equalTo("149.99")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"BATCH-SINGLE-001\"}")));

        Map<String, Object> product = buildProduct(
            "8681234567890", "Pamuklu Basic Tişört", "MAIN-001",
            1791, 411, 50, "STK-TISORT-001",
            1.2, "Pamuklu kumaş, unisex kesim.",
            199.99, 149.99, 18, 10, 3
        );

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
            .param("items", List.of(product))   // tek ürün → 1 elemanlı liste
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getHttpStatus()).isEqualTo(200);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("BATCH-SINGLE-001");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ürün Oluşturma — Çok Ürün (Multi-product)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Çok ürün — aynı satıcı, tek POST, items[] N elemanlı")
    void testTrendyolCreateMultipleProducts() {
        // Aynı endpoint, aynı sellerId — yalnızca items[] daha fazla eleman içerir
        stubFor(post(urlEqualTo("/integration/product/sellers/12345/products"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.items[0].barcode",
                equalTo("8681111111111")))
            .withRequestBody(matchingJsonPath("$.items[0].title",
                equalTo("Ürün A — Kırmızı")))
            .withRequestBody(matchingJsonPath("$.items[1].barcode",
                equalTo("8682222222222")))
            .withRequestBody(matchingJsonPath("$.items[1].title",
                equalTo("Ürün B — Mavi")))
            .withRequestBody(matchingJsonPath("$.items[2].barcode",
                equalTo("8683333333333")))
            .withRequestBody(matchingJsonPath("$.items[2].title",
                equalTo("Ürün C — Yeşil")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"BATCH-MULTI-002\"}")));

        Map<String, Object> p1 = buildProduct(
            "8681111111111", "Ürün A — Kırmızı", "MAIN-A",
            1791, 411, 50, "STK-A",
            0.8, "Kırmızı renk seçeneği",
            100.0, 90.0, 18, 10, 2
        );
        Map<String, Object> p2 = buildProduct(
            "8682222222222", "Ürün B — Mavi", "MAIN-B",
            1791, 411, 30, "STK-B",
            0.8, "Mavi renk seçeneği",
            100.0, 90.0, 18, 10, 2
        );
        Map<String, Object> p3 = buildProduct(
            "8683333333333", "Ürün C — Yeşil", "MAIN-C",
            1791, 411, 20, "STK-C",
            0.8, "Yeşil renk seçeneği",
            100.0, 90.0, 18, 10, 2
        );

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
            .param("items", List.of(p1, p2, p3))   // 3 ürün → 3 elemanlı liste
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("BATCH-MULTI-002");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Stok Güncelleme — Çok Ürün
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Stok güncelleme — birden fazla ürün tek istekte")
    void testTrendyolUpdateStockMultipleProducts() {
        stubFor(post(urlEqualTo(
                "/integration/inventory/sellers/12345/products/price-and-inventory"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.items[0].barcode",
                equalTo("8681111111111")))
            .withRequestBody(matchingJsonPath("$.items[0].quantity",
                equalTo("100")))
            .withRequestBody(matchingJsonPath("$.items[1].barcode",
                equalTo("8682222222222")))
            .withRequestBody(matchingJsonPath("$.items[1].quantity",
                equalTo("0")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"BATCH-STOCK-003\"}")));

        List<Map<String, Object>> stockItems = List.of(
            Map.of("barcode", "8681111111111",
                   "quantity", 100, "salePrice", 90.0, "listPrice", 100.0),
            Map.of("barcode", "8682222222222",
                   "quantity", 0, "salePrice", 90.0, "listPrice", 100.0)
        );

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.UPDATE_STOCK)
            .param("items", stockItems)
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("BATCH-STOCK-003");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fiyat Güncelleme — Çok Ürün
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Fiyat güncelleme — birden fazla ürün tek istekte")
    void testTrendyolUpdatePriceMultipleProducts() {
        stubFor(post(urlEqualTo(
                "/integration/inventory/sellers/12345/products/price-and-inventory"))
            .withRequestBody(matchingJsonPath("$.items[0].barcode",
                equalTo("8681111111111")))
            .withRequestBody(matchingJsonPath("$.items[0].salePrice",
                equalTo("85.0")))
            .withRequestBody(matchingJsonPath("$.items[1].barcode",
                equalTo("8682222222222")))
            .withRequestBody(matchingJsonPath("$.items[1].salePrice",
                equalTo("170.0")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"BATCH-PRICE-004\"}")));

        List<Map<String, Object>> priceItems = List.of(
            Map.of("barcode", "8681111111111",
                   "salePrice", 85.0, "listPrice", 95.0, "quantity", 50),
            Map.of("barcode", "8682222222222",
                   "salePrice", 170.0, "listPrice", 190.0, "quantity", 30)
        );

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.UPDATE_PRICE)
            .param("items", priceItems)
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("BATCH-PRICE-004");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Batch Sonucu Takibi
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: Batch sonucu sorgulanır — başarılı ve başarısız ayrımı")
    void testTrendyolGetBatchRequestResult() {
        stubFor(get(urlEqualTo(
                "/integration/product/sellers/12345/products/batch-requests/BATCH-MULTI-002"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "batchRequestId": "BATCH-MULTI-002",
                      "status": "COMPLETED",
                      "items": [
                        {"barcode": "8681111111111", "status": "SUCCESS",
                         "failureReasons": []},
                        {"barcode": "8682222222222", "status": "SUCCESS",
                         "failureReasons": []},
                        {"barcode": "8683333333333", "status": "FAILED",
                         "failureReasons": [{"code": "BARCODE_DUPLICATE",
                                             "message": "Bu barkod zaten mevcut."}]}
                      ]
                    }
                    """)));

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.GET_BATCH_REQUEST_RESULT)
            .param("batchRequestId", "BATCH-MULTI-002")
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();

        assertThat(data.get("status")).isEqualTo("COMPLETED");
        // responseMapping'den: items[*] ve items[?(@.status == 'FAILED')]
        assertThat(data).containsKey("items");
        assertThat(data).containsKey("failedItems");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hata Senaryoları
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Trendyol: 400 hatası — geçersiz istek yanıtı ele alınır")
    void testTrendyolBadRequest() {
        stubFor(post(urlEqualTo("/integration/product/sellers/12345/products"))
            .willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                    {
                      "title": "Validation Error",
                      "errors": [
                        {"code": "BARCODE_REQUIRED", "message": "Barkod zorunludur."}
                      ]
                    }
                    """)));

        // Barkod eksik ürün
        Map<String, Object> invalidProduct = Map.of(
            "title",     "Barkod Yok Ürün",
            "salePrice", 99.0,
            "quantity",  10
        );

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
            .param("items", List.of(invalidProduct))
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.getHttpStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("Trendyol: Ürün silme — items array ile")
    void testTrendyolDeleteProduct() {
        stubFor(delete(urlEqualTo("/integration/product/sellers/12345/products"))
            .withRequestBody(matchingJsonPath("$.items[0].barcode",
                equalTo("8681234567890")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"BATCH-DELETE-005\"}")));

        OperationRequest request = OperationRequest
            .builder(MarketplaceType.TRENDYOL, Operation.DELETE_PRODUCT)
            .param("items", List.of(Map.of("barcode", "8681234567890")))
            .build();

        OperationResponse response = client.execute(request);

        assertThat(response.isSuccess()).isTrue();

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("BATCH-DELETE-005");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Yardımcı
    // ─────────────────────────────────────────────────────────────────────────

    /** Trendyol ürün nesnesini oluşturan yardımcı metot. */
    private Map<String, Object> buildProduct(
            String barcode, String title, String productMainId,
            int brandId, int categoryId, int quantity, String stockCode,
            double dimensionalWeight, String description,
            double listPrice, double salePrice, int vatRate,
            int cargoCompanyId, int deliveryDuration) {

        return Map.ofEntries(
            Map.entry("barcode",           barcode),
            Map.entry("title",             title),
            Map.entry("productMainId",     productMainId),
            Map.entry("brandId",           brandId),
            Map.entry("categoryId",        categoryId),
            Map.entry("quantity",          quantity),
            Map.entry("stockCode",         stockCode),
            Map.entry("dimensionalWeight", dimensionalWeight),
            Map.entry("description",       description),
            Map.entry("listPrice",         listPrice),
            Map.entry("salePrice",         salePrice),
            Map.entry("vatRate",           vatRate),
            Map.entry("cargoCompanyId",    cargoCompanyId),
            Map.entry("deliveryDuration",  deliveryDuration)
        );
    }
}
