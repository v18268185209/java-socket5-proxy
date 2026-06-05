package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.management.dto.ProxyTestRequest;
import com.zqzqq.proxyhub.management.dto.ProxyTestResponse;
import com.zqzqq.proxyhub.management.dto.ScenarioProbeRequest;
import com.zqzqq.proxyhub.management.dto.ScenarioProbeResponse;
import com.zqzqq.proxyhub.management.service.ProxyProbeService;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

@RestController
@RequestMapping("/api/v1/tools")
public class ProxyToolController {

    private final ProxyProbeService probeService;

    public ProxyToolController(ProxyProbeService probeService) {
        this.probeService = probeService;
    }

    @PostMapping("/proxy-test")
    public ProxyTestResponse proxyTest(@Valid @RequestBody ProxyTestRequest request) {
        return probeService.runProbe(request);
    }

    @PostMapping("/scenario-test")
    public ScenarioProbeResponse scenarioTest(@Valid @RequestBody ScenarioProbeRequest request) {
        return probeService.runScenarioProbe(request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(errorBody(ex.getMessage(), HttpStatus.BAD_REQUEST));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorBody(ex.getMessage(), HttpStatus.CONFLICT));
    }

    private Map<String, Object> errorBody(String message, HttpStatus status) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null ? "Request failed" : message);
        return body;
    }
}
