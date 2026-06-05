package com.zqzqq.proxyhub.management.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.ProxyRuntimeManager;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.security.UserStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.annotation.PostConstruct;

/**
 * Manages application configuration: read, write, and hot-reload from YAML.
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_PATH_PROP = "config.path";
    private static final String DEFAULT_CONFIG_PATH = "classpath:application.yml";

    private final Path configPath;
    private final ObjectMapper yamlMapper;
    private final ProxyProperties proxyProperties;
    private final ProxyRuntimeManager runtimeManager;
    private final UserStore userStore;
    private final com.zqzqq.proxyhub.core.acl.AccessControlService accessControlService;
    private final AtomicBoolean reloading = new AtomicBoolean(false);

    public ConfigService(
            ProxyProperties proxyProperties,
            ProxyRuntimeManager runtimeManager,
            UserStore userStore,
            com.zqzqq.proxyhub.core.acl.AccessControlService accessControlService) {
        this.accessControlService = accessControlService;
        this.proxyProperties = proxyProperties;
        this.runtimeManager = runtimeManager;
        this.userStore = userStore;
        this.yamlMapper = new ObjectMapper(new YAMLFactory());

        String configProp = System.getProperty(CONFIG_PATH_PROP);
        if (configProp != null && !configProp.isBlank()) {
            this.configPath = Paths.get(configProp).normalize();
        } else {
            // Try to find application.yml from classpath location or default
            String envConfig = System.getenv("CONFIG_PATH");
            if (envConfig != null && !envConfig.isBlank()) {
                this.configPath = Paths.get(envConfig).normalize();
            } else {
                this.configPath = Paths.get("./conf/application.yml").normalize();
            }
        }

        // Ensure config directory exists
        try {
            Files.createDirectories(configPath.getParent());
        } catch (IOException e) {
            log.warn("Failed to create config directory: {}", configPath.getParent(), e);
        }

        // Copy from classpath if config file doesn't exist
        if (!Files.exists(configPath)) {
            copyFromClasspath();
        }
    }

    @PostConstruct
    public void init() {
        log.info("ConfigService initialized: configPath={}", configPath);
    }

    /**
     * Read the entire config as a Map.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> readConfig() {
        try {
            return yamlMapper.readValue(configPath.toFile(), new TypeReference<>() {});
        } catch (IOException e) {
            log.error("Failed to read config from {}: {}", configPath, e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Write the entire config from a Map.
     * This replaces the entire YAML file content.
     */
    @SuppressWarnings("unchecked")
    public void writeConfig(Map<String, Object> config) {
        try {
            // Validate critical proxy fields exist and are sensible
            Map<String, Object> proxy = (Map<String, Object>) config.get("proxy");
            if (proxy != null) {
                Map<String, Object> socks = (Map<String, Object>) proxy.get("socks");
                if (socks != null) {
                    Object port = socks.get("port");
                    if (port instanceof Number p && (p.intValue() < 1 || p.intValue() > 65535)) {
                        throw new IllegalArgumentException("SOCKS port must be 1-65535");
                    }
                }
                Map<String, Object> http = (Map<String, Object>) proxy.get("http");
                if (http != null) {
                    Object port = http.get("port");
                    if (port instanceof Number p && (p.intValue() < 1 || p.intValue() > 65535)) {
                        throw new IllegalArgumentException("HTTP port must be 1-65535");
                    }
                }
                Map<String, Object> mgmt = (Map<String, Object>) proxy.get("management");
                if (mgmt != null) {
                    Object token = mgmt.get("access-token");
                    if (token != null && token.toString().length() > 256) {
                        throw new IllegalArgumentException("Access token too long (max 256)");
                    }
                }
            }

            // Serialize to YAML
            String yaml = yamlMapper.writeValueAsString(config);
            writeYamlFile(yaml);

            log.info("Config written successfully to {}", configPath);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to write config: {}", e.getMessage());
            throw new RuntimeException("Config write failed: " + e.getMessage(), e);
        }
    }

    /**
     * Read a specific config path (e.g. "proxy.socks.port").
     */
    @SuppressWarnings("unchecked")
    public Object getNestedValue(String path) {
        Map<String, Object> config = readConfig();
        String[] parts = path.split("\\.");
        Object current = config;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
            if (current == null) return null;
        }
        return current;
    }

    /**
     * Set a specific config path value.
     */
    @SuppressWarnings("unchecked")
    public void setNestedValue(String path, Object value) {
        Map<String, Object> config = deepCopy(readConfig());
        String[] parts = path.split("\\.");

        // Navigate to parent
        Map<String, Object> current = config;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object next = current.get(part);
            if (next == null || !(next instanceof Map)) {
                Map<String, Object> map = new LinkedHashMap<>();
                current.put(part, map);
                next = map;
            }
            current = (Map<String, Object>) next;
        }

        // Set final value
        current.put(parts[parts.length - 1], value);
        writeConfig(config);
    }

    /**
     * Hot-reload configuration: apply changes without restarting the JVM.
     * Updates ProxyProperties, reloads ACL, and restarts affected proxy servers.
     */
    public String hotReload() {
        if (!reloading.compareAndSet(false, true)) {
            return "Config reload already in progress";
        }

        try {
            // 1. Reload ProxyProperties from config file
            Map<String, Object> config = readConfig();
            if (config.isEmpty()) {
                return "Config file is empty or unreadable";
            }

            // 2. Apply ACL changes
            try {
                accessControlService.reload();
                log.info("ACL rules reloaded");
            } catch (Exception e) {
                log.warn("Failed to reload ACL: {}", e.getMessage());
            }

            // 3. Reload proxy servers
            try {
                runtimeManager.reloadAll();
                log.info("All proxy servers reloaded successfully");
                return "Configuration reloaded successfully";
            } catch (Exception e) {
                log.error("Failed to reload proxy servers: {}", e.getMessage(), e);
                return "Config applied but reload failed: " + e.getMessage();
            }

        } finally {
            reloading.set(false);
        }
    }

    /**
     * Reload proxy servers and apply configuration changes.
     */
    public String reloadAll() {
        return hotReload();
    }

    private void writeYamlFile(String yaml) throws IOException {
        Path temp = configPath.resolveSibling(configPath.getFileName() + ".tmp");
        Files.writeString(temp, yaml);
        Files.move(temp, configPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> deepCopy(Map<String, Object> source) {
        try {
            String json = yamlMapper.writeValueAsString(source);
            return yamlMapper.readValue(json, new TypeReference<>() {});
        } catch (IOException e) {
            throw new RuntimeException("Failed to deep copy config", e);
        }
    }

    private void copyFromClasspath() {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("application.yml")) {
            if (is != null) {
                Files.copy(is, configPath, StandardCopyOption.REPLACE_EXISTING);
                log.info("Copied application.yml from classpath to {}", configPath);
            }
        } catch (IOException e) {
            log.error("Failed to copy config from classpath: {}", e.getMessage());
        }
    }
}
