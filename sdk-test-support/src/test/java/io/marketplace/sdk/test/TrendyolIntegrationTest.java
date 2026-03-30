package io.marketplace.sdk.test;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.operation.Operation;
import io.marketplace.sdk.core.spi.AdapterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Trendyol YAML config doğrulama testleri.
 *
 * HTTP katmanına girmeden yalnızca config yükleme,
 * sellerId çözümlemesi ve operasyon varlığını doğrular.
 *
 * Bu testler geçerse:
 *   - trendyol.yaml parse edilebiliyor
 *   - cache alanı hata vermeden okunuyor
 *   - sellerId credentials'dan alınıyor
 *   - Tüm beklenen operasyonlar YAML'da tanımlı
 */
class TrendyolIntegrationTest {

    @Test
    @DisplayName("trendyol.yaml başarıyla yüklenir ve TRENDYOL adapter'ı kayıt olur")
    void yamlLoadsSuccessfully() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        Map<MarketplaceType, AdapterConfig> configs = sdk.getConfigurations();
        assertThat(configs).containsKey(MarketplaceType.TRENDYOL);

        sdk.shutdown();
    }

    @Test
    @DisplayName("sellerId credentials'dan okunur ve '12345' değerine sahip")
    void sellerIdIsReadFromCredentials() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        AdapterConfig config = sdk.getConfigurations().get(MarketplaceType.TRENDYOL);

        // sellerId trendyol.yaml'daki credentials.sellerId'den gelir
        assertThat(config.getCredentials().getSellerId()).isEqualTo("12345");

        // extra map'e de kopyalandı — buildUrl() bunu kullanır
        assertThat((String) config.getExtra("sellerId")).isEqualTo("12345");

        sdk.shutdown();
    }

    @Test
    @DisplayName("cache alanı olan operasyonlar hata vermeden yüklenir")
    void cacheFieldsAreIgnoredGracefully() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        AdapterConfig config = sdk.getConfigurations().get(MarketplaceType.TRENDYOL);

        // cache: tanımlı operasyonlar yüklenmiş olmalı
        assertThat(config.getOperation("getCategories")).isNotNull();
        assertThat(config.getOperation("getBrands")).isNotNull();
        assertThat(config.getOperation("getAttributes")).isNotNull();
        assertThat(config.getOperation("getShipmentProviders")).isNotNull();

        sdk.shutdown();
    }

    @Test
    @DisplayName("createProduct operasyonu YAML'da 'items' template ile tanımlı")
    void createProductOperationHasItemsTemplate() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        AdapterConfig config = sdk.getConfigurations().get(MarketplaceType.TRENDYOL);

        var createProductOp = config.getOperation("createProduct");
        assertThat(createProductOp).isNotNull();
        assertThat(createProductOp.getMethod()).isEqualTo("POST");
        assertThat(createProductOp.getPath())
            .isEqualTo("/integration/product/sellers/{sellerId}/products");
        // requestTemplate içinde ${items} var — multi-product desteği
        assertThat(createProductOp.getRequestTemplate()).contains("${items}");

        sdk.shutdown();
    }

    @Test
    @DisplayName("Tüm temel operasyonlar YAML'da tanımlı")
    void allExpectedOperationsAreDefined() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        AdapterConfig config = sdk.getConfigurations().get(MarketplaceType.TRENDYOL);

        Set<String> expectedOps = Set.of(
            "getOrders",
            "getOrderDetail",
            "updateOrderStatus",
            "createProduct",
            "updateProduct",
            "deleteProduct",
            "updateStock",
            "updatePrice",
            "getBatchRequestResult",
            "getCategories",
            "getBrands",
            "getAttributes",
            "getShipmentProviders"
        );

        for (String op : expectedOps) {
            assertThat(config.getOperation(op))
                .as("Operasyon tanımlı olmalı: " + op)
                .isNotNull();
        }

        sdk.shutdown();
    }

    @Test
    @DisplayName("TRENDYOL adapter desteklenen operasyonları bildirir")
    void adapterDeclaresAllSupportedOperations() {
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();

        // client() üzerinden adapter'a dolaylı ulaşıyoruz; operations kümesi
        // supportedOperations() ile kontrol edilir
        var client = sdk.client();

        assertThat(client.supports(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)).isTrue();
        assertThat(client.supports(MarketplaceType.TRENDYOL, Operation.UPDATE_STOCK)).isTrue();
        assertThat(client.supports(MarketplaceType.TRENDYOL, Operation.GET_ORDERS)).isTrue();
        assertThat(client.supports(MarketplaceType.TRENDYOL, Operation.GET_BATCH_REQUEST_RESULT)).isTrue();

        sdk.shutdown();
    }
}
