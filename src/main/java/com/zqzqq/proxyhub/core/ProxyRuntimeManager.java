package com.zqzqq.proxyhub.core;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

@Service
public class ProxyRuntimeManager implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProxyRuntimeManager.class);

    private final ProxyProperties properties;
    private final List<ProxyServer> proxyServers;
    private final ProxyMetricsService metricsService;
    private final AtomicBoolean started = new AtomicBoolean(false);

    public ProxyRuntimeManager(
            ProxyProperties properties,
            List<ProxyServer> proxyServers,
            ProxyMetricsService metricsService) {
        this.properties = properties;
        this.proxyServers = proxyServers;
        this.metricsService = metricsService;
    }

    @Override
    public void run(ApplicationArguments args) {
        startAll();
    }

    public synchronized void startAll() {
        log.info("Effective proxy config: socks(enabled={}, bind={}, port={}, auth={}) | http(enabled={}, bind={}, port={}, engine={}, auth={}) | acl(enabled={}) | maxConnectionsPerClient={}",
                properties.getSocks().isEnabled(),
                properties.getSocks().getBindHost(),
                properties.getSocks().getPort(),
                properties.getSocks().getAuth().isEnabled(),
                properties.getHttp().isEnabled(),
                properties.getHttp().getBindHost(),
                properties.getHttp().getPort(),
                properties.getHttp().getEngine(),
                properties.getHttp().getAuth().isEnabled(),
                properties.getAcl().isEnabled(),
                properties.getPerformance().getMaxConnectionsPerClient());
        for (ProxyServer server : proxyServers) {
            try {
                boolean wasRunning = server.isRunning();
                server.start();
                boolean isRunning = server.isRunning();
                if (!wasRunning && isRunning) {
                    log.info("Started proxy listener: {}", server.name());
                } else if (!isRunning) {
                    log.info("Skipped proxy listener startup: {} (disabled or inactive)", server.name());
                }
            } catch (Exception e) {
                metricsService.addEvent("ERROR", "Failed to start " + server.name() + ": " + e.getMessage());
                metricsService.recordFailure(ProxyFailureReason.LISTENER_START_FAILED,
                        "Failed to start listener " + server.name() + ": " + e.getMessage());
                log.error("Failed to start listener {}", server.name(), e);
            }
        }
        started.set(proxyServers.stream().anyMatch(ProxyServer::isRunning));
    }

    public synchronized void stopAll() {
        for (ProxyServer server : proxyServers) {
            try {
                boolean wasRunning = server.isRunning();
                server.stop();
                if (wasRunning && !server.isRunning()) {
                    log.info("Stopped proxy listener: {}", server.name());
                }
            } catch (Exception e) {
                log.warn("Failed to stop listener {}", server.name(), e);
            }
        }
        started.set(proxyServers.stream().anyMatch(ProxyServer::isRunning));
    }

    public synchronized void restartAll() {
        stopAll();
        startAll();
    }

    public synchronized void reloadAll() {
        for (ProxyServer server : proxyServers) {
            if (server instanceof ReloadableProxyServer reloadable) {
                try {
                    boolean ok = reloadable.reload();
                    if (ok) {
                        log.info("Reloaded proxy listener: {}", server.name());
                    } else {
                        metricsService.recordFailure(ProxyFailureReason.LISTENER_RELOAD_FAILED,
                                "Reload returned false for listener " + server.name());
                        log.warn("Reload returned false for listener: {}", server.name());
                    }
                } catch (Exception e) {
                    metricsService.addEvent("ERROR", "Failed to reload " + server.name() + ": " + e.getMessage());
                    metricsService.recordFailure(ProxyFailureReason.LISTENER_RELOAD_FAILED,
                            "Failed to reload listener " + server.name() + ": " + e.getMessage());
                    log.error("Failed to reload listener {}", server.name(), e);
                }
            }
        }
    }

    public boolean isRunning() {
        boolean running = proxyServers.stream().anyMatch(ProxyServer::isRunning);
        started.set(running);
        return running;
    }

    @PreDestroy
    public void shutdown() {
        stopAll();
    }
}
