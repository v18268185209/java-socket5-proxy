package com.zqzqq.proxyhub.management.api;

import com.zqzqq.proxyhub.core.ProxyRuntimeManager;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.http.SquidAccessLogMetricsCollector;
import com.zqzqq.proxyhub.management.dto.OverviewResponse;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/metrics")
public class MetricsController {

    private final ProxyMetricsService metricsService;
    private final ProxyRuntimeManager runtimeManager;
    private final ObjectProvider<SquidAccessLogMetricsCollector> squidCollectorProvider;

    public MetricsController(
            ProxyMetricsService metricsService,
            ProxyRuntimeManager runtimeManager,
            ObjectProvider<SquidAccessLogMetricsCollector> squidCollectorProvider) {
        this.metricsService = metricsService;
        this.runtimeManager = runtimeManager;
        this.squidCollectorProvider = squidCollectorProvider;
    }

    @GetMapping("/overview")
    public OverviewResponse overview() {
        SquidAccessLogMetricsCollector collector = squidCollectorProvider.getIfAvailable();
        if (collector != null) {
            collector.collectIncremental();
        }
        ProxyMetricsService.OverviewSnapshot snapshot = metricsService.snapshot();
        return new OverviewResponse(
                runtimeManager.isRunning(),
                snapshot.activeConnections(),
                snapshot.totalConnections(),
                snapshot.blockedConnections(),
                snapshot.authFailures(),
                snapshot.connectFailures(),
                snapshot.totalBytesFromClient(),
                snapshot.totalBytesFromTarget(),
                snapshot.socksConnections(),
                snapshot.httpConnections(),
                snapshot.httpsTunnelConnections(),
                snapshot.failureReasons(),
                snapshot.activeSessions(),
                snapshot.events());
    }
}
