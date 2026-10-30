package com.bharat.stockline.ui;

import com.bharat.stockline.service.StockException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Optional;
import java.util.function.Function;
import javafx.animation.PauseTransition;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Small builders that keep the screens short and consistent */
public final class Ui {

    private Ui() {}

    public static Label label(String text, String... styles) {
        Label l = new Label(text);
        l.getStyleClass().addAll(styles);
        return l;
    }

    public static Label wrap(String text, String... styles) {
        Label l = label(text, styles);
        l.setWrapText(true);
        return l;
    }

    public static Button button(String text, String style, Runnable action) {
        Button b = new Button(text);
        b.getStyleClass().add(style);
        b.setMinWidth(Region.USE_PREF_SIZE); // never shrink to "..."
        b.setOnAction(e -> action.run());
        return b;
    }

    public static Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static VBox card(Node... children) {
        VBox box = new VBox(12, children);
        box.getStyleClass().add("card");
        return box;
    }

    /** Page heading: small eyebrow above a serif title, with actions on the right */
    public static HBox header(String eyebrow, String title, Node... actions) {
        VBox titles = new VBox(4, label(eyebrow.toUpperCase(), "eyebrow"), label(title, "page-title"));
        HBox row = new HBox(12);
        row.getChildren().add(titles);
        row.getChildren().add(spacer());
        row.getChildren().addAll(actions);
        row.setAlignment(Pos.BOTTOM_LEFT);
        return row;
    }

    /** Coloured stock status pill: OK / LOW / OUT / NONE */
    public static Label badge(String status, String text) {
        Label l = label(text, "badge", "badge-" + status.toLowerCase());
        l.setMinWidth(Region.USE_PREF_SIZE);
        return l;
    }

    public static String statusText(String status) {
        return switch (status) {
            case "OK" -> "In stock";
            case "LOW" -> "Low";
            case "OUT" -> "Out";
            default -> "Not stocked";
        };
    }

    public static <S> TableColumn<S, String> textColumn(String title, Function<S, String> value) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyStringWrapper(value.apply(cell.getValue())));
        return c;
    }

    /** Right-aligned column for numbers and money */
    public static <S> TableColumn<S, String> numberColumn(String title, Function<S, String> value) {
        TableColumn<S, String> c = textColumn(title, value);
        c.getStyleClass().add("num");
        return c;
    }

    /** A column that shows any node (badges, buttons) built from the row's item */
    public static <S> TableColumn<S, S> nodeColumn(String title, Function<S, Node> build) {
        TableColumn<S, S> c = new TableColumn<>(title);
        c.setCellValueFactory(cell -> new ReadOnlyObjectWrapper<>(cell.getValue()));
        c.setCellFactory(col -> new TableCell<S, S>() {
            @Override
            protected void updateItem(S item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : build.apply(item));
            }
        });
        c.setSortable(false);
        return c;
    }

    /** Fixed row height used by every table, so tables can size themselves to their rows */
    public static final double ROW_HEIGHT = 56;

    /** Shows rows in a table and sizes it to fit them (no inner scrollbar) */
    public static <S> void fill(TableView<S> table, java.util.List<S> rows) {
        table.setItems(javafx.collections.FXCollections.observableArrayList(rows));
        double height = Math.max(rows.size(), 2) * ROW_HEIGHT + 46; // rows + header
        table.setFixedCellSize(ROW_HEIGHT);
        table.setMinHeight(height);
        table.setPrefHeight(height);
        table.setMaxHeight(height);
    }

    public static boolean confirm(String title, String message) {
        Alert a = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.OK, ButtonType.CANCEL);
        a.setTitle(title);
        a.setHeaderText(title);
        Optional<ButtonType> r = a.showAndWait();
        return r.isPresent() && r.get() == ButtonType.OK;
    }

    /** A friendly message for anything that went wrong */
    public static String message(Throwable e) {
        if (e instanceof StockException) return e.getMessage();
        if (e instanceof SQLIntegrityConstraintViolationException) {
            return "That conflicts with existing data (for example, a duplicate SKU).";
        }
        if (e instanceof SQLException sql) {
            if ("45000".equals(sql.getSQLState())) return sql.getMessage(); // raised by our stored procedure
            if (sql.getSQLState() != null && sql.getSQLState().startsWith("08")) {
                return "Can’t reach MySQL. Check that the server is running.";
            }
            return "Database error: " + sql.getMessage();
        }
        return e.getMessage() == null ? e.toString() : e.getMessage();
    }

    public static void error(Throwable e) {
        Alert a = new Alert(Alert.AlertType.ERROR, message(e), ButtonType.OK);
        a.setTitle("Something went wrong");
        a.setHeaderText("That didn’t work");
        a.showAndWait();
    }

    /** Fades a label's highlight back to normal; used when live data changes */
    public static void pulse(Node node) {
        node.getStyleClass().add("changed");
        PauseTransition p = new PauseTransition(Duration.seconds(1.4));
        p.setOnFinished(e -> node.getStyleClass().remove("changed"));
        p.play();
    }
}
