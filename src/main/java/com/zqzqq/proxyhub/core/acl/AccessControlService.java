package com.zqzqq.proxyhub.core.acl;

import com.zqzqq.proxyhub.config.ProxyProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;

@Service
public class AccessControlService {

    private final ProxyProperties properties;
    private volatile List<CidrMatcher> allowCidrs = new ArrayList<>();
    private final ConcurrentHashMap<String, AtomicInteger> activeClientConnections = new ConcurrentHashMap<>();

    public AccessControlService(ProxyProperties properties) {
        this.properties = properties;
        reload();
    }

    public synchronized void reload() {
        List<CidrMatcher> matchers = new ArrayList<>();
        for (String cidr : properties.getAcl().getAllowClientCidrs()) {
            try {
                matchers.add(new CidrMatcher(cidr));
            } catch (Exception ignore) {
                // invalid CIDR is ignored and can be observed from startup logs/health checks
            }
        }
        this.allowCidrs = matchers;
    }

    public boolean isClientAllowed(String clientIp) {
        if (!properties.getAcl().isEnabled()) {
            return true;
        }
        if (allowCidrs.isEmpty()) {
            return true;
        }
        for (CidrMatcher matcher : allowCidrs) {
            if (matcher.matches(clientIp)) {
                return true;
            }
        }
        return false;
    }

    public boolean isTargetAllowed(String host, int port) {
        if (!properties.getAcl().isEnabled()) {
            return true;
        }
        for (Integer denyPort : properties.getAcl().getDenyTargetPorts()) {
            if (denyPort != null && denyPort == port) {
                return false;
            }
        }
        if (host == null || host.isBlank()) {
            return true;
        }
        String normalized = host.toLowerCase();
        for (String denyRule : properties.getAcl().getDenyTargetHosts()) {
            if (denyRule == null || denyRule.isBlank()) {
                continue;
            }
            String rule = denyRule.trim().toLowerCase();
            if (rule.startsWith("*.") && normalized.endsWith(rule.substring(1))) {
                return false;
            }
            if (rule.equals(normalized)) {
                return false;
            }
        }
        return true;
    }

    public boolean tryAcquireClientConnection(String clientIp) {
        int maxPerClient = properties.getPerformance().getMaxConnectionsPerClient();
        if (maxPerClient <= 0 || clientIp == null || clientIp.isBlank()) {
            return true;
        }
        AtomicInteger counter = activeClientConnections.computeIfAbsent(clientIp, k -> new AtomicInteger());
        while (true) {
            int current = counter.get();
            if (current >= maxPerClient) {
                return false;
            }
            if (counter.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void releaseClientConnection(String clientIp) {
        int maxPerClient = properties.getPerformance().getMaxConnectionsPerClient();
        if (maxPerClient <= 0 || clientIp == null || clientIp.isBlank()) {
            return;
        }
        AtomicInteger counter = activeClientConnections.get(clientIp);
        if (counter == null) {
            return;
        }
        int value = counter.updateAndGet(v -> v > 0 ? v - 1 : 0);
        if (value == 0) {
            activeClientConnections.remove(clientIp, counter);
        }
    }
}
