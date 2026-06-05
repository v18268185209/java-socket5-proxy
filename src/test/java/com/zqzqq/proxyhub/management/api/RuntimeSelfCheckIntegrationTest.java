package com.zqzqq.proxyhub.management.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.management.dto.RuntimeSelfCheckResponse;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "proxy.socks.enabled=false",
                "proxy.http.enabled=false",
                "proxy.acl.enabled=true",
                "proxy.performance.max-connections-per-client=77",
                "proxy.users.store-path=/tmp/proxyhub-test-users.db",
                "proxy.management.enabled=false"
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

        // Use basic auth for management API (default: mgmtadmin/mgmtadmin)
        String credentials = "mgmtadmin:mgmtadmin";
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes());
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encoded);
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<RuntimeSelfCheckResponse> response = restTemplate.exchange(
                "/api/v1/runtime/self-check?top=2",
                HttpMethod.GET,
                entity,
                RuntimeSelfCheckResponse.class);
        RuntimeSelfCheckResponse body = response.getBody();

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
