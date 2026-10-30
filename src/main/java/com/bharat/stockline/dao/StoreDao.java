package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.Store;
import com.bharat.stockline.model.StoreSummary;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class StoreDao {

    private final Database db;
    private final ActivityDao activity;

    public StoreDao(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    public List<Store> findAll() throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT id, name, city, address FROM stores ORDER BY name");
             ResultSet rs = ps.executeQuery()) {
            List<Store> list = new ArrayList<>();
            while (rs.next()) list.add(map(rs));
            return list;
        }
    }

    /** Each store with its number of product lines, total units and stock alerts (LEFT JOIN + GROUP BY) */
    public List<StoreSummary> summaries() throws SQLException {
        String sql = """
                SELECT s.id, s.name, s.city, s.address,
                       COUNT(i.id)                                                AS line_count,
                       COALESCE(SUM(i.quantity), 0)                               AS units,
                       COALESCE(SUM(CASE WHEN i.quantity <= i.reorder_level THEN 1 ELSE 0 END), 0) AS alerts
                FROM stores s
                LEFT JOIN inventory i ON i.store_id = s.id
                GROUP BY s.id, s.name, s.city, s.address
                ORDER BY s.name
                """;
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            List<StoreSummary> list = new ArrayList<>();
            while (rs.next()) list.add(new StoreSummary(map(rs), rs.getInt("line_count"), rs.getInt("units"), rs.getInt("alerts")));
            return list;
        }
    }

    public long insert(String name, String city, String address, String actor) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("INSERT INTO stores (name, city, address) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, city);
            ps.setString(3, address);
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Opened store " + name + " in " + city, actor);
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                return keys.getLong(1);
            }
        }
    }

    public void update(long id, String name, String city, String address, String actor) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("UPDATE stores SET name = ?, city = ?, address = ? WHERE id = ?")) {
            ps.setString(1, name);
            ps.setString(2, city);
            ps.setString(3, address);
            ps.setLong(4, id);
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Updated store " + name, actor);
        }
    }

    /** Stock is removed by ON DELETE CASCADE; past orders keep the store name */
    public void delete(Store store, String actor) throws SQLException {
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement("DELETE FROM stores WHERE id = ?")) {
            ps.setLong(1, store.id());
            ps.executeUpdate();
            activity.insert(conn, "CATALOG", "Closed store " + store.name(), actor);
        }
    }

    private static Store map(ResultSet rs) throws SQLException {
        return new Store(rs.getLong("id"), rs.getString("name"), rs.getString("city"), rs.getString("address"));
    }
}
