package com.zqzqq.proxyhub.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "menu-ops")
public class MenuOpsProperties {

    private boolean enabled = true;

    @NotBlank
    private String bashPath = "/bin/bash";

    @NotBlank
    private String workdir = ".";

    @Min(10)
    @Max(86400)
    private int timeoutSeconds = 3600;

    @Min(200)
    @Max(20000)
    private int maxLogLines = 3000;

    @Min(10)
    @Max(5000)
    private int maxHistory = 100;

    @Min(1)
    @Max(64)
    private int maxConcurrency = 4;

    private boolean allowRawArgs = true;
    private boolean allowDestructive = false;
    private boolean allowRemoteScripts = false;
    private String auditLogPath = "/var/log/proxy-hub/strategy-audit.log";
    private boolean auditDbEnabled = true;
    private String auditDbPath = "./logs/menu-ops-audit.db";
    @Min(1)
    @Max(3650)
    private int auditDbRetentionDays = 90;
    @Min(100)
    @Max(1000000)
    private int auditDbMaxRows = 200000;
    private String diagnosticsDir = "./logs/diagnostics";
    @Min(0)
    @Max(3650)
    private int diagnosticsRetentionDays = 14;
    @Min(0)
    @Max(100000)
    private int diagnosticsMaxFiles = 300;
    @Min(0)
    @Max(1073741824)
    private int diagnosticsMaxDownloadBytes = 52428800;
    private String autoSwitchEndpoints = "engage.cloudflareclient.com:2408,162.159.192.1:2408,162.159.193.1:2408";
    private String autoSwitchStacks = "d,4,6";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBashPath() {
        return bashPath;
    }

    public void setBashPath(String bashPath) {
        this.bashPath = bashPath;
    }

    public String getWorkdir() {
        return workdir;
    }

    public void setWorkdir(String workdir) {
        this.workdir = workdir;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getMaxLogLines() {
        return maxLogLines;
    }

    public void setMaxLogLines(int maxLogLines) {
        this.maxLogLines = maxLogLines;
    }

    public int getMaxHistory() {
        return maxHistory;
    }

    public void setMaxHistory(int maxHistory) {
        this.maxHistory = maxHistory;
    }

    public int getMaxConcurrency() {
        return maxConcurrency;
    }

    public void setMaxConcurrency(int maxConcurrency) {
        this.maxConcurrency = maxConcurrency;
    }

    public boolean isAllowRawArgs() {
        return allowRawArgs;
    }

    public void setAllowRawArgs(boolean allowRawArgs) {
        this.allowRawArgs = allowRawArgs;
    }

    public boolean isAllowDestructive() {
        return allowDestructive;
    }

    public void setAllowDestructive(boolean allowDestructive) {
        this.allowDestructive = allowDestructive;
    }

    public boolean isAllowRemoteScripts() {
        return allowRemoteScripts;
    }

    public void setAllowRemoteScripts(boolean allowRemoteScripts) {
        this.allowRemoteScripts = allowRemoteScripts;
    }

    public String getAuditLogPath() {
        return auditLogPath;
    }

    public void setAuditLogPath(String auditLogPath) {
        this.auditLogPath = auditLogPath;
    }

    public boolean isAuditDbEnabled() {
        return auditDbEnabled;
    }

    public void setAuditDbEnabled(boolean auditDbEnabled) {
        this.auditDbEnabled = auditDbEnabled;
    }

    public String getAuditDbPath() {
        return auditDbPath;
    }

    public void setAuditDbPath(String auditDbPath) {
        this.auditDbPath = auditDbPath;
    }

    public int getAuditDbRetentionDays() {
        return auditDbRetentionDays;
    }

    public void setAuditDbRetentionDays(int auditDbRetentionDays) {
        this.auditDbRetentionDays = auditDbRetentionDays;
    }

    public int getAuditDbMaxRows() {
        return auditDbMaxRows;
    }

    public void setAuditDbMaxRows(int auditDbMaxRows) {
        this.auditDbMaxRows = auditDbMaxRows;
    }

    public String getAutoSwitchEndpoints() {
        return autoSwitchEndpoints;
    }

    public void setAutoSwitchEndpoints(String autoSwitchEndpoints) {
        this.autoSwitchEndpoints = autoSwitchEndpoints;
    }

    public String getDiagnosticsDir() {
        return diagnosticsDir;
    }

    public void setDiagnosticsDir(String diagnosticsDir) {
        this.diagnosticsDir = diagnosticsDir;
    }

    public int getDiagnosticsRetentionDays() {
        return diagnosticsRetentionDays;
    }

    public void setDiagnosticsRetentionDays(int diagnosticsRetentionDays) {
        this.diagnosticsRetentionDays = diagnosticsRetentionDays;
    }

    public int getDiagnosticsMaxFiles() {
        return diagnosticsMaxFiles;
    }

    public void setDiagnosticsMaxFiles(int diagnosticsMaxFiles) {
        this.diagnosticsMaxFiles = diagnosticsMaxFiles;
    }

    public int getDiagnosticsMaxDownloadBytes() {
        return diagnosticsMaxDownloadBytes;
    }

    public void setDiagnosticsMaxDownloadBytes(int diagnosticsMaxDownloadBytes) {
        this.diagnosticsMaxDownloadBytes = diagnosticsMaxDownloadBytes;
    }

    public String getAutoSwitchStacks() {
        return autoSwitchStacks;
    }

    public void setAutoSwitchStacks(String autoSwitchStacks) {
        this.autoSwitchStacks = autoSwitchStacks;
    }
}
