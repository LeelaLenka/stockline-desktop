package com.bharat.stockline.ui.views;

import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.Review;
import com.bharat.stockline.ui.AppContext;
import com.bharat.stockline.ui.Async;
import com.bharat.stockline.ui.Shell;
import com.bharat.stockline.ui.Ui;
import com.bharat.stockline.ui.View;
import com.bharat.stockline.util.Format;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public final class ReviewsView implements View {

    private record Data(List<Product> products, List<Review> reviews) {}

    private final AppContext ctx;
    private final VBox root = new VBox(18);
    private final ComboBox<Product> filter = new ComboBox<>();
    private final VBox list = new VBox(12);
    private final Label average = Ui.label("", "big-number");
    private final Label averageSub = Ui.label("", "muted");
    private final ComboBox<Product> product = new ComboBox<>();
    private final TextField name = new TextField();
    private final TextArea comment = new TextArea();
    private final List<Button> stars = new ArrayList<>();
    private final Label formError = Ui.wrap("", "error-text");
    private int rating = 5;
    private boolean loading;

    public ReviewsView(AppContext ctx, Shell shell) {
        this.ctx = ctx;
        root.getStyleClass().add("page");

        filter.setPromptText("All products");
        filter.setOnAction(e -> {
            if (!loading) refresh();
        });

        HBox starRow = new HBox(2);
        for (int i = 1; i <= 5; i++) {
            int value = i;
            Button b = Ui.button("★", "star", () -> setRating(value));
            stars.add(b);
            starRow.getChildren().add(b);
        }
        setRating(5);
        comment.setPrefRowCount(3);
        comment.setWrapText(true);
        product.setMaxWidth(Double.MAX_VALUE);
        Button add = Ui.button("ADD REVIEW", "btn-dark", this::addReview);

        VBox form = Ui.card(Ui.label("Add a review", "h2"),
                labeled("Product", product), labeled("Customer name", name),
                labeled("Rating", starRow), labeled("Review", comment), add, formError);
        form.setPrefWidth(340);
        form.setMinWidth(300);

        ScrollList scroll = new ScrollList(list);
        HBox.setHgrow(scroll.box, Priority.ALWAYS);
        HBox body = new HBox(22, scroll.box, form);

        VBox summary = new VBox(0, average, averageSub);
        summary.setAlignment(Pos.BOTTOM_RIGHT);
        root.getChildren().addAll(Ui.header("Customer feedback", "Reviews", summary), filter, body);
    }

    /** Small wrapper so the list column can grow */
    private static final class ScrollList {
        final VBox box;

        ScrollList(VBox content) {
            box = new VBox(content);
            box.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private static VBox labeled(String label, Node input) {
        return new VBox(6, Ui.label(label, "field-label"), input);
    }

    private void setRating(int value) {
        rating = value;
        for (int i = 0; i < stars.size(); i++) {
            stars.get(i).getStyleClass().remove("on");
            if (i < value) stars.get(i).getStyleClass().add("on");
        }
    }

    private void addReview() {
        Product p = product.getValue();
        String n = name.getText().trim();
        String c = comment.getText() == null ? "" : comment.getText().trim();
        if (p == null) { formError.setText("Choose a product."); return; }
        if (n.isEmpty() || n.length() > 60) { formError.setText("Enter the customer’s name (up to 60 characters)."); return; }
        if (c.isEmpty() || c.length() > 500) { formError.setText("Write a short review (up to 500 characters)."); return; }
        formError.setText("");
        int r = rating;
        Async.run(() -> {
            ctx.reviews.insert(p, n, r, c, ctx.actor());
            return true;
        }, ok -> {
            name.clear();
            comment.clear();
            setRating(5);
            refresh();
        }, err -> formError.setText(Ui.message(err)));
    }

    @Override
    public Node root() {
        return root;
    }

    @Override
    public void refresh() {
        Product selected = filter.getValue();
        long id = selected == null || selected.id() == 0 ? 0 : selected.id();
        Async.run(() -> new Data(ctx.products.findAll(), ctx.reviews.list(id)), d -> {
            loading = true;
            List<Product> options = new ArrayList<>();
            options.add(new Product(0, "All products", "", "", 0, ""));
            options.addAll(d.products());
            filter.setItems(FXCollections.observableArrayList(options));
            options.stream().filter(p -> p.id() == id).findFirst().ifPresent(filter::setValue);
            product.setItems(FXCollections.observableArrayList(d.products()));
            loading = false;

            double avg = d.reviews().stream().mapToInt(Review::rating).average().orElse(0);
            average.setText(d.reviews().isEmpty() ? "–" : String.format("%.1f", avg));
            averageSub.setText(d.reviews().size() + (d.reviews().size() == 1 ? " review" : " reviews"));

            list.getChildren().clear();
            if (d.reviews().isEmpty()) list.getChildren().add(Ui.label("No reviews yet.", "muted"));
            for (Review r : d.reviews()) {
                HBox head = new HBox(Ui.label(Format.stars(r.rating()), "stars"), Ui.spacer(), Ui.label(Format.ago(r.createdAt()), "tiny"));
                head.setAlignment(Pos.CENTER_LEFT);
                list.getChildren().add(Ui.card(head, Ui.wrap(r.comment(), "review-text"),
                        Ui.label(r.customerName() + " on " + r.productName(), "muted")));
            }
        }, Ui::error);
    }
}
