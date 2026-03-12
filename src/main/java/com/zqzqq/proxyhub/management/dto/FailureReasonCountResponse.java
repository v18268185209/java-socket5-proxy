package com.zqzqq.proxyhub.management.dto;

public record FailureReasonCountResponse(
        String reason,
        long count) {
}
