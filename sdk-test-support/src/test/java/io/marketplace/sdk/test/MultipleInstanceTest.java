package io.marketplace.sdk.test;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.operation.OperationRequest;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tek satıcı — çok ürün (multi-product) request builder testleri.
 * <p>
 * Bu testler HTTP katmanını test etmez; OperationRequest'in
 * doğru params ile kurulduğunu ve SDK'nın config'i başarıyla
 * yüklediğini doğrular.
 * <p>
 * HTTP + WireMock testleri → MarketplaceIntegrationTest
 * <p>
 * Trendyol'da single/multi product ayrımı YOKTUR:
 * - Aynı endpoint: POST /integration/product/sellers/{sellerId}/products
 * - Aynı operasyon: CREATE_PRODUCT
 * - Fark sadece "items" listesinin boyutundadır (1 veya N)
 */
class MultipleInstanceTest {

    /**
     * SDK'nın config dizinini bulup adapter'ları kaydettiğini doğrular.
     * Bu test geçerse "No adapter registered" hatası olmaz.
     */
    @Test
    @DisplayName("SDK config dizinini yükler ve adapter'ları kaydeder")
    void sdkLoadsConfigSuccessfully() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
                .configDir("../marketplace-configs")
                .build();

        // Config yüklendi → TRENDYOL adapter kayıtlı olmalı
        Map<MarketplaceType, ?> configs = sdk.getConfigurations();
        assertThat(configs).containsKey(MarketplaceType.TRENDYOL);

        sdk.shutdown();
    }

    @Test
    @DisplayName("Tek ürün isteği doğru params ile oluşturulur")
    void singleProductRequestBuildsCorrectly() {
        HashMap<String, Object> product = getProduct();

        OperationRequest request = OperationRequest
                .builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
                .param("items", List.of(product))
                .build();

        assertThat(request.getMarketplace()).isEqualTo(MarketplaceType.TRENDYOL);
        assertThat(request.getOperation()).isEqualTo(Operation.CREATE_PRODUCT);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) request.getParams().get("items");

        assertThat(items).hasSize(1);
        assertThat(items.get(0)).containsEntry("barcode", "8681234567890");
        assertThat(items.get(0)).containsEntry("title", "Tek Ürün Testi");
    }

    @NotNull
    private static HashMap<String, Object> getProduct() {
        HashMap<String, Object> product = new HashMap<>(Map.of(
                "barcode", "8681234567890",
                "title", "Tek Ürün Testi",
                "productMainId", "MAIN-001",
                "brandId", 1791,
                "categoryId", 411,
                "quantity", 100,
                "stockCode", "STK-001",
                "description", "Test açıklaması",
                "dimensionalWeight", 1.5,
                "listPrice", 299.99));
        product.put("salePrice", 249.99);
        product.put("vatRate", 18);
        product.put("cargoCompanyId", 10);
        product.put("deliveryDuration", 3);
        return product;
    }

    @Test
    @DisplayName("Çok ürün — aynı satıcı, tek istek, items array N elemanlı")
    void multipleProductsRequestBuildsCorrectly() {
        // Trendyol API bir POST'ta birden fazla ürünü kabul eder.
        // Satıcı (sellerId) tekdir; fark yalnızca items[] boyutundadır.
        Map<String, Object> product1 = Map.of(
                "barcode", "8681111111111",
                "title", "Ürün A",
                "listPrice", 100.0,
                "salePrice", 90.0,
                "quantity", 50,
                "stockCode", "STK-A",
                "categoryId", 411,
                "brandId", 1791,
                "vatRate", 18
        );
        Map<String, Object> product2 = Map.of(
                "barcode", "8682222222222",
                "title", "Ürün B",
                "listPrice", 200.0,
                "salePrice", 180.0,
                "quantity", 30,
                "stockCode", "STK-B",
                "categoryId", 411,
                "brandId", 1791,
                "vatRate", 18
        );
        Map<String, Object> product3 = Map.of(
                "barcode", "8683333333333",
                "title", "Ürün C",
                "listPrice", 300.0,
                "salePrice", 270.0,
                "quantity", 20,
                "stockCode", "STK-C",
                "categoryId", 411,
                "brandId", 1791,
                "vatRate", 18
        );

        OperationRequest request = OperationRequest
                .builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
                .param("items", List.of(product1, product2, product3))
                .build();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items =
                (List<Map<String, Object>>) request.getParams().get("items");

        assertThat(items).hasSize(3);
        assertThat(items.get(0)).containsEntry("barcode", "8681111111111");
        assertThat(items.get(1)).containsEntry("barcode", "8682222222222");
        assertThat(items.get(2)).containsEntry("barcode", "8683333333333");
    }

    @Test
    @DisplayName("Stok güncelleme isteği — çok ürün, tek istek")
    void batchStockUpdateRequestBuildsCorrectly() {
        List<Map<String, Object>> stockItems = List.of(
                Map.of("barcode", "8681111111111", "quantity", 100,
                        "salePrice", 90.0, "listPrice", 100.0),
                Map.of("barcode", "8682222222222", "quantity", 50,
                        "salePrice", 180.0, "listPrice", 200.0),
                Map.of("barcode", "8683333333333", "quantity", 0,
                        "salePrice", 270.0, "listPrice", 300.0)
        );

        OperationRequest request = OperationRequest
                .builder(MarketplaceType.TRENDYOL, Operation.UPDATE_STOCK)
                .param("items", stockItems)
                .build();

        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) request.getParams().get("items");

        assertThat(items).hasSize(3);
        assertThat(request.getOperation()).isEqualTo(Operation.UPDATE_STOCK);
    }

    @Test
    @DisplayName("Batch sonucu sorgu isteği doğru params ile oluşturulur")
    void batchRequestResultQueryBuildsCorrectly() {
        OperationRequest request = OperationRequest
                .builder(MarketplaceType.TRENDYOL, Operation.GET_BATCH_REQUEST_RESULT)
                .param("batchRequestId", "BATCH-ABC-123")
                .build();

        assertThat(request.getOperation()).isEqualTo(Operation.GET_BATCH_REQUEST_RESULT);
        assertThat(request.getParams()).containsEntry("batchRequestId", "BATCH-ABC-123");
    }

    @Test
    @DisplayName("Fiyat güncelleme isteği — çok ürün")
    void batchPriceUpdateRequestBuildsCorrectly() {
        List<Map<String, Object>> priceItems = List.of(
                Map.of("barcode", "8681111111111", "salePrice", 85.0, "listPrice", 95.0),
                Map.of("barcode", "8682222222222", "salePrice", 170.0, "listPrice", 190.0)
        );

        OperationRequest request = OperationRequest
                .builder(MarketplaceType.TRENDYOL, Operation.UPDATE_PRICE)
                .param("items", priceItems)
                .build();

        @SuppressWarnings("unchecked")
        List<?> items = (List<?>) request.getParams().get("items");

        assertThat(items).hasSize(2);
        assertThat(request.getOperation()).isEqualTo(Operation.UPDATE_PRICE);
    }
}
