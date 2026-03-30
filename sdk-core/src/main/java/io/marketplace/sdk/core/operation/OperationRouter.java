package io.marketplace.sdk.core.operation;

import io.marketplace.sdk.core.exception.OperationNotSupportedException;
import io.marketplace.sdk.core.model.MarketplaceType;
import io.marketplace.sdk.core.spi.MarketplaceAdapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Gelen OperationRequest'i doğru adapter instance'ına yönlendirir.
 *
 * Multiple Instance Desteği:
 *   Aynı MarketplaceType için birden fazla adapter kaydedilebilir.
 *   Her instance bir "instanceKey" ile ayırt edilir.
 *   instanceKey yoksa "default" kullanılır.
 *
 *   Kullanım:
 *     // Kayıt
 *     router.register("seller-A", trendyolAdapterA);
 *     router.register("seller-B", trendyolAdapterB);
 *
 *     // İstek (instanceKey param ile)
 *     OperationRequest.builder(TRENDYOL, GET_ORDERS)
 *         .param("instanceKey", "seller-A")
 *         .build();
 *
 *     // instanceKey yoksa "default" instance seçilir
 *     OperationRequest.builder(TRENDYOL, GET_ORDERS).build();
 */
public class OperationRouter {

    public static final String DEFAULT_INSTANCE = "default";

    // key: "TRENDYOL::seller-A", value: adapter instance
    private final Map<String, MarketplaceAdapter> adapters = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Kayıt
    // -------------------------------------------------------------------------

    /** Varsayılan instance olarak kaydeder (geriye dönük uyumlu). */
    public void register(MarketplaceAdapter adapter) {
        register(DEFAULT_INSTANCE, adapter);
    }

    /** Belirli bir instanceKey ile kaydeder. */
    public void register(String instanceKey, MarketplaceAdapter adapter) {
        String key = routerKey(adapter.getType(), instanceKey);
        adapters.put(key, adapter);
    }

    public void deregister(MarketplaceType type) {
        deregister(type, DEFAULT_INSTANCE);
    }

    public void deregister(MarketplaceType type, String instanceKey) {
        adapters.remove(routerKey(type, instanceKey));
    }

    // -------------------------------------------------------------------------
    // Yönlendirme
    // -------------------------------------------------------------------------

    public OperationResponse route(OperationRequest request) {
        String instanceKey = resolveInstanceKey(request);
        String key = routerKey(request.getMarketplace(), instanceKey);

        MarketplaceAdapter adapter = adapters.get(key);

        if (adapter == null && !DEFAULT_INSTANCE.equals(instanceKey)) {
            // İstenen instance yok — default'a düş
            adapter = adapters.get(routerKey(request.getMarketplace(), DEFAULT_INSTANCE));
        }

        if (adapter == null) {
            throw new OperationNotSupportedException(
                "No adapter registered for: " + request.getMarketplace()
                + " (instanceKey='" + instanceKey + "'). "
                + "Call SDK.builder().configDir(...).build() or register an adapter manually."
            );
        }

        if (!adapter.supportedOperations().contains(request.getOperation())) {
            throw new OperationNotSupportedException(
                request.getOperation() + " not supported by " + request.getMarketplace()
            );
        }

        return adapter.execute(request);
    }

    // -------------------------------------------------------------------------
    // Sorgulama
    // -------------------------------------------------------------------------

    public boolean hasAdapter(MarketplaceType type) {
        return adapters.containsKey(routerKey(type, DEFAULT_INSTANCE));
    }

    public boolean hasAdapter(MarketplaceType type, String instanceKey) {
        return adapters.containsKey(routerKey(type, instanceKey));
    }

    public MarketplaceAdapter getAdapter(MarketplaceType type) {
        return adapters.get(routerKey(type, DEFAULT_INSTANCE));
    }

    public MarketplaceAdapter getAdapter(MarketplaceType type, String instanceKey) {
        return adapters.get(routerKey(type, instanceKey));
    }

    /** Belirli bir pazaryerinin tüm kayıtlı instance'larını döner. */
    public List<MarketplaceAdapter> getAdapters(MarketplaceType type) {
        String prefix = type.name() + "::";
        return adapters.entrySet().stream()
            .filter(e -> e.getKey().startsWith(prefix))
            .map(Map.Entry::getValue)
            .toList();
    }

    // -------------------------------------------------------------------------
    // Yardımcılar
    // -------------------------------------------------------------------------

    private String routerKey(MarketplaceType type, String instanceKey) {
        return type.name() + "::" + (instanceKey != null ? instanceKey : DEFAULT_INSTANCE);
    }

    private String resolveInstanceKey(OperationRequest request) {
        Object key = request.getParams() != null ? request.getParams().get("instanceKey") : null;
        return key != null ? key.toString() : DEFAULT_INSTANCE;
    }
}
