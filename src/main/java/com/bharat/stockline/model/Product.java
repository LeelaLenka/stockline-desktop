package com.bharat.stockline.model;

/** Prices are stored in paise (1 rupee = 100 paise) so money never has rounding errors */
public record Product(long id, String name, String sku, String category, long pricePaise, String description) {
    @Override
    public String toString() {
        return name;
    }
}
