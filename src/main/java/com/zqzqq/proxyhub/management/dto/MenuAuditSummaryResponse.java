package com.zqzqq.proxyhub.management.dto;

import java.util.List;

public record MenuAuditSummaryResponse(
        int windowMinutes,
        int total,
        int success,
        int failed,
        int timeout,
        int canceled,
        int running,
        int pending,
        String topOperationId,
        List<MenuAuditOperationCountResponse> topOperations) {
}

