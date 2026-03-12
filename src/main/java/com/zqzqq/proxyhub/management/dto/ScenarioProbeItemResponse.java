package com.zqzqq.proxyhub.management.dto;

public record ScenarioProbeItemResponse(
        String scenario,
        String targetUrl,
        boolean success,
        String message,
        Integer httpStatus,
        long durationMillis) {
}
