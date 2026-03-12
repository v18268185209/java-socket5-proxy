package com.zqzqq.proxyhub.core.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.model.ProxyProtocol;
import com.zqzqq.proxyhub.core.model.SessionStatus;
import org.junit.jupiter.api.Test;

class ProxyMetricsServiceTest {

    @Test
    void snapshotAggregatesFailureReasonsWithoutPerSessionNoiseByDefault() {
        ProxyProperties properties = new ProxyProperties();
        ProxyMetricsService service = new ProxyMetricsService(properties);

        String sessionId = service.openSession(ProxyProtocol.SOCKS5, "SOCKS5", "1.1.1.1:5000", "example.com:443");
        service.recordFailure(ProxyFailureReason.ACL_DENIED, "client blocked");
        service.closeSession(sessionId, SessionStatus.CLOSED);

        ProxyMetricsService.OverviewSnapshot snapshot = service.snapshot();

        assertEquals(0, snapshot.activeConnections());
        assertEquals(1L, snapshot.totalConnections());
        assertEquals(1L, snapshot.failureReasons().get(ProxyFailureReason.ACL_DENIED));
        assertEquals(1, snapshot.events().size());
        assertTrue(snapshot.events().get(0).getMessage().contains("client blocked"));
    }

    @Test
    void sessionLifecycleEventsCanBeEnabledExplicitly() {
        ProxyProperties properties = new ProxyProperties();
        properties.getDashboard().setRecordSessionLifecycleEvents(true);
        ProxyMetricsService service = new ProxyMetricsService(properties);

        String sessionId = service.openSession(ProxyProtocol.HTTP, "HTTP", "2.2.2.2:6000", "example.com:80");
        service.closeSession(sessionId, SessionStatus.CLOSED);

        ProxyMetricsService.OverviewSnapshot snapshot = service.snapshot();

        assertNotNull(sessionId);
        assertFalse(sessionId.isBlank());
        assertEquals(2, snapshot.events().size());
        assertTrue(snapshot.events().stream().anyMatch(event -> event.getMessage().contains("Session opened")));
        assertTrue(snapshot.events().stream().anyMatch(event -> event.getMessage().contains("Session closed")));
    }
}
