package com.bharat.stockline.service;

import com.bharat.stockline.dao.ActivityDao;
import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.OrderRequest;
import com.bharat.stockline.util.Format;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Places an order as one database transaction:
 * lock each stock row (SELECT ... FOR UPDATE), check there's enough, take it,
 * write the order and its items, log it, then COMMIT. Any problem means ROLLBACK,
 * so stock is never half-updated and two people can't both sell the last unit.
 */
public class OrderService {

    private final Database db;
    private final ActivityDao activity;

    public OrderService(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    private record Picked(long productId, String name, String sku, long pricePaise, int quantity) {}

    public long place(OrderRequest req, String actor) throws StockException, SQLException {
        if (req.customerName() == null || req.customerName().isBlank()) throw new StockException("Enter the customer’s name.");

        // Merge repeated products into one line each
        Map<Long, Integer> wanted = new LinkedHashMap<>();
        for (OrderRequest.Line l : req.lines()) {
            if (l.quantity() < 1 || l.quantity() > 1000) throw new StockException("Quantities must be between 1 and 1,000.");
            wanted.merge(l.productId(), l.quantity(), Integer::sum);
        }
        if (wanted.isEmpty()) throw new StockException("Add at least one product.");

        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            conn.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            try {
                String storeName = storeName(conn, req.storeId());
                List<Picked> picked = new ArrayList<>();

                String lockSql = """
                        SELECT i.id, i.quantity, p.name, p.sku, p.price_paise
                        FROM inventory i JOIN products p ON p.id = i.product_id
                        WHERE i.store_id = ? AND i.product_id = ?
                        FOR UPDATE
                        """;
                try (PreparedStatement lock = conn.prepareStatement(lockSql);
                     PreparedStatement take = conn.prepareStatement("UPDATE inventory SET quantity = quantity - ? WHERE id = ?")) {
                    for (Map.Entry<Long, Integer> e : wanted.entrySet()) {
                        lock.setLong(1, req.storeId());
                        lock.setLong(2, e.getKey());
                        try (ResultSet rs = lock.executeQuery()) {
                            if (!rs.next()) throw new StockException("A product in this order isn’t stocked at " + storeName + ".");
                            int available = rs.getInt("quantity");
                            if (available < e.getValue()) {
                                throw new StockException("Not enough " + rs.getString("name") + " at " + storeName + ": "
                                        + available + " left, " + e.getValue() + " requested.");
                            }
                            take.setInt(1, e.getValue());
                            take.setLong(2, rs.getLong("id"));
                            take.executeUpdate();
                            picked.add(new Picked(e.getKey(), rs.getString("name"), rs.getString("sku"), rs.getLong("price_paise"), e.getValue()));
                        }
                    }
                }

                long total = picked.stream().mapToLong(p -> p.pricePaise() * p.quantity()).sum();
                long orderId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO orders (store_id, store_name, customer_name, customer_email, total_paise, placed_by) VALUES (?, ?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setLong(1, req.storeId());
                    ps.setString(2, storeName);
                    ps.setString(3, req.customerName().trim());
                    ps.setString(4, req.customerEmail() == null ? "" : req.customerEmail().trim());
                    ps.setLong(5, total);
                    ps.setString(6, actor);
                    ps.executeUpdate();
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        keys.next();
                        orderId = keys.getLong(1);
                    }
                }

                // All items in one round trip
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO order_items (order_id, product_id, product_name, sku, quantity, unit_price_paise) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (Picked p : picked) {
                        ps.setLong(1, orderId);
                        ps.setLong(2, p.productId());
                        ps.setString(3, p.name());
                        ps.setString(4, p.sku());
                        ps.setInt(5, p.quantity());
                        ps.setLong(6, p.pricePaise());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                int items = picked.stream().mapToInt(Picked::quantity).sum();
                activity.insert(conn, "ORDER", "Order of " + items + (items == 1 ? " item (" : " items (") + Format.money(total)
                        + ") at " + storeName + " for " + req.customerName().trim(), actor);

                conn.commit();
                return orderId;
            } catch (StockException | SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private static String storeName(Connection conn, long storeId) throws SQLException, StockException {
        try (PreparedStatement ps = conn.prepareStatement("SELECT name FROM stores WHERE id = ?")) {
            ps.setLong(1, storeId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) throw new StockException("Choose a store.");
                return rs.getString(1);
            }
        }
    }
}
