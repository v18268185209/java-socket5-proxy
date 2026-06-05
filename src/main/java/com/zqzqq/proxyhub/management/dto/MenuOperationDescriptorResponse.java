package com.zqzqq.proxyhub.management.dto;

public record MenuOperationDescriptorResponse(
        String id,
        String title,
        String description,
        String optionToken,
        String riskLevel,
        boolean supportsArgument,
        String argumentHint,
        String defaultArgument,
        boolean supportsStdin,
        String defaultStdin) {
}
