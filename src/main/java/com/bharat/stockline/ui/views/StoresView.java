package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.StockLine;
import com.bharat.stockline.model.Store;
import com.bharat.stockline.model.StoreSummary;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.ui.View;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** Store cards; click one to manage its stock */
public final class StoresView implements View {

    private record Detail(List<StockLine> lines, List<Product> notStocked) {}

    private final AppContext ctx;
    private final Shell shell;
    private final VBox root = new VBox(18);
    private final FlowPane cards = new FlowPane(16, 16);
    private final VBox detail = new VBox(14);
    private final TableView<StockLine> table = new TableView<>();
    private final Label detailTitle = Ui.label("", "page-title");
    private final Label detailSub = Ui.label("", "muted");
    private final ComboBox<Product> addProduct = new ComboBox<>();
    private Store selected;
    private boolean alertsOnly;

    public StoresView(AppContext ctx, Shell shell) {
        this.ctx = ctx;
        this.shell = shell;
        root.getStyleClass().add("page");

        table.getColumns().add(Ui.nodeColumn("Product", s -> new VBox(2, Ui.label(s.productName(), "strong"), Ui.label(s.sku(), "mono-tiny"))));
        table.getColumns().add(Ui.numberColumn("In stock", s -> s.quantity() + "  / reorder at " + s.reorderLevel()));
        table.getColumns().add(Ui.nodeColumn("Status", s -> Ui.badge(s.status(), Ui.statusText(s.status()))));
        table.getColumns().add(Ui.nodeColumn("Restock", this::restockCell));
        table.getColumns().add(Ui.nodeColumn("", s -> Ui.button("Set count", "link", () -> adjust(s))));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(Ui.label("No stock here yet.", "muted"));
        table.getStyleClass().add("data-table");

        Button all = Ui.button("All stock", "tab", () -> {});
        Button alerts = Ui.button("Needs restocking", "tab", () -> {});
        all.getStyleClass().add("active");
        all.setOnAction(e -> {
            alertsOnly = false;
            all.getStyleClass().add("active");
            alerts.getStyleClass().remove("active");
            loadDetail();
        });
        alerts.setOnAction(e -> {
            alertsOnly = true;
            alerts.getStyleClass().add("active");
            all.getStyleClass().remove("active");
            loadDetail();
        });

        TextField qty = new TextField("20");
        qty.setPrefColumnCount(4);
        TextField reorder = new TextField("10");
        reorder.setPrefColumnCount(4);
        addProduct.setPromptText("Choose a product");
        Button addBtn = Ui.button("ADD TO STOCK", "btn-dark-sm", () -> addToStore(qty.getText(), reorder.getText()));
        FlowPane addRow = new FlowPane(10, 10, Ui.label("Stock a new product here:", "field-label"), addProduct,
                Ui.label("Units", "tiny"), qty, Ui.label("Reorder at", "tiny"), reorder, addBtn);
        addRow.setAlignment(Pos.CENTER_LEFT);

        HBox detailButtons = new HBox(10, Ui.button("Edit store", "btn-ghost", this::editStore),
                Ui.button("NEW ORDER HERE", "btn-dark", () -> new NewOrderDialog(ctx, root.getScene().getWindow(), selected)
                        .open(id -> refresh())));
        FlowPane detailHead = new FlowPane(16, 12, new VBox(4, detailTitle, detailSub), detailButtons);
        detailHead.setAlignment(Pos.BOTTOM_LEFT);
        detail.getChildren().addAll(detailHead, new HBox(6, all, alerts), table, Ui.card(addRow));
        detail.setVisible(false);
        detail.setManaged(false);

        root.getChildren().addAll(
                Ui.header("Locations", "Stores", Ui.button("ADD STORE", "btn-dark", () -> openStoreDialog(null))),
                cards, detail);
    }

    private HBox restockCell(StockLine s) {
        TextField units = new TextField("10");
        units.setPrefColumnCount(3);
        units.getStyleClass().add("input-sm");
        Button add = Ui.button("ADD", "btn-dark-sm", () -> {});
        add.setOnAction(e -> restock(ctx, s, units.getText(), add, this::refresh));
        HBox box = new HBox(6, units, add);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    /** Shared by the dashboard: validates the number and calls the sp_restock stored procedure */
    static void restock(AppContext ctx, StockLine s, String unitsText, Button button, Runnable after) {
        int units;
        try {
            units = Integer.parseInt(unitsText.trim());
        } catch (NumberFormatException e) {
            Ui.error(new IllegalArgumentException("Enter how many units to add, e.g. 20."));
            return;
        }
        button.setDisable(true);
        Async.run(() -> {
            ctx.inventory.restock(s.inventoryId(), units, ctx.actor());
            return true;
        }, ok -> after.run(), err -> {
            button.setDisable(false);
            Ui.error(err);
        });
    }

    private void adjust(StockLine s) {
        new AdjustDialog(ctx, root.getScene().getWindow(), s).open(this::refresh);
    }

    private void addToStore(String qtyText, String reorderText) {
        Product p = addProduct.getValue();
        if (p == null || selected == null) return;
        int qty;
        int reorder;
        try {
            qty = Integer.parseInt(qtyText.trim());
            reorder = Integer.parseInt(reorderText.trim());
            if (qty < 0 || reorder < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            Ui.error(new IllegalArgumentException("Units and reorder level must be whole numbers, 0 or more."));
            return;
        }
        Store store = selected;
        Async.run(() -> {
            ctx.inventory.addToStore(store.id(), p, store.name(), qty, reorder, ctx.actor());
            return true;
        }, ok -> refresh(), Ui::error);
    }

    private void editStore() {
        if (selected != null) openStoreDialog(selected);
    }

    private void openStoreDialog(Store existing) {
        new StoreDialog(ctx, root.getScene().getWindow(), existing).open(msg -> {
            if (existing != null && msg.endsWith("deleted.")) selected = null;
            shell.toast(msg, ctx.actor());
            refresh();
        });
    }

    private void select(Store s) {
        selected = s;
        detail.setVisible(true);
        detail.setManaged(true);
        detailTitle.setText(s.name());
        detailSub.setText(s.city() + (s.address().isBlank() ? "" : " · " + s.address()));
        loadDetail();
    }

    private void loadDetail() {
        if (selected == null) return;
        long id = selected.id();
        Async.run(() -> new Detail(ctx.inventory.byStore(id, alertsOnly), ctx.inventory.notStockedAt(id)), d -> {
            Ui.fill(table, d.lines());
            addProduct.setItems(FXCollections.observableArrayList(d.notStocked()));
        }, Ui::error);
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Async.run(ctx.stores::summaries, list -> {
            cards.getChildren().clear();
            for (StoreSummary s : list) cards.getChildren().add(card(s));
            if (selected != null && list.stream().noneMatch(s -> s.store().id() == selected.id())) {
                selected = null;
                detail.setVisible(false);
                detail.setManaged(false);
            }
            if (selected == null && !list.isEmpty()) select(list.get(0).store());
            else loadDetail();
        }, Ui::error);
    }

    private VBox card(StoreSummary s) {
        HBox stats = new HBox(26,
                stat("PRODUCTS", String.valueOf(s.productLines()), false),
                stat("UNITS", String.valueOf(s.units()), false),
                stat("ALERTS", String.valueOf(s.alerts()), s.alerts() > 0));
        VBox c = Ui.card(Ui.label(s.store().city().toUpperCase(), "eyebrow"), Ui.label(s.store().name(), "card-title"),
                Ui.label(s.store().address(), "tiny"), stats);
        c.getStyleClass().add("store-card");
        if (selected != null && selected.id() == s.store().id()) c.getStyleClass().add("selected");
        c.setPrefWidth(300);
        c.setOnMouseClicked(e -> {
            select(s.store());
            cards.getChildren().forEach(n -> n.getStyleClass().remove("selected"));
            c.getStyleClass().add("selected");
        });
        return c;
    }

    private static VBox stat(String label, String value, boolean warn) {
        Label v = Ui.label(value, "stat-value");
        if (warn) v.getStyleClass().add("warn");
        return new VBox(2, Ui.label(label, "kpi-label"), v);
    }
}
