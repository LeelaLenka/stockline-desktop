package com.bharat.stockline.service;

/** A business rule failed (e.g. not enough stock); the message is shown to the user as-is */
public class StockException extends Exception {
    private static final long serialVersionUID = 1L;

    public StockException(String message) {
        super(message);
    }
}
