package io.marketplace.sdk.core.operation;

/**
 * SDK'nın desteklediği tüm operasyonlar.
 *
 * Yeni operasyon eklendiğinde:
 *  1. Buraya ekle
 *  2. İlgili adapter'ın supportedOperations() setine ekle
 *  3. İlgili pazaryerinin YAML'ına operation bloğu ekle
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
    GET_INVOICES,

    /**
     * Trendyol batch işlem sonucu sorgulama.
     * createProduct, updateProduct, updateStock gibi operasyonlar
     * batchRequestId döner — bu ID ile işlem sonucu sorgulanır.
     * YAML key: getBatchRequestResult
     * Path: /integration/product/sellers/{supplierId}/products/batch-requests/{batchRequestId}
     */
    GET_BATCH_REQUEST_RESULT
}
