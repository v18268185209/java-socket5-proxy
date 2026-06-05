package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.management.service.ConfigService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REST API for reading, writing, and hot-reloading configuration.
 */
@RestController
@RequestMapping("/api/v1/config")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    /**
     * GET /api/v1/config - Get the entire configuration as a nested map.
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> config = configService.readConfig();
        return ResponseEntity.ok(config);
    }

    /**
     * PUT /api/v1/config - Replace the entire configuration.
     */
    @PutMapping
    public ResponseEntity<Map<String, String>> setConfig(@RequestBody Map<String, Object> config) {
        configService.writeConfig(config);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "Config written. Call POST /api/v1/config/reload to apply.");
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/config/{path} - Get a nested config value by path.
     * Example: GET /api/v1/config/proxy/socks/port
     */
    @GetMapping("/{path:.+}")
    public ResponseEntity<Object> getConfigValue(@PathVariable String path) {
        Object value = configService.getNestedValue(path);
        if (value != null) {
            return ResponseEntity.ok(value);
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * PATCH /api/v1/config/{path} - Set a single config value.
     */
    @PatchMapping("/{path:.+}")
    public ResponseEntity<Map<String, String>> setConfigValue(
            @PathVariable String path,
            @RequestBody Map<String, Object> body) {
        Object value = body.get("value");
        configService.setNestedValue(path, value);
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "Config updated. Call POST /api/v1/config/reload to apply.");
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/config/reload - Hot reload all proxy servers.
     */
    @PostMapping("/reload")
    public ResponseEntity<Map<String, String>> reloadConfig() {
        String result = configService.hotReload();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("result", result);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/config/reload-all - Alias for reload.
     */
    @PostMapping("/reload-all")
    public ResponseEntity<Map<String, String>> reloadAll() {
        String result = configService.reloadAll();
        Map<String, String> response = new LinkedHashMap<>();
        response.put("result", result);
        return ResponseEntity.ok(response);
    }
}
