package com.zqzqq.proxyhub.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "proxy")
public class ProxyProperties {

    @Valid
    private ListenerProperties socks = new ListenerProperties("SOCKS5", 1080);

    @Valid
    private HttpListenerProperties http = new HttpListenerProperties("HTTP", 8080);

    @Valid
    private AclProperties acl = new AclProperties();

    @Valid
    private PerformanceProperties performance = new PerformanceProperties();

    @Valid
    private DashboardProperties dashboard = new DashboardProperties();

    @Valid
    private ManagementProperties management = new ManagementProperties();

    public ListenerProperties getSocks() {
        return socks;
    }

    public void setSocks(ListenerProperties socks) {
        this.socks = socks;
    }

    public HttpListenerProperties getHttp() {
        return http;
    }

    public void setHttp(HttpListenerProperties http) {
        this.http = http;
    }

    public AclProperties getAcl() {
        return acl;
    }

    public void setAcl(AclProperties acl) {
        this.acl = acl;
    }

    public PerformanceProperties getPerformance() {
        return performance;
    }

    public void setPerformance(PerformanceProperties performance) {
        this.performance = performance;
    }

    public DashboardProperties getDashboard() {
        return dashboard;
    }

    public void setDashboard(DashboardProperties dashboard) {
        this.dashboard = dashboard;
    }

    public ManagementProperties getManagement() {
        return management;
    }

    public void setManagement(ManagementProperties management) {
        this.management = management;
    }

    public static class ListenerProperties {

        private String name;
        private boolean enabled = true;

        @NotBlank
        private String bindHost = "0.0.0.0";

        @Min(1)
        @Max(65535)
        private int port;

        @Valid
        private AuthProperties auth = new AuthProperties();

        public ListenerProperties() {
        }

        public ListenerProperties(String name, int port) {
            this.name = name;
            this.port = port;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getBindHost() {
            return bindHost;
        }

        public void setBindHost(String bindHost) {
            this.bindHost = bindHost;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public AuthProperties getAuth() {
            return auth;
        }

        public void setAuth(AuthProperties auth) {
            this.auth = auth;
        }
    }

    public static class HttpListenerProperties extends ListenerProperties {

        @NotBlank
        private String engine = "legacy";

        @Valid
        private SquidEngineProperties squid = new SquidEngineProperties();

        public HttpListenerProperties() {
        }

        public HttpListenerProperties(String name, int port) {
            super(name, port);
        }

        public String getEngine() {
            return engine;
        }

        public void setEngine(String engine) {
            this.engine = engine;
        }

        public SquidEngineProperties getSquid() {
            return squid;
        }

        public void setSquid(SquidEngineProperties squid) {
            this.squid = squid;
        }
    }

    public static class SquidEngineProperties {

        @NotBlank
        private String executable = "squid";

        @NotBlank
        private String configPath = "./conf/squid/squid.conf";

        @NotBlank
        private String workdir = ".";

        @NotBlank
        private String accessLogPath = "./logs/squid/access.log";

        private boolean manageConfig = true;

        private boolean foreground = true;

        private List<String> extraArgs = new ArrayList<>();

        public String getExecutable() {
            return executable;
        }

        public void setExecutable(String executable) {
            this.executable = executable;
        }

        public String getConfigPath() {
            return configPath;
        }

        public void setConfigPath(String configPath) {
            this.configPath = configPath;
        }

        public String getWorkdir() {
            return workdir;
        }

        public void setWorkdir(String workdir) {
            this.workdir = workdir;
        }

        public String getAccessLogPath() {
            return accessLogPath;
        }

        public void setAccessLogPath(String accessLogPath) {
            this.accessLogPath = accessLogPath;
        }

        public boolean isForeground() {
            return foreground;
        }

        public void setForeground(boolean foreground) {
            this.foreground = foreground;
        }

        public boolean isManageConfig() {
            return manageConfig;
        }

        public void setManageConfig(boolean manageConfig) {
            this.manageConfig = manageConfig;
        }

        public List<String> getExtraArgs() {
            return extraArgs;
        }

        public void setExtraArgs(List<String> extraArgs) {
            this.extraArgs = extraArgs;
        }
    }

    public static class AuthProperties {

        private boolean enabled = false;
        private String username = "admin";
        private String password = "admin";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class AclProperties {

        private boolean enabled = false;
        private List<String> allowClientCidrs = new ArrayList<>();
        private List<String> denyTargetHosts = new ArrayList<>();
        private List<Integer> denyTargetPorts = new ArrayList<>();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public List<String> getAllowClientCidrs() {
            return allowClientCidrs;
        }

        public void setAllowClientCidrs(List<String> allowClientCidrs) {
            this.allowClientCidrs = allowClientCidrs;
        }

        public List<String> getDenyTargetHosts() {
            return denyTargetHosts;
        }

        public void setDenyTargetHosts(List<String> denyTargetHosts) {
            this.denyTargetHosts = denyTargetHosts;
        }

        public List<Integer> getDenyTargetPorts() {
            return denyTargetPorts;
        }

        public void setDenyTargetPorts(List<Integer> denyTargetPorts) {
            this.denyTargetPorts = denyTargetPorts;
        }
    }

    public static class PerformanceProperties {

        @Min(1000)
        private int connectTimeoutMillis = 10000;

        @Min(10)
        private int idleTimeoutSeconds = 120;

        @Min(1)
        private int bossThreads = 1;

        @Min(0)
        private int workerThreads = 0;

        @Min(1)
        private int backlog = 1024;

        @Min(0)
        private int maxConnectionsPerClient = 0;

        @Min(256)
        private int recvByteBufMin = 1024;

        @Min(256)
        private int recvByteBufInitial = 8192;

        @Min(512)
        private int recvByteBufMax = 65536;

        @Min(1024)
        private int writeBufferLowWaterMark = 32768;

        @Min(2048)
        private int writeBufferHighWaterMark = 131072;

        private boolean preferNativeTransport = true;

        @Min(65536)
        private int httpPendingRequestMaxBytes = 1024 * 1024;

        public int getConnectTimeoutMillis() {
            return connectTimeoutMillis;
        }

        public void setConnectTimeoutMillis(int connectTimeoutMillis) {
            this.connectTimeoutMillis = connectTimeoutMillis;
        }

        public int getIdleTimeoutSeconds() {
            return idleTimeoutSeconds;
        }

        public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
            this.idleTimeoutSeconds = idleTimeoutSeconds;
        }

        public int getBossThreads() {
            return bossThreads;
        }

        public void setBossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
        }

        public int getWorkerThreads() {
            return workerThreads;
        }

        public void setWorkerThreads(int workerThreads) {
            this.workerThreads = workerThreads;
        }

        public int getBacklog() {
            return backlog;
        }

        public void setBacklog(int backlog) {
            this.backlog = backlog;
        }

        public int getMaxConnectionsPerClient() {
            return maxConnectionsPerClient;
        }

        public void setMaxConnectionsPerClient(int maxConnectionsPerClient) {
            this.maxConnectionsPerClient = maxConnectionsPerClient;
        }

        public int getRecvByteBufMin() {
            return recvByteBufMin;
        }

        public void setRecvByteBufMin(int recvByteBufMin) {
            this.recvByteBufMin = recvByteBufMin;
        }

        public int getRecvByteBufInitial() {
            return recvByteBufInitial;
        }

        public void setRecvByteBufInitial(int recvByteBufInitial) {
            this.recvByteBufInitial = recvByteBufInitial;
        }

        public int getRecvByteBufMax() {
            return recvByteBufMax;
        }

        public void setRecvByteBufMax(int recvByteBufMax) {
            this.recvByteBufMax = recvByteBufMax;
        }

        public int getWriteBufferLowWaterMark() {
            return writeBufferLowWaterMark;
        }

        public void setWriteBufferLowWaterMark(int writeBufferLowWaterMark) {
            this.writeBufferLowWaterMark = writeBufferLowWaterMark;
        }

        public int getWriteBufferHighWaterMark() {
            return writeBufferHighWaterMark;
        }

        public void setWriteBufferHighWaterMark(int writeBufferHighWaterMark) {
            this.writeBufferHighWaterMark = writeBufferHighWaterMark;
        }

        public boolean isPreferNativeTransport() {
            return preferNativeTransport;
        }

        public void setPreferNativeTransport(boolean preferNativeTransport) {
            this.preferNativeTransport = preferNativeTransport;
        }

        public int getHttpPendingRequestMaxBytes() {
            return httpPendingRequestMaxBytes;
        }

        public void setHttpPendingRequestMaxBytes(int httpPendingRequestMaxBytes) {
            this.httpPendingRequestMaxBytes = httpPendingRequestMaxBytes;
        }

        @AssertTrue(message = "recvByteBufInitial must be between recvByteBufMin and recvByteBufMax")
        public boolean isRecvAllocatorRangeValid() {
            return recvByteBufMin <= recvByteBufInitial && recvByteBufInitial <= recvByteBufMax;
        }

        @AssertTrue(message = "writeBufferHighWaterMark must be >= writeBufferLowWaterMark")
        public boolean isWriteBufferRangeValid() {
            return writeBufferLowWaterMark <= writeBufferHighWaterMark;
        }
    }

    public static class DashboardProperties {

        @Min(1)
        @Max(120)
        private int refreshSeconds = 2;

        @Min(100)
        @Max(100000)
        private int maxRecentEvents = 3000;

        @Size(min = 1, max = 100)
        private String timezone = "Asia/Shanghai";

        private boolean recordSessionLifecycleEvents = false;

        public int getRefreshSeconds() {
            return refreshSeconds;
        }

        public void setRefreshSeconds(int refreshSeconds) {
            this.refreshSeconds = refreshSeconds;
        }

        public int getMaxRecentEvents() {
            return maxRecentEvents;
        }

        public void setMaxRecentEvents(int maxRecentEvents) {
            this.maxRecentEvents = maxRecentEvents;
        }

        public String getTimezone() {
            return timezone;
        }

        public void setTimezone(String timezone) {
            this.timezone = timezone;
        }

        public boolean isRecordSessionLifecycleEvents() {
            return recordSessionLifecycleEvents;
        }

        public void setRecordSessionLifecycleEvents(boolean recordSessionLifecycleEvents) {
            this.recordSessionLifecycleEvents = recordSessionLifecycleEvents;
        }
    }

    public static class ManagementProperties {

        private boolean enabled = false;
        private boolean protectActuator = false;
        private List<String> allowCidrs = new ArrayList<>();
        private boolean allowBasicAuth = true;
        private boolean allowTokenAuth = false;

        @Size(max = 256)
        private String accessToken = "";

        @Valid
        private AuthProperties basic = new AuthProperties();

        public ManagementProperties() {
            this.basic.setEnabled(true);
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isProtectActuator() {
            return protectActuator;
        }

        public void setProtectActuator(boolean protectActuator) {
            this.protectActuator = protectActuator;
        }

        public List<String> getAllowCidrs() {
            return allowCidrs;
        }

        public void setAllowCidrs(List<String> allowCidrs) {
            this.allowCidrs = allowCidrs;
        }

        public boolean isAllowBasicAuth() {
            return allowBasicAuth;
        }

        public void setAllowBasicAuth(boolean allowBasicAuth) {
            this.allowBasicAuth = allowBasicAuth;
        }

        public boolean isAllowTokenAuth() {
            return allowTokenAuth;
        }

        public void setAllowTokenAuth(boolean allowTokenAuth) {
            this.allowTokenAuth = allowTokenAuth;
        }

        public String getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        public AuthProperties getBasic() {
            return basic;
        }

        public void setBasic(AuthProperties basic) {
            this.basic = basic;
        }
    }
}
