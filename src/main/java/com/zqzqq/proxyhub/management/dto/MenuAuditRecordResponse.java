package com.zqzqq.proxyhub.management.dto;

public record MenuAuditRecordResponse(
        long id,
        String jobId,
        String operationId,
        String operationTitle,
        String riskLevel,
        String status,
        String argument,
        Long durationMillis,
        Integer exitCode,
        String errorMessage,
        String createdAt,
        long createdEpochMillis) {
}

