package com.bharat.stockline.ui;

import com.bharat.stockline.model.User;
import java.util.function.Consumer;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class LoginView {

    public static final String DEMO_EMAIL = "demo@stockline.app";
    public static final String DEMO_PASSWORD = "demo1234";

    private final StackPane root = new StackPane();

    public LoginView(AppContext ctx, boolean needsOwner, Consumer<User> onLogin, Runnable onChangeDatabase) {
        TextField email = new TextField();
        email.setPromptText("you@example.com");
        PasswordField password = new PasswordField();
        Label status = Ui.wrap("", "error-text");

        Button fillDemo = Ui.button("FILL IN DEMO DETAILS", "btn-ghost", () -> {
            email.setText(DEMO_EMAIL);
            password.setText(DEMO_PASSWORD);
        });
        VBox demoBox = new VBox(8,
                Ui.wrap("Recruiter or visitor? Use the demo account. Everything works.", "small"),
                Ui.label(DEMO_EMAIL + "   ·   " + DEMO_PASSWORD, "mono"),
                fillDemo);
        demoBox.getStyleClass().add("demo-box");

        Button login = Ui.button("LOG IN", "btn-dark", () -> {});
        login.setDefaultButton(true);
        login.setMaxWidth(Double.MAX_VALUE);
        login.setOnAction(e -> {
            login.setDisable(true);
            status.setText("");
            Async.run(() -> ctx.users.authenticate(email.getText(), password.getText()), user -> {
                login.setDisable(false);
                if (user.isPresent()) onLogin.accept(user.get());
                else status.setText("That email and password don’t match.");
            }, err -> {
                login.setDisable(false);
                status.setText(Ui.message(err));
            });
        });

        VBox card = Ui.card(Ui.label("Log in", "h2"), demoBox,
                new VBox(6, Ui.label("Email", "field-label"), email),
                new VBox(6, Ui.label("Password", "field-label"), password),
                login, status);
        card.setPrefWidth(420);
        card.setMaxWidth(420);

        if (needsOwner) card.getChildren().add(ownerSetup(ctx));
        card.getChildren().add(Ui.button("Use a different MySQL database", "link", onChangeDatabase));

        VBox intro = new VBox(20,
                Ui.label("REAL-TIME RETAIL INVENTORY", "eyebrow"),
                Ui.wrap("Every shelf, every store, live.", "display"),
                Ui.wrap("Stockline tracks stock across stores as it changes. Run it on two computers against the same MySQL database, sell something on one, and watch the other update.", "lead"),
                new VBox(6, Ui.label("—  JavaFX 21 desktop interface", "fact"),
                        Ui.label("—  JDBC with transactions and row locking", "fact"),
                        Ui.label("—  MySQL views and a stored procedure", "fact")));
        intro.setMaxWidth(460);

        HBox split = new HBox(64, intro, card);
        split.setAlignment(Pos.CENTER);
        split.setFillHeight(false); // cards keep their natural height
        root.getChildren().add(split);
        root.getStyleClass().add("screen");
    }

    /** Shown until an owner account exists */
    private VBox ownerSetup(AppContext ctx) {
        TextField email = new TextField();
        email.setPromptText("Your email");
        PasswordField pw = new PasswordField();
        pw.setPromptText("Password, 10+ characters");
        Label msg = Ui.wrap("", "small");
        Button create = Ui.button("CREATE OWNER ACCOUNT", "btn-ghost", () -> {
            if (!email.getText().contains("@") || pw.getText().length() < 10) {
                msg.setText("Enter an email and a password of at least 10 characters.");
                return;
            }
            Async.run(() -> {
                ctx.users.upsert(email.getText(), "Owner", pw.getText(), "ADMIN");
                return true;
            }, ok -> msg.setText("Owner account created. Log in above."), err -> msg.setText(Ui.message(err)));
        });
        VBox box = new VBox(8, Ui.label("First time? Create your owner account", "field-label"), email, pw, create, msg);
        box.getStyleClass().add("owner-box");
        return box;
    }

    public Parent root() {
        return root;
    }
}
