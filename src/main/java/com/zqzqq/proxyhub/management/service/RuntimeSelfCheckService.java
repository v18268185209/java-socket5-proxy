package com.zqzqq.proxyhub.management.service;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.ProxyRuntimeManager;
import com.zqzqq.proxyhub.core.ProxyServer;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.net.NettyTuningSupport;
import com.zqzqq.proxyhub.http.ExternalHttpProxyServer;
import com.zqzqq.proxyhub.management.dto.FailureReasonCountResponse;
import com.zqzqq.proxyhub.management.dto.RuntimeSelfCheckResponse;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

@Service
public class RuntimeSelfCheckService {

    private final ProxyProperties properties;
    private final ProxyRuntimeManager runtimeManager;
    private final List<ProxyServer> proxyServers;
    private final ProxyMetricsService metricsService;
    private final ConfigurableEnvironment environment;
    private final ObjectProvider<ExternalHttpProxyServer> externalHttpProxyServerProvider;

    public RuntimeSelfCheckService(
            ProxyProperties properties,
            ProxyRuntimeManager runtimeManager,
            List<ProxyServer> proxyServers,
            ProxyMetricsService metricsService,
            ConfigurableEnvironment environment,
            ObjectProvider<ExternalHttpProxyServer> externalHttpProxyServerProvider) {
        this.properties = properties;
        this.runtimeManager = runtimeManager;
        this.proxyServers = proxyServers;
        this.metricsService = metricsService;
        this.environment = environment;
        this.externalHttpProxyServerProvider = externalHttpProxyServerProvider;
    }

    public RuntimeSelfCheckResponse snapshot(int topLimit) {
        int effectiveTopLimit = Math.max(1, topLimit);
        RuntimeSelfCheckResponse.ListenerCheckResponse socks = buildListenerCheck(
                "SOCKS5",
                properties.getSocks(),
                findListenerRunning("SOCKS5:"));
        RuntimeSelfCheckResponse.ListenerCheckResponse http = buildListenerCheck(
                normalizeHttpListenerName(),
                properties.getHttp(),
                findHttpRunning());

        ExternalHttpProxyServer externalHttpProxyServer = externalHttpProxyServerProvider.getIfAvailable();
        RuntimeSelfCheckResponse.HttpEngineStatusResponse httpEngine = new RuntimeSelfCheckResponse.HttpEngineStatusResponse(
                properties.getHttp().getEngine(),
                isExternalHttpEngine(),
                properties.getHttp().isEnabled(),
                isExternalHttpEngine()
                        ? externalHttpProxyServer != null && externalHttpProxyServer.isRunning()
                        : http.running(),
                properties.getHttp().getSquid().isManageConfig(),
                isExternalHttpEngine() ? properties.getHttp().getSquid().getExecutable() : null,
                isExternalHttpEngine() ? properties.getHttp().getSquid().getConfigPath() : null,
                isExternalHttpEngine() ? properties.getHttp().getSquid().getWorkdir() : null,
                isExternalHttpEngine() ? properties.getHttp().getSquid().getAccessLogPath() : null);

        List<FailureReasonCountResponse> topFailureReasons = metricsService.snapshot().failureReasons().entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(effectiveTopLimit)
                .map(entry -> new FailureReasonCountResponse(entry.getKey(), entry.getValue()))
                .toList();

        return new RuntimeSelfCheckResponse(
                Instant.now(),
                runtimeManager.isRunning(),
                NettyTuningSupport.transportName(properties),
                Arrays.asList(environment.getActiveProfiles()),
                resolveConfigSources(),
                properties.getAcl().isEnabled(),
                properties.getPerformance().getMaxConnectionsPerClient(),
                socks,
                http,
                httpEngine,
                topFailureReasons);
    }

    private RuntimeSelfCheckResponse.ListenerCheckResponse buildListenerCheck(
            String name,
            ProxyProperties.ListenerProperties listener,
            boolean running) {
        return new RuntimeSelfCheckResponse.ListenerCheckResponse(
                name,
                listener.isEnabled(),
                listener.isEnabled() && running,
                listener.getBindHost(),
                listener.getPort(),
                listener.getAuth().isEnabled(),
                properties.getAcl().isEnabled());
    }

    private boolean findListenerRunning(String namePrefix) {
        return proxyServers.stream()
                .filter(server -> server.name() != null && server.name().startsWith(namePrefix))
                .anyMatch(ProxyServer::isRunning);
    }

    private boolean findHttpRunning() {
        String prefix = isExternalHttpEngine() ? "HTTP-EXTERNAL:" : "HTTP:";
        return findListenerRunning(prefix);
    }

    private boolean isExternalHttpEngine() {
        return "squid".equalsIgnoreCase(properties.getHttp().getEngine());
    }

    private String normalizeHttpListenerName() {
        return isExternalHttpEngine()
                ? "HTTP-EXTERNAL"
                : "HTTP";
    }

    private List<String> resolveConfigSources() {
        return StreamSupport.stream(environment.getPropertySources().spliterator(), false)
                .map(PropertySource::getName)
                .filter(this::isRelevantConfigSource)
                .map(this::normalizeConfigSourceName)
                .distinct()
                .toList();
    }

    private boolean isRelevantConfigSource(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.startsWith("config resource '")
                || lower.contains("applicationconfig")
                || "commandlineargs".equals(lower)
                || "systemproperties".equals(lower)
                || "systemenvironment".equals(lower)
                || lower.contains("inlined test properties");
    }

    private String normalizeConfigSourceName(String name) {
        return name.replace('\\', '/');
    }
}
