package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.ProductRow;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ProductDao {

    private final Database db;
    private final ActivityDao activity;

    public ProductDao(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    public List<Product> findAll() throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM products ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Product> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        }
    }

    /**
     * Catalogue rows with stock and rating summaries.
     * Two derived tables are joined so stock and reviews don't multiply each other's rows.
     */
    public List<ProductRow> rows(String search, String category) throws SQLException {
        String sql = """
                SELECT p.*,
                       COALESCE(st.units, 0)  AS units,
                       COALESCE(st.stores, 0) AS stores,
                       COALESCE(st.outs, 0)   AS outs,
                       COALESCE(st.lows, 0)   AS lows,
                       COALESCE(rv.avg_rating, 0) AS avg_rating,
                       COALESCE(rv.reviews, 0)    AS reviews
                FROM products p
                LEFT JOIN (SELECT product_id, SUM(quantity) AS units, COUNT(*) AS stores,
                                  SUM(quantity = 0) AS outs,
                                  SUM(quantity > 0 AND quantity <= reorder_level) AS lows
                           FROM inventory GROUP BY product_id) st ON st.product_id = p.id
                LEFT JOIN (SELECT product_id, AVG(rating) AS avg_rating, COUNT(*) AS reviews
                           FROM reviews GROUP BY product_id) rv ON rv.product_id = p.id
                WHERE (? = '' OR p.name LIKE ? OR p.sku LIKE ?)
                  AND (? = '' OR p.category = ?)
                ORDER BY p.name
                """;
        String like = "%" + search.trim() + "%";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, search.trim());
            ps.setString(2, like);
            ps.setString(3, like);
            ps.setString(4, category);
            ps.setString(5, category);
            List<ProductRow> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int stores = rs.getInt("stores");
                    String status = stores == 0 ? "NONE" : rs.getInt("outs") > 0 ? "OUT" : rs.getInt("lows") > 0 ? "LOW" : "OK";
                    list.add(new ProductRow(map(rs), rs.getInt("units"), stores, status,
                            Math.round(rs.getDouble("avg_rating") * 10) / 10.0, rs.getInt("reviews")));
                }
            }
            return list;
        }
    }

    public boolean skuTaken(String sku, long exceptId) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM products WHERE sku = ? AND id <> ?")) {
            ps.setString(1, sku);
            ps.setLong(2, exceptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public void insert(Product p, String actor) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO products (name, sku, category, price_paise, description) VALUES (?, ?, ?, ?, ?)")) {
            bind(ps, p);
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Added product " + p.name(), actor);
        }
    }

    public void update(Product p, String actor) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE products SET name = ?, sku = ?, category = ?, price_paise = ?, description = ? WHERE id = ?")) {
            bind(ps, p);
            ps.setLong(6, p.id());
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Updated product " + p.name(), actor);
        }
    }

    /** Stock and reviews go with it (ON DELETE CASCADE); order history keeps the name */
    public void delete(Product p, String actor) throws SQLException {
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement("DELETE FROM products WHERE id = ?")) {
            ps.setLong(1, p.id());
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Removed product " + p.name(), actor);
        }
    }

    private static void bind(PreparedStatement ps, Product p) throws SQLException {
        ps.setString(1, p.name());
        ps.setString(2, p.sku());
        ps.setString(3, p.category());
        ps.setLong(4, p.pricePaise());
        ps.setString(5, p.description());
    }

    static Product map(ResultSet rs) throws SQLException {
        return new Product(rs.getLong("id"), rs.getString("name"), rs.getString("sku"), rs.getString("category"),
                rs.getLong("price_paise"), rs.getString("description"));
    }
}
