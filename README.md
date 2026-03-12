# Proxy Hub

Netty + Spring Boot based enterprise proxy gateway.

- SOCKS5 proxy (RFC 1928, CONNECT)
- HTTP proxy
- HTTPS CONNECT tunnel proxy
- ACL policy (client CIDR whitelist, target host/port deny list)
- Optional auth for SOCKS5 and HTTP proxy
- Optional management-plane auth and CIDR restriction
- Per-client active connection quota
- Visual dashboard + runtime control APIs
- Java native MenuOps operation center (covers menu.sh capability set, no direct menu.sh invocation)
- `java -jar` runnable, with CentOS7 startup scripts

## 1. Architecture

Control plane and data plane are separated:

- Control plane:
  - Spring Boot Web + Thymeleaf dashboard
  - Runtime APIs for start/stop/restart and status query
  - Metrics/observability APIs
  - MenuOps async job engine (Java-native operation handlers)
- Data plane:
  - Netty listeners for SOCKS5 and HTTP
  - CONNECT tunnel and stream relay through bridge handlers

Listener mode is dual-port by design:

- `proxy.socks.port`: SOCKS5 listener
- `proxy.http.port`: HTTP/HTTPS proxy listener

This avoids protocol ambiguity and is easier to operate in production.

## 2. Build

Requirements:

- JDK 17+
- Maven 3.8+

Build jar:

```bash
mvn clean package -DskipTests
```

Run automated tests:

```bash
mvn test
```

Jar output example:

- `target/proxy-hub-1.0.0.jar`

## 3. Run (`java -jar`)

### 3.1 Local quick start

```bash
java -jar target/proxy-hub-1.0.0.jar
```

Dashboard:

- `http://127.0.0.1:9090/dashboard`

### 3.2 Use external production config

```bash
java -jar target/proxy-hub-1.0.0.jar \
  --spring.config.location=file:./conf/application-prod.yml
```

## 4. CentOS7 deployment

### 4.1 Directory suggestion

```text
/opt/proxy-hub/
  bin/
  conf/
  logs/
  run/
  target/proxy-hub-1.0.0.jar
```

### 4.2 Script startup

```bash
cd /opt/proxy-hub
chmod +x bin/*.sh

# start
bin/start.sh

# status
bin/status.sh

# stop
bin/stop.sh
```

`bin/start.sh` uses:

- `JAVA_OPTS` env (overrideable)
- `conf/application-prod.yml`
- pid file: `run/proxy-hub.pid`
- console log: `logs/console.out`

### 4.3 systemd hosting (recommended)

1. Copy service file:

```bash
cp /opt/proxy-hub/bin/proxy-hub.service /etc/systemd/system/
```

2. Reload and enable:

```bash
systemctl daemon-reload
systemctl enable proxy-hub
systemctl start proxy-hub
```

3. Check:

```bash
systemctl status proxy-hub
journalctl -u proxy-hub -f
```

## 5. Full configuration reference

All configurable keys currently used by the project are listed below.

### 5.1 Spring / server / management

| Key | Type | Default | Description |
|---|---|---|---|
| `spring.application.name` | string | `proxy-hub` | Application name |
| `spring.thymeleaf.cache` | boolean | `false` | Thymeleaf template cache |
| `server.port` | int | `9090` | Dashboard + management API HTTP port |
| `management.endpoints.web.exposure.include` | string/list | `health,info,metrics,prometheus` | Exposed actuator endpoints |
| `management.endpoint.health.show-details` | string | `always` | Health details visibility |

### 5.2 Proxy listeners

| Key | Type | Default | Description |
|---|---|---|---|
| `proxy.socks.name` | string | `SOCKS5` | Display name of SOCKS listener |
| `proxy.socks.enabled` | boolean | `true` | Enable SOCKS5 listener |
| `proxy.socks.bind-host` | string | `0.0.0.0` | Bind host for SOCKS5 listener |
| `proxy.socks.port` | int | `1080` | SOCKS5 listen port |
| `proxy.socks.auth.enabled` | boolean | `false` | Enable SOCKS5 username/password auth |
| `proxy.socks.auth.username` | string | `admin` | SOCKS5 auth username |
| `proxy.socks.auth.password` | string | `admin` | SOCKS5 auth password |
| `proxy.http.name` | string | `HTTP` | Display name of HTTP listener |
| `proxy.http.enabled` | boolean | `true` | Enable HTTP/HTTPS proxy listener |
| `proxy.http.bind-host` | string | `0.0.0.0` | Bind host for HTTP listener |
| `proxy.http.port` | int | `8080` | HTTP/HTTPS proxy listen port |
| `proxy.http.engine` | string | `legacy` | HTTP engine selector: `legacy` (Netty in-process) or `squid` (external process) |
| `proxy.http.auth.enabled` | boolean | `false` | Enable HTTP proxy basic auth |
| `proxy.http.auth.username` | string | `admin` | HTTP basic auth username |
| `proxy.http.auth.password` | string | `admin` | HTTP basic auth password |
| `proxy.http.squid.executable` | string | `squid` | Squid executable path when `proxy.http.engine=squid` |
| `proxy.http.squid.config-path` | string | `./conf/squid/squid.conf` | Squid config path |
| `proxy.http.squid.workdir` | string | `.` | Squid process working directory |
| `proxy.http.squid.access-log-path` | string | `./logs/squid/access.log` | Squid access log path used for dashboard metric ingestion |
| `proxy.http.squid.manage-config` | boolean | `true` | Render squid config from ProxyHub properties before start/reload |
| `proxy.http.squid.foreground` | boolean | `true` | Start squid with `-N` so lifecycle is controlled by ProxyHub |
| `proxy.http.squid.extra-args` | list[string] | `[]` | Extra arguments appended to squid startup command |

Notes for `proxy.http.engine=squid` (current phase):

- ProxyHub controls squid process lifecycle via `ProxyServer` runtime manager.
- When `proxy.http.squid.manage-config=true`, ProxyHub renders squid ACL rules from `proxy.acl.*`.
- `GET /api/v1/metrics/overview` will trigger incremental parse of squid access log and map it into dashboard counters.
- `proxy.http.auth.*` is still not mapped into squid auth helpers in this phase.
- A bootstrap config file is provided at `conf/squid/squid.conf`; it can be used with `manage-config=false`.

### 5.3 ACL policies

| Key | Type | Default | Description |
|---|---|---|---|
| `proxy.acl.enabled` | boolean | `false` | Enable ACL checks |
| `proxy.acl.allow-client-cidrs` | list[string] | `[]` | Client CIDR allow list (empty means allow all clients) |
| `proxy.acl.deny-target-hosts` | list[string] | `localhost,*.internal.local` | Target host deny rules |
| `proxy.acl.deny-target-ports` | list[int] | `25,3306,6379` | Target port deny list |

### 5.4 Performance

| Key | Type | Default | Description |
|---|---|---|---|
| `proxy.performance.connect-timeout-millis` | int | `10000` | Outbound connect timeout |
| `proxy.performance.idle-timeout-seconds` | int | `120` | Client idle timeout |
| `proxy.performance.boss-threads` | int | `1` | Netty boss eventloop threads |
| `proxy.performance.worker-threads` | int | `0` | Netty worker threads (`0` means Netty default) |
| `proxy.performance.backlog` | int | `1024` | Socket backlog |
| `proxy.performance.max-connections-per-client` | int | `0` | Max active sessions per client IP (`0` means unlimited) |

### 5.5 Dashboard and monitoring cache

| Key | Type | Default | Description |
|---|---|---|---|
| `proxy.dashboard.refresh-seconds` | int | `2` | Frontend auto-refresh interval |
| `proxy.dashboard.max-recent-events` | int | `3000` | In-memory recent events limit |
| `proxy.dashboard.timezone` | string | `Asia/Shanghai` | Dashboard timezone hint |
| `proxy.dashboard.warp-runtime-cache-seconds` | int | `6` | Cache TTL for `/api/v1/tools/warp-runtime-status` to reduce shell/network probe overhead |
| `proxy.dashboard.warp-history-enabled` | boolean | `true` | Enable SQLite persistence for WARP runtime history |
| `proxy.dashboard.warp-history-db-path` | string | `./logs/warp-history.db` | SQLite file path for WARP history |
| `proxy.dashboard.warp-history-sample-seconds` | int | `15` | Minimum sampling interval for writing history records |
| `proxy.dashboard.warp-history-retention-days` | int | `30` | Retention days for automatic history cleanup |
| `proxy.dashboard.warp-history-max-rows` | int | `100000` | Max retained rows after periodic cleanup |
| `proxy.dashboard.warp-alert-enabled` | boolean | `true` | Enable WARP alert engine persistence and APIs |
| `proxy.dashboard.warp-alert-db-path` | string | `./logs/warp-history.db` | SQLite file path for WARP alert events (can share DB with history) |
| `proxy.dashboard.warp-alert-connecting-threshold` | int | `4` | Consecutive samples threshold to trigger `WARP_CONNECTING_STUCK` |
| `proxy.dashboard.warp-alert-repeat-cooldown-seconds` | int | `120` | Cooldown for repeated active alert updates with changed details |
| `proxy.dashboard.warp-alert-retention-days` | int | `30` | Retention days for alert event cleanup |
| `proxy.dashboard.warp-alert-max-rows` | int | `200000` | Max retained alert events after cleanup |

### 5.6 Management access control

| Key | Type | Default | Description |
|---|---|---|---|
| `proxy.management.enabled` | boolean | `false` | Enable management-plane protection |
| `proxy.management.protect-actuator` | boolean | `false` | Include `/actuator/**` in protected scope |
| `proxy.management.allow-cidrs` | list[string] | `[]` | Allowed client CIDRs for management access (empty means allow all) |
| `proxy.management.allow-basic-auth` | boolean | `true` | Allow HTTP Basic auth for management access |
| `proxy.management.allow-token-auth` | boolean | `false` | Allow token header auth for management access |
| `proxy.management.access-token` | string | `""` | Token value for `X-ProxyHub-Token` header |
| `proxy.management.basic.enabled` | boolean | `true` | Enable basic credential checking |
| `proxy.management.basic.username` | string | `admin` | Basic auth username |
| `proxy.management.basic.password` | string | `admin` | Basic auth password |

### 5.7 Logging

| Key | Type | Default | Description |
|---|---|---|---|
| `logging.level.root` | string | `INFO` | Root log level |
| `logging.level.com.zqzqq.proxyhub` | string | `INFO` | Application package log level |

### 5.8 MenuOps (Java native operation center)

| Key | Type | Default | Description |
|---|---|---|---|
| `menu-ops.enabled` | boolean | `true` | Enable MenuOps job center APIs and UI |
| `menu-ops.bash-path` | string | `/bin/bash` | Shell executable used by Java operation handlers |
| `menu-ops.workdir` | string | `.` | Working directory for operation execution |
| `menu-ops.timeout-seconds` | int | `3600` | Timeout per command step |
| `menu-ops.max-log-lines` | int | `3000` | Max in-memory log lines per job |
| `menu-ops.max-history` | int | `100` | Max retained recent jobs |
| `menu-ops.max-concurrency` | int | `4` | Max concurrent MenuOps worker threads |
| `menu-ops.allow-raw-args` | boolean | `true` (dev) / `false` (prod) | Allow `RAW_ARGS` operation |
| `menu-ops.allow-destructive` | boolean | `false` (dev) / `true` (prod sample) | Allow destructive operations (uninstall/install/switch) |
| `menu-ops.allow-remote-scripts` | boolean | `false` (dev) / `true` (prod sample) | Allow remote script operations (`UPGRADE_BBR`, `MENU_UNLOCK_SCRIPT`) |
| `menu-ops.audit-log-path` | string | `/var/log/proxy-hub/strategy-audit.log` | Persistent audit log path for strategy auto-switch engine |
| `menu-ops.audit-db-enabled` | boolean | `true` | Enable SQLite persistence for MenuOps job audit records |
| `menu-ops.audit-db-path` | string | `./logs/menu-ops-audit.db` | SQLite file path for MenuOps audit records |
| `menu-ops.audit-db-retention-days` | int | `90` | Retention days for MenuOps audit cleanup |
| `menu-ops.audit-db-max-rows` | int | `200000` | Max retained MenuOps audit rows after cleanup |
| `menu-ops.diagnostics-dir` | string | `./logs/diagnostics` | Directory for persisted WARP diagnostics bundles |
| `menu-ops.diagnostics-retention-days` | int | `14` | Retention in days for diagnostics cleanup policy (`0` means disabled) |
| `menu-ops.diagnostics-max-files` | int | `300` | Max diagnostics files to keep (`0` means unlimited) |
| `menu-ops.diagnostics-max-download-bytes` | int | `52428800` | Max per-file download size allowed by diagnostics API (`0` means unlimited) |
| `menu-ops.auto-switch-endpoints` | string | `engage.cloudflareclient.com:2408,162.159.192.1:2408,162.159.193.1:2408` | Endpoint candidates for auto-switch attempts |
| `menu-ops.auto-switch-stacks` | string | `d,4,6` | Stack candidates for auto-switch attempts (`d`/`4`/`6`) |

## 6. Management APIs

- `GET /api/v1/runtime/status`
- `POST /api/v1/runtime/start`
- `POST /api/v1/runtime/stop`
- `POST /api/v1/runtime/restart`
- `POST /api/v1/runtime/reload`
- `GET /api/v1/metrics/overview`
- `POST /api/v1/tools/proxy-test`
- `POST /api/v1/tools/scenario-test`
- `GET /api/v1/tools/warp-runtime-status?refresh=false`
- `GET /api/v1/tools/warp-runtime-history?limit=120`
- `GET /api/v1/tools/warp-runtime-history/summary?minutes=60`
- `GET /api/v1/tools/warp-alerts?limit=120`
- `GET /api/v1/tools/warp-alerts/active?limit=20`
- `GET /api/v1/tools/warp-alerts/summary?minutes=60`
- `GET /api/v1/tools/warp-alert-rules`
- `PUT /api/v1/tools/warp-alert-rules`
- `POST /api/v1/tools/warp-alert-rules/reset`
- `GET /api/v1/menu-ops/catalog`
- `GET /api/v1/menu-ops/jobs?limit=40`
- `GET /api/v1/menu-ops/jobs/delta?limit=40&since=<epochMillis>`
- `GET /api/v1/menu-ops/jobs/{jobId}?includeLogs=true&tail=500`
- `GET /api/v1/menu-ops/jobs/{jobId}/logs.txt?tail=3000`
- `GET /api/v1/menu-ops/audit?limit=120`
- `GET /api/v1/menu-ops/audit/summary?minutes=240`
- `GET /api/v1/menu-ops/diagnostics?limit=80`
- `GET /api/v1/menu-ops/diagnostics/summary`
- `GET /api/v1/menu-ops/diagnostics/{fileName}`
- `DELETE /api/v1/menu-ops/diagnostics/{fileName}`
- `POST /api/v1/menu-ops/jobs`
- `POST /api/v1/menu-ops/jobs/{jobId}/cancel`

Query options:

- `GET /api/v1/tools/warp-runtime-status`
  - `refresh=true` forces real-time probing and bypasses cache.
  - Response includes structured WARP+ fields:
    - `warpTraceState` (`on` / `plus` / `off` / `-`)
    - `warpPlusAccount` (whether current account is Plus/Teams)
    - `warpPlusVerified` (connected + trace active + Plus/Teams account)
    - `connectivityPhase` / `connectivityReason` (parsed from `warp-cli -l status`)
    - `pmtudEnabled` / `pmtudStatus` (parsed from current WARP settings output)
    - `firewallPrecheckPass` / `firewallTcpStatus` / `firewallUdpStatus` (required edge-port precheck summary)
- `GET /api/v1/tools/warp-runtime-history`
  - Returns recent WARP runtime snapshots persisted in SQLite.
  - `limit` controls returned records.
- `GET /api/v1/tools/warp-runtime-history/summary`
  - Returns aggregated summary for a time window.
  - `minutes` controls summary window (default `60`).
- `GET /api/v1/tools/warp-alerts`
  - Returns recent WARP alert events from SQLite.
  - `limit` controls returned events.
- `GET /api/v1/tools/warp-alerts/active`
  - Returns current active alerts (latest state per alert code where active=1).
  - `limit` controls returned active alerts.
- `GET /api/v1/tools/warp-alerts/summary`
  - Returns alert aggregate metrics for a time window.
  - `minutes` controls summary window (default `60`).
- `GET /api/v1/tools/warp-alert-rules`
  - Returns current WARP alert rule config list (`alertCode`, `title`, `severity`, `enabled`, `updatedAt`).
- `PUT /api/v1/tools/warp-alert-rules`
  - Upserts one or more rule configs (`alertCode`, `severity`, `enabled`).
  - Returns full refreshed rule config list.
- `POST /api/v1/tools/warp-alert-rules/reset`
  - Resets all rule configs to built-in defaults and returns the full rule list.
- `GET /api/v1/menu-ops/jobs`
  - `limit` controls returned job count (default `40`, max clamped by `menu-ops.max-history`).
  - `since` (epoch millis) returns jobs updated after the specified timestamp for incremental polling.
- `GET /api/v1/menu-ops/jobs/delta`
  - Returns envelope with `serverNowEpochMillis`, `nextSinceEpochMillis`, `returnedCount`, and `items`.
  - `nextSinceEpochMillis` can be used as cursor for the next incremental request.
  - `compact=true` returns lightweight items (empty `commandLine/argument/logs`) for low-bandwidth polling.
- `GET /api/v1/menu-ops/jobs/{jobId}`
  - `includeLogs=true/false` controls whether logs are included.
  - `tail` can limit returned log lines (for example `tail=500`) to reduce payload size.
- `GET /api/v1/menu-ops/jobs/{jobId}/logs.txt`
  - Download text export of a job (metadata + logs).
  - `tail` can limit exported log lines.
- `GET /api/v1/menu-ops/audit`
  - Returns MenuOps audit records persisted in SQLite.
  - `limit` controls returned rows.
- `GET /api/v1/menu-ops/audit/summary`
  - Returns MenuOps audit aggregate metrics for a time window.
  - `minutes` controls summary window (default `240`).
- `GET /api/v1/menu-ops/diagnostics`
  - List diagnostics files under `menu-ops.diagnostics-dir`.
  - `limit` controls returned file count.
- `GET /api/v1/menu-ops/diagnostics/summary`
  - Returns diagnostics directory summary: file count, total bytes, latest modified timestamp, and retention policy values.
- `GET /api/v1/menu-ops/diagnostics/{fileName}`
  - Download one diagnostics file (enforced by `menu-ops.diagnostics-max-download-bytes`).
- `DELETE /api/v1/menu-ops/diagnostics/{fileName}`
  - Delete one diagnostics file.

When `proxy.management.enabled=true`, protected endpoints require one of:

- Basic auth (`Authorization: Basic ...`)
- Token auth (`X-ProxyHub-Token: <token>`) if enabled
- If both auth methods are disabled, at least one `proxy.management.allow-cidrs` rule must be configured (CIDR-only mode)

Proxy probe request example:

```json
{
  "mode": "SOCKS5",
  "targetUrl": "https://httpbin.org/ip",
  "proxyHost": "127.0.0.1",
  "proxyPort": 1080,
  "username": "optional_user",
  "password": "optional_pass",
  "timeoutMillis": 8000
}
```

Scenario probe request example:

```json
{
  "mode": "SOCKS5",
  "proxyHost": "127.0.0.1",
  "proxyPort": 1080,
  "username": null,
  "password": null,
  "timeoutMillis": 8000,
  "scenarios": [
    "CHATGPT",
    "NETFLIX",
    "GOOGLE_SCHOLAR",
    "TELEGRAM",
    "IPV4_EGRESS"
  ]
}
```

MenuOps request example:

```json
{
  "operationId": "SHOW_STATUS",
  "argument": null,
  "stdin": null,
  "confirmRisk": false
}
```

WARP alert rule update example:

```json
{
  "rules": [
    {
      "alertCode": "WARP_FIREWALL_BLOCKED",
      "severity": "CRITICAL",
      "enabled": true
    },
    {
      "alertCode": "WARP_GOOGLE_PROBE_FAILED",
      "severity": "LOW",
      "enabled": false
    }
  ]
}
```

High-risk example:

```json
{
  "operationId": "UNINSTALL_ALL",
  "confirmRisk": true
}
```

MenuOps design note:

- MenuOps in this project is Java-native orchestration (operation handlers + async jobs).
- It maps the menu capability set into Java operations and does not execute local `menu.sh` as runtime entrypoint.
- In addition to menu option parity, MenuOps also provides extra WARP operations:
  - `SHOW_WARP_ACCOUNT`
  - `VERIFY_WARP_PLUS`
  - `REREGISTER_WARP_ACCOUNT`
  - `SET_WARP_LICENSE`
  - `SET_WARP_TEAMS_ACCOUNT`
  - `LEAVE_WARP_TEAMS_ACCOUNT`
  - `SET_WARP_ACCOUNT_MODE`
  - `SET_CLIENT_MODE`
  - `SET_PMTUD_MODE`
  - `WARP_FIREWALL_PRECHECK`
  - `COLLECT_WARP_DIAGNOSTICS`
  - `CLEANUP_DIAGNOSTICS`
  - `START_WARP_PLUS`
  - `STOP_WARP_PLUS`
  - `REPAIR_WARP_NETWORK`
  - `TUNE_WARP_MTU`
  - `SET_WARP_ENDPOINT`
  - `APPLY_WARP_PROFILE`
  - `AUTO_RECOVER_WARP`
  - `CHECK_SCENARIO_REACHABILITY`
  - `AUTO_SWITCH_POLICY`
- `INSTALL_IPTABLES_STREAM` now includes policy-route domains for:
  - ChatGPT / OpenAI
  - Netflix
  - Google / Google Scholar
  - Telegram

Profile operation (`APPLY_WARP_PROFILE`) argument examples:

- `chatgpt`
- `google`
- `telegram`
- `netflix:hk`
- `full`

WARP+ and Teams operations:

- `START_WARP_PLUS`: explicit connect + connectivity verify
- `STOP_WARP_PLUS`: explicit disconnect
- `VERIFY_WARP_PLUS`: verify connected state + account tier + trace (`warp=on/plus`)
- `SET_CLIENT_MODE`: switch client mode without reinstall (`warp` / `proxy` / `proxy:40000`)
- `SET_PMTUD_MODE`: best-effort PMTUD toggle (`on` / `off`) depending on current warp-cli version
- `WARP_FIREWALL_PRECHECK`: precheck required Cloudflare WARP edge ports (`443,500,1701,4500,4443,8443,8095`)
- `SET_WARP_TEAMS_ACCOUNT` argument examples:
  - team organization name
  - team domain
  - team enrollment token
- `LEAVE_WARP_TEAMS_ACCOUNT`: leave/unenroll current teams account
- `SET_WARP_ACCOUNT_MODE`: account quick switch:
  - `free`
  - `plus:<license>`
  - `teams:<organization/domain/token>`
  - `teams-leave`
- `COLLECT_WARP_DIAGNOSTICS`: collect service + warp-cli + route + trace diagnostics in one job log
- `CLEANUP_DIAGNOSTICS`: cleanup diagnostics files by `diagnostics-retention-days` and `diagnostics-max-files` policy

Auto-switch engine operation (`AUTO_SWITCH_POLICY`) argument examples:

- `auto`
- `chatgpt`
- `netflix:hk`
- `google`
- `telegram`
- `full`

Auto-switch engine behavior:

- Iterates candidates from `menu-ops.auto-switch-endpoints` and `menu-ops.auto-switch-stacks`.
- Evaluates scenario reachability score for selected profile.
- Selects best combination and reapplies it as final runtime config.
- Writes persistent audit records to `menu-ops.audit-log-path`.

Dashboard built-in probe panel:

- Open `/dashboard`, go to `Proxy Connectivity Test`.
- Choose mode (`SOCKS5` or `HTTP`), set target URL, and optional credentials.
- Click `Fill Current Config` to auto-populate proxy host/port from listener settings.
- Click `Run Proxy Test` to execute probe.
- Click `Run Scenario Test` to batch-check `CHATGPT / NETFLIX / GOOGLE_SCHOLAR / TELEGRAM / IPV4_EGRESS`.
- Results are shown inline, and the latest 20 probe records are kept in panel history.

Dashboard WARP operations panel:

- Open `/dashboard`, go to `WARP 运行态与账户`.
- Runtime card shows:
  - `warp-cli` installed state
  - `warp-svc` service state
  - current connectivity phase/reason from `warp-cli -l status`
  - WARP connected/disconnected state
  - PMTUD effective state (parsed from warp settings)
  - firewall precheck summary (TCP/UDP required port probes)
  - account type / mode / tunnel protocol / teams organization
  - Cloudflare trace state (`warp=on/plus/off`)
  - WARP+ account detection and verification state
  - public IPv4 / public IPv6 / Google reachability probe
- WARP history sub-panel:
  - SQLite persisted runtime snapshots
  - recent timeline table (connected/phase/firewall/PMTUD/IP)
  - window summary metrics for quick stability diagnosis
- Quick actions are mapped to MenuOps jobs:
  - `TOGGLE_WARP`
  - `START_WARP_PLUS`
  - `STOP_WARP_PLUS`
  - `TOGGLE_CLIENT` (`warp r` equivalent)
  - `SET_WARP_ACCOUNT_MODE` (`free` / `plus:<license>` / `teams:<account>` / `teams-leave`)
  - `SET_CLIENT_MODE` (`warp` / `proxy[:port]`)
  - `INSTALL_CLIENT_WARP`
  - `INSTALL_CLIENT_PROXY`
  - `REPAIR_WARP_NETWORK`
  - `TUNE_WARP_MTU`
  - `SET_STACK_PRIORITY`
  - `SET_WARP_ENDPOINT`
  - `SHOW_WARP_ACCOUNT`
  - `VERIFY_WARP_PLUS`
  - `SET_PMTUD_MODE`
  - `WARP_FIREWALL_PRECHECK`
  - `COLLECT_WARP_DIAGNOSTICS`
  - `CLEANUP_DIAGNOSTICS`
  - `SET_WARP_LICENSE`
  - `REREGISTER_WARP_ACCOUNT`
  - `APPLY_WARP_PROFILE`
  - `AUTO_RECOVER_WARP`
  - `AUTO_SWITCH_POLICY`
  - `SET_WARP_TEAMS_ACCOUNT`
  - `LEAVE_WARP_TEAMS_ACCOUNT`
- Diagnostics files panel (MenuOps area):
  - List persisted diagnostics bundles.
  - View diagnostics summary (count/bytes/latest + retention policy).
  - One-click download / delete per file.
  - One-click cleanup by retention policy.
  - Supports auto-refresh and manual refresh.

Probe troubleshooting (`SOCKS5 connect failed: RULESET_BLOCKED`):

- If ACL is enabled and you run probe from dashboard, add loopback CIDR:
  - `proxy.acl.allow-client-cidrs` includes `127.0.0.1/32`
- Ensure target port is not denied by `proxy.acl.deny-target-ports`
- If per-client quota is enabled, check `proxy.performance.max-connections-per-client`

WARP client troubleshooting (`INSTALL_CLIENT_WARP` stuck at `Connecting`):

- Ensure outbound traffic to Cloudflare WARP endpoints is allowed:
  - IP example from runtime logs: `162.159.198.2`
  - Ports used by WARP runtime: `443,500,1701,4500,4443,8443,8095` (UDP/TCP as required)
- Verify cloud security group and host firewall (`iptables/nftables/firewalld`) are not blocking these ports.
- If IPv6 is unavailable, this is acceptable; WARP should still connect via IPv4.
- MenuOps now auto-attempts tunnel-protocol fallback (`WireGuard`/`MASQUE`) during connect retries.
- If protocol fallback still fails, MenuOps additionally tries endpoint candidates from `menu-ops.auto-switch-endpoints`.
- `START_WARP_PLUS` also uses the same resilient connect chain (daemon recovery + registration repair + protocol/endpoint fallback).

Actuator endpoints (default):

- `/actuator/health`
- `/actuator/info`
- `/actuator/metrics`
- `/actuator/prometheus`

## 7. Proxy verification examples

### 7.1 SOCKS5

No auth:

```bash
curl --proxy socks5h://127.0.0.1:1080 https://httpbin.org/ip
```

With auth:

```bash
curl --proxy socks5h://user:pass@127.0.0.1:1080 https://httpbin.org/ip
```

### 7.2 HTTP proxy

No auth:

```bash
curl -x http://127.0.0.1:8080 http://httpbin.org/get
```

With basic auth:

```bash
curl -x http://user:pass@127.0.0.1:8080 http://httpbin.org/get
```

### 7.3 HTTPS through CONNECT tunnel

```bash
curl -x http://127.0.0.1:8080 https://httpbin.org/ip
```

## 8. Production hardening checklist

- Enable auth on both listeners:
  - `proxy.socks.auth.enabled=true`
  - `proxy.http.auth.enabled=true`
- Enable ACL and define strict `allow-client-cidrs`
- Restrict inbound firewall to trusted source IPs only
- Put management port behind internal network or reverse proxy auth
- Rotate credentials and avoid default passwords
- Export actuator metrics to Prometheus/Grafana
- Set proper JVM options (`JAVA_OPTS`) and log retention policy

## 9. Current capability boundaries

- HTTPS is supported as CONNECT tunnel passthrough only (no MITM TLS decrypt)
- SOCKS5 currently supports CONNECT command
- CIDR matcher currently handles IPv4 CIDR rules

## 10. Release packaging for CentOS7

Build and package in Windows (PowerShell):

```powershell
cd C:\usersoft\sockets
.\bin\build-release.ps1
```

Build and package in Linux:

```bash
cd /opt/proxy-hub-source
chmod +x bin/build-release.sh
bin/build-release.sh
```

Skip build stage if jar already exists:

```powershell
.\bin\build-release.ps1 -SkipBuild
```

```bash
bin/build-release.sh --skip-build
```

Output:

- `dist/proxy-hub-<version>-centos7.tar.gz`

## 11. Release packaging for Ubuntu

Build and package in Windows (PowerShell):

```powershell
cd C:\usersoft\sockets
.\bin\build-release-ubuntu.ps1
```

Build and package in Linux:

```bash
cd /opt/proxy-hub-source
chmod +x bin/build-release-ubuntu.sh
bin/build-release-ubuntu.sh
```

Skip build stage if jar already exists:

```powershell
.\bin\build-release-ubuntu.ps1 -SkipBuild
```

```bash
bin/build-release-ubuntu.sh --skip-build
```

Output:

- `dist/proxy-hub-<version>-ubuntu.tar.gz`

Ubuntu package production install:

```bash
tar -xzf proxy-hub-<version>-ubuntu.tar.gz -C /opt
cd /opt/proxy-hub-<version>-ubuntu
chmod +x bin/*.sh
sudo bash bin/install-ubuntu.sh
```

