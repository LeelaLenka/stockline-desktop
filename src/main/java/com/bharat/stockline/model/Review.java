package com.bharat.stockline.model;

import java.time.Instant;

public record Review(long id, long productId, String productName, String customerName, int rating, String comment,
                     Instant createdAt) {}
