package com.bharat.stockline.ui;

import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** A grid that uses fewer columns when the window gets narrow, like a CSS media query */
public final class Responsive {

    private Responsive() {}

    /**
     * @param watch      the region whose width decides the layout (usually the page)
     * @param breakpoint below this width, use narrowCols
     */
    public static GridPane grid(Region watch, double breakpoint, int wideCols, int narrowCols, double gap, Node... items) {
        GridPane grid = new GridPane();
        grid.setHgap(gap);
        grid.setVgap(gap);
        for (Node n : items) {
            if (n instanceof Region r) r.setMaxWidth(Double.MAX_VALUE);
        }
        int[] current = {-1};
        java.util.function.DoubleConsumer apply = width -> {
            int cols = width > 0 && width < breakpoint ? narrowCols : wideCols;
            if (cols == current[0]) return;
            current[0] = cols;
            grid.getChildren().clear();
            grid.getColumnConstraints().clear();
            for (int c = 0; c < cols; c++) {
                ColumnConstraints cc = new ColumnConstraints();
                cc.setPercentWidth(100.0 / cols);
                cc.setHgrow(Priority.ALWAYS);
                grid.getColumnConstraints().add(cc);
            }
            for (int i = 0; i < items.length; i++) grid.add(items[i], i % cols, i / cols);
        };
        watch.widthProperty().addListener((obs, oldWidth, newWidth) -> apply.accept(newWidth.doubleValue()));
        apply.accept(0);
        return grid;
    }
}
