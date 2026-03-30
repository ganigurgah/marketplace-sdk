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
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

public class MarketplaceIntegrationTest {

    private static WireMockServer wireMockServer;
    private static MarketplaceClient client;

    @BeforeAll
    static void setupClass() {
        wireMockServer = new WireMockServer(wireMockConfig().port(8080));
        wireMockServer.start();
        WireMock.configureFor("localhost", 8080);
        
        // Ensure tests read config from the committed repository directory
        MarketplaceSDK sdk = MarketplaceSDK.builder()
            .configDir("../marketplace-configs")
            .build();
        client = sdk.client();
    }

    @AfterAll
    static void teardownClass() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Test
    void testTrendyolCreateProduct() {
        // Mock Trendyol Expectation
        stubFor(post(urlEqualTo("/trendyol/integration/product/sellers/12345/products"))
            .withHeader("Content-Type", containing("application/json"))
            .withRequestBody(matchingJsonPath("$.items[0].barcode", equalTo("8691234")))
            .withRequestBody(matchingJsonPath("$.items[0].attributes[0].attributeId", equalTo("1")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"batchRequestId\": \"TR-BATCH-100\"}")));

        OperationRequest request = OperationRequest.builder(MarketplaceType.TRENDYOL, Operation.CREATE_PRODUCT)
            .param("barcode", "8691234")
            .param("sku", "STK-10")
            .param("title", "Test Product")
            .param("description", "A very nice product describing \n \t characters and \"quotes\"")
            .param("price", 150.0)
            .param("stock", 20)
            .param("images", List.of(Map.of("url", "https://img.com/1")))
            .param("attributes", List.of(Map.of("attributeId", 1, "attributeValueId", 100)))
            .build();

        OperationResponse response = client.execute(request);
        assertThat(response.isSuccess()).isTrue();
        
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("batchRequestId")).isEqualTo("TR-BATCH-100");
    }



    @Test
    void testAmazonCreateProduct() {
        stubFor(put(urlEqualTo("/amazon/listings/2021-08-01/items/A-SELLER/AMZ-SKU"))
            .withRequestBody(matchingJsonPath("$.productType", equalTo("SHIRT")))
            .withRequestBody(matchingJsonPath("$.attributes.item_name[0].value", equalTo("Amazon Title")))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{\"submissionId\": \"AMZ-SUB-1\"}")));

        OperationRequest request = OperationRequest.builder(MarketplaceType.AMAZON_TR, Operation.CREATE_PRODUCT)
            .param("sku", "AMZ-SKU")
            .param("title", "Amazon Title")
            .param("price", 99.99)
            .param("stock", 500)
            .build();

        OperationResponse response = client.execute(request);
        assertThat(response.isSuccess()).isTrue();
        Map<String, Object> data = (Map<String, Object>) response.getData();
        assertThat(data.get("submissionId")).isEqualTo("AMZ-SUB-1");
    }
}
