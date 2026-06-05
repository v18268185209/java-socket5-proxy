package com.zqzqq.proxyhub.management.dto;

import java.time.Instant;
import java.util.List;

public record MenuJobResponse(
        String jobId,
        String operationId,
        String operationTitle,
        String riskLevel,
        String status,
        boolean finished,
        String commandLine,
        String argument,
        Instant createdAt,
        Instant updatedAt,
        Instant startedAt,
        Instant endedAt,
        Long durationMillis,
        Integer exitCode,
        String errorMessage,
        List<String> logs) {
}
