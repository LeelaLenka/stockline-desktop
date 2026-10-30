package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.ProductRow;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.ui.View;
import com.bharat.stockline.util.Format;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class ProductsView implements View {

    static final List<String> CATEGORIES = List.of("Living", "Kitchen", "Bath", "Decor", "Other");

    private final AppContext ctx;
    private final Shell shell;
    private final VBox root = new VBox(18);
    private final TextField search = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final TableView<ProductRow> table = new TableView<>();

    public ProductsView(AppContext ctx, Shell shell) {
        this.ctx = ctx;
        this.shell = shell;
        root.getStyleClass().add("page");

        search.setPromptText("Search by name or SKU");
        search.textProperty().addListener((obs, oldValue, newValue) -> refresh());
        HBox.setHgrow(search, Priority.ALWAYS);
        category.getItems().add("All categories");
        category.getItems().addAll(CATEGORIES);
        category.getSelectionModel().select(0);
        category.setOnAction(e -> refresh());
        HBox toolbar = new HBox(10, search, category);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        table.getColumns().add(Ui.nodeColumn("Product", r -> new VBox(2, Ui.label(r.product().name(), "strong"), Ui.label(r.product().sku(), "mono-tiny"))));
        table.getColumns().add(Ui.textColumn("Category", r -> r.product().category()));
        table.getColumns().add(Ui.numberColumn("Price", r -> Format.money(r.product().pricePaise())));
        table.getColumns().add(Ui.numberColumn("In stock", r -> r.units() + " in " + r.storeCount() + (r.storeCount() == 1 ? " store" : " stores")));
        table.getColumns().add(Ui.nodeColumn("Status", r -> Ui.badge(r.status(), switch (r.status()) {
            case "OK" -> "In stock";
            case "LOW" -> "Low somewhere";
            case "OUT" -> "Out somewhere";
            default -> "Not stocked";
        })));
        table.getColumns().add(Ui.textColumn("Rating", r -> r.reviewCount() == 0 ? "No reviews" : r.rating() + " ★  (" + r.reviewCount() + ")"));
        table.getColumns().add(Ui.nodeColumn("", r -> Ui.button("Edit", "link", () -> edit(r.product()))));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No products match.", "muted"));
        table.getStyleClass().add("data-table");

        Button add = Ui.button("ADD PRODUCT", "btn-dark", () -> edit(null));
        root.getChildren().addAll(Ui.header("Catalogue", "Products", add), toolbar, table);
    }

    private void edit(Product existing) {
        new ProductDialog(ctx, root.getScene().getWindow(), existing).open(saved -> {
            refresh();
            shell.toast(saved, ctx.actor());
        });
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        String q = search.getText() == null ? "" : search.getText();
        String cat = category.getSelectionModel().getSelectedIndex() <= 0 ? "" : category.getValue();
        Async.run(() -> ctx.products.rows(q, cat), rows -> Ui.fill(table, rows), Ui::error);
    }
}
