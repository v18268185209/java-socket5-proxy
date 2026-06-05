package com.zqzqq.proxyhub.core.security;

import com.zqzqq.proxyhub.config.ProxyProperties;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Authentication service - unified auth entry point.
 *
 * FIX: Simplified to use UserStore exclusively; removed redundant
 * AuthService.validateManagement() that duplicated UserStore logic.
 * Legacy config fallback removed since UserStore always initializes.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final ProxyProperties properties;
    private final UserStore userStore;

    public AuthService(ProxyProperties properties, UserStore userStore) {
        this.properties = properties;
        this.userStore = userStore;
    }

    public boolean isSocksAuthRequired() {
        return properties.getSocks().getAuth().isEnabled();
    }

    public boolean isHttpAuthRequired() {
        return properties.getHttp().getAuth().isEnabled();
    }

    /**
     * Validate SOCKS5 credentials against the multi-user store.
     * FIX: Removed redundant legacy fallback - UserStore is always available.
     */
    public boolean validateSocksUserPassword(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getSocks().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }

        if (username == null || username.isBlank()) {
            return false;
        }

        boolean ok = userStore.validateProxyUser(username, password);
        if (!ok) {
            log.debug("SOCKS5 auth failed for user={}", maskSensitive(username));
        }
        return ok;
    }

    /**
     * Validate HTTP Basic proxy credentials against the multi-user store.
     * FIX: Unified with validateSocksUserPassword via userStore.
     */
    public boolean validateHttpBasic(String username, String password) {
        ProxyProperties.AuthProperties auth = properties.getHttp().getAuth();
        if (!auth.isEnabled()) {
            return true;
        }

        if (username == null || username.isBlank()) {
            return false;
        }

        boolean ok = userStore.validateProxyUser(username, password);
        if (!ok) {
            log.debug("HTTP auth failed for user={}", maskSensitive(username));
        }
        return ok;
    }

    /**
     * Validate management UI login.
     * FIX: Delegates to UserStore exclusively.
     */
    public boolean validateManagementUser(String username, String password) {
        if (username == null || username.isBlank()) {
            return false;
        }
        return userStore.validateProxyUser(username, password);
    }

    private String maskSensitive(String value) {
        if (value == null || value.isBlank()) {
            return "***";
        }
        return value;
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
