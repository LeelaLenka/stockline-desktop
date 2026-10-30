package com.bharat.stockline.model;

/** One row of the v_inventory_status view: how much of a product one store has */
public record StockLine(long inventoryId, long storeId, String storeName, long productId, String productName,
                        String sku, String category, int quantity, int reorderLevel, String status) {}
