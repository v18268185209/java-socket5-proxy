package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.management.dto.DiagnosticFileResponse;
import com.zqzqq.proxyhub.management.dto.DiagnosticsSummaryResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditRecordResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditSummaryResponse;
import com.zqzqq.proxyhub.management.dto.MenuJobResponse;
import com.zqzqq.proxyhub.management.dto.MenuJobsDeltaResponse;
import com.zqzqq.proxyhub.management.dto.MenuOperationDescriptorResponse;
import com.zqzqq.proxyhub.management.dto.MenuOperationRequest;
import com.zqzqq.proxyhub.management.service.MenuOpsService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/menu-ops")
public class MenuOpsController {

    private final MenuOpsService menuOpsService;

    public MenuOpsController(MenuOpsService menuOpsService) {
        this.menuOpsService = menuOpsService;
    }

    @GetMapping("/catalog")
    public List<MenuOperationDescriptorResponse> catalog() {
        return menuOpsService.catalog();
    }

    @GetMapping("/jobs")
    public List<MenuJobResponse> listRecentJobs(
            @RequestParam(name = "limit", defaultValue = "40") int limit,
            @RequestParam(name = "since", required = false) Long sinceEpochMillis) {
        Instant since = null;
        if (sinceEpochMillis != null && sinceEpochMillis > 0) {
            since = Instant.ofEpochMilli(sinceEpochMillis);
        }
        return menuOpsService.listRecent(limit, since);
    }

    @GetMapping("/jobs/delta")
    public MenuJobsDeltaResponse listRecentJobsDelta(
            @RequestParam(name = "limit", defaultValue = "40") int limit,
            @RequestParam(name = "since", required = false) Long sinceEpochMillis,
            @RequestParam(name = "compact", defaultValue = "false") boolean compact) {
        Instant since = null;
        if (sinceEpochMillis != null && sinceEpochMillis > 0) {
            since = Instant.ofEpochMilli(sinceEpochMillis);
        }
        return menuOpsService.listDelta(limit, since, compact);
    }

    @GetMapping("/jobs/{jobId}")
    public MenuJobResponse getJob(
            @PathVariable String jobId,
            @RequestParam(defaultValue = "true") boolean includeLogs,
            @RequestParam(name = "tail", required = false) Integer tailLines) {
        return menuOpsService.getJob(jobId, includeLogs, tailLines);
    }

    @GetMapping(value = "/jobs/{jobId}/logs.txt", produces = "text/plain;charset=UTF-8")
    public ResponseEntity<byte[]> downloadJobLogs(
            @PathVariable String jobId,
            @RequestParam(name = "tail", required = false) Integer tailLines) {
        String content = menuOpsService.exportJobLogs(jobId, tailLines);
        String safeJobId = jobId == null ? "unknown" : jobId.replaceAll("[^a-zA-Z0-9._-]", "_");
        String filename = "menu-job-" + safeJobId + ".log.txt";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .body(content.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/diagnostics")
    public List<DiagnosticFileResponse> listDiagnostics(
            @RequestParam(name = "limit", defaultValue = "80") int limit) {
        return menuOpsService.listDiagnostics(limit);
    }

    @GetMapping("/diagnostics/summary")
    public DiagnosticsSummaryResponse diagnosticsSummary() {
        return menuOpsService.diagnosticsSummary();
    }

    @GetMapping("/audit")
    public List<MenuAuditRecordResponse> auditRecords(
            @RequestParam(name = "limit", defaultValue = "120") int limit) {
        return menuOpsService.listAuditRecent(limit);
    }

    @GetMapping("/audit/summary")
    public MenuAuditSummaryResponse auditSummary(
            @RequestParam(name = "minutes", defaultValue = "240") int minutes) {
        return menuOpsService.auditSummary(minutes);
    }

    @GetMapping(value = "/diagnostics/{fileName}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> downloadDiagnostic(
            @PathVariable String fileName) {
        byte[] content = menuOpsService.readDiagnosticFile(fileName);
        String safeName = fileName == null ? "diagnostic.log" : fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + safeName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(content);
    }

    @DeleteMapping("/diagnostics/{fileName}")
    public ResponseEntity<Map<String, Object>> deleteDiagnostic(@PathVariable String fileName) {
        menuOpsService.deleteDiagnosticFile(fileName);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("deleted", true);
        body.put("fileName", fileName);
        body.put("timestamp", Instant.now());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/jobs")
    public MenuJobResponse submit(@Valid @RequestBody MenuOperationRequest request) {
        return menuOpsService.submit(request);
    }

    @PostMapping("/jobs/{jobId}/cancel")
    public MenuJobResponse cancel(@PathVariable String jobId) {
        return menuOpsService.cancel(jobId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        HttpStatus status = ex.getMessage() != null && ex.getMessage().startsWith("Job not found")
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(errorBody(ex.getMessage(), status));
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
