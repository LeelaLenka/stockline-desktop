package com.bharat.stockline.app;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.DbConfig;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.LoginView;
import com.bharat.stockline.ui.SetupView;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/** Startup: connect to MySQL (or ask how), install the schema, then show the login screen */
public class StocklineApp extends Application {

    private Scene scene;

    @Override
    public void start(Stage stage) {
        StackPane loading = new StackPane(Ui.label("Connecting to MySQL…", "muted"));
        loading.getStyleClass().add("screen");
        scene = new Scene(loading, 1280, 800);
        scene.getStylesheets().add(StocklineApp.class.getResource("/com/bharat/stockline/ui/stockline.css").toExternalForm());
        stage.setTitle("Stockline: real-time retail inventory");
        stage.setMinWidth(760);
        stage.setMinHeight(600);
        stage.setScene(scene);
        stage.show();

        DbConfig saved = DbConfig.load();
        if (saved == null) showSetup(null, null);
        else connect(saved);
    }

    private void connect(DbConfig cfg) {
        Async.run(() -> {
            Database db = new Database(cfg);
            db.installSchema(); // safe to run every time: CREATE TABLE IF NOT EXISTS, CREATE OR REPLACE VIEW
            AppContext ctx = new AppContext(db);
            if (ctx.demoData.isEmpty()) ctx.demoData.seed();
            ctx.users.upsert(LoginView.DEMO_EMAIL, "Demo Recruiter", LoginView.DEMO_PASSWORD, "DEMO");
            return ctx;
        }, this::showLogin, err -> showSetup(cfg, "Couldn’t connect: " + Ui.message(err)));
    }

    private void showSetup(DbConfig cfg, String problem) {
        scene.setRoot(new SetupView(cfg, problem, this::connect).root());
    }

    private void showLogin(AppContext ctx) {
        Async.run(ctx.users::adminExists, hasOwner -> scene.setRoot(new LoginView(ctx, !hasOwner, user -> {
            ctx.setUser(user);
            scene.setRoot(new Shell(ctx, () -> showLogin(new AppContext(ctx.db))).root());
        }, () -> showSetup(DbConfig.load(), null)).root()), err -> showSetup(DbConfig.load(), Ui.message(err)));
    }
}
