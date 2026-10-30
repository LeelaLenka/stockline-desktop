package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.model.Dashboard;
import com.bharat.stockline.model.StockLine;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Dashboard numbers, all calculated by MySQL with aggregate queries */
public class ReportDao {

    /** Times are stored in UTC and reported in India Standard Time */
    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final String IST = "'+05:30'";

    private final Database db;

    public ReportDao(Database db) {
        this.db = db;
    }

    public Dashboard dashboard() throws SQLException {
        try (Connection conn = db.connect()) {
            long revenueToday;
            int ordersToday;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*), COALESCE(SUM(total_paise), 0) FROM orders "
                            + "WHERE DATE(CONVERT_TZ(created_at, '+00:00', " + IST + ")) = ?")) {
                ps.setObject(1, LocalDate.now(ZONE));
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    ordersToday = rs.getInt(1);
                    revenueToday = rs.getLong(2);
                }
            }

            long units;
            int low;
            int out;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COALESCE(SUM(quantity), 0), COALESCE(SUM(status = 'LOW'), 0), COALESCE(SUM(status = 'OUT'), 0) "
                            + "FROM v_inventory_status");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                units = rs.getLong(1);
                low = rs.getInt(2);
                out = rs.getInt(3);
            }

            int products;
            int stores;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT (SELECT COUNT(*) FROM products), (SELECT COUNT(*) FROM stores)");
                 ResultSet rs = ps.executeQuery()) {
                rs.next();
                products = rs.getInt(1);
                stores = rs.getInt(2);
            }

            // Sales per day for the last 7 days, filling in days with no sales
            LocalDate today = LocalDate.now(ZONE);
            Map<LocalDate, Long> byDay = new LinkedHashMap<>();
            for (int d = 6; d >= 0; d--) byDay.put(today.minusDays(d), 0L);
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT DATE(CONVERT_TZ(created_at, '+00:00', " + IST + ")) AS day, SUM(total_paise) AS total "
                            + "FROM orders WHERE created_at >= UTC_TIMESTAMP() - INTERVAL 8 DAY GROUP BY day")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        LocalDate day = rs.getObject("day", LocalDate.class);
                        long total = rs.getLong("total");
                        if (byDay.containsKey(day)) byDay.put(day, total);
                    }
                }
            }
            DateTimeFormatter label = DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH);
            List<Dashboard.DaySales> week = new ArrayList<>();
            byDay.forEach((day, total) -> week.add(new Dashboard.DaySales(day.equals(today) ? "Today" : label.format(day), total)));

            // Best sellers in the last 7 days
            List<Dashboard.TopProduct> top = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement("""
                    SELECT oi.product_name, SUM(oi.quantity) AS units, SUM(oi.quantity * oi.unit_price_paise) AS revenue
                    FROM order_items oi JOIN orders o ON o.id = oi.order_id
                    WHERE o.created_at >= UTC_TIMESTAMP() - INTERVAL 7 DAY
                    GROUP BY oi.product_name
                    ORDER BY units DESC
                    LIMIT 5
                    """);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) top.add(new Dashboard.TopProduct(rs.getString(1), rs.getInt(2), rs.getLong(3)));
            }

            List<StockLine> restock;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT * FROM v_inventory_status WHERE status <> 'OK' ORDER BY quantity, product_name LIMIT 8")) {
                restock = InventoryDao.read(ps);
            }

            return new Dashboard(revenueToday, ordersToday, units, low, out, products, stores, week, top, restock);
        }
    }
}
