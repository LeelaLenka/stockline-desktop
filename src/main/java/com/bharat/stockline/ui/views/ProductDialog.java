package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Product;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Ui;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.function.Consumer;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.stage.Window;

/** Add or edit a product */
public final class ProductDialog extends FormDialog {

    private final AppContext ctx;
    private final Product existing;
    private final TextField name = new TextField();
    private final TextField sku = new TextField();
    private final ComboBox<String> category = new ComboBox<>();
    private final TextField price = new TextField();
    private final TextArea description = new TextArea();
    private final Label error = Ui.wrap("", "error-text");

    public ProductDialog(AppContext ctx, Window owner, Product existing) {
        super(owner, existing == null ? "Add a product" : "Edit " + existing.name());
        this.ctx = ctx;
        this.existing = existing;
        category.getItems().addAll(ProductsView.CATEGORIES);
        description.setPrefRowCount(3);
        description.setWrapText(true);
        sku.setPromptText("LIV-THR-01");
        price.setPromptText("e.g. 1499.00");
        if (existing != null) {
            name.setText(existing.name());
            sku.setText(existing.sku());
            category.setValue(existing.category());
            price.setText(BigDecimal.valueOf(existing.pricePaise()).movePointLeft(2).toPlainString());
            description.setText(existing.description());
        }
        addField("Name", name);
        addRow(field("SKU", sku), field("Category", category));
        addField("Price (₹)", price);
        addField("Description (optional)", description);
        body.getChildren().add(error);
    }

    /** @param onSaved receives a short confirmation message */
    public void open(Consumer<String> onSaved) {
        Button save = Ui.button(existing == null ? "ADD PRODUCT" : "SAVE CHANGES", "btn-dark", () -> {});
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            Product p = validate();
            if (p == null) return;
            save.setDisable(true);
            Async.run(() -> {
                if (ctx.products.skuTaken(p.sku(), p.id())) return "Another product already uses SKU " + p.sku() + ".";
                if (existing == null) ctx.products.insert(p, ctx.actor());
                else ctx.products.update(p, ctx.actor());
                return null;
            }, problem -> {
                save.setDisable(false);
                if (problem != null) {
                    error.setText(problem);
                } else {
                    close();
                    onSaved.accept(p.name() + (existing == null ? " added." : " saved."));
                }
            }, err -> {
                save.setDisable(false);
                error.setText(Ui.message(err));
            });
        });
        if (existing != null) {
            actions.getChildren().add(Ui.button("Delete", "btn-danger", () -> {
                if (!Ui.confirm("Delete " + existing.name() + "?",
                        "Its stock at every store and its reviews are removed too. Past orders are kept.")) return;
                Async.run(() -> {
                    ctx.products.delete(existing, ctx.actor());
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

    private Product validate() {
        String n = name.getText().trim();
        String s = sku.getText().trim().toUpperCase(Locale.ROOT);
        if (n.isEmpty() || n.length() > 80) return fail("Enter a product name (up to 80 characters).");
        if (!s.matches("[A-Z0-9-]{3,24}")) return fail("SKU: use 3–24 letters, numbers or hyphens.");
        if (category.getValue() == null) return fail("Choose a category.");
        long paise;
        try {
            BigDecimal rupees = new BigDecimal(price.getText().trim().replace(",", ""));
            if (rupees.scale() > 2 || rupees.signum() <= 0 || rupees.compareTo(new BigDecimal("10000000")) >= 0) {
                return fail("Enter a price above zero, with up to 2 decimal places.");
            }
            paise = rupees.setScale(2, RoundingMode.UNNECESSARY).movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException ex) {
            return fail("Enter the price as a number, e.g. 1499.00");
        }
        String d = description.getText() == null ? "" : description.getText().trim();
        if (d.length() > 300) return fail("Keep the description under 300 characters.");
        return new Product(existing == null ? 0 : existing.id(), n, s, category.getValue(), paise, d);
    }

    private Product fail(String message) {
        error.setText(message);
        return null;
    }
}
