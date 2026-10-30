package com.bharat.stockline.model;

public record OrderItem(String productName, String sku, int quantity, long unitPricePaise) {
    public long lineTotalPaise() {
        return unitPricePaise * quantity;
    }
}
