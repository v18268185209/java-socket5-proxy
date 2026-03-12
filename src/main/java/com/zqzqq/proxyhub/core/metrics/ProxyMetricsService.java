package com.zqzqq.proxyhub.core.metrics;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.model.ConnectionSession;
import com.zqzqq.proxyhub.core.model.ProxyEvent;
import com.zqzqq.proxyhub.core.model.ProxyProtocol;
import com.zqzqq.proxyhub.core.model.SessionStatus;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;

@Service
public class ProxyMetricsService {

    private final AtomicInteger activeConnections = new AtomicInteger();
    private final LongAdder totalConnections = new LongAdder();
    private final LongAdder blockedConnections = new LongAdder();
    private final LongAdder authFailures = new LongAdder();
    private final LongAdder connectFailures = new LongAdder();
    private final LongAdder totalBytesFromClient = new LongAdder();
    private final LongAdder totalBytesFromTarget = new LongAdder();
    private final LongAdder socksConnections = new LongAdder();
    private final LongAdder httpConnections = new LongAdder();
    private final LongAdder httpsTunnelConnections = new LongAdder();
    private final AtomicLong sessionSequence = new AtomicLong();

    private final Map<String, ConnectionSession> activeSessions = new ConcurrentHashMap<>();
    private final Map<String, LongAdder> failureReasons = new ConcurrentHashMap<>();
    private final Deque<ProxyEvent> recentEvents = new ArrayDeque<>();

    private final ProxyProperties properties;

    public ProxyMetricsService(ProxyProperties properties) {
        this.properties = properties;
    }

    public String openSession(ProxyProtocol protocol, String listener, String clientAddress, String targetAddress) {
        String sessionId = Long.toHexString(sessionSequence.incrementAndGet());
        ConnectionSession session = new ConnectionSession(sessionId, protocol, listener, clientAddress, targetAddress);
        activeSessions.put(sessionId, session);
        activeConnections.incrementAndGet();
        totalConnections.increment();
        switch (protocol) {
            case SOCKS5 -> socksConnections.increment();
            case HTTP -> httpConnections.increment();
            case HTTPS_TUNNEL -> httpsTunnelConnections.increment();
            default -> {
            }
        }
        if (properties.getDashboard().isRecordSessionLifecycleEvents()) {
            addEvent("INFO", "Session opened: " + protocol + " " + clientAddress + " -> " + targetAddress);
        }
        return sessionId;
    }

    public void closeSession(String sessionId, SessionStatus status) {
        if (sessionId == null) {
            return;
        }
        ConnectionSession session = activeSessions.remove(sessionId);
        if (session == null) {
            return;
        }
        session.close(status);
        activeConnections.decrementAndGet();
        if (properties.getDashboard().isRecordSessionLifecycleEvents()) {
            addEvent("INFO", "Session closed: " + session.getProtocol() + " " + session.getClientAddress()
                    + " -> " + session.getTargetAddress() + " status=" + status);
        }
    }

    public void markBlocked() {
        blockedConnections.increment();
    }

    public void markAuthFailure() {
        authFailures.increment();
    }

    public void markConnectFailure() {
        connectFailures.increment();
    }

    public void recordExternalHttpTransaction(
            ProxyProtocol protocol,
            long bytesFromTarget,
            boolean blocked,
            boolean authFailed,
            boolean connectFailed) {
        if (protocol != ProxyProtocol.HTTP && protocol != ProxyProtocol.HTTPS_TUNNEL) {
            return;
        }

        totalConnections.increment();
        if (protocol == ProxyProtocol.HTTP) {
            httpConnections.increment();
        } else {
            httpsTunnelConnections.increment();
        }

        if (bytesFromTarget > 0) {
            totalBytesFromTarget.add(bytesFromTarget);
        }
        if (blocked) {
            blockedConnections.increment();
        }
        if (authFailed) {
            authFailures.increment();
        }
        if (connectFailed) {
            connectFailures.increment();
        }
    }

    public void recordFromClient(String sessionId, long bytes) {
        if (sessionId == null || bytes <= 0) {
            return;
        }
        ConnectionSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.addBytesFromClient(bytes);
        }
        totalBytesFromClient.add(bytes);
    }

    public void recordFromTarget(String sessionId, long bytes) {
        if (sessionId == null || bytes <= 0) {
            return;
        }
        ConnectionSession session = activeSessions.get(sessionId);
        if (session != null) {
            session.addBytesFromTarget(bytes);
        }
        totalBytesFromTarget.add(bytes);
    }

    public void addEvent(String level, String message) {
        synchronized (recentEvents) {
            recentEvents.addFirst(new ProxyEvent(level, message));
            int max = properties.getDashboard().getMaxRecentEvents();
            while (recentEvents.size() > max) {
                recentEvents.removeLast();
            }
        }
    }

    public void recordFailure(String reason, String message) {
        if (reason == null || reason.isBlank()) {
            return;
        }
        failureReasons.computeIfAbsent(reason, key -> new LongAdder()).increment();
        if (message != null && !message.isBlank()) {
            addEvent("WARN", "[" + reason + "] " + message);
        }
    }

    public OverviewSnapshot snapshot() {
        List<ConnectionSession> sessions = activeSessions.values().stream()
                .sorted(Comparator.comparing(ConnectionSession::getStartTime).reversed())
                .limit(300)
                .toList();
        Map<String, Long> reasonSnapshot = new LinkedHashMap<>();
        failureReasons.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, LongAdder>>comparingLong(entry -> entry.getValue().sum())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> reasonSnapshot.put(entry.getKey(), entry.getValue().sum()));
        List<ProxyEvent> events;
        synchronized (recentEvents) {
            events = new ArrayList<>(recentEvents);
        }
        return new OverviewSnapshot(
                activeConnections.get(),
                totalConnections.sum(),
                blockedConnections.sum(),
                authFailures.sum(),
                connectFailures.sum(),
                totalBytesFromClient.sum(),
                totalBytesFromTarget.sum(),
                socksConnections.sum(),
                httpConnections.sum(),
                httpsTunnelConnections.sum(),
                reasonSnapshot,
                sessions,
                events
        );
    }

    public record OverviewSnapshot(
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
}
