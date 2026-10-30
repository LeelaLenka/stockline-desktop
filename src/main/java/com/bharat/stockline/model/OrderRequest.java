package com.bharat.stockline.model;

import java.util.List;

/** What the New order dialog submits */
public record OrderRequest(long storeId, String customerName, String customerEmail, List<Line> lines) {
    public record Line(long productId, int quantity) {}
}
