package com.zqzqq.proxyhub.core.security;
import org.springframework.stereotype.Component;

import com.zqzqq.proxyhub.config.ProxyProperties;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SQLite-backed multi-user authentication store.
 * Supports per-proxy-user (SOCKS5/HTTP) accounts and optional management accounts.
 *
 * FIX: Uses openDbConnection() for each operation (short-lived connections)
 * to avoid thread-safety issues with a single shared Connection.
 * SQLite JDBC driver uses MUTEX_SINGLE connection mode by default,
 * so concurrent operations are serialized safely.
 */
@Component
public class UserStore implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(UserStore.class);
    private static final String CREATE_USERS_SQL = """
            CREATE TABLE IF NOT EXISTS proxy_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                enabled INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                UNIQUE(username)
            )""";

    private static final String CREATE_MGMT_USERS_SQL = """
            CREATE TABLE IF NOT EXISTS mgmt_users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                username TEXT NOT NULL,
                password_hash TEXT NOT NULL,
                enabled INTEGER DEFAULT 1,
                created_at TEXT DEFAULT (datetime('now')),
                UNIQUE(username)
            )""";

    private final Path dbPath;
    final ConcurrentMap<String, UserRecord> cache = new ConcurrentHashMap<>();

    public record UserRecord(
            long id,
            String username,
            String passwordHash,
            boolean enabled,
            String createdAt
    ) {}

    public UserStore(ProxyProperties properties) {
        this.dbPath = Path.of(properties.getUsers().getStorePath()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(dbPath.getParent());
        } catch (Exception ignore) {
        }
        initDatabase(properties);
    }

    /**
     * Initialize users with a default admin account if the table is empty.
     * Each statement opens its own connection and auto-closes it.
     */
    private void initDatabase(ProxyProperties properties) {
        try (Connection conn = openDbConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_USERS_SQL);
                stmt.execute(CREATE_MGMT_USERS_SQL);
            }
        } catch (SQLException e) {
            log.error("Failed to initialize user database: {}", e.getMessage());
            return;
        }

        // Check if users table is empty
        long count = 0;
        try (Connection conn = openDbConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM proxy_users");
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                count = rs.getLong(1);
            }
        } catch (SQLException e) {
            log.error("Failed to check user table: {}", e.getMessage());
            return;
        }

        if (count == 0) {
            generateDefaultAdmin(properties);
        }
    }

    private void generateDefaultAdmin(ProxyProperties properties) {
        String mgmtBasic = properties.getManagement().getBasic().getUsername();
        String mgmtPassword = properties.getManagement().getBasic().getPassword();
        if (mgmtBasic == null || mgmtBasic.isBlank()) mgmtBasic = "mgmtadmin";
        if (mgmtPassword == null || mgmtPassword.isBlank()) mgmtPassword = "mgmtpassword";
        String hash = BCryptService.hashStatic(mgmtPassword);

        try (Connection conn = openDbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO proxy_users (username, password_hash) VALUES (?, ?)")) {
            ps.setString(1, mgmtBasic);
            ps.setString(2, hash);
            ps.executeUpdate();
            cache.put(mgmtBasic, new UserRecord(1, mgmtBasic, hash, true, Instant.now().toString()));
        } catch (SQLException e) {
            log.error("Failed to create default admin: {}", e.getMessage());
            return;
        }

        // Also create default management user
        try (Connection conn = openDbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO mgmt_users (username, password_hash) VALUES (?, ?)")) {
            ps.setString(1, mgmtBasic);
            ps.setString(2, hash);
            ps.executeUpdate();
        } catch (SQLException e) {
            // Silently ignore — mgmt account is optional
        }

        // Print to stderr so operator sees the credential
        System.err.println("=================================================================");
        System.err.println("⚠️  DEFAULT CREDENTIALS GENERATED — PLEASE CHANGE IMMEDIATELY!");
        System.err.println("Username: " + mgmtBasic);
        System.err.println("Password: " + mgmtPassword);
        System.err.println("=================================================================");
        log.warn("Default admin account created: username={}", mgmtBasic);
    }

    /**
     * Opens a short-lived SQLite connection for a single operation.
     * FIX: Replaces the old single-Connection approach that had thread-safety issues.
     * SQLite JDBC MUTEX_SINGLE mode ensures concurrent calls are serialized safely.
     */
    private Connection openDbConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        conn.setAutoCommit(true);
        return conn;
    }

    /**
     * Validate credentials against the proxy user store.
     */
    public boolean validateProxyUser(String username, String password) {
        // Try cache first
        UserRecord record = cache.get(username);
        if (record == null) {
            record = lookupUser(username);
            if (record != null) {
                cache.put(username, record);
            }
        }

        if (record != null) {
            if (!record.enabled()) {
                log.debug("Account disabled: username={}", username);
                return false;
            }
            return BCryptService.matchesStatic(password, record.passwordHash());
        }

        return false;
    }

    private UserRecord lookupUser(String username) {
        try (Connection conn = openDbConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id, username, password_hash, enabled FROM proxy_users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String createdAt = null;
                    try { createdAt = rs.getString("created_at"); } catch (Exception ignore) {}
                    return new UserRecord(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getInt("enabled") == 1,
                            createdAt);
                }
            }
        } catch (SQLException e) {
            log.warn("Failed to look up user {}: {}", username, e.getMessage());
        }
        return null;
    }

    /**
     * Add or update a user. Creates the user if they don't exist, updates password if they do.
     */
    public void upsertUser(String username, String plainPassword, boolean enabled) {
        String hash = BCryptService.hashStatic(plainPassword);

        try (Connection conn = openDbConnection()) {
            // Check if user exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM proxy_users WHERE username = ?")) {
                ps.setString(1, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        // Update
                        try (PreparedStatement ps2 = conn.prepareStatement(
                                "UPDATE proxy_users SET password_hash = ?, enabled = ?, created_at = datetime('now') WHERE username = ?")) {
                            ps2.setString(1, hash);
                            ps2.setInt(2, enabled ? 1 : 0);
                            ps2.setString(3, username);
                            ps2.executeUpdate();
                        }
                    } else {
                        // Insert
                        try (PreparedStatement ps2 = conn.prepareStatement(
                                "INSERT INTO proxy_users (username, password_hash, enabled) VALUES (?, ?, ?)")) {
                            ps2.setString(1, username);
                            ps2.setString(2, hash);
                            ps2.setInt(3, enabled ? 1 : 0);
                            ps2.executeUpdate();
                        }
                    }
                }
            }
            cache.remove(username);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to upsert user: " + e.getMessage(), e);
        }
    }

    /**
     * List all proxy users (passwords excluded).
     */
    public List<UserRecord> listUsers() {
        List<UserRecord> result = new ArrayList<>();
        try (Connection conn = openDbConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id, username, password_hash, enabled FROM proxy_users")) {
            while (rs.next()) {
                String createdAt = null;
                try { createdAt = rs.getString("created_at"); } catch (Exception ignore) {}
                result.add(new UserRecord(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getInt("enabled") == 1,
                        createdAt));
            }
        } catch (SQLException e) {
            log.warn("Failed to list users: {}", e.getMessage());
        }
        return result;
    }

    /**
     * Delete a user by username.
     */
    public void deleteUser(String username) {
        try (Connection conn = openDbConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM proxy_users WHERE username = ?")) {
            ps.setString(1, username);
            ps.executeUpdate();
            cache.remove(username);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to delete user: " + e.getMessage(), e);
        }
    }

    /** Invalidate cache for a specific user or all users if username is null. */
    void invalidateCache(String username) {
        if (username != null) {
            cache.remove(username);
        } else {
            cache.clear();
        }
    }

    @Override
    public void close() {
        // No long-lived connection to close (each operation opens its own connection)
        cache.clear();
        log.debug("UserStore closed");
    }
}
