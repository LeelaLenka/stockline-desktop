package com.bharat.stockline.ui;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.DbConfig;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** First run: ask for MySQL details, create the database and tables, then continue */
public class SetupView {

    private final StackPane root = new StackPane();

    public SetupView(DbConfig current, String problem, Consumer<DbConfig> onConnected) {
        DbConfig c = current == null ? DbConfig.defaults() : current;
        TextField host = new TextField(c.host());
        TextField port = new TextField(String.valueOf(c.port()));
        TextField database = new TextField(c.database());
        TextField user = new TextField(c.user());
        PasswordField password = new PasswordField();
        password.setText(c.password());

        GridPane form = new GridPane();
        form.setHgap(14);
        form.setVgap(12);
        form.add(field("MySQL host", host), 0, 0);
        form.add(field("Port", port), 1, 0);
        form.add(field("Database name", database), 0, 1, 2, 1);
        form.add(field("Username", user), 0, 2);
        form.add(field("Password", password), 1, 2);

        Label status = Ui.wrap(problem == null ? "" : problem, problem == null ? "muted" : "error-text");
        Button connect = Ui.button("CONNECT AND SET UP", "btn-dark", () -> {});
        connect.setDefaultButton(true);
        connect.setOnAction(e -> {
            int p;
            try {
                p = Integer.parseInt(port.getText().trim());
            } catch (NumberFormatException ex) {
                status.setText("Port must be a number, usually 3306.");
                return;
            }
            DbConfig cfg = new DbConfig(host.getText().trim(), p, database.getText().trim(), user.getText().trim(), password.getText());
            connect.setDisable(true);
            status.getStyleClass().setAll("label", "muted");
            status.setText("Connecting and creating tables…");
            Async.run(() -> {
                Database.createDatabaseIfMissing(cfg);
                new Database(cfg).installSchema();
                cfg.save();
                return cfg;
            }, onConnected, err -> {
                connect.setDisable(false);
                status.getStyleClass().setAll("label", "error-text");
                status.setText(Ui.message(err));
            });
        });

        VBox card = Ui.card(
                Ui.label("Connect to MySQL", "h2"),
                Ui.wrap("Stockline stores everything in MySQL. Enter your server’s details; the database and tables are created for you.", "muted"),
                form, connect, status,
                Ui.wrap("Saved to " + DbConfig.file() + " on this computer.", "tiny"));
        card.setMaxWidth(520);

        VBox intro = new VBox(18,
                Ui.label("REAL-TIME RETAIL INVENTORY", "eyebrow"),
                Ui.wrap("One-time setup", "display"),
                Ui.wrap("Install MySQL 8 if you haven’t already (MySQL Community Server). Every computer that connects to the same database sees the same live stock.", "lead"));
        intro.setMaxWidth(420);

        HBox split = new HBox(56, intro, card);
        split.setAlignment(Pos.CENTER);
        split.setFillHeight(false); // cards keep their natural height
        root.getChildren().add(split);
        root.getStyleClass().add("screen");
    }

    private static VBox field(String label, javafx.scene.control.Control input) {
        input.setMaxWidth(Double.MAX_VALUE);
        return new VBox(6, Ui.label(label, "field-label"), input);
    }

    public Parent root() {
        return root;
    }
}
