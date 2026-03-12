package com.zqzqq.proxyhub.management.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.management.dto.RuntimeSelfCheckResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "proxy.socks.enabled=false",
                "proxy.http.enabled=false",
                "proxy.acl.enabled=true",
                "proxy.performance.max-connections-per-client=77"
        })
class RuntimeSelfCheckIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ProxyMetricsService metricsService;

    @Test
    void exposesRuntimeSelfCheckSnapshot() {
        metricsService.recordFailure(ProxyFailureReason.ACL_DENIED, "blocked once");
        metricsService.recordFailure(ProxyFailureReason.ACL_DENIED, "blocked twice");
        metricsService.recordFailure(ProxyFailureReason.UPSTREAM_CONNECT_FAILED, "upstream timeout");

        RuntimeSelfCheckResponse body = restTemplate.getForObject(
                "/api/v1/runtime/self-check?top=2",
                RuntimeSelfCheckResponse.class);

        assertNotNull(body);
        assertFalse(body.runtimeRunning());
        assertNotNull(body.transport());
        assertTrue(body.aclEnabled());
        assertEquals(77, body.maxConnectionsPerClient());
        assertNotNull(body.socks());
        assertFalse(body.socks().enabled());
        assertNotNull(body.http());
        assertFalse(body.http().enabled());
        assertNotNull(body.httpEngine());
        assertFalse(body.httpEngine().enabled());
        assertNotNull(body.configSources());
        assertTrue(body.configSources().stream().anyMatch(source -> source.toLowerCase().contains("application")
                || source.toLowerCase().contains("inlined test properties")));
        assertEquals(2, body.topFailureReasons().size());
        assertEquals(ProxyFailureReason.ACL_DENIED, body.topFailureReasons().get(0).reason());
        assertEquals(2L, body.topFailureReasons().get(0).count());
    }
}
