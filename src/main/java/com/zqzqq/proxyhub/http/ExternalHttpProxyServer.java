package com.zqzqq.proxyhub.http;

import com.zqzqq.proxyhub.config.ProxyProperties;
import com.zqzqq.proxyhub.core.ReloadableProxyServer;
import com.zqzqq.proxyhub.core.metrics.ProxyFailureReason;
import com.zqzqq.proxyhub.core.metrics.ProxyMetricsService;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "proxy.http", name = "engine", havingValue = "squid")
public class ExternalHttpProxyServer implements ReloadableProxyServer {

    private static final Logger log = LoggerFactory.getLogger(ExternalHttpProxyServer.class);
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(?:\\d{1,3}\\.){3}\\d{1,3}$");
    private static final Pattern IPV6_PATTERN = Pattern.compile("^[0-9a-fA-F:]+$");

    private final ProxyProperties properties;
    private final ProxyMetricsService metricsService;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Process process;
    private volatile boolean stopping;

    public ExternalHttpProxyServer(ProxyProperties properties, ProxyMetricsService metricsService) {
        this.properties = properties;
        this.metricsService = metricsService;
    }

    @Override
    public String name() {
        return "HTTP-EXTERNAL:" + properties.getHttp().getPort();
    }

    @Override
    public synchronized void start() throws Exception {
        if (running.get() || !properties.getHttp().isEnabled()) {
            return;
        }

        ProxyProperties.SquidEngineProperties squid = properties.getHttp().getSquid();
        File workdir = prepareWorkdir(squid);
        File configFile = resolvePath(workdir, squid.getConfigPath());
        renderManagedConfigIfEnabled(workdir, configFile);
        if (!configFile.exists()) {
            throw new IllegalStateException("Squid config not found: " + configFile.getAbsolutePath());
        }

        List<String> parseCommand = buildParseCommand(squid, configFile);
        if (!runControlCommand(parseCommand, workdir, 15)) {
            String msg = "External HTTP proxy engine preflight parse failed, check squid config and logs";
            metricsService.addEvent("ERROR", msg + ": " + configFile.getAbsolutePath());
            metricsService.recordFailure(ProxyFailureReason.EXTERNAL_ENGINE_PRECHECK_FAILED,
                    msg + ": " + configFile.getAbsolutePath());
            throw new IllegalStateException(msg + " (" + configFile.getAbsolutePath() + ")");
        }

        List<String> command = buildStartCommand(squid, configFile);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workdir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(resolveLogFile(workdir)));

        Process started;
        try {
            started = builder.start();
        } catch (IOException ex) {
            metricsService.addEvent("ERROR", "Failed to start external HTTP proxy engine: " + ex.getMessage());
            throw ex;
        }
        process = started;
        running.set(true);
        stopping = false;

        metricsService.addEvent("INFO", "External HTTP proxy engine started with command: " + String.join(" ", command));
        log.info("External HTTP proxy engine started with command: {}", String.join(" ", command));

        started.onExit().thenAccept(exited -> {
            synchronized (ExternalHttpProxyServer.this) {
                if (process != exited) {
                    return;
                }
                process = null;
                running.set(false);
                int exitCode = exited.exitValue();
                if (stopping || exitCode == 0) {
                    metricsService.addEvent("INFO", "External HTTP proxy engine exited with code " + exitCode);
                    log.info("External HTTP proxy engine exited with code {}", exitCode);
                } else {
                    metricsService.addEvent("WARN", "External HTTP proxy engine exited unexpectedly with code " + exitCode);
                    metricsService.recordFailure(ProxyFailureReason.EXTERNAL_ENGINE_EXITED_UNEXPECTEDLY,
                            "External HTTP proxy engine exited unexpectedly with code " + exitCode);
                    log.warn("External HTTP proxy engine exited unexpectedly with code {}", exitCode);
                }
                stopping = false;
            }
        });
    }

    @Override
    public synchronized void stop() {
        Process current = process;
        if (current == null) {
            running.set(false);
            return;
        }

        stopping = true;
        if (current.isAlive()) {
            current.destroy();
            try {
                if (!current.waitFor(5, TimeUnit.SECONDS)) {
                    current.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                current.destroyForcibly();
            }
        }

        process = null;
        running.set(false);
        stopping = false;
        metricsService.addEvent("INFO", "External HTTP proxy engine stopped");
        log.info("External HTTP proxy engine stopped");
    }

    @Override
    public boolean isRunning() {
        Process current = process;
        return running.get() && current != null && current.isAlive();
    }

    @Override
    public synchronized boolean reload() {
        if (!properties.getHttp().isEnabled()) {
            return true;
        }
        if (!isRunning()) {
            metricsService.addEvent("WARN", "External HTTP proxy engine reload skipped: process is not running");
            return false;
        }

        ProxyProperties.SquidEngineProperties squid = properties.getHttp().getSquid();
        File workdir = prepareWorkdir(squid);
        File configFile = resolvePath(workdir, squid.getConfigPath());
        renderManagedConfigIfEnabled(workdir, configFile);
        if (!configFile.exists()) {
            metricsService.addEvent("ERROR", "External HTTP proxy engine reload failed: config file is missing");
            metricsService.recordFailure(ProxyFailureReason.LISTENER_RELOAD_FAILED,
                    "External HTTP proxy engine reload failed: config file is missing");
            return false;
        }

        List<String> command = buildReloadCommand(squid, configFile);
        if (!runControlCommand(command, workdir, 15)) {
            metricsService.addEvent("WARN", "External HTTP proxy engine reload command failed");
            metricsService.recordFailure(ProxyFailureReason.LISTENER_RELOAD_FAILED,
                    "External HTTP proxy engine reload command failed");
            return false;
        }

        metricsService.addEvent("INFO", "External HTTP proxy engine reloaded");
        log.info("External HTTP proxy engine reloaded");
        return true;
    }

    private List<String> buildStartCommand(ProxyProperties.SquidEngineProperties squid, File configFile) {
        List<String> command = new ArrayList<>();
        command.add(squid.getExecutable());
        if (squid.isForeground()) {
            command.add("-N");
        }
        command.add("-f");
        command.add(configFile.getAbsolutePath());
        if (squid.getExtraArgs() != null) {
            for (String arg : squid.getExtraArgs()) {
                if (arg != null && !arg.isBlank()) {
                    command.add(arg.trim());
                }
            }
        }
        return command;
    }

    private List<String> buildReloadCommand(ProxyProperties.SquidEngineProperties squid, File configFile) {
        List<String> command = new ArrayList<>();
        command.add(squid.getExecutable());
        command.add("-k");
        command.add("reconfigure");
        command.add("-f");
        command.add(configFile.getAbsolutePath());
        return command;
    }

    private List<String> buildParseCommand(ProxyProperties.SquidEngineProperties squid, File configFile) {
        List<String> command = new ArrayList<>();
        command.add(squid.getExecutable());
        command.add("-k");
        command.add("parse");
        command.add("-f");
        command.add(configFile.getAbsolutePath());
        return command;
    }

    private boolean runControlCommand(List<String> command, File workdir, int timeoutSeconds) {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workdir)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(resolveLogFile(workdir)));
        try {
            Process cmd = builder.start();
            if (!cmd.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                cmd.destroyForcibly();
                log.warn("External HTTP proxy engine control command timed out: {}", String.join(" ", command));
                return false;
            }
            int code = cmd.exitValue();
            if (code != 0) {
                log.warn("External HTTP proxy engine control command exited with code {}: {}", code, String.join(" ", command));
                return false;
            }
            return true;
        } catch (IOException ex) {
            log.warn("Failed to execute external HTTP proxy engine control command: {}", String.join(" ", command), ex);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("External HTTP proxy engine control command interrupted: {}", String.join(" ", command), ex);
            return false;
        }
    }

    private File resolveLogFile(File workdir) {
        File logsDir = new File(workdir, "logs");
        if (!logsDir.exists()) {
            logsDir.mkdirs();
        }
        return new File(logsDir, "http-external-engine.log");
    }

    private File prepareWorkdir(ProxyProperties.SquidEngineProperties squid) {
        File workdir = new File(squid.getWorkdir());
        ensureWritableDirectory(workdir, "squid workdir");

        File squidLogsDir = new File(workdir, "logs/squid");
        ensureWritableDirectory(squidLogsDir, "squid logs");

        File accessLogFile = resolvePath(workdir, squid.getAccessLogPath());
        File accessLogParent = accessLogFile.getParentFile();
        if (accessLogParent != null) {
            ensureWritableDirectory(accessLogParent, "squid access log");
        }
        return workdir;
    }

    private void renderManagedConfigIfEnabled(File workdir, File configFile) {
        if (!properties.getHttp().getSquid().isManageConfig()) {
            return;
        }
        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IllegalStateException("Failed to create squid config dir: " + parent.getAbsolutePath());
        }
        String content = buildManagedConfig(workdir);
        try {
            Files.writeString(configFile.toPath(), content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to render squid config: " + configFile.getAbsolutePath(), ex);
        }
    }

    private String buildManagedConfig(File workdir) {
        File accessLogFile = resolvePath(workdir, properties.getHttp().getSquid().getAccessLogPath());
        File cacheLogFile = new File(workdir, "logs/squid/cache.log");
        File pidFile = new File(workdir, "logs/squid/squid.pid");
        File coreDumpDir = new File(workdir, "logs/squid");

        List<String> lines = new ArrayList<>();
        lines.add("# Generated by ProxyHub. Manual edits may be overwritten.");
        lines.add("http_port " + normalizeBindHost(properties.getHttp().getBindHost()) + ":" + properties.getHttp().getPort());
        lines.add("");
        lines.add("cache deny all");
        lines.add("via off");
        lines.add("forwarded_for delete");
        lines.add("");

        if (properties.getAcl().isEnabled()) {
            appendAclRules(lines);
        }

        if (properties.getHttp().getAuth().isEnabled()) {
            lines.add("# WARNING: proxy.http.auth.* is enabled but squid auth mapping is not yet configured.");
        }

        lines.add("acl all_clients src all");
        lines.add("http_access allow all_clients");
        lines.add("http_access deny all");
        lines.add("");
        lines.add("access_log stdio:" + toSquidAbsolutePath(accessLogFile));
        lines.add("cache_log " + toSquidAbsolutePath(cacheLogFile));
        lines.add("pid_filename " + toSquidAbsolutePath(pidFile));
        lines.add("coredump_dir " + toSquidAbsolutePath(coreDumpDir));
        lines.add("");

        metricsService.addEvent("INFO", "Rendered managed squid config: " + toRelativePath(workdir, resolvePath(workdir, properties.getHttp().getSquid().getConfigPath())));
        return String.join(System.lineSeparator(), lines);
    }

    private void appendAclRules(List<String> lines) {
        List<Integer> denyPorts = normalizeDenyPorts(properties.getAcl().getDenyTargetPorts());
        if (!denyPorts.isEmpty()) {
            lines.add("acl denied_target_ports port " + joinIntegers(denyPorts));
            lines.add("http_access deny denied_target_ports");
        }

        Set<String> denyDomains = new LinkedHashSet<>();
        Set<String> denyIps = new LinkedHashSet<>();
        for (String rawRule : properties.getAcl().getDenyTargetHosts()) {
            String rule = normalizeRule(rawRule);
            if (rule == null) {
                continue;
            }
            if (isIpLiteralOrCidr(rule)) {
                denyIps.add(rule);
            } else {
                denyDomains.add(toSquidDomainRule(rule));
            }
        }
        if (!denyDomains.isEmpty()) {
            appendAclInChunks(lines, "acl denied_target_domains dstdomain ", new ArrayList<>(denyDomains), 12);
            lines.add("http_access deny denied_target_domains");
        }
        if (!denyIps.isEmpty()) {
            appendAclInChunks(lines, "acl denied_target_ips dst ", new ArrayList<>(denyIps), 12);
            lines.add("http_access deny denied_target_ips");
        }

        Set<String> allowedClients = new LinkedHashSet<>();
        for (String rawCidr : properties.getAcl().getAllowClientCidrs()) {
            String cidr = normalizeRule(rawCidr);
            if (cidr == null) {
                continue;
            }
            if (isValidCidr(cidr)) {
                allowedClients.add(cidr);
            } else {
                metricsService.addEvent("WARN", "Ignore invalid ACL client CIDR for squid config: " + rawCidr);
            }
        }
        if (!allowedClients.isEmpty()) {
            appendAclInChunks(lines, "acl allowed_clients src ", new ArrayList<>(allowedClients), 12);
            lines.add("http_access deny !allowed_clients");
        }
        lines.add("");
    }

    private void appendAclInChunks(List<String> lines, String prefix, List<String> values, int chunkSize) {
        for (int i = 0; i < values.size(); i += chunkSize) {
            int end = Math.min(values.size(), i + chunkSize);
            lines.add(prefix + String.join(" ", values.subList(i, end)));
        }
    }

    private List<Integer> normalizeDenyPorts(List<Integer> ports) {
        Set<Integer> sorted = new java.util.TreeSet<>();
        if (ports != null) {
            for (Integer port : ports) {
                if (port != null && port > 0 && port <= 65535) {
                    sorted.add(port);
                }
            }
        }
        return new ArrayList<>(sorted);
    }

    private String joinIntegers(List<Integer> values) {
        List<String> tmp = new ArrayList<>(values.size());
        for (Integer value : values) {
            tmp.add(String.valueOf(value));
        }
        return String.join(" ", tmp);
    }

    private String normalizeBindHost(String bindHost) {
        if (bindHost == null || bindHost.isBlank()) {
            return "0.0.0.0";
        }
        return bindHost.trim();
    }

    private String normalizeRule(String rawRule) {
        if (rawRule == null) {
            return null;
        }
        String value = rawRule.trim().toLowerCase(Locale.ROOT);
        return value.isEmpty() ? null : value;
    }

    private String toSquidDomainRule(String rule) {
        if (rule.startsWith("*.")) {
            return "." + rule.substring(2);
        }
        return rule;
    }

    private boolean isIpLiteralOrCidr(String value) {
        if (value.contains("/")) {
            return isValidCidr(value);
        }
        return isIpLiteral(value);
    }

    private boolean isValidCidr(String value) {
        int slash = value.indexOf('/');
        if (slash <= 0 || slash == value.length() - 1) {
            return false;
        }
        String ipPart = value.substring(0, slash);
        String prefixPart = value.substring(slash + 1);
        if (!isIpLiteral(ipPart)) {
            return false;
        }
        int prefix;
        try {
            prefix = Integer.parseInt(prefixPart);
        } catch (NumberFormatException ex) {
            return false;
        }
        if (isIpv4Literal(ipPart)) {
            return prefix >= 0 && prefix <= 32;
        }
        return prefix >= 0 && prefix <= 128;
    }

    private boolean isIpLiteral(String value) {
        return isIpv4Literal(value) || isIpv6Literal(value);
    }

    private boolean isIpv4Literal(String value) {
        if (!IPV4_PATTERN.matcher(value).matches()) {
            return false;
        }
        String[] parts = value.split("\\.");
        if (parts.length != 4) {
            return false;
        }
        for (String part : parts) {
            try {
                int n = Integer.parseInt(part);
                if (n < 0 || n > 255) {
                    return false;
                }
            } catch (NumberFormatException ex) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv6Literal(String value) {
        return value.contains(":") && IPV6_PATTERN.matcher(value).matches();
    }

    private File resolvePath(File workdir, String path) {
        File file = new File(path);
        if (file.isAbsolute()) {
            return file;
        }
        return new File(workdir, path);
    }

    private String toRelativePath(File workdir, File file) {
        try {
            Path base = workdir.toPath().toAbsolutePath().normalize();
            Path target = file.toPath().toAbsolutePath().normalize();
            return base.relativize(target).toString();
        } catch (Exception ex) {
            return file.getAbsolutePath();
        }
    }

    private String toSquidPath(File workdir, String configuredPath) {
        File file = resolvePath(workdir, configuredPath);
        String path;
        if (new File(configuredPath).isAbsolute()) {
            path = file.getAbsolutePath();
        } else {
            path = toRelativePath(workdir, file);
        }
        return path.replace('\\', '/');
    }

    private String toSquidAbsolutePath(File file) {
        return file.getAbsolutePath().replace('\\', '/');
    }

    private void ensureWritableDirectory(File dir, String label) {
        if (dir == null) {
            throw new IllegalStateException("Directory is null for " + label);
        }
        try {
            Files.createDirectories(dir.toPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to create directory for " + label + ": " + dir.getAbsolutePath(), ex);
        }
        if (!dir.isDirectory()) {
            throw new IllegalStateException("Path is not a directory for " + label + ": " + dir.getAbsolutePath());
        }

        Path probe = null;
        try {
            probe = Files.createTempFile(dir.toPath(), ".proxyhub-write-test-", ".tmp");
        } catch (IOException ex) {
            throw new IllegalStateException("Directory is not writable for " + label + ": " + dir.getAbsolutePath(), ex);
        } finally {
            if (probe != null) {
                try {
                    Files.deleteIfExists(probe);
                } catch (IOException ignore) {
                    // no-op
                }
            }
        }
    }
}
