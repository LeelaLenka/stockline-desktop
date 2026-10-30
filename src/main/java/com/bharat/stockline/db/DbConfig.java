package com.bharat.stockline.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * MySQL connection settings, saved in ~/.stockline/db.properties after the first-run setup.
 * Environment variables STOCKLINE_DB_URL / _USER / _PASSWORD override the file.
 */
public record DbConfig(String host, int port, String database, String user, String password) {

    private static final Path FILE = Path.of(System.getProperty("user.home"), ".stockline", "db.properties");

    public String jdbcUrl() {
        String env = System.getenv("STOCKLINE_DB_URL");
        if (env != null && !env.isBlank()) return env;
        // Store all times in UTC; allowPublicKeyRetrieval lets MySQL 8's default login work on a local server
        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
                + "&allowPublicKeyRetrieval=true&useSSL=false&connectTimeout=8000";
    }

    public static DbConfig defaults() {
        return new DbConfig("localhost", 3306, "stockline", "root", "");
    }

    /** Saved settings, or null if the app hasn't been set up on this computer yet */
    public static DbConfig load() {
        String envUser = System.getenv("STOCKLINE_DB_USER");
        if (System.getenv("STOCKLINE_DB_URL") != null && envUser != null) {
            String pw = System.getenv("STOCKLINE_DB_PASSWORD");
            return new DbConfig("", 0, "", envUser, pw == null ? "" : pw);
        }
        if (!Files.exists(FILE)) return null;
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(FILE)) {
            p.load(in);
            return new DbConfig(p.getProperty("host", "localhost"), Integer.parseInt(p.getProperty("port", "3306")),
                    p.getProperty("database", "stockline"), p.getProperty("user", "root"), p.getProperty("password", ""));
        } catch (IOException | NumberFormatException e) {
            return null;
        }
    }

    public void save() throws IOException {
        Files.createDirectories(FILE.getParent());
        Properties p = new Properties();
        p.setProperty("host", host);
        p.setProperty("port", String.valueOf(port));
        p.setProperty("database", database);
        p.setProperty("user", user);
        p.setProperty("password", password);
        try (OutputStream out = Files.newOutputStream(FILE)) {
            p.store(out, "Stockline MySQL connection");
        }
    }

    public static Path file() {
        return FILE;
    }
}
