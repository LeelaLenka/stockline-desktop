package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.Sql;
import com.bharat.stockline.model.ActivityEntry;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ActivityDao {

    private final Database db;

    public ActivityDao(Database db) {
        this.db = db;
    }

    /** Uses the caller's connection so it can be part of a larger transaction */
    public void insert(Connection conn, String type, String message, String actor) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("INSERT INTO activity (type, message, actor) VALUES (?, ?, ?)")) {
            ps.setString(1, type);
            ps.setString(2, message.length() > 300 ? message.substring(0, 300) : message);
            ps.setString(3, actor);
            ps.executeUpdate();
        }
    }

    public void insert(String type, String message, String actor) throws SQLException {
        try (Connection conn = db.connect()) {
            insert(conn, type, message, actor);
        }
    }

    public List<ActivityEntry> latest(int limit) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM activity ORDER BY id DESC LIMIT ?")) {
            ps.setInt(1, limit);
            return read(ps);
        }
    }

    /** Everything newer than the last row this app has seen; powers live updates */
    public List<ActivityEntry> since(long lastId) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM activity WHERE id > ? ORDER BY id LIMIT 50")) {
            ps.setLong(1, lastId);
            return read(ps);
        }
    }

    public long maxId() throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT COALESCE(MAX(id), 0) FROM activity");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static List<ActivityEntry> read(PreparedStatement ps) throws SQLException {
        List<ActivityEntry> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new ActivityEntry(rs.getLong("id"), rs.getString("type"), rs.getString("message"),
                        rs.getString("actor"), Sql.instant(rs, "created_at")));
            }
        }
        return list;
    }
}
