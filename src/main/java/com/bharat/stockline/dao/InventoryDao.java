package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.Product;
import com.bharat.stockline.model.StockLine;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class InventoryDao {

    private final Database db;
    private final ActivityDao activity;

    public InventoryDao(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    /** A store's stock, read from the v_inventory_status view */
    public List<StockLine> byStore(long storeId, boolean alertsOnly) throws SQLException {
        String sql = "SELECT * FROM v_inventory_status WHERE store_id = ?"
                + (alertsOnly ? " AND status <> 'OK'" : "") + " ORDER BY product_name";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, storeId);
            return read(ps);
        }
    }

    /** Products this store doesn't carry yet (anti-join with NOT EXISTS) */
    public List<Product> notStockedAt(long storeId) throws SQLException {
        String sql = """
                SELECT p.* FROM products p
                WHERE NOT EXISTS (SELECT 1 FROM inventory i WHERE i.product_id = p.id AND i.store_id = ?)
                ORDER BY p.name
                """;
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, storeId);
            List<Product> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) list.add(ProductDao.map(rs));
            }
            return list;
        }
    }

    public void addToStore(long storeId, Product product, String storeName, int quantity, int reorderLevel, String actor)
            throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO inventory (store_id, product_id, quantity, reorder_level) VALUES (?, ?, ?, ?)")) {
            ps.setLong(1, storeId);
            ps.setLong(2, product.id());
            ps.setInt(3, quantity);
            ps.setInt(4, reorderLevel);
            ps.executeUpdate();
            activity.insert(conn, "RESTOCK", "Started stocking " + product.name() + " at " + storeName + " (" + quantity + " units)", actor);
        }
    }

    /** Restocks through the sp_restock stored procedure, called with JDBC's CallableStatement */
    public void restock(long inventoryId, int units, String actor) throws SQLException {
        try (Connection conn = db.connect(); CallableStatement cs = conn.prepareCall("{call sp_restock(?, ?, ?)}")) {
            cs.setLong(1, inventoryId);
            cs.setInt(2, units);
            cs.setString(3, actor);
            cs.execute();
        }
    }

    /** A stock-take: sets an exact count. The update and its log entry commit together or not at all. */
    public void adjust(StockLine line, int quantity, int reorderLevel, String actor) throws SQLException {
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE inventory SET quantity = ?, reorder_level = ? WHERE id = ?")) {
                ps.setInt(1, quantity);
                ps.setInt(2, reorderLevel);
                ps.setLong(3, line.inventoryId());
                if (ps.executeUpdate() == 0) throw new SQLException("That stock line no longer exists.");
                activity.insert(conn, "ADJUST", "Stock count for " + line.productName() + " at " + line.storeName()
                        + " set to " + quantity + " (was " + line.quantity() + ")", actor);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    static List<StockLine> read(PreparedStatement ps) throws SQLException {
        List<StockLine> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StockLine(rs.getLong("inventory_id"), rs.getLong("store_id"), rs.getString("store_name"),
                        rs.getLong("product_id"), rs.getString("product_name"), rs.getString("sku"), rs.getString("category"),
                        rs.getInt("quantity"), rs.getInt("reorder_level"), rs.getString("status")));
            }
        }
        return list;
    }
}
