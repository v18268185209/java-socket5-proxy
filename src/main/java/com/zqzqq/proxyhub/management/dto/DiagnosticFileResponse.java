package com.zqzqq.proxyhub.management.dto;

import java.time.Instant;

public record DiagnosticFileResponse(
        String name,
        long sizeBytes,
        Instant modifiedAt) {
}
