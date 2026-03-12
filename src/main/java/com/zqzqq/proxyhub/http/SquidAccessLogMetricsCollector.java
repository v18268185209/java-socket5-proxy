package com.zqzqq.proxyhub.http;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.model.ProxyProtocol;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "proxy.http", name = "engine", havingValue = "squid")
public class SquidAccessLogMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(SquidAccessLogMetricsCollector.class);
    private static final int MAX_LINES_PER_COLLECT = 5000;

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;

    private long offset;
    private long skippedLines;

    public SquidAccessLogMetricsCollector(ProxyProperties properties, ProxyMetricsService metricsService) {
        this.properties = properties;
        this.metricsService = metricsService;
    }

    public synchronized void collectIncremental() {
        if (!properties.getHttp().isEnabled()) {
            return;
        }

        File workdir = new File(properties.getHttp().getSquid().getWorkdir());
        File accessLogFile = resolvePath(workdir, properties.getHttp().getSquid().getAccessLogPath());
        if (!accessLogFile.exists() || !accessLogFile.isFile()) {
            return;
        }

        try (RandomAccessFile raf = new RandomAccessFile(accessLogFile, "r")) {
            if (raf.length() < offset) {
                offset = 0L;
            }
            raf.seek(offset);

            int processed = 0;
            String line;
            while ((line = raf.readLine()) != null) {
                if (processed >= MAX_LINES_PER_COLLECT) {
                    skippedLines++;
                    continue;
                }
                processLine(line);
                processed++;
            }

            offset = raf.getFilePointer();
            if (skippedLines > 0) {
                metricsService.addEvent("WARN", "Squid access log collector skipped lines due to batch cap: " + skippedLines);
                skippedLines = 0;
            }
        } catch (IOException ex) {
            log.warn("Failed to parse squid access log: {}", accessLogFile.getAbsolutePath(), ex);
            metricsService.addEvent("WARN", "Failed to parse squid access log: " + ex.getMessage());
        }
    }

    private void processLine(String line) {
        if (line == null) {
            return;
        }
        String trimmed = line.trim();
        if (trimmed.isEmpty()) {
            return;
        }
        String[] parts = trimmed.split("\\s+");
        if (parts.length < 5) {
            return;
        }

        String resultToken = parts[3];
        String bytesToken = parts[4];
        String method = parts.length > 5 ? parts[5] : "";

        String resultCode = resultToken;
        int slash = resultToken.indexOf('/');
        int statusCode = -1;
        if (slash > 0 && slash < resultToken.length() - 1) {
            resultCode = resultToken.substring(0, slash);
            statusCode = parseInt(resultToken.substring(slash + 1), -1);
        }

        long bytes = parseLong(bytesToken, 0L);
        ProxyProtocol protocol = "CONNECT".equalsIgnoreCase(method) ? ProxyProtocol.HTTPS_TUNNEL : ProxyProtocol.HTTP;

        String normalizedResult = resultCode.toUpperCase(Locale.ROOT);
        boolean blocked = normalizedResult.contains("DENIED") || statusCode == 403;
        boolean authFailed = statusCode == 407;
        boolean connectFailed = normalizedResult.contains("CONNECT_FAIL")
                || normalizedResult.contains("ERR_CONNECT_FAIL")
                || statusCode == 502
                || statusCode == 503
                || statusCode == 504;

        metricsService.recordExternalHttpTransaction(protocol, bytes, blocked, authFailed, connectFailed);
    }

    private long parseLong(String value, long fallback) {
        try {
            return Long.parseLong(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private File resolvePath(File workdir, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(workdir, path);
    }
}
