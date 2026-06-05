package com.zqzqq.proxyhub.management.dto;

import java.time.Instant;

public record DiagnosticsSummaryResponse(
        String directory,
        boolean exists,
        int fileCount,
        long totalBytes,
        Instant latestModifiedAt,
        int retentionDays,
        int maxFiles,
        int maxDownloadBytes) {
}
