package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.OrderRequest;
import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.StockLine;
import com.bharat.stockline.model.Store;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.util.Format;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/** Build a multi-item order with live stock hints and a running total */
public final class NewOrderDialog extends FormDialog {

    private record Options(List<Store> stores, List<Product> products) {}

    private final AppContext ctx;
    private final Store preselected;
    private final ComboBox<Store> store = new ComboBox<>();
    private final TextField customer = new TextField();
    private final TextField email = new TextField();
    private final VBox lines = new VBox(10);
    private final Label total = Ui.label(Format.money(0), "total");
    private final Label error = Ui.wrap("", "error-text");
    private final List<Line> lineList = new ArrayList<>();
    private List<Product> products = List.of();
    private Map<Long, Integer> stockAtStore = new HashMap<>();

    /** One product row in the order */
    private final class Line {
        final ComboBox<Product> product = new ComboBox<>();
        final TextField qty = new TextField("1");
        final Label hint = Ui.label("", "tiny");
        HBox row; // not final: the remove button below refers to it

        Line() {
            product.setItems(FXCollections.observableArrayList(products));
            product.setPromptText("Choose a product");
            product.setMaxWidth(Double.MAX_VALUE);
            qty.setPrefColumnCount(4);
            product.setOnAction(e -> update());
            qty.textProperty().addListener((o, a, b) -> update());
            Button remove = Ui.button("×", "icon-btn", () -> {
                if (lineList.size() > 1) {
                    lineList.remove(this);
                    lines.getChildren().remove(this.row);
                    update();
                }
            });
            VBox left = new VBox(4, product, hint);
            HBox.setHgrow(left, Priority.ALWAYS);
            row = new HBox(10, left, qty, remove);
            row.setAlignment(Pos.TOP_LEFT);
            row.getStyleClass().add("order-line");
        }

        int quantity() {
            try {
                return Integer.parseInt(qty.getText().trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
    }

    public NewOrderDialog(AppContext ctx, Window owner, Store preselected) {
        super(owner, "New order");
        this.ctx = ctx;
        this.preselected = preselected;
        store.setPromptText("Choose a store");
        store.setOnAction(e -> loadStock());
        email.setPromptText("optional");
        addRow(field("Store", store), field("Customer name", customer));
        addField("Customer email", email);
        body.getChildren().add(Ui.label("ITEMS", "kpi-label"));
        body.getChildren().add(lines);
        body.getChildren().add(Ui.button("+ Add another item", "link", this::addLine));
        HBox totalRow = new HBox(Ui.label("Total", "h2"), Ui.spacer(), total);
        totalRow.setAlignment(Pos.CENTER_LEFT);
        totalRow.getStyleClass().add("total-row");
        body.getChildren().addAll(totalRow,
                Ui.wrap("Stock is locked and taken in one database transaction when you place the order.", "tiny"), error);
    }

    public void open(Consumer<Long> onPlaced) {
        Button place = Ui.button("PLACE ORDER", "btn-dark", () -> {});
        place.setDefaultButton(true);
        place.setOnAction(e -> {
            Store s = store.getValue();
            if (s == null) { error.setText("Choose a store."); return; }
            if (customer.getText().isBlank()) { error.setText("Enter the customer’s name."); return; }
            List<OrderRequest.Line> req = new ArrayList<>();
            for (Line l : lineList) {
                if (l.product.getValue() == null) continue;
                if (l.quantity() < 1) { error.setText("Each quantity must be at least 1."); return; }
                req.add(new OrderRequest.Line(l.product.getValue().id(), l.quantity()));
            }
            if (req.isEmpty()) { error.setText("Add at least one product."); return; }
            place.setDisable(true);
            OrderRequest order = new OrderRequest(s.id(), customer.getText(), email.getText(), req);
            Async.run(() -> ctx.orderService.place(order, ctx.actor()), id -> {
                close();
                onPlaced.accept(id);
            }, err -> {
                place.setDisable(false);
                error.setText(Ui.message(err));
                loadStock(); // show the latest numbers
            });
        });

        Async.run(() -> new Options(ctx.stores.findAll(), ctx.products.findAll()), opts -> {
            products = opts.products();
            store.setItems(FXCollections.observableArrayList(opts.stores()));
            if (preselected != null) {
                opts.stores().stream().filter(s -> s.id() == preselected.id()).findFirst().ifPresent(store::setValue);
                loadStock();
            }
            addLine();
        }, Ui::error);
        show(place);
    }

    private void addLine() {
        Line l = new Line();
        lineList.add(l);
        lines.getChildren().add(l.row);
        update();
    }

    private void loadStock() {
        Store s = store.getValue();
        if (s == null) return;
        Async.run(() -> ctx.inventory.byStore(s.id(), false), list -> {
            Map<Long, Integer> m = new HashMap<>();
            for (StockLine sl : list) m.put(sl.productId(), sl.quantity());
            stockAtStore = m;
            update();
        }, Ui::error);
    }

    private void update() {
        long sum = 0;
        for (Line l : lineList) {
            Product p = l.product.getValue();
            l.hint.getStyleClass().remove("warn");
            if (p == null) {
                l.hint.setText("");
                continue;
            }
            sum += p.pricePaise() * Math.max(0, l.quantity());
            if (store.getValue() == null) {
                l.hint.setText(Format.money(p.pricePaise()) + " each · choose a store to see stock");
                continue;
            }
            Integer left = stockAtStore.get(p.id());
            if (left == null) {
                l.hint.setText("Not stocked at this store");
                l.hint.getStyleClass().add("warn");
            } else {
                l.hint.setText(Format.money(p.pricePaise()) + " each · " + left + " in stock here");
                if (l.quantity() > left) l.hint.getStyleClass().add("warn");
            }
        }
        total.setText(Format.money(sum));
    }
}
