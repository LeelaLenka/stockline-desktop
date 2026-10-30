package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Store;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Ui;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;

public final class StoreDialog extends FormDialog {

    private final AppContext ctx;
    private final Store existing;
    private final TextField name = new TextField();
    private final TextField city = new TextField();
    private final TextField address = new TextField();
    private final Label error = Ui.wrap("", "error-text");

    public StoreDialog(AppContext ctx, Window owner, Store existing) {
        super(owner, existing == null ? "Add a store" : "Edit " + existing.name());
        this.ctx = ctx;
        this.existing = existing;
        if (existing != null) {
            name.setText(existing.name());
            city.setText(existing.city());
            address.setText(existing.address());
        }
        addField("Store name", name);
        addField("City", city);
        addField("Address (optional)", address);
        body.getChildren().add(error);
    }

    public void open(Consumer<String> onSaved) {
        Button save = Ui.button(existing == null ? "ADD STORE" : "SAVE CHANGES", "btn-dark", () -> {});
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String n = name.getText().trim();
            String c = city.getText().trim();
            String a = address.getText().trim();
            if (n.isEmpty() || n.length() > 60) { error.setText("Enter a store name (up to 60 characters)."); return; }
            if (c.isEmpty() || c.length() > 40) { error.setText("Enter a city (up to 40 characters)."); return; }
            if (a.length() > 160) { error.setText("Keep the address under 160 characters."); return; }
            save.setDisable(true);
            Async.run(() -> {
                if (existing == null) ctx.stores.insert(n, c, a, ctx.actor());
                else ctx.stores.update(existing.id(), n, c, a, ctx.actor());
                return true;
            }, ok -> {
                close();
                onSaved.accept(n + (existing == null ? " created." : " saved."));
            }, err -> {
                save.setDisable(false);
                error.setText(Ui.message(err));
            });
        });
        if (existing != null) {
            actions.getChildren().add(Ui.button("Delete", "btn-danger", () -> {
                if (!Ui.confirm("Delete " + existing.name() + "?", "Its stock is removed. Past orders are kept.")) return;
                Async.run(() -> {
                    ctx.stores.delete(existing, ctx.actor());
                    return true;
                }, ok -> {
                    close();
                    onSaved.accept(existing.name() + " deleted.");
                }, err -> error.setText(Ui.message(err)));
            }));
            actions.getChildren().add(Ui.spacer());
        }
        show(save);
    }
}
