package com.zqzqq.proxyhub.management.dto;

public record ProxyTestResponse(
        boolean success,
        String mode,
        String message,
        String targetUrl,
        String proxyAddress,
        Integer httpStatus,
        String responseSnippet,
        long durationMillis) {
}
