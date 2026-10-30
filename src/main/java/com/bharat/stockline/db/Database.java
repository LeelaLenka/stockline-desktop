package com.bharat.stockline.db;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Opens JDBC connections and installs the schema */
public class Database {

    private final DbConfig config;

    public Database(DbConfig config) {
        this.config = config;
    }

    /** A new connection; callers close it with try-with-resources */
    public Connection connect() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.user(), config.password());
    }

    /** Creates the database itself if the server allows it, so first-time setup is one click */
    public static void createDatabaseIfMissing(DbConfig c) throws SQLException {
        if (!c.database().matches("[A-Za-z0-9_]{1,64}")) throw new SQLException("Database names can only use letters, numbers and _.");
        String serverUrl = "jdbc:mysql://" + c.host() + ":" + c.port()
                + "/?allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=8000";
        try (Connection conn = DriverManager.getConnection(serverUrl, c.user(), c.password());
             Statement st = conn.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + c.database() + "` CHARACTER SET utf8mb4");
        }
    }

    /** Runs schema.sql: tables (if missing), the status view and the restock procedure */
    public void installSchema() throws SQLException {
        String script;
        try (InputStream in = Database.class.getResourceAsStream("schema.sql")) {
            if (in == null) throw new SQLException("schema.sql is missing from the app.");
            script = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new SQLException("Couldn't read schema.sql", e);
        }
        try (Connection conn = connect(); Statement st = conn.createStatement()) {
            for (String part : script.split("(?m)^-- @@\\s*$")) {
                String sql = stripComments(part).trim();
                if (!sql.isEmpty()) st.execute(sql);
            }
        }
    }

    /** Removes whole-line "--" comments (comments inside the procedure body are fine either way) */
    private static String stripComments(String sql) {
        StringBuilder out = new StringBuilder();
        for (String line : sql.split("\n")) {
            if (!line.trim().startsWith("--")) out.append(line).append('\n');
        }
        return out.toString();
    }
}
