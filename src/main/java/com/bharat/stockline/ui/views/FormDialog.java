package com.bharat.stockline.ui.views;

import com.bharat.stockline.ui.Ui;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** A styled modal window with a title, form fields and action buttons */
abstract class FormDialog {

    protected final Stage stage = new Stage();
    protected final VBox body = new VBox(14);
    protected final HBox actions = new HBox(10);
    private final Window owner;

    protected FormDialog(Window owner, String title) {
        this.owner = owner;
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setTitle(title);
        body.getChildren().add(Ui.label(title, "dialog-title"));
    }

    protected static VBox field(String label, Control input) {
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(6, Ui.label(label, "field-label"), input);
    }

    protected void addField(String label, Control input) {
        body.getChildren().add(field(label, input));
    }

    protected void addRow(Node... nodes) {
        HBox row = new HBox(14, nodes);
        for (Node n : nodes) HBox.setHgrow(n, Priority.ALWAYS);
        body.getChildren().add(row);
    }

    protected void show(Button primary) {
        actions.getChildren().addAll(primary, Ui.button("CANCEL", "btn-ghost", this::close));
        actions.setAlignment(Pos.CENTER_LEFT);
        VBox root = new VBox(18, body, actions);
        root.getStyleClass().add("dialog");
        Scene scene = new Scene(root, 520, -1);
        scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        stage.setScene(scene);
        stage.showAndWait();
    }

    protected void close() {
        stage.close();
    }
}
