package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.ActivityEntry;
import com.bharat.stockline.model.Dashboard;
import com.bharat.stockline.model.StockLine;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Responsive;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.ui.View;
import com.bharat.stockline.util.Format;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public class DashboardView implements View {

    private record Data(Dashboard d, List<ActivityEntry> activity) {}

    private final AppContext ctx;
    private final VBox root = new VBox(22);
    private final Map<String, Label> kpis = new HashMap<>();
    private final Label productsAndStores = Ui.label("", "kpi-sub");
    private final VBox restockList = new VBox();
    private final VBox feed = new VBox();
    private final HBox bars = new HBox(12);
    private final VBox topList = new VBox();

    public DashboardView(AppContext ctx, Shell shell) {
        this.ctx = ctx;
        root.getStyleClass().add("page");

        Button newOrder = Ui.button("NEW ORDER", "btn-dark", shell::newOrder);
        Node kpiRow = Responsive.grid(root, 820, 4, 2, 16,
                kpi("revenue", "REVENUE TODAY", true),
                kpi("orders", "ORDERS TODAY", false),
                kpi("units", "UNITS IN STOCK", false, productsAndStores),
                kpi("alerts", "NEEDS RESTOCKING", false));

        bars.setAlignment(Pos.BOTTOM_CENTER);
        bars.setPrefHeight(200);
        bars.setMinHeight(200);

        root.getChildren().addAll(
                Ui.header("Overview", "Today across your stores", newOrder),
                kpiRow,
                Responsive.grid(root, 1000, 2, 1, 22,
                        Ui.card(cardHead("Restock soon", null), restockList),
                        Ui.card(cardHead("Live activity", Ui.label("● LIVE", "pill-live")), feed),
                        Ui.card(cardHead("Sales, last 7 days", null), bars),
                        Ui.card(cardHead("Best sellers this week", null), topList)));
    }

    private VBox kpi(String key, String title, boolean accent, Node... extra) {
        Label value = Ui.label("–", "kpi-value");
        if (accent) value.getStyleClass().add("accent");
        kpis.put(key, value);
        VBox box = new VBox(6, Ui.label(title, "kpi-label"), value);
        box.getChildren().addAll(extra);
        box.getStyleClass().add("kpi");
        return box;
    }

    private static HBox cardHead(String title, Node right) {
        HBox h = new HBox(Ui.label(title, "h2"), Ui.spacer());
        if (right != null) h.getChildren().add(right);
        h.setAlignment(Pos.CENTER_LEFT);
        return h;
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Async.run(() -> new Data(ctx.reports.dashboard(), ctx.activity.latest(12)), this::render, err -> {});
    }

    private void render(Data data) {
        Dashboard d = data.d();
        set("revenue", Format.money(d.revenueTodayPaise()));
        set("orders", Format.number(d.ordersToday()));
        set("units", Format.number(d.unitsInStock()));
        set("alerts", d.lowStock() + " low · " + d.outOfStock() + " out");
        productsAndStores.setText(d.productCount() + " products · " + d.storeCount() + " stores");

        restockList.getChildren().clear();
        if (d.restockSoon().isEmpty()) restockList.getChildren().add(Ui.label("Everything is above its reorder level.", "muted"));
        for (StockLine s : d.restockSoon()) restockList.getChildren().add(restockRow(s));

        feed.getChildren().clear();
        if (data.activity().isEmpty()) feed.getChildren().add(Ui.wrap("Nothing yet. Place an order to see it appear here live.", "muted"));
        for (ActivityEntry a : data.activity()) {
            Region dot = new Region();
            dot.getStyleClass().addAll("feed-dot", "feed-" + a.type().toLowerCase());
            VBox text = new VBox(2, Ui.wrap(a.message(), "feed-text"), Ui.label(Format.ago(a.createdAt()) + " · " + a.actor(), "tiny"));
            HBox item = new HBox(10, dot, text);
            item.getStyleClass().add("feed-item");
            HBox.setHgrow(text, Priority.ALWAYS);
            feed.getChildren().add(item);
        }

        bars.getChildren().clear();
        long max = Math.max(1, d.week().stream().mapToLong(Dashboard.DaySales::totalPaise).max().orElse(1));
        for (int i = 0; i < d.week().size(); i++) {
            Dashboard.DaySales day = d.week().get(i);
            Region bar = new Region();
            bar.getStyleClass().add(i == d.week().size() - 1 ? "bar-today" : "bar");
            double h = Math.max(4, 150.0 * day.totalPaise() / max);
            bar.setMinHeight(h);
            bar.setPrefHeight(h);
            bar.setMaxHeight(h);
            bar.setPrefWidth(40);
            bar.setMaxWidth(44);
            String amount = day.totalPaise() == 0 ? "" : Format.money(day.totalPaise()).replace(".00", "");
            VBox col = new VBox(6, Ui.label(amount, "bar-value"), bar, Ui.label(day.label(), "tiny"));
            col.setAlignment(Pos.BOTTOM_CENTER);
            HBox.setHgrow(col, Priority.ALWAYS);
            col.setMaxWidth(Double.MAX_VALUE);
            bars.getChildren().add(col);
        }

        topList.getChildren().clear();
        if (d.topProducts().isEmpty()) topList.getChildren().add(Ui.label("No sales yet this week.", "muted"));
        int rank = 1;
        for (Dashboard.TopProduct t : d.topProducts()) {
            HBox line = new HBox(12, Ui.label(String.valueOf(rank++), "rank"), Ui.label(t.name(), "strong"), Ui.spacer(),
                    Ui.label(t.units() + " sold · " + Format.money(t.revenuePaise()), "muted"));
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("list-row");
            topList.getChildren().add(line);
        }
    }

    private VBox restockRow(StockLine s) {
        TextField units = new TextField("20");
        units.getStyleClass().add("input-sm");
        units.setMinWidth(64);
        units.setPrefWidth(64);
        Button add = Ui.button("RESTOCK", "btn-dark-sm", () -> {});
        add.setOnAction(e -> StoresView.restock(ctx, s, units.getText(), add, this::refresh));
        VBox info = new VBox(2, Ui.wrap(s.productName(), "strong"),
                Ui.wrap(s.storeName() + " · reorder at " + s.reorderLevel(), "tiny"));
        HBox.setHgrow(info, Priority.ALWAYS);
        HBox top = new HBox(12, info, Ui.badge(s.status(), s.quantity() + " left"));
        top.setAlignment(Pos.CENTER_LEFT);
        HBox controls = new HBox(8, Ui.label("Add units", "tiny"), units, add);
        controls.setAlignment(Pos.CENTER_LEFT);
        VBox row = new VBox(8, top, controls);
        row.getStyleClass().add("list-row");
        return row;
    }

    private void set(String key, String value) {
        Label l = kpis.get(key);
        if (!value.equals(l.getText()) && !"–".equals(l.getText())) Ui.pulse(l.getParent());
        l.setText(value);
    }
}
