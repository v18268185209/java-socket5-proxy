package com.zqzqq.proxyhub.http;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.security.AuthService;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class HttpProxyServerIntegrationTest {

    private HttpServer targetServer;
    private HttpProxyServer proxyServer;

    @AfterEach
    void tearDown() {
        if (proxyServer != null) {
            proxyServer.stop();
        }
        if (targetServer != null) {
            targetServer.stop(0);
        }
    }

    @Test
    void streamsLargeHttpRequestBodyThroughProxy() throws Exception {
        targetServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        targetServer.createContext("/echo", exchange -> {
            byte[] received = exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(200, received.length);
            exchange.getResponseBody().write(received);
            exchange.close();
        });
        targetServer.start();

        int proxyPort = findFreePort();
        ProxyProperties properties = new ProxyProperties();
        properties.getSocks().setEnabled(false);
        properties.getHttp().setEnabled(true);
        properties.getHttp().setEngine("legacy");
        properties.getHttp().setBindHost("127.0.0.1");
        properties.getHttp().setPort(proxyPort);
        properties.getAcl().setEnabled(false);
        properties.getPerformance().setHttpPendingRequestMaxBytes(65536);

        ProxyMetricsService metricsService = new ProxyMetricsService(properties);
        proxyServer = new HttpProxyServer(
                properties,
                metricsService,
                new AccessControlService(properties),
                new AuthService(properties));
        proxyServer.start();

        byte[] requestBody = "proxy-stream-test-".repeat(32768).getBytes(StandardCharsets.UTF_8);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .proxy(ProxySelector.of(new InetSocketAddress("127.0.0.1", proxyPort)))
                .build();

        URI targetUri = URI.create("http://127.0.0.1:" + targetServer.getAddress().getPort() + "/echo");
        HttpRequest request = HttpRequest.newBuilder(targetUri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                .build();

        HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

        assertEquals(200, response.statusCode());
        assertArrayEquals(requestBody, response.body());
        assertNull(metricsService.snapshot().failureReasons().get(ProxyFailureReason.HTTP_PENDING_BUFFER_EXCEEDED));
    }

    @Test
    void supportsHttpConnectTunnelRelay() throws Exception {
        targetServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        targetServer.createContext("/tunnel", exchange -> {
            byte[] body = "tunnel-ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        targetServer.start();

        int proxyPort = findFreePort();
        ProxyProperties properties = new ProxyProperties();
        properties.getSocks().setEnabled(false);
        properties.getHttp().setEnabled(true);
        properties.getHttp().setEngine("legacy");
        properties.getHttp().setBindHost("127.0.0.1");
        properties.getHttp().setPort(proxyPort);
        properties.getAcl().setEnabled(false);

        ProxyMetricsService metricsService = new ProxyMetricsService(properties);
        proxyServer = new HttpProxyServer(
                properties,
                metricsService,
                new AccessControlService(properties),
                new AuthService(properties));
        proxyServer.start();

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", proxyPort), 3000);
            socket.setSoTimeout(5000);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            String authority = "127.0.0.1:" + targetServer.getAddress().getPort();
            String connectRequest = "CONNECT " + authority + " HTTP/1.1\r\n"
                    + "Host: " + authority + "\r\n"
                    + "Connection: keep-alive\r\n\r\n";
            out.write(connectRequest.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String connectResponse = readHttpHeaders(in);
            assertTrue(connectResponse.startsWith("HTTP/1.1 200"), connectResponse);

            String tunneledRequest = "GET /tunnel HTTP/1.1\r\n"
                    + "Host: " + authority + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(tunneledRequest.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String tunneledResponse = readAllAsAscii(in);
            assertTrue(tunneledResponse.contains("HTTP/1.1 200 OK"), tunneledResponse);
            assertTrue(tunneledResponse.contains("tunnel-ok"), tunneledResponse);
            assertEquals(1L, metricsService.snapshot().httpsTunnelConnections());
        }
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private String readHttpHeaders(InputStream in) throws IOException {
        StringBuilder builder = new StringBuilder();
        int previous = -1;
        int current;
        while ((current = in.read()) != -1) {
            builder.append((char) current);
            if (previous == '\r' && current == '\n' && builder.toString().endsWith("\r\n\r\n")) {
                break;
            }
            previous = current;
        }
        return builder.toString();
    }

    private String readAllAsAscii(InputStream in) throws IOException {
        byte[] data = in.readAllBytes();
        return new String(data, StandardCharsets.UTF_8);
    }
}
