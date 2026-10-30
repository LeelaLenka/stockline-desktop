package com.bharat.stockline.app;

import javafx.application.Application;

/**
 * Entry point for the runnable jar. A main class that doesn't extend Application lets
 * the shaded jar start JavaFX from the classpath without extra command-line flags.
 */
public final class Launcher {
    private Launcher() {}

    public static void main(String[] args) {
        Application.launch(StocklineApp.class, args);
    }
}
