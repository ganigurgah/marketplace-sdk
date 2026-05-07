package io.marketplace.sdk.adapters;

import io.marketplace.sdk.core.MarketplaceSDK;
import io.marketplace.sdk.core.operation.OperationRequest;
import io.marketplace.sdk.core.operation.OperationResponse;
import io.marketplace.sdk.core.MarketplaceClient;
import io.marketplace.sdk.core.model.MarketplaceType;

/**
 * Trendyol bağlantı testi.
 *
 * Çalıştırmadan önce trendyol.yaml dosyasındaki credential bilgilerini güncelleyin:
 *   credentials:
 *     sellerId: "GERÇEK_SELLER_ID"
 *     apiKey:    "GERÇEK_API_KEY"
 *     apiSecret: "GERÇEK_API_SECRET"
 *
 * Veya environment variable ile:
 *   export TRENDYOL_SELLER_ID=xxx
 *   export TRENDYOL_API_KEY=xxx
 *   export TRENDYOL_API_SECRET=xxx
 */
public class TrendyolConnectionTest {

    public static void main(String[] args) {
        try {
            MarketplaceSDK sdk = MarketplaceSDK.builder()
                .configDir("marketplace-configs")
                .build();

            MarketplaceClient client = sdk.client();

            System.out.println("=== Trendyol Bağlantı Testi ===");
            System.out.println();

            // Önce health check dene
            System.out.print("Health check yapılıyor...");
            try {
                boolean healthy = client.healthCheck(MarketplaceType.TRENDYOL);
                System.out.println(healthy ? " BAŞARILI" : " BAŞARISIZ");
            } catch (Exception e) {
                System.out.println(" HATA: " + e.getMessage());
            }

            // Kategorileri çek (GET_CATEGORIES - basit bir sorgu)
            System.out.print("Kategoriler getiriliyor...");
            try {
                OperationResponse response = client.execute(
                    OperationRequest.builder(MarketplaceType.TRENDYOL, io.marketplace.sdk.core.operation.Operation.GET_CATEGORIES)
                        .build()
                );

                if (response.isSuccess()) {
                    System.out.println(" BAŞARILI");
                    Object data = response.getData();
                    if (data != null) {
                        System.out.println("Yanıt: " + data);
                    }
                } else {
                    System.out.println(" BAŞARISIZ");
                    System.out.println("Hata: " + response.getErrorMessage());
                }
            } catch (Exception e) {
                System.out.println(" HATA");
                System.err.println("Detay: " + e.getMessage());
                e.printStackTrace();
            }

            sdk.shutdown();

        } catch (Exception e) {
            System.err.println("SDK başlatılamadı: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
