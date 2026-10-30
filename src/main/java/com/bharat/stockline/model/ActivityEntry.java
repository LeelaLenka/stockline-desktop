package com.bharat.stockline.model;

import java.time.Instant;

public record ActivityEntry(long id, String type, String message, String actor, Instant createdAt) {}
