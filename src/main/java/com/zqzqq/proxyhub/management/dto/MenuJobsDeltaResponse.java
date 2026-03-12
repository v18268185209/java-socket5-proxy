package com.zqzqq.proxyhub.management.dto;

import java.util.List;

public record MenuJobsDeltaResponse(
        long serverNowEpochMillis,
        long nextSinceEpochMillis,
        int returnedCount,
        List<MenuJobResponse> items) {
}
