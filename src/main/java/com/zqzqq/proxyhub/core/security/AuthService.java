package com.zqzqq.proxyhub.core.security;

import com.zqzqq.proxyhub.config.ProxyProperties;
import java.util.List;
import org.slf4j.Logger;
import java.util.List;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authentication service that supports both legacy single-user config
 * and the new multi-user SQLite store.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ProxyProperties properties;
    private final UserStore userStore;

    private final boolean userStoreAvailable;

    public AuthService(ProxyProperties properties, UserStore userStore) {
        this.properties = properties;
        this.userStore = userStore;
        // UserStore is always available after initialization
        this.userStoreAvailable = true;
    }

    public boolean isSocksAuthRequired() {
        return properties.getSocks().getAuth().isEnabled();
    }

    public boolean isHttpAuthRequired() {
        return properties.getHttp().getAuth().isEnabled();
    }

    /**
     * Validate SOCKS5 credentials using multi-user store (with legacy fallback).
     */
    public boolean validateSocksUserPassword(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getSocks().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }

        if (username == null || username.isBlank()) {
            return false;
        }

        // Try multi-user store first
        if (userStore != null && userHasRecords()) {
            boolean ok = userStore.validateProxyUser(username, password);
            log.debug("SOCKS5 auth attempt: user={}, store=multi, result={}",
                    maskPassword(username, password), ok);
            return ok;
        }

        // Legacy fallback: config file
        return auth.getUsername().equals(username) && auth.getPassword().equals(password);
    }

    /**
     * Validate HTTP Basic proxy credentials using multi-user store (with legacy fallback).
     */
    public boolean validateHttpBasic(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getHttp().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }

        if (username == null || username.isBlank()) {
            return false;
        }

        // Try multi-user store first
        if (userStore != null && userHasRecords()) {
            boolean ok = userStore.validateProxyUser(username, password);
            log.debug("HTTP auth attempt: user={}, store=multi, result={}",
                    maskPassword(username, password), ok);
            return ok;
        }

        // Legacy fallback: config file
        return auth.getUsername().equals(username) && auth.getPassword().equals(password);
    }

    /**
     * Validate management UI login using mgmt_users table.
     */
    public boolean validateManagementUser(String username, String password) {
        if (username == null || username.isBlank()) {
            return false;
        }
        if (userStore == null) {
            // Fall back to config
            ProxyProperties.ManagementProperties mgmt = properties.getManagement();
            if (!mgmt.isAllowBasicAuth() || !mgmt.getBasic().isEnabled()) {
                return false;
            }
            return mgmt.getBasic().getUsername().equals(username)
                    && mgmt.getBasic().getPassword().equals(password);
        }
        return userStore.validateProxyUser(username, password);
    }

    /**
     * Check if the user store has any records (i.e. not a fresh DB).
     */
    private boolean userHasRecords() {
        if (userStore == null) return false;
        try {
            var users = userStore.listUsers();
            return !users.isEmpty();
        } catch (Exception e) {
            log.warn("Failed to check user store: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Mask password in log output for security.
     */
    private String maskPassword(String username, String password) {
        if (password == null) {
            return username + "@?";
        }
        return username + "@" + (password.length() <= 2 ? "**" : password.charAt(0) + "***");
    }

    // Expose store operations for management API
    public void upsertUser(String username, String plainPassword, boolean enabled) {
        if (userStore != null) userStore.upsertUser(username, plainPassword, enabled);
    }

    public void deleteUser(String username) {
        if (userStore != null) userStore.deleteUser(username);
    }

    public java.util.List<UserStore.UserRecord> listUsers() {
        if (userStore == null) return List.of();
        return userStore.listUsers();
    }
}
