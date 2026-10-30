package com.bharat.stockline.dao;

import com.bharat.stockline.db.Database;
import com.bharat.stockline.db.Passwords;
import com.bharat.stockline.model.User;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

public class UserDao {

    private final Database db;

    public UserDao(Database db) {
        this.db = db;
    }

    /** Checks an email and password; empty if they don't match */
    public Optional<User> authenticate(String email, String password) throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT id, email, name, role, password_hash FROM users WHERE email = ?")) {
            ps.setString(1, email.trim().toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next() || !Passwords.verify(password, rs.getString("password_hash"))) return Optional.empty();
                return Optional.of(new User(rs.getLong("id"), rs.getString("email"), rs.getString("name"), rs.getString("role")));
            }
        }
    }

    /** Creates the account, or resets its password and role if it exists (MySQL upsert) */
    public void upsert(String email, String name, String password, String role) throws SQLException {
        String sql = "INSERT INTO users (email, name, password_hash, role) VALUES (?, ?, ?, ?) "
                + "ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role)";
        try (Connection conn = db.connect(); PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, email.trim().toLowerCase());
            ps.setString(2, name);
            ps.setString(3, Passwords.hash(password));
            ps.setString(4, role);
            ps.executeUpdate();
        }
    }

    public boolean adminExists() throws SQLException {
        try (Connection conn = db.connect();
             PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM users WHERE role = 'ADMIN' LIMIT 1");
             ResultSet rs = ps.executeQuery()) {
            return rs.next();
        }
    }
}
