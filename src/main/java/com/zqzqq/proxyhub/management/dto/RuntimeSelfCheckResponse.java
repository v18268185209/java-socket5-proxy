package com.zqzqq.proxyhub.management.dto;

import java.time.Instant;
import java.util.List;

public record RuntimeSelfCheckResponse(
        Instant generatedAt,
        boolean runtimeRunning,
        String transport,
        List<String> activeProfiles,
        List<String> configSources,
        boolean aclEnabled,
        int maxConnectionsPerClient,
        ListenerCheckResponse socks,
        ListenerCheckResponse http,
        HttpEngineStatusResponse httpEngine,
        List<FailureReasonCountResponse> topFailureReasons) {

    public record ListenerCheckResponse(
            String name,
            boolean enabled,
            boolean running,
            String bindHost,
            int port,
            boolean authEnabled,
            boolean aclEnabled) {
    }

    public record HttpEngineStatusResponse(
            String engine,
            boolean external,
            boolean enabled,
            boolean running,
            boolean manageConfig,
            String executable,
            String configPath,
            String workdir,
            String accessLogPath) {
    }
}
