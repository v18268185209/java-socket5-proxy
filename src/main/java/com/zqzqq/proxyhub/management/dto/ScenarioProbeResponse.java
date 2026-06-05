package com.zqzqq.proxyhub.management.dto;

import java.util.List;

public record ScenarioProbeResponse(
        String mode,
        String proxyAddress,
        long durationMillis,
        List<ScenarioProbeItemResponse> items) {
}
