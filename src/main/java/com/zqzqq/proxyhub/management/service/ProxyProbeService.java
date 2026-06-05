package com.zqzqq.proxyhub.management.service;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.management.dto.ProxyTestRequest;
import com.zqzqq.proxyhub.management.dto.ProxyTestResponse;
import com.zqzqq.proxyhub.management.dto.ScenarioProbeItemResponse;
import com.zqzqq.proxyhub.management.dto.ScenarioProbeRequest;
import com.zqzqq.proxyhub.management.dto.ScenarioProbeResponse;
import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ProxyProbeService {

    private static final int DEFAULT_TIMEOUT = 8000;
    private static final Map<String, String> SCENARIO_TARGETS = buildScenarioTargets();

    private final ProxyProperties properties;

    public ProxyProbeService(ProxyProperties properties) {
        this.properties = properties;
    }

    public ProxyTestResponse runProbe(ProxyTestRequest request) {
        long start = System.currentTimeMillis();
        String mode = normalizeMode(request.getMode());
        String targetUrl = trimToDefault(request.getTargetUrl(), "https://httpbin.org/ip");
        int timeout = request.getTimeoutMillis() != null ? request.getTimeoutMillis() : DEFAULT_TIMEOUT;

        try {
            URI target = URI.create(targetUrl);
            if (!"http".equalsIgnoreCase(target.getScheme()) && !"https".equalsIgnoreCase(target.getScheme())) {
                return fail(mode, targetUrl, "", start, "Only http/https target URL is supported");
            }
            return switch (mode) {
                case "HTTP" -> probeHttpProxy(target, request, timeout, start);
                case "SOCKS5" -> probeSocks5Proxy(target, request, timeout, start);
                default -> fail(mode, targetUrl, "", start, "Unsupported test mode: " + mode);
            };
        } catch (IllegalArgumentException ex) {
            return fail(mode, targetUrl, "", start, "Invalid target URL: " + ex.getMessage());
        } catch (Exception ex) {
            return fail(mode, targetUrl, "", start, "Probe exception: " + ex.getMessage());
        }
    }

    public ScenarioProbeResponse runScenarioProbe(ScenarioProbeRequest request) {
        long start = System.currentTimeMillis();
        String mode = normalizeMode(request.getMode());
        List<String> scenarios = normalizeScenarios(request.getScenarios());
        List<ScenarioProbeItemResponse> items = new ArrayList<>();
        String proxyAddress = "";

        for (String scenario : scenarios) {
            String targetUrl = SCENARIO_TARGETS.get(scenario);
            if (targetUrl == null) {
                items.add(new ScenarioProbeItemResponse(
                        scenario,
                        "",
                        false,
                        "Unsupported scenario: " + scenario,
                        null,
                        0));
                continue;
            }

            ProxyTestRequest probeRequest = fromScenarioRequest(request, mode, targetUrl);
            ProxyTestResponse result = runProbe(probeRequest);
            if (proxyAddress.isBlank() && result.proxyAddress() != null) {
                proxyAddress = result.proxyAddress();
            }
            items.add(new ScenarioProbeItemResponse(
                    scenario,
                    result.targetUrl(),
                    result.success(),
                    scenarioMessage(scenario, result),
                    result.httpStatus(),
                    result.durationMillis()));
        }

        return new ScenarioProbeResponse(
                mode,
                proxyAddress,
                System.currentTimeMillis() - start,
                items);
    }

    private ProxyTestResponse probeHttpProxy(URI target, ProxyTestRequest request, int timeout, long start) throws Exception {
        String proxyHost = pickProxyHost(request.getProxyHost(), properties.getHttp().getBindHost());
        int proxyPort = pickProxyPort(request.getProxyPort(), properties.getHttp().getPort());
        String proxyAddr = proxyHost + ":" + proxyPort;

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeout))
                .proxy(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)))
                .build();

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(target)
                .timeout(Duration.ofMillis(timeout))
                .GET();

        String username = trimToNull(request.getUsername());
        String password = trimToNull(request.getPassword());
        if (username != null) {
            String credential = username + ":" + (password == null ? "" : password);
            String encoded = Base64.getEncoder().encodeToString(credential.getBytes(StandardCharsets.UTF_8));
            reqBuilder.header("Proxy-Authorization", "Basic " + encoded);
        }

        HttpResponse<String> response = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body();
        String snippet = truncate(cleanSnippet(body), 240);
        boolean success = status >= 200 && status < 400;

        return new ProxyTestResponse(
                success,
                "HTTP",
                success ? "HTTP proxy probe passed" : "HTTP proxy returned status " + status,
                target.toString(),
                proxyAddr,
                status,
                snippet,
                System.currentTimeMillis() - start);
    }

    private ProxyTestResponse probeSocks5Proxy(URI target, ProxyTestRequest request, int timeout, long start) throws Exception {
        String proxyHost = pickProxyHost(request.getProxyHost(), properties.getSocks().getBindHost());
        int proxyPort = pickProxyPort(request.getProxyPort(), properties.getSocks().getPort());
        String proxyAddr = proxyHost + ":" + proxyPort;

        String username = trimToNull(request.getUsername());
        String password = trimToNull(request.getPassword());
        boolean hasCredential = username != null;

        String targetHost = target.getHost();
        if (targetHost == null || targetHost.isBlank()) {
            return fail("SOCKS5", target.toString(), proxyAddr, start, "Target URL host is empty");
        }
        int targetPort = target.getPort() > 0 ? target.getPort() : ("https".equalsIgnoreCase(target.getScheme()) ? 443 : 80);

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(proxyHost, proxyPort), timeout);
            socket.setSoTimeout(timeout);
            InputStream in = new BufferedInputStream(socket.getInputStream());
            OutputStream out = socket.getOutputStream();

            if (hasCredential) {
                out.write(new byte[]{0x05, 0x02, 0x00, 0x02});
            } else {
                out.write(new byte[]{0x05, 0x01, 0x00});
            }
            out.flush();

            byte[] methodResp = readExact(in, 2);
            if ((methodResp[0] & 0xFF) != 0x05) {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 handshake failed: bad version");
            }
            int method = methodResp[1] & 0xFF;
            if (method == 0xFF) {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 handshake failed: no method accepted");
            }

            if (method == 0x02) {
                if (!hasCredential) {
                    return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 requires username/password");
                }
                byte[] userBytes = username.getBytes(StandardCharsets.UTF_8);
                byte[] passBytes = (password == null ? "" : password).getBytes(StandardCharsets.UTF_8);
                if (userBytes.length > 255 || passBytes.length > 255) {
                    return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 credential length exceeds 255");
                }

                ByteArrayOutputStream authPacket = new ByteArrayOutputStream();
                authPacket.write(0x01);
                authPacket.write(userBytes.length);
                authPacket.write(userBytes);
                authPacket.write(passBytes.length);
                authPacket.write(passBytes);
                out.write(authPacket.toByteArray());
                out.flush();

                byte[] authResp = readExact(in, 2);
                if ((authResp[1] & 0xFF) != 0x00) {
                    return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 authentication failed");
                }
            }

            byte[] hostBytes = targetHost.getBytes(StandardCharsets.UTF_8);
            if (hostBytes.length > 255) {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "Target host is too long");
            }

            ByteArrayOutputStream connectPacket = new ByteArrayOutputStream();
            connectPacket.write(0x05);
            connectPacket.write(0x01);
            connectPacket.write(0x00);
            connectPacket.write(0x03);
            connectPacket.write(hostBytes.length);
            connectPacket.write(hostBytes);
            connectPacket.write((targetPort >> 8) & 0xFF);
            connectPacket.write(targetPort & 0xFF);
            out.write(connectPacket.toByteArray());
            out.flush();

            byte[] head = readExact(in, 4);
            if ((head[0] & 0xFF) != 0x05) {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 connect reply version mismatch");
            }
            int status = head[1] & 0xFF;
            if (status != 0x00) {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 connect failed: " + socksStatus(status));
            }

            int addrType = head[3] & 0xFF;
            if (addrType == 0x01) {
                readExact(in, 4);
            } else if (addrType == 0x03) {
                int len = readExact(in, 1)[0] & 0xFF;
                readExact(in, len);
            } else if (addrType == 0x04) {
                readExact(in, 16);
            } else {
                return fail("SOCKS5", target.toString(), proxyAddr, start, "SOCKS5 connect reply atyp invalid");
            }
            readExact(in, 2);

            Integer httpStatus = null;
            String snippet = "";
            if ("http".equalsIgnoreCase(target.getScheme())) {
                String path = target.getRawPath();
                if (path == null || path.isBlank()) {
                    path = "/";
                }
                if (target.getRawQuery() != null && !target.getRawQuery().isBlank()) {
                    path = path + "?" + target.getRawQuery();
                }

                String req = "HEAD " + path + " HTTP/1.1\r\nHost: " + targetHost + "\r\nConnection: close\r\n\r\n";
                out.write(req.getBytes(StandardCharsets.US_ASCII));
                out.flush();

                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.US_ASCII));
                String statusLine = reader.readLine();
                if (statusLine != null) {
                    snippet = truncate(statusLine.trim(), 200);
                    String[] parts = statusLine.split(" ");
                    if (parts.length >= 2) {
                        try {
                            httpStatus = Integer.parseInt(parts[1]);
                        } catch (NumberFormatException ignore) {
                            // keep null status
                        }
                    }
                }
            }

            return new ProxyTestResponse(
                    true,
                    "SOCKS5",
                    "SOCKS5 proxy probe passed",
                    target.toString(),
                    proxyAddr,
                    httpStatus,
                    snippet,
                    System.currentTimeMillis() - start);
        }
    }

    private ProxyTestResponse fail(String mode, String targetUrl, String proxyAddress, long start, String message) {
        return new ProxyTestResponse(
                false,
                mode,
                message,
                targetUrl,
                proxyAddress,
                null,
                "",
                System.currentTimeMillis() - start);
    }

    private String normalizeMode(String mode) {
        String value = trimToDefault(mode, "SOCKS5");
        value = value.toUpperCase(Locale.ROOT);
        if ("HTTP_PROXY".equals(value)) {
            return "HTTP";
        }
        return value;
    }

    private String pickProxyHost(String requestHost, String configuredHost) {
        String host = trimToNull(requestHost);
        if (host != null) {
            return host;
        }
        String configured = trimToDefault(configuredHost, "127.0.0.1");
        if ("0.0.0.0".equals(configured) || "::".equals(configured) || "::0".equals(configured)) {
            return "127.0.0.1";
        }
        return configured;
    }

    private int pickProxyPort(Integer requestPort, int configuredPort) {
        if (requestPort != null) {
            return requestPort;
        }
        return configuredPort;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String trimToDefault(String value, String defaultValue) {
        String trimmed = trimToNull(value);
        return trimmed == null ? defaultValue : trimmed;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, max) + "...";
    }

    private String cleanSnippet(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", " ").replace("\n", " ").trim();
    }

    private byte[] readExact(InputStream in, int len) throws IOException {
        byte[] data = new byte[len];
        int offset = 0;
        while (offset < len) {
            int read = in.read(data, offset, len - offset);
            if (read < 0) {
                throw new IllegalStateException("Connection closed by remote host");
            }
            offset += read;
        }
        return data;
    }

    private String socksStatus(int status) {
        return switch (status) {
            case 0x01 -> "GENERAL_FAILURE";
            case 0x02 -> "RULESET_BLOCKED";
            case 0x03 -> "NETWORK_UNREACHABLE";
            case 0x04 -> "HOST_UNREACHABLE";
            case 0x05 -> "CONNECTION_REFUSED";
            case 0x06 -> "TTL_EXPIRED";
            case 0x07 -> "COMMAND_NOT_SUPPORTED";
            case 0x08 -> "ADDRESS_TYPE_NOT_SUPPORTED";
            default -> "UNKNOWN(" + status + ")";
        };
    }

    private ProxyTestRequest fromScenarioRequest(ScenarioProbeRequest request, String mode, String targetUrl) {
        ProxyTestRequest probeRequest = new ProxyTestRequest();
        probeRequest.setMode(mode);
        probeRequest.setTargetUrl(targetUrl);
        probeRequest.setProxyHost(request.getProxyHost());
        probeRequest.setProxyPort(request.getProxyPort());
        probeRequest.setUsername(request.getUsername());
        probeRequest.setPassword(request.getPassword());
        probeRequest.setTimeoutMillis(request.getTimeoutMillis());
        return probeRequest;
    }

    private List<String> normalizeScenarios(List<String> rawScenarios) {
        if (rawScenarios == null || rawScenarios.isEmpty()) {
            return new ArrayList<>(SCENARIO_TARGETS.keySet());
        }
        List<String> scenarios = new ArrayList<>();
        for (String raw : rawScenarios) {
            String value = String.valueOf(raw == null ? "" : raw).trim().toUpperCase(Locale.ROOT);
            if (!value.isEmpty() && !scenarios.contains(value)) {
                scenarios.add(value);
            }
        }
        if (scenarios.isEmpty()) {
            return new ArrayList<>(SCENARIO_TARGETS.keySet());
        }
        return scenarios;
    }

    private String scenarioMessage(String scenario, ProxyTestResponse result) {
        if (!result.success()) {
            return result.message();
        }
        if ("SOCKS5".equalsIgnoreCase(result.mode()) && result.httpStatus() == null) {
            return switch (scenario) {
                case "CHATGPT" -> "SOCKS5 CONNECT ok, ChatGPT 443 reachable";
                case "NETFLIX" -> "SOCKS5 CONNECT ok, Netflix 443 reachable";
                case "GOOGLE_SCHOLAR" -> "SOCKS5 CONNECT ok, Google Scholar 443 reachable";
                case "TELEGRAM" -> "SOCKS5 CONNECT ok, Telegram API 443 reachable";
                case "IPV4_EGRESS" -> "SOCKS5 CONNECT ok, IPv4 egress path reachable";
                default -> "SOCKS5 CONNECT ok";
            };
        }

        Integer status = result.httpStatus();
        if (status == null) {
            return "Reachable";
        }
        if ("GOOGLE_SCHOLAR".equals(scenario) && (status == 429 || status == 503)) {
            return "Google risk/captcha likely triggered, rotate egress IP and retry";
        }
        if ("NETFLIX".equals(scenario) && status >= 400) {
            return "Netflix reachable but response status is abnormal, possible geo restriction";
        }
        if ("CHATGPT".equals(scenario) && (status == 403 || status == 429)) {
            return "ChatGPT reachable but access is restricted, try another region/egress";
        }
        return "Reachable, HTTP status " + status;
    }

    private static Map<String, String> buildScenarioTargets() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("CHATGPT", "https://chatgpt.com/");
        map.put("NETFLIX", "https://www.netflix.com/title/81280792");
        map.put("GOOGLE_SCHOLAR", "https://scholar.google.com/");
        map.put("TELEGRAM", "https://api.telegram.org/");
        map.put("IPV4_EGRESS", "https://api64.ipify.org?format=json");
        return map;
    }
}
