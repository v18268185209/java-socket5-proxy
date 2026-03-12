package com.zqzqq.proxyhub.socks;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.AccessControlService;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import com.zqzqq.proxyhub.core.security.AuthService;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class Socks5ProxyServerIntegrationTest {

    private HttpServer targetServer;
    private Socks5ProxyServer proxyServer;

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
    void completesSocks5HandshakeAndRelaysHttpTraffic() throws Exception {
        targetServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        targetServer.createContext("/via-socks", exchange -> {
            byte[] body = "socks-ok".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Connection", "close");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        targetServer.start();

        int proxyPort = findFreePort();
        ProxyProperties properties = new ProxyProperties();
        properties.getHttp().setEnabled(false);
        properties.getSocks().setEnabled(true);
        properties.getSocks().setBindHost("127.0.0.1");
        properties.getSocks().setPort(proxyPort);
        properties.getAcl().setEnabled(false);

        ProxyMetricsService metricsService = new ProxyMetricsService(properties);
        proxyServer = new Socks5ProxyServer(
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

            out.write(new byte[]{0x05, 0x01, 0x00});
            out.flush();
            assertArrayEquals(new byte[]{0x05, 0x00}, readExact(in, 2));

            int targetPort = targetServer.getAddress().getPort();
            ByteArrayOutputStream connectRequest = new ByteArrayOutputStream();
            connectRequest.write(new byte[]{0x05, 0x01, 0x00, 0x01});
            connectRequest.write(new byte[]{127, 0, 0, 1});
            connectRequest.write((targetPort >> 8) & 0xFF);
            connectRequest.write(targetPort & 0xFF);
            out.write(connectRequest.toByteArray());
            out.flush();

            byte[] replyHead = readExact(in, 4);
            assertEquals(0x05, replyHead[0] & 0xFF);
            assertEquals(0x00, replyHead[1] & 0xFF);
            assertEquals(0x01, replyHead[3] & 0xFF);
            readExact(in, 4);
            readExact(in, 2);

            String authority = "127.0.0.1:" + targetPort;
            String request = "GET /via-socks HTTP/1.1\r\n"
                    + "Host: " + authority + "\r\n"
                    + "Connection: close\r\n\r\n";
            out.write(request.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String response = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.contains("HTTP/1.1 200 OK"), response);
            assertTrue(response.contains("socks-ok"), response);
            assertEquals(1L, metricsService.snapshot().socksConnections());
        }
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private byte[] readExact(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;
        while (offset < len) {
            int read = in.read(data, offset, len - offset);
            if (read < 0) {
                throw new IOException("Unexpected end of stream");
            }
            offset += read;
        }
        return data;
    }
}
