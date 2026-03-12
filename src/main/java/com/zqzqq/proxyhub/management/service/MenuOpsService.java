package com.zqzqq.proxyhub.management.service;

import com.zqzqq.proxyhub.config.MenuOpsProperties;
import com.zqzqq.proxyhub.management.dto.DiagnosticFileResponse;
import com.zqzqq.proxyhub.management.dto.DiagnosticsSummaryResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditRecordResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditSummaryResponse;
import com.zqzqq.proxyhub.management.dto.MenuJobResponse;
import com.zqzqq.proxyhub.management.dto.MenuJobsDeltaResponse;
import com.zqzqq.proxyhub.management.dto.MenuOperationDescriptorResponse;
import com.zqzqq.proxyhub.management.dto.MenuOperationRequest;
import jakarta.annotation.PreDestroy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;

@Service
public class MenuOpsService {

    private static final Pattern DIAG_FILE_NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,180}$");

    private final MenuOpsProperties properties;
    private final MenuOpsAuditStore auditStore;

    public MenuOpsService(MenuOpsProperties properties, MenuOpsAuditStore auditStore) {
        this.properties = properties;
        this.auditStore = auditStore;
    }

    public List<MenuOperationDescriptorResponse> catalog() {
        return List.of();
    }

    public List<MenuJobResponse> listRecent() {
        return List.of();
    }

    public List<MenuJobResponse> listRecent(int limit) {
        return List.of();
    }

    public List<MenuJobResponse> listRecent(int limit, Instant sinceExclusive) {
        return List.of();
    }

    public MenuJobsDeltaResponse listDelta(int limit, Instant sinceExclusive) {
        return listDelta(limit, sinceExclusive, false);
    }

    public MenuJobsDeltaResponse listDelta(int limit, Instant sinceExclusive, boolean compact) {
        long now = System.currentTimeMillis();
        long nextSince = sinceExclusive == null ? 0L : sinceExclusive.toEpochMilli();
        return new MenuJobsDeltaResponse(now, nextSince, 0, List.of());
    }

    public List<MenuAuditRecordResponse> listAuditRecent(int limit) {
        return auditStore.listRecent(limit);
    }

    public MenuAuditSummaryResponse auditSummary(int windowMinutes) {
        return auditStore.summary(windowMinutes, 10000);
    }

    public MenuJobResponse getJob(String jobId, boolean includeLogs) {
        return getJob(jobId, includeLogs, null);
    }

    public MenuJobResponse getJob(String jobId, boolean includeLogs, Integer tailLines) {
        throw new IllegalArgumentException("Job not found: " + safeJobId(jobId));
    }

    public String exportJobLogs(String jobId, Integer tailLines) {
        throw new IllegalArgumentException("Job not found: " + safeJobId(jobId));
    }

    public List<DiagnosticFileResponse> listDiagnostics(int limit) {
        int effectiveLimit = Math.max(1, Math.min(limit, 5000));
        Path dir = diagnosticsDir();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<DiagnosticFileResponse> items = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(this::lastModified).reversed())
                    .limit(effectiveLimit)
                    .forEach(path -> {
                        try {
                            items.add(new DiagnosticFileResponse(
                                    path.getFileName().toString(),
                                    Files.size(path),
                                    Files.getLastModifiedTime(path).toInstant()));
                        } catch (Exception ignore) {
                            // Ignore one broken file and keep listing others.
                        }
                    });
        } catch (Exception ignore) {
            return List.of();
        }
        return items;
    }

    public byte[] readDiagnosticFile(String fileName) {
        Path target = resolveDiagnosticFile(fileName);
        try {
            long max = Math.max(0, properties.getDiagnosticsMaxDownloadBytes());
            long size = Files.size(target);
            if (max > 0 && size > max) {
                throw new IllegalArgumentException("Diagnostic file is too large");
            }
            return Files.readAllBytes(target);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalArgumentException("Diagnostic file not found: " + fileName);
        }
    }

    public void deleteDiagnosticFile(String fileName) {
        Path target = resolveDiagnosticFile(fileName);
        try {
            Files.deleteIfExists(target);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Diagnostic file not found: " + fileName);
        }
    }

    public DiagnosticsSummaryResponse diagnosticsSummary() {
        Path dir = diagnosticsDir();
        boolean exists = Files.isDirectory(dir);
        if (!exists) {
            return new DiagnosticsSummaryResponse(
                    dir.toString(),
                    false,
                    0,
                    0L,
                    null,
                    properties.getDiagnosticsRetentionDays(),
                    properties.getDiagnosticsMaxFiles(),
                    properties.getDiagnosticsMaxDownloadBytes());
        }

        int count = 0;
        long totalBytes = 0L;
        Instant latest = null;
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> files = stream.filter(Files::isRegularFile).toList();
            count = files.size();
            for (Path path : files) {
                try {
                    totalBytes += Files.size(path);
                    Instant modified = Files.getLastModifiedTime(path).toInstant();
                    if (latest == null || modified.isAfter(latest)) {
                        latest = modified;
                    }
                } catch (Exception ignore) {
                    // Keep summary resilient if one file fails.
                }
            }
        } catch (Exception ignore) {
            // Keep defaults from above.
        }

        return new DiagnosticsSummaryResponse(
                dir.toString(),
                true,
                count,
                totalBytes,
                latest,
                properties.getDiagnosticsRetentionDays(),
                properties.getDiagnosticsMaxFiles(),
                properties.getDiagnosticsMaxDownloadBytes());
    }

    public MenuJobResponse submit(MenuOperationRequest request) {
        throw new IllegalStateException("Menu operations are disabled");
    }

    public MenuJobResponse cancel(String jobId) {
        throw new IllegalArgumentException("Job not found: " + safeJobId(jobId));
    }

    @PreDestroy
    public void shutdown() {
        // no async workers in simplified mode
    }

    private Path diagnosticsDir() {
        String raw = properties.getDiagnosticsDir();
        String path = (raw == null || raw.isBlank()) ? "./logs/diagnostics" : raw.trim();
        return Path.of(path).toAbsolutePath().normalize();
    }

    private Path resolveDiagnosticFile(String fileName) {
        String normalized = fileName == null ? "" : fileName.trim();
        if (!DIAG_FILE_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Invalid diagnostic file name");
        }
        Path target = diagnosticsDir().resolve(normalized).normalize();
        if (!target.startsWith(diagnosticsDir())) {
            throw new IllegalArgumentException("Invalid diagnostic file path");
        }
        return target;
    }

    private FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (Exception ex) {
            return FileTime.fromMillis(0L);
        }
    }

    private String safeJobId(String jobId) {
        if (jobId == null || jobId.isBlank()) {
            return "-";
        }
        return jobId.trim();
    }
}
