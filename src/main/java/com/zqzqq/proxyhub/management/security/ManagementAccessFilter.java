package com.zqzqq.proxyhub.management.security;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.acl.CidrMatcher;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ManagementAccessFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ManagementAccessFilter.class);
    private static final String TOKEN_HEADER = "X-ProxyHub-Token";
    private static final String BASIC_PREFIX = "Basic ";

    private final ProxyProperties properties;
    private final List<CidrMatcher> allowCidrs;

    public ManagementAccessFilter(ProxyProperties properties) {
        this.properties = properties;
        this.allowCidrs = buildMatchers(properties.getManagement().getAllowCidrs());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.getManagement().isEnabled()) {
            return true;
        }
        String path = request.getRequestURI();
        return !isProtectedPath(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String clientIp = request.getRemoteAddr();
        if (!isClientAllowed(clientIp)) {
            rejectForbidden(response, "Client IP is not allowed for management access");
            return;
        }

        if (isAuthorized(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        rejectUnauthorized(response, "Management authentication required");
    }

    private boolean isProtectedPath(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (path.startsWith("/api/v1/")) {
            return true;
        }
        if ("/".equals(path) || path.startsWith("/dashboard")) {
            return true;
        }
        return properties.getManagement().isProtectActuator() && path.startsWith("/actuator/");
    }

    private boolean isClientAllowed(String clientIp) {
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

    private boolean isAuthorized(HttpServletRequest request) {
        ProxyProperties.ManagementProperties management = properties.getManagement();
        boolean anyMethodEnabled = false;

        if (management.isAllowTokenAuth()) {
            anyMethodEnabled = true;
            String configuredToken = management.getAccessToken();
            String requestToken = request.getHeader(TOKEN_HEADER);
            if (configuredToken != null && !configuredToken.isBlank() && configuredToken.equals(requestToken)) {
                return true;
            }
        }

        if (management.isAllowBasicAuth() && management.getBasic().isEnabled()) {
            anyMethodEnabled = true;
            if (matchesBasicAuth(request)) {
                return true;
            }
        }

        if (!anyMethodEnabled) {
            // Explicit CIDR-only mode: only allowed when CIDR rules are configured.
            return !allowCidrs.isEmpty();
        }
        return false;
    }

    private boolean matchesBasicAuth(HttpServletRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BASIC_PREFIX)) {
            return false;
        }
        String encoded = header.substring(BASIC_PREFIX.length()).trim();
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ex) {
            return false;
        }
        int sep = decoded.indexOf(':');
        if (sep <= 0) {
            return false;
        }
        String username = decoded.substring(0, sep);
        String password = decoded.substring(sep + 1);
        ProxyProperties.AuthProperties basic = properties.getManagement().getBasic();
        return basic.getUsername().equals(username) && basic.getPassword().equals(password);
    }

    private List<CidrMatcher> buildMatchers(List<String> cidrs) {
        List<CidrMatcher> matchers = new ArrayList<>();
        if (cidrs == null) {
            return matchers;
        }
        for (String cidr : cidrs) {
            if (cidr == null || cidr.isBlank()) {
                continue;
            }
            try {
                matchers.add(new CidrMatcher(cidr.trim()));
            } catch (Exception ex) {
                log.warn("Ignore invalid management CIDR rule: {}", cidr);
            }
        }
        return matchers;
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Basic realm=\"proxy-hub-management\"");
        writePlainText(response, message);
    }

    private void rejectForbidden(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        writePlainText(response, message);
    }

    private void writePlainText(HttpServletResponse response, String body) throws IOException {
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.TEXT_PLAIN_VALUE);
        response.getWriter().write(body);
    }
}
