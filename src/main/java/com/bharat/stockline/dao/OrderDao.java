package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.Sql;
import com.bharat.stockline.model.Order;
import com.bharat.stockline.model.OrderItem;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OrderDao {

    private final Database db;

    public OrderDao(Database db) {
        this.db = db;
    }

    public List<Order> recent(int limit) throws SQLException {
        String sql = """
                SELECT o.*, COALESCE(SUM(oi.quantity), 0) AS item_count
                FROM orders o
                LEFT JOIN order_items oi ON oi.order_id = o.id
                GROUP BY o.id
                ORDER BY o.created_at DESC
                LIMIT ?
                """;
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, limit);
            List<Order> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Order(rs.getLong("id"), Sql.nullableLong(rs, "store_id"), rs.getString("store_name"),
                            rs.getString("customer_name"), rs.getString("customer_email"), rs.getLong("total_paise"),
                            rs.getString("placed_by"), Sql.instant(rs, "created_at"), rs.getInt("item_count")));
                }
            }
            return list;
        }
    }

    public List<OrderItem> items(long orderId) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT product_name, sku, quantity, unit_price_paise FROM order_items WHERE order_id = ? ORDER BY id")) {
            ps.setLong(1, orderId);
            List<OrderItem> list = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new OrderItem(rs.getString("product_name"), rs.getString("sku"), rs.getInt("quantity"),
                            rs.getLong("unit_price_paise")));
                }
            }
            return list;
        }
    }
}
