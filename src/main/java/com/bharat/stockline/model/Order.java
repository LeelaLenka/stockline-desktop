package com.bharat.stockline.model;

import java.time.Instant;

public record Order(long id, Long storeId, String storeName, String customerName, String customerEmail,
                    long totalPaise, String placedBy, Instant createdAt, int itemCount) {}
