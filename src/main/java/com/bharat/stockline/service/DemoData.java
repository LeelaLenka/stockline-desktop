package com.bharat.stockline.service;

import com.bharat.stockline.dao.ActivityDao;
import com.bharat.stockline.db.Database;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Sample stores, products, stock, orders and reviews, inserted with JDBC batches in one transaction */
public class DemoData {

    private final Database db;
    private final ActivityDao activity;

    public DemoData(Database db, ActivityDao activity) {
        this.db = db;
        this.activity = activity;
    }

    public boolean isEmpty() throws SQLException {
        try (Connection conn = db.connect(); Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT (SELECT COUNT(*) FROM stores) + (SELECT COUNT(*) FROM products)")) {
            rs.next();
            return rs.getInt(1) == 0;
        }
    }

    /** Wipes business data (not user accounts) and reloads the samples */
    public void reset(String actor) throws SQLException {
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            try (Statement st = conn.createStatement()) {
                // Child tables first because of foreign keys
                for (String table : List.of("order_items", "orders", "reviews", "inventory", "products", "stores", "activity")) {
                    st.executeUpdate("DELETE FROM " + table);
                }
                insertSamples(conn);
                activity.insert(conn, "SYSTEM", "Demo data was reset", actor);
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    public void seed() throws SQLException {
        try (Connection conn = db.connect()) {
            conn.setAutoCommit(false);
            try {
                insertSamples(conn);
                activity.insert(conn, "SYSTEM", "Sample data loaded", "system");
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    private void insertSamples(Connection conn) throws SQLException {
        Random rnd = new Random(42);

        String[][] stores = {
                {"Stockline Central", "Hyderabad", "Road No. 36, Jubilee Hills"},
                {"Stockline Riverside", "Vijayawada", "MG Road, Labbipet"},
                {"Stockline Harbour", "Visakhapatnam", "Beach Road, Siripuram"},
        };
        List<Long> storeIds = insertBatch(conn, "INSERT INTO stores (name, city, address) VALUES (?, ?, ?)", stores.length, (ps, i) -> {
            ps.setString(1, stores[i][0]);
            ps.setString(2, stores[i][1]);
            ps.setString(3, stores[i][2]);
        });

        Object[][] products = {
                {"Linen Throw Blanket", "LIV-THR-01", "Living", 2499_00L, "Stonewashed linen, 130 x 170 cm."},
                {"Ceramic Table Lamp", "LIV-LMP-02", "Living", 3299_00L, "Hand-glazed base with a linen shade."},
                {"Jute Area Rug 5x8", "LIV-RUG-03", "Living", 6999_00L, "Hand-woven natural jute."},
                {"Cushion Cover Set (2)", "LIV-CSH-04", "Living", 1199_00L, "Cotton slub, 45 x 45 cm."},
                {"Oak Serving Board", "KIT-BRD-01", "Kitchen", 1499_00L, "Solid oak with a juice groove."},
                {"Stoneware Mug Set (4)", "KIT-MUG-02", "Kitchen", 1799_00L, "Speckled glaze, 350 ml each."},
                {"Glass Water Carafe", "KIT-CRF-03", "Kitchen", 899_00L, "1 litre, with tumbler lid."},
                {"Cotton Bath Towel", "BTH-TWL-01", "Bath", 799_00L, "600 GSM combed cotton."},
                {"Bamboo Bath Mat", "BTH-MAT-02", "Bath", 1099_00L, "Slatted, quick-drying."},
                {"Scented Soy Candle", "DEC-CND-01", "Decor", 649_00L, "Sandalwood and vetiver, 40-hour burn."},
                {"Woven Storage Basket", "DEC-BSK-02", "Decor", 1299_00L, "Seagrass with handles."},
                {"Terracotta Planter", "DEC-PLT-03", "Decor", 999_00L, "Hand-thrown, with drainage hole."},
                {"Brass Wall Clock", "DEC-CLK-04", "Decor", 2799_00L, "Silent sweep movement, 30 cm."},
        };
        List<Long> productIds = insertBatch(conn,
                "INSERT INTO products (name, sku, category, price_paise, description) VALUES (?, ?, ?, ?, ?)", products.length, (ps, i) -> {
                    ps.setString(1, (String) products[i][0]);
                    ps.setString(2, (String) products[i][1]);
                    ps.setString(3, (String) products[i][2]);
                    ps.setLong(4, (Long) products[i][3]);
                    ps.setString(5, (String) products[i][4]);
                });

        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO inventory (store_id, product_id, quantity, reorder_level) VALUES (?, ?, ?, ?)")) {
            for (long s : storeIds) {
                for (long p : productIds) {
                    if (rnd.nextInt(10) == 0) continue;
                    int reorder = 8 + rnd.nextInt(8);
                    int roll = rnd.nextInt(12);
                    int qty = roll == 0 ? 0 : roll <= 2 ? 1 + rnd.nextInt(reorder) : 18 + rnd.nextInt(60);
                    ps.setLong(1, s);
                    ps.setLong(2, p);
                    ps.setInt(3, qty);
                    ps.setInt(4, reorder);
                    ps.addBatch();
                }
            }
            ps.executeBatch();
        }

        String[] customers = {"Ananya Rao", "Karthik Reddy", "Meera Iyer", "Rahul Varma", "Divya Menon", "Arjun Naidu",
                "Sneha Kapoor", "Vikram Sharma", "Priya Nair", "Rohan Das"};
        Instant now = Instant.now();
        try (PreparedStatement order = conn.prepareStatement(
                "INSERT INTO orders (store_id, store_name, customer_name, customer_email, total_paise, placed_by, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)",
                Statement.RETURN_GENERATED_KEYS);
             PreparedStatement item = conn.prepareStatement(
                     "INSERT INTO order_items (order_id, product_id, product_name, sku, quantity, unit_price_paise) VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int n = 0; n < 34; n++) {
                int s = rnd.nextInt(storeIds.size());
                String customer = customers[rnd.nextInt(customers.length)];
                List<int[]> lines = new ArrayList<>(); // {productIndex, quantity}
                int lineCount = 1 + rnd.nextInt(3);
                for (int l = 0; l < lineCount; l++) {
                    int pi = rnd.nextInt(productIds.size());
                    if (lines.stream().noneMatch(x -> x[0] == pi)) lines.add(new int[]{pi, 1 + rnd.nextInt(3)});
                }
                long total = lines.stream().mapToLong(x -> (Long) products[x[0]][3] * x[1]).sum();
                order.setLong(1, storeIds.get(s));
                order.setString(2, stores[s][0]);
                order.setString(3, customer);
                order.setString(4, customer.toLowerCase().replace(' ', '.') + "@example.com");
                order.setLong(5, total);
                order.setString(6, "demo@stockline.app");
                order.setTimestamp(7, Timestamp.from(now.minus(Duration.ofMinutes(20 + rnd.nextInt(6 * 24 * 60)))));
                order.executeUpdate();
                long orderId;
                try (ResultSet keys = order.getGeneratedKeys()) {
                    keys.next();
                    orderId = keys.getLong(1);
                }
                for (int[] x : lines) {
                    item.setLong(1, orderId);
                    item.setLong(2, productIds.get(x[0]));
                    item.setString(3, (String) products[x[0]][0]);
                    item.setString(4, (String) products[x[0]][1]);
                    item.setInt(5, x[1]);
                    item.setLong(6, (Long) products[x[0]][3]);
                    item.addBatch();
                }
            }
            item.executeBatch();
        }

        String[][] reviewText = {
                {"5", "Beautiful quality, even better in person."},
                {"4", "Lovely, though delivery took a few days."},
                {"5", "Exactly as described. Bought a second one."},
                {"3", "Good, but the colour is lighter than the photo."},
                {"5", "Feels premium for the price."},
                {"4", "Sturdy and well made."},
        };
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO reviews (product_id, customer_name, rating, comment, created_at) VALUES (?, ?, ?, ?, ?)")) {
            for (int r = 0; r < 16; r++) {
                String[] t = reviewText[rnd.nextInt(reviewText.length)];
                ps.setLong(1, productIds.get(rnd.nextInt(productIds.size())));
                ps.setString(2, customers[rnd.nextInt(customers.length)]);
                ps.setInt(3, Integer.parseInt(t[0]));
                ps.setString(4, t[1]);
                ps.setTimestamp(5, Timestamp.from(now.minus(Duration.ofHours(2 + rnd.nextInt(14 * 24)))));
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    @FunctionalInterface
    private interface Binder {
        void bind(PreparedStatement ps, int index) throws SQLException;
    }

    /** Inserts rows one by one (to collect their generated ids) on a single prepared statement */
    private static List<Long> insertBatch(Connection conn, String sql, int count, Binder binder) throws SQLException {
        List<Long> ids = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            for (int i = 0; i < count; i++) {
                binder.bind(ps, i);
                ps.executeUpdate();
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    keys.next();
                    ids.add(keys.getLong(1));
                }
            }
        }
        return ids;
    }
}
