package com.bharat.stockline.ui;

import com.bharat.stockline.model.ActivityEntry;
import com.bharat.stockline.ui.views.DashboardView;
import com.bharat.stockline.ui.views.NewOrderDialog;
import com.bharat.stockline.ui.views.OrdersView;
import com.bharat.stockline.ui.views.ProductsView;
import com.bharat.stockline.ui.views.ReviewsView;
import com.bharat.stockline.ui.views.StoresView;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/** The main window: sidebar navigation, the current page, and live notifications */
public final class Shell {

    private final AppContext ctx;
    private final Runnable onLogout;
    private final StackPane root = new StackPane();
    private final BorderPane frame = new BorderPane();
    private final ScrollPane content = new ScrollPane();
    private final VBox toasts = new VBox(10);
    private final Label liveDot = new Label();
    private final Label liveText = Ui.label("Connecting…", "live-text");
    private final Map<String, View> views = new LinkedHashMap<>();
    private final Map<String, Button> navButtons = new LinkedHashMap<>();
    private View current;

    public Shell(AppContext ctx, Runnable onLogout) {
        this.ctx = ctx;
        this.onLogout = onLogout;

        views.put("Dashboard", new DashboardView(ctx, this));
        views.put("Products", new ProductsView(ctx, this));
        views.put("Stores", new StoresView(ctx, this));
        views.put("Orders", new OrdersView(ctx, this));
        views.put("Reviews", new ReviewsView(ctx, this));

        content.setFitToWidth(true);
        content.getStyleClass().add("content");
        frame.setLeft(sidebar());
        VBox main = new VBox();
        if (!ctx.user().isAdmin()) {
            Label banner = Ui.wrap("You’re using the shared demo account. Everything works; the owner can reset the sample data at any time.", "banner");
            banner.setMaxWidth(Double.MAX_VALUE);
            main.getChildren().add(banner);
        }
        main.getChildren().add(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        frame.setCenter(main);

        toasts.setAlignment(Pos.BOTTOM_RIGHT);
        toasts.setPickOnBounds(false);
        toasts.setPadding(new Insets(20));
        toasts.setMaxWidth(420);
        StackPane.setAlignment(toasts, Pos.BOTTOM_RIGHT);
        root.getChildren().addAll(frame, toasts);

        show("Dashboard");
        ctx.liveFeed.start(
                changes -> Platform.runLater(() -> onLiveChanges(changes)),
                ok -> Platform.runLater(() -> setLive(ok)));
    }

    public Parent root() {
        return root;
    }

    private VBox sidebar() {
        HBox brand = new HBox(10, logo(), Ui.label("Stockline", "brand-name"));
        brand.setAlignment(Pos.CENTER_LEFT);

        VBox nav = new VBox(2);
        for (String name : views.keySet()) {
            Button b = Ui.button(name, "nav-item", () -> show(name));
            b.setMaxWidth(Double.MAX_VALUE);
            navButtons.put(name, b);
            nav.getChildren().add(b);
        }

        Button newOrder = Ui.button("NEW ORDER", "btn-dark", this::newOrder);
        newOrder.setMaxWidth(Double.MAX_VALUE);

        liveDot.getStyleClass().add("live-dot");
        HBox live = new HBox(8, liveDot, liveText);
        live.setAlignment(Pos.CENTER_LEFT);

        Label avatar = Ui.label(ctx.user().email().substring(0, 1).toUpperCase(), "avatar");
        VBox who = new VBox(1, Ui.label(ctx.user().email(), "who-email"),
                Ui.label(ctx.user().isAdmin() ? "Owner" : "Demo account", "who-role"));
        HBox user = new HBox(10, avatar, who);
        user.setAlignment(Pos.CENTER_LEFT);

        VBox foot = new VBox(12, live, user);
        if (ctx.user().isAdmin()) foot.getChildren().add(Ui.button("Reset demo data", "link", this::resetDemo));
        foot.getChildren().add(Ui.button("Log out", "link", this::logout));

        Region grow = new Region();
        VBox.setVgrow(grow, Priority.ALWAYS);
        VBox side = new VBox(22, brand, nav, newOrder, grow, foot);
        side.getStyleClass().add("sidebar");
        side.setPrefWidth(236);
        return side;
    }

    /** Three rising bars: the Stockline mark */
    private static HBox logo() {
        HBox mark = new HBox(3);
        mark.getStyleClass().add("logo");
        mark.setAlignment(Pos.BOTTOM_CENTER);
        double[] heights = {8, 13, 18};
        String[] styles = {"logo-bar-1", "logo-bar-2", "logo-bar-3"};
        for (int i = 0; i < 3; i++) {
            Rectangle r = new Rectangle(4, heights[i]);
            r.setArcWidth(2);
            r.setArcHeight(2);
            r.getStyleClass().add(styles[i]);
            mark.getChildren().add(r);
        }
        return mark;
    }

    public void show(String name) {
        current = views.get(name);
        navButtons.forEach((n, b) -> b.getStyleClass().remove("active"));
        navButtons.get(name).getStyleClass().add("active");
        content.setContent(current.root());
        content.setVvalue(0);
        current.refresh();
    }

    public void newOrder() {
        new NewOrderDialog(ctx, root.getScene().getWindow(), null).open(id -> current.refresh());
    }

    /** New rows in the activity table, from this computer or any other */
    private void onLiveChanges(List<ActivityEntry> changes) {
        for (ActivityEntry a : changes) {
            if (!a.actor().equals(ctx.actor()) || changes.size() == 1) toast(a.message(), a.actor());
        }
        current.refresh();
    }

    public void toast(String message, String meta) {
        VBox t = new VBox(2, Ui.wrap(message, "toast-text"), Ui.label(meta, "toast-meta"));
        t.getStyleClass().add("toast");
        t.setMaxWidth(380);
        toasts.getChildren().add(t);
        while (toasts.getChildren().size() > 4) toasts.getChildren().remove(0);
        PauseTransition p = new PauseTransition(Duration.seconds(5));
        p.setOnFinished(e -> toasts.getChildren().remove(t));
        p.play();
    }

    private void setLive(boolean ok) {
        liveDot.getStyleClass().removeAll("on", "off");
        liveDot.getStyleClass().add(ok ? "on" : "off");
        liveText.setText(ok ? "Live updates on" : "Database unreachable");
    }

    private void resetDemo() {
        if (!Ui.confirm("Reset demo data?", "This replaces all stores, products, stock, orders and reviews with the sample data.")) return;
        Async.run(() -> {
            ctx.demoData.reset(ctx.actor());
            return true;
        }, ok -> current.refresh(), Ui::error);
    }

    private void logout() {
        ctx.liveFeed.stop();
        onLogout.run();
    }
}
