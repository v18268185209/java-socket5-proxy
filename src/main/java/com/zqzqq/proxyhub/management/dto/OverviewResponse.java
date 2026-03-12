package com.zqzqq.proxyhub.management.dto;

import com.zqzqq.proxyhub.core.model.ConnectionSession;
import com.zqzqq.proxyhub.core.model.ProxyEvent;
import java.util.List;
import java.util.Map;

public record OverviewResponse(
        boolean running,
        int activeConnections,
        long totalConnections,
        long blockedConnections,
        long authFailures,
        long connectFailures,
        long totalBytesFromClient,
        long totalBytesFromTarget,
        long socksConnections,
        long httpConnections,
        long httpsTunnelConnections,
        Map<String, Long> failureReasons,
        List<ConnectionSession> activeSessions,
        List<ProxyEvent> events) {
}
