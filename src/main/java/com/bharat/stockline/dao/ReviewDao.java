package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.Sql;
import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.Review;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ReviewDao {

    private final Database db;
    private final ActivityDao activity;

    public ReviewDao(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    /** All reviews, or one product's (productId 0 = all) */
    public List<Review> list(long productId) throws SQLException {
        String sql = """
                SELECT r.*, p.name AS product_name
                FROM reviews r JOIN products p ON p.id = r.product_id
                WHERE (? = 0 OR r.product_id = ?)
                ORDER BY r.created_at DESC
                LIMIT 200
                """;
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, productId);
            ps.setLong(2, productId);
            List<Review> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Review(rs.getLong("id"), rs.getLong("product_id"), rs.getString("product_name"),
                            rs.getString("customer_name"), rs.getInt("rating"), rs.getString("comment"), Sql.instant(rs, "created_at")));
                }
            }
            return list;
        }
    }

    public void insert(Product product, String customerName, int rating, String comment, String actor) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO reviews (product_id, customer_name, rating, comment) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, product.id());
            ps.setString(2, customerName);
            ps.setInt(3, rating);
            ps.setString(4, comment);
            ps.executeUpdate();
            activity.insert(conn, "REVIEW", rating + "-star review for " + product.name() + " from " + customerName, actor);
        }
    }
}
