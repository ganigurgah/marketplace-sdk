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
