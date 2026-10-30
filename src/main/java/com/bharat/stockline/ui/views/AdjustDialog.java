package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.StockLine;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Ui;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.Window;

/** Stock-take: set the exact count and reorder level */
public final class AdjustDialog extends FormDialog {

    private final AppContext ctx;
    private final StockLine line;
    private final TextField quantity = new TextField();
    private final TextField reorder = new TextField();
    private final Label error = Ui.wrap("", "error-text");

    public AdjustDialog(AppContext ctx, Window owner, StockLine line) {
        super(owner, "Stock count: " + line.productName());
        this.ctx = ctx;
        this.line = line;
        quantity.setText(String.valueOf(line.quantity()));
        reorder.setText(String.valueOf(line.reorderLevel()));
        body.getChildren().add(Ui.label(line.storeName(), "muted"));
        addRow(field("Units on the shelf", quantity), field("Reorder when at or below", reorder));
        body.getChildren().add(error);
    }

    public void open(Runnable onSaved) {
        Button save = Ui.button("SAVE COUNT", "btn-dark", () -> {});
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            int q;
            int r;
            try {
                q = Integer.parseInt(quantity.getText().trim());
                r = Integer.parseInt(reorder.getText().trim());
            } catch (NumberFormatException ex) {
                error.setText("Enter whole numbers.");
                return;
            }
            if (q < 0 || q > 100_000 || r < 0 || r > 10_000) {
                error.setText("Units must be 0–100,000 and the reorder level 0–10,000.");
                return;
            }
            save.setDisable(true);
            Async.run(() -> {
                ctx.inventory.adjust(line, q, r, ctx.actor());
                return true;
            }, ok -> {
                close();
                onSaved.run();
            }, err -> {
                save.setDisable(false);
                error.setText(Ui.message(err));
            });
        });
        show(save);
    }
}
