package com.bharat.stockline.ui;

import javafx.scene.Node;

/** A page in the main window */
public interface View {
    Node root();

    /** Reload from the database (called on open and when live changes arrive) */
    void refresh();
}
