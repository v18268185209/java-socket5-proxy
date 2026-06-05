package com.zqzqq.proxyhub.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration for multi-user authentication store.
 */
@Validated
@ConfigurationProperties(prefix = "proxy.users")
public class UserProperties {

    @NotBlank
    private String storePath = "./logs/proxy-users.db";

    /**
     * When true, the legacy single-user config (username/password in auth block)
     * is still honored as a fallback for data migration scenarios.
     */
    private boolean legacyFallbackEnabled = true;

    public String getStorePath() {
        return storePath;
    }

    public void setStorePath(String storePath) {
        this.storePath = storePath;
    }

    public boolean isLegacyFallbackEnabled() {
        return legacyFallbackEnabled;
    }

    public void setLegacyFallbackEnabled(boolean legacyFallbackEnabled) {
        this.legacyFallbackEnabled = legacyFallbackEnabled;
    }

}
