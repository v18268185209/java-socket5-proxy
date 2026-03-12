package com.zqzqq.proxyhub.management.service;

import com.zqzqq.proxyhub.config.MenuOpsProperties;
import com.zqzqq.proxyhub.management.dto.MenuAuditOperationCountResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditRecordResponse;
import com.zqzqq.proxyhub.management.dto.MenuAuditSummaryResponse;
import com.zqzqq.proxyhub.management.dto.MenuJobResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Service;

@Service
public class MenuOpsAuditStore {

    private static final long CLEANUP_INTERVAL_MILLIS = TimeUnit.MINUTES.toMillis(30);

    private final MenuOpsProperties properties;
    private final Object lock = new Object();

    private volatile boolean initialized = false;
    private volatile long lastCleanupEpochMillis = 0L;

    public MenuOpsAuditStore(MenuOpsProperties properties) {
        this.properties = properties;
    }

    public void record(MenuJobResponse job) {
        if (!isEnabled() || job == null) {
            return;
        }
        String jobId = normalize(job.jobId());
        if (jobId.isBlank() || "-".equals(jobId)) {
            return;
        }

        Instant eventTime = job.endedAt() != null ? job.endedAt()
                : (job.updatedAt() != null ? job.updatedAt() : job.createdAt());
        long epochMillis = eventTime == null ? System.currentTimeMillis() : eventTime.toEpochMilli();
        String createdAt = eventTime == null ? Instant.ofEpochMilli(epochMillis).toString() : eventTime.toString();

        synchronized (lock) {
            initIfNeeded();
            try (Connection conn = openConnection()) {
                String sql = "INSERT INTO menu_ops_audit("
                        + "job_id, operation_id, operation_title, risk_level, status, argument, duration_millis, "
                        + "exit_code, error_message, created_at, created_epoch_millis"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                        + "ON CONFLICT(job_id) DO UPDATE SET "
                        + "operation_id=excluded.operation_id, "
                        + "operation_title=excluded.operation_title, "
                        + "risk_level=excluded.risk_level, "
                        + "status=excluded.status, "
                        + "argument=excluded.argument, "
                        + "duration_millis=excluded.duration_millis, "
                        + "exit_code=excluded.exit_code, "
                        + "error_message=excluded.error_message, "
                        + "created_at=excluded.created_at, "
                        + "created_epoch_millis=excluded.created_epoch_millis";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, jobId);
                    ps.setString(2, normalize(job.operationId()));
                    ps.setString(3, normalize(job.operationTitle()));
                    ps.setString(4, normalize(job.riskLevel()));
                    ps.setString(5, normalize(job.status()));
                    ps.setString(6, normalize(job.argument()));
                    if (job.durationMillis() == null) {
                        ps.setObject(7, null);
                    } else {
                        ps.setLong(7, Math.max(0L, job.durationMillis()));
                    }
                    if (job.exitCode() == null) {
                        ps.setObject(8, null);
                    } else {
                        ps.setInt(8, job.exitCode());
                    }
                    ps.setString(9, normalize(job.errorMessage()));
                    ps.setString(10, createdAt);
                    ps.setLong(11, epochMillis);
                    ps.executeUpdate();
                }
                maybeCleanup(conn, System.currentTimeMillis());
            } catch (Exception ignore) {
                // Keep operation execution resilient even if sqlite is unavailable.
            }
        }
    }

    public List<MenuAuditRecordResponse> listRecent(int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(limit, 5000));
        synchronized (lock) {
            initIfNeeded();
            List<MenuAuditRecordResponse> list = new ArrayList<>();
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT id, job_id, operation_id, operation_title, risk_level, status, argument, duration_millis, "
                                 + "exit_code, error_message, created_at, created_epoch_millis "
                                 + "FROM menu_ops_audit ORDER BY created_epoch_millis DESC LIMIT ?")) {
                ps.setInt(1, size);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRecord(rs));
                    }
                }
            } catch (Exception ignore) {
                return List.of();
            }
            return list;
        }
    }

    public MenuAuditSummaryResponse summary(int windowMinutes, int maxRows) {
        if (!isEnabled()) {
            return new MenuAuditSummaryResponse(windowMinutes, 0, 0, 0, 0, 0, 0, 0, "-", List.of());
        }
        int minutes = Math.max(1, Math.min(windowMinutes, 7 * 24 * 60));
        int rows = Math.max(100, Math.min(maxRows, 100000));
        long fromEpoch = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutes);

        int total = 0;
        int success = 0;
        int failed = 0;
        int timeout = 0;
        int canceled = 0;
        int running = 0;
        int pending = 0;
        Map<String, Integer> opCounter = new LinkedHashMap<>();

        synchronized (lock) {
            initIfNeeded();
            try (Connection conn = openConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "SELECT operation_id, status FROM menu_ops_audit "
                                 + "WHERE created_epoch_millis >= ? "
                                 + "ORDER BY created_epoch_millis DESC LIMIT ?")) {
                ps.setLong(1, fromEpoch);
                ps.setInt(2, rows);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        total += 1;
                        String status = normalize(rs.getString("status")).toUpperCase();
                        switch (status) {
                            case "SUCCESS" -> success += 1;
                            case "FAILED" -> failed += 1;
                            case "TIMEOUT" -> timeout += 1;
                            case "CANCELED" -> canceled += 1;
                            case "RUNNING" -> running += 1;
                            default -> pending += 1;
                        }
                        String operationId = normalize(rs.getString("operation_id"));
                        opCounter.put(operationId, opCounter.getOrDefault(operationId, 0) + 1);
                    }
                }
            } catch (Exception ignore) {
                return new MenuAuditSummaryResponse(minutes, 0, 0, 0, 0, 0, 0, 0, "-", List.of());
            }
        }

        List<MenuAuditOperationCountResponse> topOperations = opCounter.entrySet().stream()
                .map(entry -> new MenuAuditOperationCountResponse(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(MenuAuditOperationCountResponse::count).reversed())
                .limit(8)
                .toList();
        String topOperationId = topOperations.isEmpty() ? "-" : topOperations.get(0).operationId();

        return new MenuAuditSummaryResponse(
                minutes,
                total,
                success,
                failed,
                timeout,
                canceled,
                running,
                pending,
                topOperationId,
                topOperations);
    }

    private MenuAuditRecordResponse mapRecord(ResultSet rs) throws Exception {
        Long duration = null;
        Object durationRaw = rs.getObject("duration_millis");
        if (durationRaw != null) {
            duration = rs.getLong("duration_millis");
        }
        Integer exitCode = null;
        Object exitRaw = rs.getObject("exit_code");
        if (exitRaw != null) {
            exitCode = rs.getInt("exit_code");
        }
        return new MenuAuditRecordResponse(
                rs.getLong("id"),
                rs.getString("job_id"),
                rs.getString("operation_id"),
                rs.getString("operation_title"),
                rs.getString("risk_level"),
                rs.getString("status"),
                rs.getString("argument"),
                duration,
                exitCode,
                rs.getString("error_message"),
                rs.getString("created_at"),
                rs.getLong("created_epoch_millis"));
    }

    private void initIfNeeded() {
        if (initialized) {
            return;
        }
        synchronized (lock) {
            if (initialized) {
                return;
            }
            try {
                Path dbPath = resolveDbPath();
                Path parent = dbPath.getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                try (Connection conn = openConnection(); Statement st = conn.createStatement()) {
                    st.execute("PRAGMA journal_mode=WAL");
                    st.execute("PRAGMA synchronous=NORMAL");
                    st.execute("PRAGMA busy_timeout=3000");
                    st.execute("CREATE TABLE IF NOT EXISTS menu_ops_audit ("
                            + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                            + "job_id TEXT NOT NULL UNIQUE,"
                            + "operation_id TEXT NOT NULL,"
                            + "operation_title TEXT NOT NULL,"
                            + "risk_level TEXT,"
                            + "status TEXT NOT NULL,"
                            + "argument TEXT,"
                            + "duration_millis INTEGER,"
                            + "exit_code INTEGER,"
                            + "error_message TEXT,"
                            + "created_at TEXT NOT NULL,"
                            + "created_epoch_millis INTEGER NOT NULL"
                            + ")");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_menu_ops_audit_epoch "
                            + "ON menu_ops_audit(created_epoch_millis DESC)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_menu_ops_audit_operation "
                            + "ON menu_ops_audit(operation_id)");
                    st.execute("CREATE INDEX IF NOT EXISTS idx_menu_ops_audit_status "
                            + "ON menu_ops_audit(status)");
                }
                initialized = true;
            } catch (Exception ignore) {
                initialized = false;
            }
        }
    }

    private void maybeCleanup(Connection conn, long now) {
        if ((now - lastCleanupEpochMillis) < CLEANUP_INTERVAL_MILLIS) {
            return;
        }
        int retentionDays = Math.max(1, properties.getAuditDbRetentionDays());
        int maxRows = Math.max(100, properties.getAuditDbMaxRows());
        long expireBefore = now - TimeUnit.DAYS.toMillis(retentionDays);
        try (PreparedStatement deleteByDate = conn.prepareStatement(
                "DELETE FROM menu_ops_audit WHERE created_epoch_millis < ?")) {
            deleteByDate.setLong(1, expireBefore);
            deleteByDate.executeUpdate();
        } catch (Exception ignore) {
            // ignore cleanup failure
        }
        try (PreparedStatement deleteByRows = conn.prepareStatement(
                "DELETE FROM menu_ops_audit WHERE id IN ("
                        + "SELECT id FROM menu_ops_audit ORDER BY created_epoch_millis DESC LIMIT -1 OFFSET ?"
                        + ")")) {
            deleteByRows.setInt(1, maxRows);
            deleteByRows.executeUpdate();
        } catch (Exception ignore) {
            // ignore cleanup failure
        }
        lastCleanupEpochMillis = now;
    }

    private Connection openConnection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + resolveDbPath());
    }

    private Path resolveDbPath() {
        String raw = properties.getAuditDbPath();
        String path = (raw == null || raw.isBlank()) ? "./logs/menu-ops-audit.db" : raw.trim();
        return Path.of(path).toAbsolutePath().normalize();
    }

    private boolean isEnabled() {
        return properties.isAuditDbEnabled();
    }

    private String normalize(String value) {
        if (value == null) {
            return "-";
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? "-" : trimmed;
    }
}

