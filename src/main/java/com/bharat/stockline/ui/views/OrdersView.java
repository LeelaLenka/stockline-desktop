package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Order;
import com.bharat.stockline.model.OrderItem;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.ui.View;
import com.bharat.stockline.util.Format;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class OrdersView implements View {

    private final AppContext ctx;
    private final VBox root = new VBox(18);
    private final TableView<Order> table = new TableView<>();
    private final TableView<OrderItem> items = new TableView<>();
    private final VBox detail = new VBox(12);

    public OrdersView(AppContext ctx, Shell shell) {
        this.ctx = ctx;
        root.getStyleClass().add("page");

        table.getColumns().add(Ui.nodeColumn("Placed", o -> new VBox(2, Ui.label(Format.ago(o.createdAt()), "strong"),
                Ui.label(Format.dateTime(o.createdAt()), "tiny"))));
        table.getColumns().add(Ui.textColumn("Customer", Order::customerName));
        table.getColumns().add(Ui.textColumn("Store", Order::storeName));
        table.getColumns().add(Ui.numberColumn("Items", o -> String.valueOf(o.itemCount())));
        table.getColumns().add(Ui.numberColumn("Total", o -> Format.money(o.totalPaise())));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No orders yet.", "muted"));
        table.getStyleClass().add("data-table");
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldValue, order) -> showOrder(order));

        items.getColumns().add(Ui.textColumn("Product", i -> i.productName() + "  (" + i.sku() + ")"));
        items.getColumns().add(Ui.numberColumn("Qty", i -> String.valueOf(i.quantity())));
        items.getColumns().add(Ui.numberColumn("Price", i -> Format.money(i.unitPricePaise())));
        items.getColumns().add(Ui.numberColumn("Line total", i -> Format.money(i.lineTotalPaise())));
        items.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        items.getStyleClass().add("data-table");
        detail.getChildren().add(Ui.label("Select an order to see its items.", "muted"));

        root.getChildren().addAll(Ui.header("Sales", "Orders", Ui.button("NEW ORDER", "btn-dark", shell::newOrder)),
                table, Ui.card(detail));
    }

    private void showOrder(Order o) {
        detail.getChildren().clear();
        if (o == null) {
            detail.getChildren().add(Ui.label("Select an order to see its items.", "muted"));
            return;
        }
        HBox head = new HBox(12, new VBox(2, Ui.label("Order for " + o.customerName(), "h2"),
                Ui.label(o.storeName() + " · " + Format.dateTime(o.createdAt()) + " · placed by " + o.placedBy(), "tiny")),
                Ui.spacer(), Ui.label(Format.money(o.totalPaise()), "total"));
        head.setAlignment(Pos.CENTER_LEFT);
        detail.getChildren().addAll(head, items);
        Async.run(() -> ctx.orders.items(o.id()), list -> Ui.fill(items, list), Ui::error);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Async.run(() -> ctx.orders.recent(100), list -> Ui.fill(table, list), Ui::error);
    }
}
