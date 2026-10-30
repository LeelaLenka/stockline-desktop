package com.bharat.stockline.model;

/** A product with its stock and rating summary, for the catalogue table */
public record ProductRow(Product product, int units, int storeCount, String status, double rating, int reviewCount) {}
