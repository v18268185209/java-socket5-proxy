const cfg = window.PROXY_DASHBOARD_CONFIG || {};
const refreshSeconds = Number(cfg.refreshSeconds || 2);
const timezone = cfg.timezone || 'Asia/Shanghai';
const socksHostCfg = String(cfg.socksHost || '127.0.0.1');
const socksPortCfg = Number(cfg.socksPort || 1080);
const socksAuthEnabledCfg = Boolean(cfg.socksAuthEnabled);
const httpHostCfg = String(cfg.httpHost || '127.0.0.1');
const httpPortCfg = Number(cfg.httpPort || 8080);
const httpAuthEnabledCfg = Boolean(cfg.httpAuthEnabled);
const menuOpsEnabledCfg = Boolean(cfg.menuOpsEnabled);
const menuOpsWorkdirCfg = String(cfg.menuOpsWorkdir || '.');
const menuOpsAllowDestructiveCfg = Boolean(cfg.menuOpsAllowDestructive);
const menuOpsAllowRemoteScriptsCfg = Boolean(cfg.menuOpsAllowRemoteScripts);
const menuOpsAuditLogPathCfg = String(cfg.menuOpsAuditLogPath || '/var/log/proxy-hub/strategy-audit.log');
const menuOpsAuditDbEnabledCfg = Boolean(cfg.menuOpsAuditDbEnabled);
const menuOpsAuditDbPathCfg = String(cfg.menuOpsAuditDbPath || './logs/menu-ops-audit.db');
const menuOpsAuditDbRetentionDaysCfg = Number(cfg.menuOpsAuditDbRetentionDays || 90);
const menuOpsAuditDbMaxRowsCfg = Number(cfg.menuOpsAuditDbMaxRows || 200000);
const menuOpsDiagnosticsDirCfg = String(cfg.menuOpsDiagnosticsDir || './logs/diagnostics');
const menuOpsDiagnosticsRetentionDaysCfg = Number(cfg.menuOpsDiagnosticsRetentionDays || 0);
const menuOpsDiagnosticsMaxFilesCfg = Number(cfg.menuOpsDiagnosticsMaxFiles || 0);
const menuOpsDiagnosticsMaxDownloadBytesCfg = Number(cfg.menuOpsDiagnosticsMaxDownloadBytes || 0);
const menuOpsAutoSwitchEndpointsCfg = String(cfg.menuOpsAutoSwitchEndpoints || '');
const menuOpsAutoSwitchStacksCfg = String(cfg.menuOpsAutoSwitchStacks || '');
const warpAlertEnabledCfg = cfg.warpAlertEnabled !== false;
const WARP_FEATURE_ENABLED = false;
const PROBE_HISTORY_KEY = 'proxyHubProbeHistoryV1';
const PROBE_HISTORY_MAX = 20;
const BASE_REFRESH_MS = Math.max(1000, Math.max(1, refreshSeconds) * 1000);
const MENU_REFRESH_MS_VISIBLE = Math.max(3000, BASE_REFRESH_MS * 2);
const WARP_REFRESH_MS_VISIBLE = Math.max(5000, BASE_REFRESH_MS * 3);
const DIAG_REFRESH_MS_VISIBLE = Math.max(12000, BASE_REFRESH_MS * 6);
const HIDDEN_REFRESH_MS = 15000;
const UI_LOG_MAX_LINES = 500;
const MAX_RENDER_SESSIONS = 300;
const MENU_JOBS_FETCH_LIMIT = 40;
const MENU_JOBS_DELTA_ROUNDS_MAX = 6;

const I18N = {
  title: '\u4F01\u4E1A\u7EA7\u4EE3\u7406\u8FD0\u7EF4\u63A7\u5236\u53F0',
  eyebrow: 'Proxy Hub \u7BA1\u7406\u5E73\u9762',
  hero: '\u4F01\u4E1A\u7EA7\u4EE3\u7406\u8FD0\u7EF4\u63A7\u5236\u53F0',
  socksPort: 'SOCKS5 \u76D1\u542C\u7AEF\u53E3',
  httpPort: 'HTTP/HTTPS \u76D1\u542C\u7AEF\u53E3',
  runtimeMeta: '\u5237\u65B0\u5468\u671F {s}s | \u65F6\u533A {tz}',
  start: '\u542F\u52A8\u76D1\u542C',
  stop: '\u505C\u6B62\u76D1\u542C',
  restart: '\u91CD\u542F\u76D1\u542C',
  mActive: '\u5F53\u524D\u6D3B\u8DC3\u8FDE\u63A5',
  mTotal: '\u7D2F\u8BA1\u8FDE\u63A5\u6570',
  mBlocked: '\u62E6\u622A\u8BF7\u6C42\u6570',
  mAuthFail: '\u8BA4\u8BC1\u5931\u8D25\u6570',
  mConnFail: '\u8FDE\u63A5\u5931\u8D25\u6570',
  mIngress: '\u5165\u7AD9\u6D41\u91CF',
  mEgress: '\u51FA\u7AD9\u6D41\u91CF',
  protocolTitle: '\u534F\u8BAE\u5206\u5E03',
  probeTitle: '\u4EE3\u7406\u8FDE\u901A\u6027\u6D4B\u8BD5',
  probeSub: '\u652F\u6301 SOCKS5 \u548C HTTP \u4EE3\u7406\u63A2\u6D4B',
  labelMode: '\u6D4B\u8BD5\u6A21\u5F0F',
  optSocks5: 'SOCKS5 \u4EE3\u7406',
  optHttp: 'HTTP \u4EE3\u7406',
  labelTarget: '\u76EE\u6807 URL',
  labelProxyHost: '\u4EE3\u7406\u4E3B\u673A\uFF08\u53EF\u7A7A\uFF09',
  labelProxyPort: '\u4EE3\u7406\u7AEF\u53E3\uFF08\u53EF\u7A7A\uFF09',
  labelUser: '\u8BA4\u8BC1\u7528\u6237\u540D\uFF08\u53EF\u7A7A\uFF09',
  labelPass: '\u8BA4\u8BC1\u5BC6\u7801\uFF08\u53EF\u7A7A\uFF09',
  labelTimeout: '\u8D85\u65F6\u6BEB\u79D2',
  probeBtn: '\u6267\u884C\u4EE3\u7406\u6D4B\u8BD5',
  scenarioBtn: '\u6267\u884C\u573A\u666F\u6D4B\u8BD5',
  fillCurrent: '\u586B\u5145\u5F53\u524D\u914D\u7F6E',
  probeTip: '\u5EFA\u8BAE\u5148\u6D4B\u8BD5 SOCKS5\uFF0C\u518D\u6D4B\u8BD5 HTTP/HTTPS \u94FE\u8DEF\u3002',
  probeInit: '\u5C1A\u672A\u6267\u884C\u6D4B\u8BD5',
  scenarioTitle: '\u573A\u666F\u6D4B\u8BD5\u77E9\u9635',
  scenarioInit: '\u5C1A\u672A\u6267\u884C\u573A\u666F\u6D4B\u8BD5',
  scenarioRunning: '\u573A\u666F\u6D4B\u8BD5\u4E2D...',
  scenarioSelectRequired: '\u8BF7\u81F3\u5C11\u9009\u62E9\u4E00\u4E2A\u573A\u666F',
  scenarioSummary: '\u573A\u666F\u6D4B\u8BD5\u5B8C\u6210',
  scenario_CHATGPT: 'ChatGPT',
  scenario_NETFLIX: 'Netflix',
  scenario_GOOGLE_SCHOLAR: 'Google Scholar',
  scenario_TELEGRAM: 'Telegram',
  scenario_IPV4_EGRESS: 'IPv4 \u51FA\u53E3',
  probeHistoryTitle: '\u6D4B\u8BD5\u5386\u53F2',
  probeHistorySub: '\u6700\u8FD1 20 \u6761\u8BB0\u5F55',
  thProbeTime: '\u65F6\u95F4',
  thProbeMode: '\u6A21\u5F0F',
  thProbeStatus: '\u7ED3\u679C',
  thProbeTarget: '\u76EE\u6807',
  thProbeProxy: '\u4EE3\u7406',
  thProbeCost: '\u8017\u65F6',
  selfCheckTitle: '\u8FD0\u884C\u65F6\u81EA\u68C0',
  selfCheckSub: '\u914D\u7F6E\u6765\u6E90\u3001\u76D1\u542C\u5668\u3001ACL/Auth\u3001\u5916\u90E8 HTTP \u5F15\u64CE\u4E0E\u5931\u8D25 TopN',
  selfCheckConfigTitle: '\u914D\u7F6E\u6765\u6E90',
  selfCheckConfigSub: '\u5F53\u524D\u751F\u6548\u7684\u914D\u7F6E\u6765\u6E90',
  selfCheckTopFailuresTitle: 'Top \u5931\u8D25\u539F\u56E0',
  selfCheckTopFailuresSub: '\u6700\u8FD1\u5931\u8D25\u539F\u56E0 TopN',
  failureReasonsTitle: '\u5931\u8D25\u539F\u56E0\u8BA1\u6570',
  failureReasonsSub: '\u6392\u969C\u65F6\u4F18\u5148\u770B\u7684\u5931\u8D25\u539F\u56E0\u8BA1\u6570',
  thFailureReason: '\u5931\u8D25\u539F\u56E0',
  thFailureCount: '\u6B21\u6570',
  emptyFailureReason: '\u6682\u65E0\u5931\u8D25\u539F\u56E0',
  selfCheckLoading: '\u81EA\u68C0\u4FE1\u606F\u52A0\u8F7D\u4E2D...',
  selfCheckUnavailable: '\u81EA\u68C0\u4FE1\u606F\u6682\u4E0D\u53EF\u7528',
  selfCheckConfigEmpty: '\u672A\u8BC6\u522B\u5230\u914D\u7F6E\u6765\u6E90',
  selfCheckProfilesEmpty: 'default',
  selfCheckTransport: '\u4F20\u8F93\u6A21\u578B',
  selfCheckProfiles: '\u6D3B\u52A8 Profiles',
  selfCheckAcl: 'ACL',
  selfCheckConnLimit: '\u5355\u5BA2\u6237\u7AEF\u8FDE\u63A5\u4E0A\u9650',
  selfCheckSocks: 'SOCKS5 \u76D1\u542C',
  selfCheckHttp: 'HTTP \u76D1\u542C',
  selfCheckHttpEngine: 'HTTP \u5F15\u64CE',
  selfCheckAuthOn: '\u8BA4\u8BC1\u5F00\u542F',
  selfCheckAuthOff: '\u8BA4\u8BC1\u5173\u95ED',
  selfCheckAclOn: 'ACL \u5F00\u542F',
  selfCheckAclOff: 'ACL \u5173\u95ED',
  selfCheckEnabled: '\u5DF2\u542F\u7528',
  selfCheckDisabled: '\u5DF2\u7981\u7528',
  selfCheckManagedConfig: '\u6258\u7BA1\u914D\u7F6E',
  selfCheckManualConfig: '\u624B\u52A8\u914D\u7F6E',
  runtimeMetaDetail: '\u5237\u65B0\u5468\u671F {s}s | \u65F6\u533A {tz} | \u4F20\u8F93 {transport} | Profiles {profiles}',
  warpTitle: 'WARP \u8FD0\u884C\u6001\u4E0E\u8D26\u6237',
  warpSub: 'WARP+ \u542F\u505C\u3001Teams \u8D26\u6237\u4E0E\u516C\u7F51\u94FE\u8DEF\u53EF\u89C6\u5316',
  warpLabelCli: 'warp-cli',
  warpLabelService: 'warp-svc \u670D\u52A1',
  warpLabelConnected: '\u8FDE\u63A5\u72B6\u6001',
  warpLabelStatus: '\u5B88\u62A4\u8FDB\u7A0B\u72B6\u6001',
  warpLabelPhase: '\u8FDE\u63A5\u9636\u6BB5',
  warpLabelReason: '\u9636\u6BB5\u539F\u56E0',
  warpLabelAccount: '\u8D26\u6237\u7C7B\u578B',
  warpLabelRegId: '\u6CE8\u518C ID',
  warpLabelMode: '\u8FD0\u884C\u6A21\u5F0F',
  warpLabelProtocol: '\u96A7\u9053\u534F\u8BAE',
  warpLabelPmtud: 'PMTUD',
  warpLabelFirewallPrecheck: '\u9632\u706B\u5899\u9884\u68C0',
  warpLabelFirewallTcp: '\u9632\u706B\u5899 TCP',
  warpLabelFirewallUdp: '\u9632\u706B\u5899 UDP',
  warpLabelOrg: 'Teams \u7EC4\u7EC7',
  warpLabelTrace: 'Cloudflare Trace',
  warpLabelPlusAccount: 'WARP+ \u8D26\u6237',
  warpLabelPlusVerified: 'WARP+ \u9A8C\u8BC1',
  warpLabelIpv4: '\u516C\u7F51 IPv4',
  warpLabelIpv6: '\u516C\u7F51 IPv6',
  warpLabelGoogle: 'Google \u63A2\u6D4B',
  warpLabelDns: 'DNS \u63A2\u6D4B',
  warpLabelUdp: 'Edge UDP 443',
  warpLabelTcp: 'Edge TCP 443',
  warpLabelRoute: '\u8DEF\u7531\u72B6\u6001',
  warpLabelChecked: '\u68C0\u67E5\u65F6\u95F4',
  warpHistoryTitle: 'WARP \u5386\u53F2\u8D8B\u52BF',
  warpHistorySub: 'SQLite \u6301\u4E45\u5316\u91C7\u6837',
  thWarpHistoryTime: '\u65F6\u95F4',
  thWarpHistoryConnected: '\u8FDE\u63A5',
  thWarpHistoryPhase: '\u9636\u6BB5',
  thWarpHistoryFirewall: '\u9632\u706B\u5899\u9884\u68C0',
  thWarpHistoryPmtud: 'PMTUD',
  thWarpHistoryIp: '\u51FA\u53E3 IPv4',
  warpHistoryLoading: '\u5386\u53F2\u8BB0\u5F55\u52A0\u8F7D\u4E2D...',
  warpHistoryEmpty: '\u6682\u65E0 WARP \u5386\u53F2\u8BB0\u5F55',
  warpHistorySummaryFmt: '\u7A97\u53E3: {m}\u5206\u949F | \u6837\u672C: {t} | \u5DF2\u8FDE\u63A5: {c} | \u65AD\u8FDE: {d} | \u9632\u706B\u5899\u9884\u68C0\u5931\u8D25: {f} | PMTUD \u5173\u95ED: {po} | PMTUD \u672A\u77E5: {pu} | \u4E3B\u5BFC\u9636\u6BB5: {p}',
  warpAlertTitle: 'WARP \u544A\u8B66\u4E2D\u5FC3',
  warpAlertSub: '\u57FA\u4E8E\u8FD0\u884C\u6001\u89C4\u5219\u7684\u5B9E\u65F6\u544A\u8B66\u4E0E\u6062\u590D\u8FFD\u8E2A',
  warpAlertSummaryLoading: '\u544A\u8B66\u6C47\u603B\u52A0\u8F7D\u4E2D...',
  warpAlertSummaryEmpty: '\u6682\u65E0 WARP \u544A\u8B66\u4E8B\u4EF6',
  warpAlertSummaryFmt: '\u7A97\u53E3: {m}\u5206\u949F | \u4E8B\u4EF6: {t} | \u6D3B\u8DC3: {a} | \u5DF2\u6062\u590D: {r} | CRITICAL: {c} | HIGH: {h} | MEDIUM: {md} | LOW: {l} | \u9AD8\u9891\u544A\u8B66: {top}',
  warpAlertActiveEmpty: '\u6682\u65E0\u6D3B\u8DC3\u544A\u8B66',
  warpAlertActiveFmt: '\u6D3B\u8DC3\u544A\u8B66({n}): {codes}',
  warpAlertStateActive: '\u6D3B\u8DC3',
  warpAlertStateResolved: '\u5DF2\u6062\u590D',
  warpAlertRuleTitle: '\u544A\u8B66\u89C4\u5219\u914D\u7F6E',
  warpAlertRuleSub: '\u542F\u7528\u72B6\u6001\u4E0E\u7EA7\u522B\u5B9E\u65F6\u751F\u6548',
  warpAlertRuleLoading: '\u89C4\u5219\u52A0\u8F7D\u4E2D...',
  warpAlertRuleEmpty: '\u6682\u65E0\u53EF\u7528\u544A\u8B66\u89C4\u5219',
  warpAlertRuleSave: '\u4FDD\u5B58\u89C4\u5219',
  warpAlertRuleReset: '\u6062\u590D\u9ED8\u8BA4',
  warpAlertRuleSaveDone: '\u544A\u8B66\u89C4\u5219\u5DF2\u4FDD\u5B58',
  warpAlertRuleResetDone: '\u544A\u8B66\u89C4\u5219\u5DF2\u6062\u590D\u9ED8\u8BA4',
  warpAlertRuleEnabled: '\u542F\u7528',
  warpAlertRuleDisabled: '\u7981\u7528',
  thWarpAlertRuleCode: '\u89C4\u5219\u7F16\u7801',
  thWarpAlertRuleTitle: '\u6807\u9898',
  thWarpAlertRuleSeverity: '\u7EA7\u522B',
  thWarpAlertRuleEnabled: '\u542F\u7528',
  thWarpAlertRuleUpdated: '\u66F4\u65B0\u65F6\u95F4',
  thWarpAlertTime: '\u65F6\u95F4',
  thWarpAlertSeverity: '\u7EA7\u522B',
  thWarpAlertCode: '\u544A\u8B66\u7F16\u7801',
  thWarpAlertState: '\u72B6\u6001',
  thWarpAlertDetail: '\u8BE6\u60C5',
  menuAuditTitle: 'MenuOps \u5BA1\u8BA1',
  menuAuditSub: 'SQLite \u5BA1\u8BA1\u8BB0\u5F55\u4E0E\u8D8B\u52BF\u6C47\u603B',
  menuAuditSummaryLoading: '\u5BA1\u8BA1\u6C47\u603B\u52A0\u8F7D\u4E2D...',
  menuAuditSummaryEmpty: '\u6682\u65E0\u5BA1\u8BA1\u8BB0\u5F55',
  menuAuditSummaryFmt: '\u7A97\u53E3: {m}\u5206\u949F | \u8BB0\u5F55: {t} | \u6210\u529F: {s} | \u5931\u8D25: {f} | \u8D85\u65F6: {to} | \u53D6\u6D88: {c} | \u8FD0\u884C: {r} | \u6392\u961F: {p} | \u9AD8\u9891\u64CD\u4F5C: {top}',
  menuAuditNoData: '\u6682\u65E0\u5BA1\u8BA1\u8BB0\u5F55',
  menuAuditHintFmt: '\u5BA1\u8BA1\u8BB0\u5F55: {n} | DB: {db}',
  thMenuAuditTime: '\u65F6\u95F4',
  thMenuAuditOp: '\u64CD\u4F5C',
  thMenuAuditStatus: '\u72B6\u6001',
  thMenuAuditRisk: '\u98CE\u9669',
  thMenuAuditCost: '\u8017\u65F6',
  warpLicenseArgLabel: 'WARP+ \u6388\u6743\u7801',
  warpTeamsArgLabel: 'WARP Teams \u53C2\u6570',
  warpStackArgLabel: '\u6808\u4F18\u5148\u7EA7\u53C2\u6570',
  warpEndpointArgLabel: 'WARP Endpoint \u53C2\u6570',
  warpProfileArgLabel: '\u7B56\u7565 Profile \u53C2\u6570',
  warpBtnInstallClientWarp: '\u5B89\u88C5 Client WARP \u6A21\u5F0F',
  warpBtnInstallClientProxy: '\u5B89\u88C5 Client Proxy \u6A21\u5F0F',
  warpBtnInterfaceToggle: 'WARP \u63A5\u53E3\u5F00\u5173',
  warpHintInit: '\u7B49\u5F85\u5237\u65B0 WARP \u8FD0\u884C\u6001',
  warpQuickInit: '\u5FEB\u6377\u64CD\u4F5C\u4F1A\u8C03\u7528 MenuOps \u4F5C\u4E1A\u4E2D\u5FC3\u5E76\u8BB0\u5F55\u5BA1\u8BA1\u65E5\u5FD7',
  warpBtnStart: '\u542F\u52A8 WARP+',
  warpBtnStop: '\u65AD\u5F00 WARP+',
  warpBtnToggleClient: '\u8FDE\u63A5/\u65AD\u5F00 Client (warp r)',
  warpBtnClientModeWarp: '\u5207\u6362 Client \u5230 WARP \u6A21\u5F0F',
  warpBtnClientModeProxy: '\u5207\u6362 Client \u5230 Proxy \u6A21\u5F0F',
  warpBtnRepair: '\u4FEE\u590D WARP \u7F51\u7EDC',
  warpBtnTuneMtu: '\u81EA\u52A8\u8C03\u4F18 MTU',
  warpBtnSetStackPriority: '\u8BBE\u7F6E\u6808\u4F18\u5148\u7EA7',
  warpBtnSetEndpoint: '\u8BBE\u7F6E Endpoint',
  warpBtnShowAccount: '\u67E5\u770B WARP \u8D26\u6237',
  warpBtnVerifyPlus: '\u68C0\u67E5 WARP+ \u72B6\u6001',
  warpBtnPmtudOn: '\u5F00\u542F PMTUD',
  warpBtnPmtudOff: '\u5173\u95ED PMTUD',
  warpBtnFirewallPrecheck: '\u9632\u706B\u5899\u9884\u68C0',
  warpBtnDiagnostics: '\u91C7\u96C6 WARP \u8BCA\u65AD',
  warpBtnSetLicense: '\u8BBE\u7F6E WARP+ \u6388\u6743',
  warpBtnReregister: '\u91CD\u6CE8\u518C WARP \u8D26\u6237',
  warpBtnApplyProfile: '\u5E94\u7528 Profile',
  warpBtnAutoRecover: '\u4E00\u952E\u81EA\u6108 WARP',
  warpBtnAutoSwitch: '\u7B56\u7565\u81EA\u52A8\u5207\u6362',
  warpBtnSetTeams: '\u8BBE\u7F6E Teams \u8D26\u6237',
  warpBtnLeaveTeams: '\u9000\u51FA Teams \u8D26\u6237',
  warpBtnRefresh: '\u5237\u65B0 WARP \u72B6\u6001',
  warpStateInstalled: '\u5DF2\u5B89\u88C5',
  warpStateMissing: '\u672A\u5B89\u88C5',
  warpStateActive: '\u8FD0\u884C\u4E2D',
  warpStateInactive: '\u672A\u8FD0\u884C',
  warpStateConnected: '\u5DF2\u8FDE\u63A5',
  warpStateDisconnected: '\u672A\u8FDE\u63A5',
  warpStateYes: '\u662F',
  warpStateNo: '\u5426',
  warpStateNA: '\u4E0D\u9002\u7528',
  warpGoogleOk: '\u53EF\u8BBF\u95EE',
  warpGoogleFail: '\u4E0D\u53EF\u8BBF\u95EE',
  warpDiagOk: '\u6B63\u5E38',
  warpDiagFail: '\u5F02\u5E38',
  warpQuickNeedLicense: '\u8BF7\u5148\u8F93\u5165 WARP+ \u6388\u6743\u7801',
  warpQuickNeedArg: '\u8BF7\u5148\u8F93\u5165 Teams \u53C2\u6570 (organization/domain/token)',
  warpQuickNeedEndpoint: '\u8BF7\u5148\u8F93\u5165 Endpoint \u53C2\u6570\uff0c\u4F8B\u5982 engage.cloudflareclient.com:2408',
  warpTrackQueued: '\u4F5C\u4E1A\u5DF2\u63D0\u4EA4',
  warpTrackRunning: '\u4F5C\u4E1A\u8FDB\u884C\u4E2D',
  warpTrackDone: '\u4F5C\u4E1A\u5DF2\u5B8C\u6210',
  warpTrackLost: '\u8FFD\u8E2A\u4F5C\u4E1A\u5DF2\u4E0D\u5728\u5217\u8868\u4E2D',
  warpQuickDisabled: '\u5F53\u524D\u672A\u542F\u7528 menu-ops \u6216\u64CD\u4F5C\u76EE\u5F55\u672A\u52A0\u8F7D',
  warpStackSelected: '\u5DF2\u9009\u62E9\u6808\u7B56\u7565: {stack}',
  warpProfileSelected: '\u5DF2\u9009\u62E9\u7B56\u7565: {profile} | \u5EFA\u8BAE\u6808: {stack}',
  listeners: '\u76D1\u542C\u5668\u72B6\u6001',
  listenersSub: '\u5404\u76D1\u542C\u5668\u8FD0\u884C\u72B6\u6001',
  listenerName: '\u76D1\u542C\u5668',
  listenerStatus: '\u72B6\u6001',
  events: '\u6700\u8FD1\u4E8B\u4EF6',
  eventsSub: '\u5C55\u793A\u6700\u8FD1 80 \u6761\u4E8B\u4EF6',
  sessions: '\u6D3B\u8DC3\u4F1A\u8BDD',
  sessionsSub: '\u5B9E\u65F6\u4EE3\u7406\u8FDE\u63A5\u89C6\u56FE',
  thId: '\u4F1A\u8BDD ID',
  thProtocol: '\u534F\u8BAE',
  thClient: '\u5BA2\u6237\u7AEF',
  thTarget: '\u76EE\u6807\u5730\u5740',
  thIn: '\u5165\u7AD9',
  thOut: '\u51FA\u7AD9',
  thStart: '\u5F00\u59CB\u65F6\u95F4',
  stateRunning: '\u8FD0\u884C\u4E2D',
  stateStopped: '\u5DF2\u505C\u6B62',
  stateLoading: '\u52A0\u8F7D\u4E2D',
  stateError: '\u9519\u8BEF',
  emptyListener: '\u6682\u65E0\u76D1\u542C\u5668\u4FE1\u606F',
  emptySession: '\u5F53\u524D\u65E0\u6D3B\u8DC3\u4F1A\u8BDD',
  emptyEvent: '\u6682\u65E0\u4E8B\u4EF6',
  lastUpdate: '\u6700\u8FD1\u66F4\u65B0\u65F6\u95F4',
  probeRunning: '\u6D4B\u8BD5\u4E2D...',
  probeError: '\u6D4B\u8BD5\u5F02\u5E38',
  targetEmpty: '\u76EE\u6807 URL \u4E0D\u80FD\u4E3A\u7A7A',
  resultMode: '\u6A21\u5F0F',
  resultStatus: '\u7ED3\u679C',
  resultMessage: '\u6D88\u606F',
  resultTarget: '\u76EE\u6807',
  resultProxy: '\u4EE3\u7406',
  resultCost: '\u8017\u65F6',
  resultHttp: 'HTTP \u72B6\u6001\u7801',
  resultSnippet: '\u54CD\u5E94\u7247\u6BB5',
  success: '\u6210\u529F',
  fail: '\u5931\u8D25',
  fillDone: '\u5DF2\u586B\u5145\u5F53\u524D\u4EE3\u7406\u914D\u7F6E',
  noHistory: '\u6682\u65E0\u6D4B\u8BD5\u5386\u53F2',
  refreshFail: '\u5237\u65B0\u5931\u8D25',
  proxyPlaceholder: '\u7559\u7A7A\u4F7F\u7528\u5F53\u524D\u76D1\u542C\u914D\u7F6E',
  authPlaceholder: '\u6309\u9700\u586B\u5199',
  menuOpsTitle: 'Menu \u8FD0\u7EF4\u4F5C\u4E1A\u4E2D\u5FC3',
  menuOpsSub: 'Java \u539F\u751F\u5B9E\u73B0\uFF0C\u8986\u76D6 menu.sh \u529F\u80FD\u80FD\u529B\u96C6',
  menuOpLabel: '\u64CD\u4F5C\u7C7B\u578B',
  menuOpRisk: '\u98CE\u9669\u7B49\u7EA7',
  menuOpToken: '\u517C\u5BB9\u9009\u9879',
  menuOpArg: '\u53C2\u6570\uFF08\u53EF\u9009\uFF09',
  menuOpStdin: '\u8F93\u5165\u6D41\uFF08\u53EF\u9009\uFF09',
  menuOpConfirm: '\u6211\u786E\u8BA4\u6267\u884C\u9AD8\u98CE\u9669\u64CD\u4F5C',
  menuOpRun: '\u63D0\u4EA4\u4F5C\u4E1A',
  menuOpCancel: '\u53D6\u6D88\u5F53\u524D\u4F5C\u4E1A',
  menuOpDownload: '\u4E0B\u8F7D\u4F5C\u4E1A\u65E5\u5FD7',
  menuOpRefresh: '\u5237\u65B0\u4F5C\u4E1A\u5217\u8868',
  menuOpHintInit: '\u5C1A\u672A\u9009\u62E9\u8FD0\u7EF4\u64CD\u4F5C',
  menuOpDisabled: 'menu-ops \u672A\u542F\u7528\uFF0C\u8BF7\u5728\u914D\u7F6E\u4E2D\u5F00\u542F',
  menuOpEnvInfo: '\u5DE5\u4F5C\u76EE\u5F55: {wd} | \u7834\u574F\u6027\u64CD\u4F5C: {d} | \u8FDC\u7A0B\u811A\u672C: {r} | \u5BA1\u8BA1\u65E5\u5FD7: {a} | \u5BA1\u8BA1DB: {adb} ({adbe}) | \u5BA1\u8BA1\u7559\u5B58: {adr}\u5929 | \u5BA1\u8BA1\u884C\u4E0A\u9650: {adm} | \u8BCA\u65AD\u76EE\u5F55: {dg} | \u7559\u5B58: {dr}\u5929 | \u6700\u5927\u6587\u4EF6\u6570: {df} | \u4E0B\u8F7D\u4E0A\u9650: {dl} | \u7B56\u7565 endpoint: {ep} | \u7B56\u7565 stack: {st}',
  menuOpNoCatalog: '\u672A\u83B7\u53D6\u5230\u64CD\u4F5C\u76EE\u5F55',
  menuOpNoJob: '\u6682\u65E0\u4F5C\u4E1A',
  menuJobTitle: '\u4F5C\u4E1A\u5386\u53F2',
  menuJobSub: '\u6700\u8FD1\u6267\u884C\u8BB0\u5F55',
  menuJobTime: '\u65F6\u95F4',
  menuJobOp: '\u64CD\u4F5C',
  menuJobStatus: '\u72B6\u6001',
  menuJobCost: '\u8017\u65F6',
  menuLogTitle: '\u4F5C\u4E1A\u65E5\u5FD7',
  menuLogMeta: '\u672A\u9009\u62E9\u4F5C\u4E1A',
  diagTitle: '\u8BCA\u65AD\u6587\u4EF6',
  diagSub: 'WARP \u8BCA\u65AD\u5305\u5386\u53F2\u5217\u8868',
  thDiagName: '\u6587\u4EF6\u540D',
  thDiagSize: '\u5927\u5C0F',
  thDiagTime: '\u66F4\u65B0\u65F6\u95F4',
  thDiagAction: '\u64CD\u4F5C',
  diagRefresh: '\u5237\u65B0\u8BCA\u65AD\u6587\u4EF6',
  diagCleanup: '\u6309\u7B56\u7565\u6E05\u7406\u8BCA\u65AD\u6587\u4EF6',
  diagCleanupSubmit: '\u8BCA\u65AD\u6E05\u7406\u4F5C\u4E1A\u5DF2\u63D0\u4EA4',
  diagSummaryLoading: '\u8BCA\u65AD\u76EE\u5F55\u6C47\u603B\u52A0\u8F7D\u4E2D...',
  diagSummaryEmpty: '\u8BCA\u65AD\u76EE\u5F55\u6682\u65E0\u7EDF\u8BA1\u4FE1\u606F',
  diagSummaryFmt: '\u76EE\u5F55: {dir} | \u6587\u4EF6: {count} | \u603B\u5927\u5C0F: {size} | \u6700\u65B0: {latest} | \u7559\u5B58: {days}\u5929 | \u6700\u5927\u6587\u4EF6\u6570: {maxFiles} | \u4E0B\u8F7D\u4E0A\u9650: {maxDownload}',
  diagNoFiles: '\u6682\u65E0\u8BCA\u65AD\u6587\u4EF6',
  diagDownload: '\u4E0B\u8F7D',
  diagDelete: '\u5220\u9664',
  diagDeleteConfirm: '\u786E\u8BA4\u5220\u9664\u8BCA\u65AD\u6587\u4EF6\uff1A{name}\uFF1F',
  diagDeleteDone: '\u5DF2\u5220\u9664\u8BCA\u65AD\u6587\u4EF6',
  diagLoadFail: '\u8BCA\u65AD\u6587\u4EF6\u5217\u8868\u5237\u65B0\u5931\u8D25',
  menuJobLoadFail: '\u4F5C\u4E1A\u5217\u8868\u5237\u65B0\u5931\u8D25',
  menuOpSubmitRunning: '\u63D0\u4EA4\u4E2D...',
  menuOpSubmitDone: '\u4F5C\u4E1A\u5DF2\u63D0\u4EA4: {id}',
  menuOpSelectRequired: '\u8BF7\u5148\u9009\u62E9\u64CD\u4F5C\u7C7B\u578B',
  menuNoActiveJob: '\u5F53\u524D\u65E0\u53EF\u53D6\u6D88\u7684\u8FD0\u884C\u4F5C\u4E1A',
  menuNoSelectedJob: '\u8BF7\u5148\u9009\u62E9\u9700\u4E0B\u8F7D\u65E5\u5FD7\u7684\u4F5C\u4E1A',
  menuDownloadDone: '\u4F5C\u4E1A\u65E5\u5FD7\u5DF2\u5F00\u59CB\u4E0B\u8F7D',
  menuCancelDone: '\u53D6\u6D88\u6307\u4EE4\u5DF2\u4E0B\u53D1',
  status_PENDING: '\u6392\u961F\u4E2D',
  status_RUNNING: '\u8FD0\u884C\u4E2D',
  status_SUCCESS: '\u6210\u529F',
  status_FAILED: '\u5931\u8D25',
  status_CANCELED: '\u5DF2\u53D6\u6D88',
  status_TIMEOUT: '\u8D85\u65F6'
};

const MENU_OP_ZH = {
  MENU_EXIT: '\u9000\u51FA',
  SHOW_HELP: '\u5E2E\u52A9',
  SHOW_STATUS: '\u67E5\u770B WARP \u72B6\u6001',
  SHOW_VERSION: '\u67E5\u770B\u7248\u672C',
  SHOW_WARP_ACCOUNT: '\u67E5\u770B WARP \u8D26\u6237',
  TOGGLE_WARP: 'WARP \u5F00\u5173',
  UNINSTALL_ALL: '\u5378\u8F7D WARP \u5957\u4EF6',
  UPGRADE_BBR: 'BBR/\u5185\u6838\u5DE5\u5177',
  SYNC_SCRIPT_VERSION: '\u540C\u6B65\u8FD0\u884C\u73AF\u5883',
  TOGGLE_CLIENT: 'WARP Client \u5F00\u5173',
  SET_CLIENT_MODE: 'Client \u6A21\u5F0F\u5207\u6362',
  START_WARP_PLUS: '\u542F\u52A8 WARP+',
  STOP_WARP_PLUS: '\u65AD\u5F00 WARP+',
  VERIFY_WARP_PLUS: '\u68C0\u67E5 WARP+ \u72B6\u6001',
  SET_PMTUD_MODE: '\u8BBE\u7F6E PMTUD \u6A21\u5F0F',
  WARP_FIREWALL_PRECHECK: 'WARP \u9632\u706B\u5899\u9884\u68C0',
  COLLECT_WARP_DIAGNOSTICS: '\u91C7\u96C6 WARP \u8BCA\u65AD',
  CLEANUP_DIAGNOSTICS: '\u6E05\u7406\u8BCA\u65AD\u6587\u4EF6',
  TOGGLE_WIREPROXY: 'WireProxy \u5F00\u5173',
  REREGISTER_WARP_ACCOUNT: '\u91CD\u6CE8\u518C WARP \u8D26\u6237',
  SET_WARP_LICENSE: '\u8BBE\u7F6E WARP \u6388\u6743',
  SET_WARP_TEAMS_ACCOUNT: '\u8BBE\u7F6E WARP Teams \u8D26\u6237',
  LEAVE_WARP_TEAMS_ACCOUNT: '\u9000\u51FA WARP Teams \u8D26\u6237',
  SET_WARP_ACCOUNT_MODE: '\u5FEB\u901F\u5207\u6362 WARP \u8D26\u6237\u7C7B\u578B',
  ADD_WARP_IPV4: '\u5B89\u88C5 WARP IPv4',
  ADD_WARP_IPV6: '\u5B89\u88C5 WARP IPv6',
  ADD_WARP_DUAL: '\u5B89\u88C5 WARP \u53CC\u6808',
  INSTALL_CLIENT_PROXY: '\u5B89\u88C5 Client \u4EE3\u7406\u6A21\u5F0F',
  INSTALL_CLIENT_WARP: '\u5B89\u88C5 Client WARP \u6A21\u5F0F',
  CHANGE_PROXY_PORT: '\u4FEE\u6539\u4EE3\u7406\u7AEF\u53E3',
  CHANGE_NETFLIX_IP: '\u66F4\u6362 Netflix \u533A\u57DF IP',
  SET_STACK_PRIORITY: '\u8BBE\u7F6E IPv4/IPv6 \u4F18\u5148\u7EA7',
  REPAIR_WARP_NETWORK: '\u4FEE\u590D WARP \u7F51\u7EDC',
  TUNE_WARP_MTU: '\u81EA\u52A8\u8C03\u4F18 MTU',
  SET_WARP_ENDPOINT: '\u8BBE\u7F6E WARP Endpoint',
  APPLY_WARP_PROFILE: '\u4E00\u952E\u5957\u7528\u573A\u666F\u7B56\u7565',
  AUTO_RECOVER_WARP: '\u4E00\u952E\u81EA\u6108 WARP',
  CHECK_SCENARIO_REACHABILITY: '\u573A\u666F\u53EF\u8FBE\u6027\u68C0\u67E5',
  AUTO_SWITCH_POLICY: '\u7B56\u7565\u81EA\u52A8\u5207\u6362\u5F15\u64CE',
  INSTALL_IPTABLES_STREAM: '\u5B89\u88C5\u6D41\u5A92\u4F53\u5206\u6D41\u65B9\u6848',
  INSTALL_WIREPROXY_SOLUTION: '\u5B89\u88C5 WireProxy \u65B9\u6848',
  SWITCH_KERNEL_RESERVED: '\u5207\u6362\u5185\u6838/\u7528\u6237\u6001 WireGuard',
  SWITCH_GLOBAL_MODE: '\u5207\u6362\u5168\u5C40/\u975E\u5168\u5C40\u8DEF\u7531',
  RULE_ADD_INTERNAL: '\u5185\u90E8\u89C4\u5219\u65B0\u589E',
  RULE_DEL_INTERNAL: '\u5185\u90E8\u89C4\u5219\u5220\u9664',
  MENU_CHOOSE: '\u83DC\u5355\u7F16\u53F7\u6267\u884C',
  MENU_UNLOCK_SCRIPT: '\u83DC\u5355 #11 \u89E3\u9501\u811A\u672C',
  MENU_INTERACTIVE: '\u4EA4\u4E92\u5F0F\u83DC\u5355\u4F1A\u8BDD',
  RAW_ARGS: '\u9AD8\u7EA7\u539F\u59CB\u547D\u4EE4'
};

const MENU_ARG_HINT_ZH = {
  SET_WARP_LICENSE: '\u6388\u6743\u7801\uff0c\u4F8B\u5982 ABCD-EFGH-IJKL',
  CHANGE_PROXY_PORT: '40000 \u6216 client:40000 \u6216 wireproxy:40000',
  CHANGE_NETFLIX_IP: '\u4E24\u4F4D\u5730\u533A\u7801\uff0c\u4F8B\u5982 hk/sg/jp',
  SET_WARP_TEAMS_ACCOUNT: 'organization/domain/token',
  SET_WARP_ACCOUNT_MODE: 'free | plus:license | teams:organization/domain/token | teams-leave',
  SET_CLIENT_MODE: 'warp | proxy | proxy:40000',
  SET_PMTUD_MODE: 'on | off',
  SET_STACK_PRIORITY: '4\u30016\u3001d',
  SET_WARP_ENDPOINT: 'Endpoint \u4F8B\u5982 engage.cloudflareclient.com:2408',
  MENU_CHOOSE: '\u83DC\u5355\u7F16\u53F7 0-14\uff0c\u4F8B\u5982 6:hk',
  APPLY_WARP_PROFILE: 'chatgpt | netflix:hk | google | telegram | full',
  AUTO_SWITCH_POLICY: 'auto | chatgpt | netflix:hk | google | telegram | full'
};

const chartCanvas = document.getElementById('protocolChart');
const chartCtx = chartCanvas.getContext('2d');

const stateEl = document.getElementById('runtimeState');
const runtimeMetaEl = document.getElementById('runtimeMeta');
const lastUpdateEl = document.getElementById('lastUpdate');

const listenersBody = document.getElementById('listenersBody');
const sessionsBody = document.getElementById('sessionsBody');
const eventsBox = document.getElementById('eventsBox');
const selfCheckGrid = document.getElementById('selfCheckGrid');
const selfCheckConfigSourcesEl = document.getElementById('selfCheckConfigSources');
const selfCheckTopFailuresEl = document.getElementById('selfCheckTopFailures');
const failureReasonsBody = document.getElementById('failureReasonsBody');

const mActive = document.getElementById('mActive');
const mTotal = document.getElementById('mTotal');
const mBlocked = document.getElementById('mBlocked');
const mAuthFail = document.getElementById('mAuthFail');
const mConnectFail = document.getElementById('mConnectFail');
const mInBytes = document.getElementById('mInBytes');
const mOutBytes = document.getElementById('mOutBytes');

const btnStart = document.getElementById('btnStart');
const btnStop = document.getElementById('btnStop');
const btnRestart = document.getElementById('btnRestart');
const actionButtons = [btnStart, btnStop, btnRestart];

const testModeEl = document.getElementById('testMode');
const testTargetUrlEl = document.getElementById('testTargetUrl');
const testProxyHostEl = document.getElementById('testProxyHost');
const testProxyPortEl = document.getElementById('testProxyPort');
const testUsernameEl = document.getElementById('testUsername');
const testPasswordEl = document.getElementById('testPassword');
const testTimeoutEl = document.getElementById('testTimeout');
const testResultBoxEl = document.getElementById('testResultBox');
const scenarioResultBoxEl = document.getElementById('scenarioResultBox');
const btnRunProxyTest = document.getElementById('btnRunProxyTest');
const btnRunScenarioTest = document.getElementById('btnRunScenarioTest');
const btnFillProxyConfig = document.getElementById('btnFillProxyConfig');
const probeHistoryBody = document.getElementById('probeHistoryBody');
const scenarioCheckEls = Array.from(document.querySelectorAll('.scenario-item'));

const warpCliInstalledEl = document.getElementById('warpCliInstalled');
const warpServiceRunningEl = document.getElementById('warpServiceRunning');
const warpConnectedEl = document.getElementById('warpConnected');
const warpDaemonStatusEl = document.getElementById('warpDaemonStatus');
const warpConnectivityPhaseEl = document.getElementById('warpConnectivityPhase');
const warpConnectivityReasonEl = document.getElementById('warpConnectivityReason');
const warpAccountTypeEl = document.getElementById('warpAccountType');
const warpRegistrationIdEl = document.getElementById('warpRegistrationId');
const warpModeEl = document.getElementById('warpMode');
const warpProtocolEl = document.getElementById('warpProtocol');
const warpPmtudStatusEl = document.getElementById('warpPmtudStatus');
const warpFirewallPrecheckEl = document.getElementById('warpFirewallPrecheck');
const warpFirewallTcpEl = document.getElementById('warpFirewallTcp');
const warpFirewallUdpEl = document.getElementById('warpFirewallUdp');
const warpOrganizationEl = document.getElementById('warpOrganization');
const warpTraceStateEl = document.getElementById('warpTraceState');
const warpPlusAccountEl = document.getElementById('warpPlusAccount');
const warpPlusVerifiedEl = document.getElementById('warpPlusVerified');
const warpIpv4El = document.getElementById('warpIpv4');
const warpIpv6El = document.getElementById('warpIpv6');
const warpGoogleStatusEl = document.getElementById('warpGoogleStatus');
const warpDnsStatusEl = document.getElementById('warpDnsStatus');
const warpUdpStatusEl = document.getElementById('warpUdpStatus');
const warpTcpStatusEl = document.getElementById('warpTcpStatus');
const warpRouteStatusEl = document.getElementById('warpRouteStatus');
const warpCheckedAtEl = document.getElementById('warpCheckedAt');
const warpHistorySummaryEl = document.getElementById('warpHistorySummary');
const warpHistoryBodyEl = document.getElementById('warpHistoryBody');
const warpAlertSummaryEl = document.getElementById('warpAlertSummary');
const warpAlertActiveEl = document.getElementById('warpAlertActive');
const warpAlertBodyEl = document.getElementById('warpAlertBody');
const warpAlertRuleBodyEl = document.getElementById('warpAlertRuleBody');
const warpAlertRuleHintEl = document.getElementById('warpAlertRuleHint');
const btnWarpAlertRuleSave = document.getElementById('btnWarpAlertRuleSave');
const btnWarpAlertRuleReset = document.getElementById('btnWarpAlertRuleReset');
const warpStatusHintEl = document.getElementById('warpStatusHint');
const warpQuickHintEl = document.getElementById('warpQuickHint');
const warpLicenseArgEl = document.getElementById('warpLicenseArg');
const warpTeamsArgEl = document.getElementById('warpTeamsArg');
const warpStackArgEl = document.getElementById('warpStackArg');
const warpEndpointArgEl = document.getElementById('warpEndpointArg');
const warpProfileArgEl = document.getElementById('warpProfileArg');
const warpStackOptionEls = Array.from(document.querySelectorAll('.warp-stack-option'));
const warpProfileOptionEls = Array.from(document.querySelectorAll('.warp-profile-option'));
const btnWarpInstallClientWarp = document.getElementById('btnWarpInstallClientWarp');
const btnWarpInstallClientProxy = document.getElementById('btnWarpInstallClientProxy');
const btnWarpInterfaceToggle = document.getElementById('btnWarpInterfaceToggle');
const btnWarpPlusStart = document.getElementById('btnWarpPlusStart');
const btnWarpPlusStop = document.getElementById('btnWarpPlusStop');
const btnWarpClientToggle = document.getElementById('btnWarpClientToggle');
const btnWarpClientModeWarp = document.getElementById('btnWarpClientModeWarp');
const btnWarpClientModeProxy = document.getElementById('btnWarpClientModeProxy');
const btnWarpRepair = document.getElementById('btnWarpRepair');
const btnWarpTuneMtu = document.getElementById('btnWarpTuneMtu');
const btnWarpSetStackPriority = document.getElementById('btnWarpSetStackPriority');
const btnWarpSetEndpoint = document.getElementById('btnWarpSetEndpoint');
const btnWarpShowAccount = document.getElementById('btnWarpShowAccount');
const btnWarpVerifyPlus = document.getElementById('btnWarpVerifyPlus');
const btnWarpPmtudOn = document.getElementById('btnWarpPmtudOn');
const btnWarpPmtudOff = document.getElementById('btnWarpPmtudOff');
const btnWarpFirewallPrecheck = document.getElementById('btnWarpFirewallPrecheck');
const btnWarpDiagnostics = document.getElementById('btnWarpDiagnostics');
const btnWarpSetLicense = document.getElementById('btnWarpSetLicense');
const btnWarpReregister = document.getElementById('btnWarpReregister');
const btnWarpApplyProfile = document.getElementById('btnWarpApplyProfile');
const btnWarpAutoRecover = document.getElementById('btnWarpAutoRecover');
const btnWarpAutoSwitch = document.getElementById('btnWarpAutoSwitch');
const btnWarpTeamsSet = document.getElementById('btnWarpTeamsSet');
const btnWarpTeamsLeave = document.getElementById('btnWarpTeamsLeave');
const btnWarpStatusRefresh = document.getElementById('btnWarpStatusRefresh');
const warpQuickButtons = [
  btnWarpInstallClientWarp,
  btnWarpInstallClientProxy,
  btnWarpInterfaceToggle,
  btnWarpPlusStart,
  btnWarpPlusStop,
  btnWarpClientToggle,
  btnWarpClientModeWarp,
  btnWarpClientModeProxy,
  btnWarpRepair,
  btnWarpTuneMtu,
  btnWarpSetStackPriority,
  btnWarpSetEndpoint,
  btnWarpShowAccount,
  btnWarpVerifyPlus,
  btnWarpPmtudOn,
  btnWarpPmtudOff,
  btnWarpFirewallPrecheck,
  btnWarpDiagnostics,
  btnWarpSetLicense,
  btnWarpReregister,
  btnWarpApplyProfile,
  btnWarpAutoRecover,
  btnWarpAutoSwitch,
  btnWarpTeamsSet,
  btnWarpTeamsLeave,
  btnWarpStatusRefresh
];
const WARP_PROFILE_STACK_HINT = {
  auto: 'd',
  chatgpt: '4',
  'netflix:hk': '4',
  google: '4',
  telegram: '4',
  full: 'd'
};

const menuOpSelectEl = document.getElementById('menuOpSelect');
const menuOpRiskEl = document.getElementById('menuOpRisk');
const menuOpTokenEl = document.getElementById('menuOpToken');
const menuOpArgEl = document.getElementById('menuOpArg');
const menuOpStdinEl = document.getElementById('menuOpStdin');
const menuOpConfirmEl = document.getElementById('menuOpConfirm');
const menuOpHintEl = document.getElementById('menuOpHint');
const menuJobBodyEl = document.getElementById('menuJobBody');
const menuJobLogsEl = document.getElementById('menuJobLogs');
const menuLogMetaEl = document.getElementById('menuLogMeta');
const btnMenuOpRun = document.getElementById('btnMenuOpRun');
const btnMenuOpCancel = document.getElementById('btnMenuOpCancel');
const btnMenuOpDownload = document.getElementById('btnMenuOpDownload');
const btnMenuOpRefresh = document.getElementById('btnMenuOpRefresh');
const diagFileBodyEl = document.getElementById('diagFileBody');
const diagHintEl = document.getElementById('diagHint');
const diagSummaryEl = document.getElementById('diagSummary');
const btnDiagRefresh = document.getElementById('btnDiagRefresh');
const btnDiagCleanup = document.getElementById('btnDiagCleanup');
const menuAuditBodyEl = document.getElementById('menuAuditBody');
const menuAuditSummaryEl = document.getElementById('menuAuditSummary');
const menuAuditHintEl = document.getElementById('menuAuditHint');

let probeHistory = [];
let menuCatalog = [];
let menuJobs = [];
let menuSelectedJobId = '';
let warpTrackedJobId = '';
let warpTrackedOperationId = '';
let isRefreshingRuntime = false;
let isRefreshingMenuJobs = false;
let isRefreshingWarp = false;
let lastRuntimeRefreshAt = 0;
let lastMenuRefreshAt = 0;
let lastWarpRefreshAt = 0;
let schedulerTimer = null;
let lastRenderedLogJobId = '';
let lastProtocolChartKey = '';
let lastListenersRenderKey = '';
let lastEventsRenderKey = '';
let lastSelfCheckRenderKey = '';
let lastFailureReasonsRenderKey = '';
let lastSessionsEmptyState = false;
let lastWarpHistoryRenderKey = '';
let lastWarpHistorySummaryKey = '';
let lastWarpAlertRenderKey = '';
let lastWarpAlertSummaryKey = '';
let lastWarpAlertActiveKey = '';
let lastWarpAlertRuleKey = '';
let warpAlertRules = [];
let menuJobsCursorMillis = 0;
let menuJobsDeltaRounds = 0;
let diagnosticsFiles = [];
let diagnosticsSummary = null;
let menuAuditRecords = [];
let menuAuditSummary = null;
let isRefreshingDiagnostics = false;
let lastDiagnosticsRefreshAt = 0;
let lastDiagnosticsRenderKey = '';
let lastDiagnosticsSummaryKey = '';
let isRefreshingMenuAudit = false;
let lastMenuAuditRefreshAt = 0;
let lastMenuAuditRenderKey = '';
let lastMenuAuditSummaryKey = '';
let lastRenderedLogDigest = '';
const sessionRowCache = new Map();
setWarpQuickButtonsDisabled(true);

function setText(id, text) {
  const el = document.getElementById(id);
  if (el) {
    el.textContent = text;
  }
}

function setNodeTextIfChanged(node, value) {
  if (!node) return;
  const text = value == null ? '' : String(value);
  if (node.textContent !== text) {
    node.textContent = text;
  }
}

function applyI18n() {
  setText('pageTitle', I18N.title);
  document.title = I18N.title;
  setText('heroEyebrow', I18N.eyebrow);
  setText('heroTitle', I18N.hero);
  setText('socksPortLabel', I18N.socksPort);
  setText('httpPortLabel', I18N.httpPort);
  setText('btnStart', I18N.start);
  setText('btnStop', I18N.stop);
  setText('btnRestart', I18N.restart);

  setText('metricLabelActive', I18N.mActive);
  setText('metricLabelTotal', I18N.mTotal);
  setText('metricLabelBlocked', I18N.mBlocked);
  setText('metricLabelAuthFail', I18N.mAuthFail);
  setText('metricLabelConnectFail', I18N.mConnFail);
  setText('metricLabelIngress', I18N.mIngress);
  setText('metricLabelEgress', I18N.mEgress);

  setText('selfCheckTitle', I18N.selfCheckTitle);
  setText('selfCheckSub', I18N.selfCheckSub);
  setText('selfCheckConfigTitle', I18N.selfCheckConfigTitle);
  setText('selfCheckConfigSub', I18N.selfCheckConfigSub);
  setText('selfCheckTopFailuresTitle', I18N.selfCheckTopFailuresTitle);
  setText('selfCheckTopFailuresSub', I18N.selfCheckTopFailuresSub);
  setText('failureReasonsTitle', I18N.failureReasonsTitle);
  setText('failureReasonsSub', I18N.failureReasonsSub);
  setText('thFailureReason', I18N.thFailureReason);
  setText('thFailureCount', I18N.thFailureCount);

  setText('protocolTitle', I18N.protocolTitle);
  setText('probeTitle', I18N.probeTitle);
  setText('probeSub', I18N.probeSub);

  setText('labelTestMode', I18N.labelMode);
  setText('optSocks5', I18N.optSocks5);
  setText('optHttp', I18N.optHttp);
  setText('labelTargetUrl', I18N.labelTarget);
  setText('labelProxyHost', I18N.labelProxyHost);
  setText('labelProxyPort', I18N.labelProxyPort);
  setText('labelUsername', I18N.labelUser);
  setText('labelPassword', I18N.labelPass);
  setText('labelTimeout', I18N.labelTimeout);
  setText('btnRunProxyTest', I18N.probeBtn);
  setText('btnRunScenarioTest', I18N.scenarioBtn);
  setText('btnFillProxyConfig', I18N.fillCurrent);
  setText('probeTip', I18N.probeTip);
  setText('scenarioTitle', I18N.scenarioTitle);
  setText('probeHistoryTitle', I18N.probeHistoryTitle);
  setText('probeHistorySub', I18N.probeHistorySub);
  setText('thProbeTime', I18N.thProbeTime);
  setText('thProbeMode', I18N.thProbeMode);
  setText('thProbeStatus', I18N.thProbeStatus);
  setText('thProbeTarget', I18N.thProbeTarget);
  setText('thProbeProxy', I18N.thProbeProxy);
  setText('thProbeCost', I18N.thProbeCost);
  setText('warpTitle', I18N.warpTitle);
  setText('warpSub', I18N.warpSub);
  setText('warpLabelCli', I18N.warpLabelCli);
  setText('warpLabelService', I18N.warpLabelService);
  setText('warpLabelConnected', I18N.warpLabelConnected);
  setText('warpLabelStatus', I18N.warpLabelStatus);
  setText('warpLabelPhase', I18N.warpLabelPhase);
  setText('warpLabelReason', I18N.warpLabelReason);
  setText('warpLabelAccount', I18N.warpLabelAccount);
  setText('warpLabelRegId', I18N.warpLabelRegId);
  setText('warpLabelMode', I18N.warpLabelMode);
  setText('warpLabelProtocol', I18N.warpLabelProtocol);
  setText('warpLabelPmtud', I18N.warpLabelPmtud);
  setText('warpLabelFirewallPrecheck', I18N.warpLabelFirewallPrecheck);
  setText('warpLabelFirewallTcp', I18N.warpLabelFirewallTcp);
  setText('warpLabelFirewallUdp', I18N.warpLabelFirewallUdp);
  setText('warpLabelOrg', I18N.warpLabelOrg);
  setText('warpLabelTrace', I18N.warpLabelTrace);
  setText('warpLabelPlusAccount', I18N.warpLabelPlusAccount);
  setText('warpLabelPlusVerified', I18N.warpLabelPlusVerified);
  setText('warpLabelIpv4', I18N.warpLabelIpv4);
  setText('warpLabelIpv6', I18N.warpLabelIpv6);
  setText('warpLabelGoogle', I18N.warpLabelGoogle);
  setText('warpLabelDns', I18N.warpLabelDns);
  setText('warpLabelUdp', I18N.warpLabelUdp);
  setText('warpLabelTcp', I18N.warpLabelTcp);
  setText('warpLabelRoute', I18N.warpLabelRoute);
  setText('warpLabelChecked', I18N.warpLabelChecked);
  setText('warpHistoryTitle', I18N.warpHistoryTitle);
  setText('warpHistorySub', I18N.warpHistorySub);
  setText('thWarpHistoryTime', I18N.thWarpHistoryTime);
  setText('thWarpHistoryConnected', I18N.thWarpHistoryConnected);
  setText('thWarpHistoryPhase', I18N.thWarpHistoryPhase);
  setText('thWarpHistoryFirewall', I18N.thWarpHistoryFirewall);
  setText('thWarpHistoryPmtud', I18N.thWarpHistoryPmtud);
  setText('thWarpHistoryIp', I18N.thWarpHistoryIp);
  setText('warpAlertTitle', I18N.warpAlertTitle);
  setText('warpAlertSub', I18N.warpAlertSub);
  setText('warpAlertRuleTitle', I18N.warpAlertRuleTitle);
  setText('warpAlertRuleSub', I18N.warpAlertRuleSub);
  setText('btnWarpAlertRuleSave', I18N.warpAlertRuleSave);
  setText('btnWarpAlertRuleReset', I18N.warpAlertRuleReset);
  setText('thWarpAlertRuleCode', I18N.thWarpAlertRuleCode);
  setText('thWarpAlertRuleTitle', I18N.thWarpAlertRuleTitle);
  setText('thWarpAlertRuleSeverity', I18N.thWarpAlertRuleSeverity);
  setText('thWarpAlertRuleEnabled', I18N.thWarpAlertRuleEnabled);
  setText('thWarpAlertRuleUpdated', I18N.thWarpAlertRuleUpdated);
  setText('thWarpAlertTime', I18N.thWarpAlertTime);
  setText('thWarpAlertSeverity', I18N.thWarpAlertSeverity);
  setText('thWarpAlertCode', I18N.thWarpAlertCode);
  setText('thWarpAlertState', I18N.thWarpAlertState);
  setText('thWarpAlertDetail', I18N.thWarpAlertDetail);
  setText('warpLicenseArgLabel', I18N.warpLicenseArgLabel);
  setText('warpTeamsArgLabel', I18N.warpTeamsArgLabel);
  setText('warpStackArgLabel', I18N.warpStackArgLabel);
  setText('warpEndpointArgLabel', I18N.warpEndpointArgLabel);
  setText('warpProfileArgLabel', I18N.warpProfileArgLabel);
  setText('btnWarpInstallClientWarp', I18N.warpBtnInstallClientWarp);
  setText('btnWarpInstallClientProxy', I18N.warpBtnInstallClientProxy);
  setText('btnWarpInterfaceToggle', I18N.warpBtnInterfaceToggle);
  setText('btnWarpPlusStart', I18N.warpBtnStart);
  setText('btnWarpPlusStop', I18N.warpBtnStop);
  setText('btnWarpClientToggle', I18N.warpBtnToggleClient);
  setText('btnWarpClientModeWarp', I18N.warpBtnClientModeWarp);
  setText('btnWarpClientModeProxy', I18N.warpBtnClientModeProxy);
  setText('btnWarpRepair', I18N.warpBtnRepair);
  setText('btnWarpTuneMtu', I18N.warpBtnTuneMtu);
  setText('btnWarpSetStackPriority', I18N.warpBtnSetStackPriority);
  setText('btnWarpSetEndpoint', I18N.warpBtnSetEndpoint);
  setText('btnWarpShowAccount', I18N.warpBtnShowAccount);
  setText('btnWarpVerifyPlus', I18N.warpBtnVerifyPlus);
  setText('btnWarpPmtudOn', I18N.warpBtnPmtudOn);
  setText('btnWarpPmtudOff', I18N.warpBtnPmtudOff);
  setText('btnWarpFirewallPrecheck', I18N.warpBtnFirewallPrecheck);
  setText('btnWarpDiagnostics', I18N.warpBtnDiagnostics);
  setText('btnWarpSetLicense', I18N.warpBtnSetLicense);
  setText('btnWarpReregister', I18N.warpBtnReregister);
  setText('btnWarpApplyProfile', I18N.warpBtnApplyProfile);
  setText('btnWarpAutoRecover', I18N.warpBtnAutoRecover);
  setText('btnWarpAutoSwitch', I18N.warpBtnAutoSwitch);
  setText('btnWarpTeamsSet', I18N.warpBtnSetTeams);
  setText('btnWarpTeamsLeave', I18N.warpBtnLeaveTeams);
  setText('btnWarpStatusRefresh', I18N.warpBtnRefresh);

  setText('menuOpsTitle', I18N.menuOpsTitle);
  setText('menuOpsSub', I18N.menuOpsSub);
  setText('menuOpLabel', I18N.menuOpLabel);
  setText('menuOpRiskLabel', I18N.menuOpRisk);
  setText('menuOpTokenLabel', I18N.menuOpToken);
  setText('menuOpArgLabel', I18N.menuOpArg);
  setText('menuOpStdinLabel', I18N.menuOpStdin);
  setText('menuOpConfirmText', I18N.menuOpConfirm);
  setText('btnMenuOpRun', I18N.menuOpRun);
  setText('btnMenuOpCancel', I18N.menuOpCancel);
  setText('btnMenuOpDownload', I18N.menuOpDownload);
  setText('btnMenuOpRefresh', I18N.menuOpRefresh);
  setText('menuJobTitle', I18N.menuJobTitle);
  setText('menuJobSub', I18N.menuJobSub);
  setText('thMenuJobTime', I18N.menuJobTime);
  setText('thMenuJobOp', I18N.menuJobOp);
  setText('thMenuJobStatus', I18N.menuJobStatus);
  setText('thMenuJobCost', I18N.menuJobCost);
  setText('menuLogTitle', I18N.menuLogTitle);
  setText('menuLogMeta', I18N.menuLogMeta);
  setText('diagTitle', I18N.diagTitle);
  setText('diagSub', I18N.diagSub);
  setText('thDiagName', I18N.thDiagName);
  setText('thDiagSize', I18N.thDiagSize);
  setText('thDiagTime', I18N.thDiagTime);
  setText('thDiagAction', I18N.thDiagAction);
  setText('btnDiagRefresh', I18N.diagRefresh);
  setText('btnDiagCleanup', I18N.diagCleanup);
  setText('menuAuditTitle', I18N.menuAuditTitle);
  setText('menuAuditSub', I18N.menuAuditSub);
  setText('thMenuAuditTime', I18N.thMenuAuditTime);
  setText('thMenuAuditOp', I18N.thMenuAuditOp);
  setText('thMenuAuditStatus', I18N.thMenuAuditStatus);
  setText('thMenuAuditRisk', I18N.thMenuAuditRisk);
  setText('thMenuAuditCost', I18N.thMenuAuditCost);

  setText('listenerTitle', I18N.listeners);
  setText('listenerSub', I18N.listenersSub);
  setText('thListenerName', I18N.listenerName);
  setText('thListenerStatus', I18N.listenerStatus);

  setText('eventsTitle', I18N.events);
  setText('eventsSub', I18N.eventsSub);

  setText('sessionTitle', I18N.sessions);
  setText('sessionSub', I18N.sessionsSub);
  setText('thSessionId', I18N.thId);
  setText('thSessionProtocol', I18N.thProtocol);
  setText('thSessionClient', I18N.thClient);
  setText('thSessionTarget', I18N.thTarget);
  setText('thSessionIn', I18N.thIn);
  setText('thSessionOut', I18N.thOut);
  setText('thSessionStart', I18N.thStart);

  testProxyHostEl.placeholder = I18N.proxyPlaceholder;
  testProxyPortEl.placeholder = I18N.proxyPlaceholder;
  testUsernameEl.placeholder = I18N.authPlaceholder;
  testPasswordEl.placeholder = I18N.authPlaceholder;
  testResultBoxEl.textContent = I18N.probeInit;
  scenarioResultBoxEl.textContent = I18N.scenarioInit;
  warpStatusHintEl.textContent = I18N.warpHintInit;
  warpQuickHintEl.textContent = menuOpsEnabledCfg ? I18N.warpQuickInit : I18N.warpQuickDisabled;
  if (!menuOpsEnabledCfg) {
    setWarpQuickButtonsDisabled(true);
    btnDiagRefresh.disabled = true;
    btnDiagCleanup.disabled = true;
  }
  if (diagHintEl) {
    diagHintEl.textContent = I18N.diagNoFiles;
  }
  if (diagSummaryEl) {
    diagSummaryEl.textContent = I18N.diagSummaryLoading;
  }
  if (warpHistorySummaryEl) {
    warpHistorySummaryEl.textContent = I18N.warpHistoryLoading;
  }
  if (warpAlertSummaryEl) {
    warpAlertSummaryEl.textContent = I18N.warpAlertSummaryLoading;
  }
  if (warpAlertActiveEl) {
    warpAlertActiveEl.textContent = I18N.warpAlertActiveEmpty;
  }
  if (warpAlertRuleHintEl) {
    warpAlertRuleHintEl.textContent = I18N.warpAlertRuleLoading;
  }
  if (menuAuditSummaryEl) {
    menuAuditSummaryEl.textContent = I18N.menuAuditSummaryLoading;
  }
  if (menuAuditHintEl) {
    menuAuditHintEl.textContent = I18N.menuAuditNoData;
  }
  menuOpHintEl.textContent = menuOpsEnabledCfg
      ? I18N.menuOpEnvInfo
          .replace('{wd}', menuOpsWorkdirCfg)
          .replace('{d}', menuOpsAllowDestructiveCfg ? '\u5141\u8BB8' : '\u7981\u7528')
          .replace('{r}', menuOpsAllowRemoteScriptsCfg ? '\u5141\u8BB8' : '\u7981\u7528')
          .replace('{a}', menuOpsAuditLogPathCfg)
          .replace('{adb}', menuOpsAuditDbPathCfg)
          .replace('{adbe}', menuOpsAuditDbEnabledCfg ? '\u5F00\u542F' : '\u7981\u7528')
          .replace('{adr}', String(menuOpsAuditDbRetentionDaysCfg))
          .replace('{adm}', String(menuOpsAuditDbMaxRowsCfg))
          .replace('{dg}', menuOpsDiagnosticsDirCfg)
          .replace('{dr}', String(menuOpsDiagnosticsRetentionDaysCfg))
          .replace('{df}', String(menuOpsDiagnosticsMaxFilesCfg))
          .replace('{dl}', formatBytes(menuOpsDiagnosticsMaxDownloadBytesCfg))
          .replace('{ep}', menuOpsAutoSwitchEndpointsCfg || '-')
          .replace('{st}', menuOpsAutoSwitchStacksCfg || '-')
      : I18N.menuOpDisabled;

  scenarioCheckEls.forEach(el => {
    const key = `scenario_${String(el.value || '').toUpperCase()}`;
    const label = el.closest('label');
    if (!label) return;
    const textNode = Array.from(label.childNodes).find(node => node.nodeType === Node.TEXT_NODE);
    if (textNode && I18N[key]) {
      textNode.textContent = ` ${I18N[key]}`;
    }
  });

  runtimeMetaEl.textContent = buildRuntimeMeta();
}

function buildRuntimeMeta(selfCheck) {
  const transport = String(selfCheck?.transport || '-').toLowerCase();
  const profiles = Array.isArray(selfCheck?.activeProfiles) && selfCheck.activeProfiles.length > 0
      ? selfCheck.activeProfiles.join(',')
      : I18N.selfCheckProfilesEmpty;
  return I18N.runtimeMetaDetail
      .replace('{s}', refreshSeconds)
      .replace('{tz}', timezone)
      .replace('{transport}', transport || '-')
      .replace('{profiles}', profiles);
}

function formatBytes(v) {
  if (!Number.isFinite(v) || v <= 0) return '0 B';
  if (v < 1024) return `${v} B`;
  if (v < 1024 * 1024) return `${(v / 1024).toFixed(2)} KB`;
  if (v < 1024 * 1024 * 1024) return `${(v / 1024 / 1024).toFixed(2)} MB`;
  return `${(v / 1024 / 1024 / 1024).toFixed(2)} GB`;
}

function formatTime(ts) {
  if (!ts) return '-';
  const date = new Date(ts);
  if (Number.isNaN(date.getTime())) return ts;
  try {
    if (!formatTime.formatter) {
      formatTime.formatter = new Intl.DateTimeFormat('zh-CN', {
        timeZone: timezone,
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false
      });
    }
    return formatTime.formatter.format(date);
  } catch (_) {
    return date.toISOString();
  }
}

function setRuntimeState(running, message) {
  stateEl.classList.remove('state-running', 'state-stopped', 'state-loading', 'state-error');
  if (message) {
    stateEl.classList.add('state-error');
    stateEl.textContent = `${I18N.stateError}: ${message}`;
    return;
  }
  if (running) {
    stateEl.classList.add('state-running');
    stateEl.textContent = I18N.stateRunning;
  } else {
    stateEl.classList.add('state-stopped');
    stateEl.textContent = I18N.stateStopped;
  }
}

function clearNode(node) {
  while (node.firstChild) {
    node.removeChild(node.firstChild);
  }
}

function appendCell(tr, value) {
  const td = document.createElement('td');
  td.textContent = value;
  tr.appendChild(td);
}

function sortedFailureReasonEntries(failureReasons) {
  return Object.entries(failureReasons || {})
      .map(([reason, count]) => [String(reason || ''), Number(count || 0)])
      .filter(([reason, count]) => !!reason && Number.isFinite(count) && count > 0)
      .sort((a, b) => (b[1] - a[1]) || a[0].localeCompare(b[0]));
}

function renderEmptyTableRow(tbody, colSpan, text) {
  clearNode(tbody);
  const tr = document.createElement('tr');
  const td = document.createElement('td');
  td.colSpan = colSpan;
  td.className = 'empty-tip';
  td.textContent = text;
  tr.appendChild(td);
  tbody.appendChild(tr);
}

function appendSelfCheckCard(container, label, value, meta) {
  const card = document.createElement('div');
  card.className = 'self-check-card';

  const labelEl = document.createElement('span');
  labelEl.className = 'self-check-label';
  labelEl.textContent = label;

  const valueEl = document.createElement('strong');
  valueEl.className = 'self-check-value';
  valueEl.textContent = value || '-';

  card.appendChild(labelEl);
  card.appendChild(valueEl);

  if (meta) {
    const metaEl = document.createElement('div');
    metaEl.className = 'self-check-meta-text';
    metaEl.textContent = meta;
    card.appendChild(metaEl);
  }

  container.appendChild(card);
}

function renderListItems(container, items, emptyText, inline = false) {
  clearNode(container);
  if (!Array.isArray(items) || items.length === 0) {
    container.classList.add('empty-tip');
    container.textContent = emptyText;
    return;
  }

  container.classList.remove('empty-tip');
  items.forEach(item => {
    const div = document.createElement('div');
    div.className = inline ? 'self-check-list-item self-check-list-item-inline' : 'self-check-list-item';
    item(div);
    container.appendChild(div);
  });
}

function drawProtocolChart(socks, http, httpsTunnel) {
  const chartKey = `${Number(socks || 0)}|${Number(http || 0)}|${Number(httpsTunnel || 0)}`;
  if (chartKey === lastProtocolChartKey) {
    return;
  }
  lastProtocolChartKey = chartKey;
  const bars = [
    {name: 'SOCKS5', value: Number(socks || 0), color: '#0bb8d7'},
    {name: 'HTTP', value: Number(http || 0), color: '#5ae7bb'},
    {name: 'HTTPS \u96A7\u9053', value: Number(httpsTunnel || 0), color: '#ff986b'}
  ];
  const total = Math.max(1, bars.reduce((sum, b) => sum + b.value, 0));

  chartCtx.clearRect(0, 0, chartCanvas.width, chartCanvas.height);
  chartCtx.fillStyle = 'rgba(21, 63, 105, 0.03)';
  chartCtx.fillRect(0, 0, chartCanvas.width, chartCanvas.height);

  chartCtx.strokeStyle = 'rgba(122, 153, 185, 0.28)';
  chartCtx.lineWidth = 1;
  for (let i = 1; i <= 4; i += 1) {
    const y = (chartCanvas.height / 5) * i;
    chartCtx.beginPath();
    chartCtx.moveTo(12, y);
    chartCtx.lineTo(chartCanvas.width - 12, y);
    chartCtx.stroke();
  }

  const startX = 160;
  const barHeight = 34;
  const gap = 36;
  const maxBarWidth = chartCanvas.width - startX - 120;

  bars.forEach((bar, idx) => {
    const y = 34 + idx * (barHeight + gap);
    const width = Math.max(8, Math.round((bar.value / total) * maxBarWidth));
    const pct = ((bar.value / total) * 100).toFixed(1);

    chartCtx.fillStyle = '#5d7896';
    chartCtx.font = '600 14px IBM Plex Sans';
    chartCtx.fillText(bar.name, 22, y + 22);

    chartCtx.fillStyle = 'rgba(140, 168, 196, 0.35)';
    roundRect(startX, y, maxBarWidth, barHeight, 8);
    chartCtx.fill();

    chartCtx.fillStyle = bar.color;
    roundRect(startX, y, width, barHeight, 8);
    chartCtx.fill();

    chartCtx.fillStyle = '#1f344c';
    chartCtx.font = '700 13px IBM Plex Sans';
    chartCtx.fillText(`${bar.value} (${pct}%)`, startX + width + 10, y + 22);
  });
}

function roundRect(x, y, w, h, r) {
  chartCtx.beginPath();
  chartCtx.moveTo(x + r, y);
  chartCtx.arcTo(x + w, y, x + w, y + h, r);
  chartCtx.arcTo(x + w, y + h, x, y + h, r);
  chartCtx.arcTo(x, y + h, x, y, r);
  chartCtx.arcTo(x, y, x + w, y, r);
  chartCtx.closePath();
}

async function fetchJson(url, options = {}) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 12000);
  try {
    const res = await fetch(url, {...options, signal: controller.signal});
    const bodyText = await res.text();
    let data = {};
    if (bodyText) {
      try {
        data = JSON.parse(bodyText);
      } catch (_) {
        data = {message: bodyText};
      }
    }

    if (!res.ok) {
      const msg = data.message || bodyText || `HTTP ${res.status}`;
      throw new Error(`${res.status} ${String(msg).substring(0, 220)}`);
    }
    return data;
  } finally {
    clearTimeout(timeout);
  }
}

function renderListeners(runtime) {
  const listeners = Array.isArray(runtime.listeners) ? runtime.listeners : [];
  const renderKey = listeners.length === 0
      ? 'EMPTY'
      : listeners.map(item => `${item?.name || '-'}:${item?.running ? '1' : '0'}`).join('|');
  if (renderKey === lastListenersRenderKey) {
    return;
  }
  lastListenersRenderKey = renderKey;

  clearNode(listenersBody);
  if (listeners.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 2;
    td.className = 'empty-tip';
    td.textContent = I18N.emptyListener;
    tr.appendChild(td);
    listenersBody.appendChild(tr);
    return;
  }

  listeners.forEach(item => {
    const tr = document.createElement('tr');
    appendCell(tr, item.name || '-');

    const statusTd = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = `badge ${item.running ? 'badge-running' : 'badge-stopped'}`;
    badge.textContent = item.running ? I18N.stateRunning : I18N.stateStopped;
    statusTd.appendChild(badge);
    tr.appendChild(statusTd);
    listenersBody.appendChild(tr);
  });
}

function renderFailureReasons(failureReasons) {
  const items = sortedFailureReasonEntries(failureReasons);
  const renderKey = items.length === 0
      ? 'EMPTY'
      : items.map(item => `${item[0]}:${item[1]}`).join('|');
  if (renderKey === lastFailureReasonsRenderKey) {
    return;
  }
  lastFailureReasonsRenderKey = renderKey;

  if (items.length === 0) {
    renderEmptyTableRow(failureReasonsBody, 2, I18N.emptyFailureReason);
    return;
  }

  clearNode(failureReasonsBody);
  items.forEach(([reason, count]) => {
    const tr = document.createElement('tr');

    const reasonTd = document.createElement('td');
    const reasonCode = document.createElement('span');
    reasonCode.className = 'reason-code';
    reasonCode.textContent = reason;
    reasonTd.appendChild(reasonCode);
    tr.appendChild(reasonTd);

    const countTd = document.createElement('td');
    const countBadge = document.createElement('span');
    countBadge.className = 'reason-count';
    countBadge.textContent = String(count);
    countTd.appendChild(countBadge);
    tr.appendChild(countTd);

    failureReasonsBody.appendChild(tr);
  });
}

function renderSelfCheck(selfCheck) {
  const renderKey = selfCheck ? JSON.stringify(selfCheck) : 'EMPTY';
  if (renderKey === lastSelfCheckRenderKey) {
    return;
  }
  lastSelfCheckRenderKey = renderKey;

  clearNode(selfCheckGrid);
  if (!selfCheck) {
    selfCheckGrid.classList.add('empty-tip');
    selfCheckGrid.textContent = I18N.selfCheckUnavailable;
    renderListItems(selfCheckConfigSourcesEl, [], I18N.selfCheckConfigEmpty);
    renderListItems(selfCheckTopFailuresEl, [], I18N.emptyFailureReason);
    runtimeMetaEl.textContent = buildRuntimeMeta();
    return;
  }

  selfCheckGrid.classList.remove('empty-tip');
  const profiles = Array.isArray(selfCheck.activeProfiles) && selfCheck.activeProfiles.length > 0
      ? selfCheck.activeProfiles.join(', ')
      : I18N.selfCheckProfilesEmpty;

  appendSelfCheckCard(
      selfCheckGrid,
      I18N.selfCheckTransport,
      String(selfCheck.transport || '-').toLowerCase(),
      selfCheck.runtimeRunning ? I18N.stateRunning : I18N.stateStopped);
  appendSelfCheckCard(
      selfCheckGrid,
      I18N.selfCheckProfiles,
      profiles,
      `\u66F4\u65B0\u65F6\u95F4 ${formatTime(selfCheck.generatedAt)}`);
  appendSelfCheckCard(
      selfCheckGrid,
      I18N.selfCheckAcl,
      selfCheck.aclEnabled ? I18N.selfCheckEnabled : I18N.selfCheckDisabled,
      `\u5355\u5BA2\u6237\u7AEF\u4E0A\u9650 ${Number(selfCheck.maxConnectionsPerClient || 0) > 0 ? selfCheck.maxConnectionsPerClient : '\u4E0D\u9650'}`);

  const socks = selfCheck.socks || {};
  appendSelfCheckCard(
      selfCheckGrid,
      I18N.selfCheckSocks,
      `${socks.bindHost || '-'}:${socks.port ?? '-'}`,
      [
        socks.enabled ? (socks.running ? I18N.stateRunning : I18N.stateStopped) : I18N.selfCheckDisabled,
        socks.authEnabled ? I18N.selfCheckAuthOn : I18N.selfCheckAuthOff,
        socks.aclEnabled ? I18N.selfCheckAclOn : I18N.selfCheckAclOff
      ].join(' | '));

  const http = selfCheck.http || {};
  appendSelfCheckCard(
      selfCheckGrid,
      I18N.selfCheckHttp,
      `${http.bindHost || '-'}:${http.port ?? '-'}`,
      [
        http.enabled ? (http.running ? I18N.stateRunning : I18N.stateStopped) : I18N.selfCheckDisabled,
        http.authEnabled ? I18N.selfCheckAuthOn : I18N.selfCheckAuthOff,
        http.aclEnabled ? I18N.selfCheckAclOn : I18N.selfCheckAclOff
      ].join(' | '));

  const httpEngine = selfCheck.httpEngine || {};
  const engineValue = httpEngine.external
      ? `${httpEngine.engine || '-'} (external)`
      : `${httpEngine.engine || '-'} (embedded)`;
  const engineMeta = [
    httpEngine.enabled ? (httpEngine.running ? I18N.stateRunning : I18N.stateStopped) : I18N.selfCheckDisabled,
    httpEngine.external
        ? (httpEngine.manageConfig ? I18N.selfCheckManagedConfig : I18N.selfCheckManualConfig)
        : null,
    httpEngine.external && httpEngine.configPath ? `config ${httpEngine.configPath}` : null,
    httpEngine.external && httpEngine.workdir ? `workdir ${httpEngine.workdir}` : null
  ].filter(Boolean).join(' | ');
  appendSelfCheckCard(selfCheckGrid, I18N.selfCheckHttpEngine, engineValue, engineMeta);

  renderListItems(
      selfCheckConfigSourcesEl,
      Array.isArray(selfCheck.configSources) ? selfCheck.configSources.map(source => div => {
        div.textContent = source;
      }) : [],
      I18N.selfCheckConfigEmpty);

  renderListItems(
      selfCheckTopFailuresEl,
      Array.isArray(selfCheck.topFailureReasons) ? selfCheck.topFailureReasons.map(item => div => {
        const reason = document.createElement('span');
        reason.className = 'reason-code';
        reason.textContent = item.reason || '-';
        const count = document.createElement('span');
        count.className = 'reason-count';
        count.textContent = String(item.count ?? 0);
        div.appendChild(reason);
        div.appendChild(count);
      }) : [],
      I18N.emptyFailureReason,
      true);

  runtimeMetaEl.textContent = buildRuntimeMeta(selfCheck);
}

function renderSessions(overview) {
  const sessions = Array.isArray(overview.activeSessions)
      ? overview.activeSessions.slice(0, MAX_RENDER_SESSIONS)
      : [];
  if (sessions.length === 0) {
    if (lastSessionsEmptyState) {
      return;
    }
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 7;
    td.className = 'empty-tip';
    td.textContent = I18N.emptySession;
    clearNode(sessionsBody);
    sessionRowCache.clear();
    tr.appendChild(td);
    sessionsBody.appendChild(tr);
    lastSessionsEmptyState = true;
    return;
  }
  if (lastSessionsEmptyState) {
    clearNode(sessionsBody);
    lastSessionsEmptyState = false;
  }

  const seen = new Set();
  sessions.forEach(s => {
    const key = String(s.id || `${s.clientAddress || '-'}|${s.targetAddress || '-'}|${s.startTime || '-'}`);
    let cached = sessionRowCache.get(key);
    if (!cached) {
      const tr = document.createElement('tr');
      const cells = [];
      for (let i = 0; i < 7; i += 1) {
        const td = document.createElement('td');
        tr.appendChild(td);
        cells.push(td);
      }
      cached = {tr, cells};
      sessionRowCache.set(key, cached);
    }

    setNodeTextIfChanged(cached.cells[0], (s.id || '').substring(0, 8));
    setNodeTextIfChanged(cached.cells[1], s.protocol || '-');
    setNodeTextIfChanged(cached.cells[2], s.clientAddress || '-');
    setNodeTextIfChanged(cached.cells[3], s.targetAddress || '-');
    setNodeTextIfChanged(cached.cells[4], formatBytes(s.bytesFromClient || 0));
    setNodeTextIfChanged(cached.cells[5], formatBytes(s.bytesFromTarget || 0));
    setNodeTextIfChanged(cached.cells[6], formatTime(s.startTime));

    sessionsBody.appendChild(cached.tr);
    seen.add(key);
  });

  Array.from(sessionRowCache.keys()).forEach(key => {
    if (seen.has(key)) {
      return;
    }
    const stale = sessionRowCache.get(key);
    if (stale?.tr?.parentNode === sessionsBody) {
      sessionsBody.removeChild(stale.tr);
    }
    sessionRowCache.delete(key);
  });
}

function renderEvents(overview) {
  const events = Array.isArray(overview.events) ? overview.events.slice(0, 80) : [];
  const renderKey = events.length === 0
      ? 'EMPTY'
      : events.map(e => `${e?.timestamp || '-'}|${String(e?.level || '').toUpperCase()}|${e?.message || ''}`).join('\n');
  if (renderKey === lastEventsRenderKey) {
    return;
  }
  lastEventsRenderKey = renderKey;

  clearNode(eventsBox);
  if (events.length === 0) {
    const div = document.createElement('div');
    div.className = 'empty-tip';
    div.textContent = I18N.emptyEvent;
    eventsBox.appendChild(div);
    return;
  }

  events.forEach(e => {
    const div = document.createElement('div');
    const level = (e.level || '').toUpperCase();
    let cls = 'event-item';
    if (level === 'ERROR') cls += ' event-error';
    if (level === 'WARN') cls += ' event-warn';
    div.className = cls;
    div.textContent = `[${formatTime(e.timestamp)}] [${level || 'INFO'}] ${e.message || ''}`;
    eventsBox.appendChild(div);
  });
}

function setActionButtonsDisabled(disabled) {
  actionButtons.forEach(btn => {
    btn.disabled = disabled;
  });
}

function setProbeDefaultPortByMode() {
  if (String(testProxyPortEl.value || '').trim() !== '') {
    return;
  }
  if (testModeEl.value === 'SOCKS5') {
    testProxyPortEl.placeholder = String(socksPortCfg || 1080);
  } else {
    testProxyPortEl.placeholder = String(httpPortCfg || 8080);
  }
}

function normalizeHost(host) {
  const value = String(host || '').trim();
  if (!value) return '127.0.0.1';
  if (value === '0.0.0.0' || value === '::' || value === '::0') return '127.0.0.1';
  return value;
}

function fillProxyConfig(force = false) {
  const isSocks = testModeEl.value === 'SOCKS5';
  const host = isSocks ? normalizeHost(socksHostCfg) : normalizeHost(httpHostCfg);
  const port = isSocks ? socksPortCfg : httpPortCfg;
  const authEnabled = isSocks ? socksAuthEnabledCfg : httpAuthEnabledCfg;

  if (force || !String(testProxyHostEl.value || '').trim()) {
    testProxyHostEl.value = host;
  }
  if (force || !String(testProxyPortEl.value || '').trim()) {
    testProxyPortEl.value = String(port || '');
  }
  if (force) {
    testUsernameEl.value = '';
    testPasswordEl.value = '';
  }
  if (authEnabled) {
    testUsernameEl.placeholder = '\u4EE3\u7406\u5DF2\u542F\u7528\u8BA4\u8BC1\uFF0C\u8BF7\u586B\u5199';
    testPasswordEl.placeholder = '\u4EE3\u7406\u5DF2\u542F\u7528\u8BA4\u8BC1\uFF0C\u8BF7\u586B\u5199';
  } else {
    testUsernameEl.placeholder = I18N.authPlaceholder;
    testPasswordEl.placeholder = I18N.authPlaceholder;
  }
}

function boolText(value, yesText, noText) {
  return value ? yesText : noText;
}

function setWarpHint(message, isError, isQuick = false) {
  const target = isQuick ? warpQuickHintEl : warpStatusHintEl;
  if (!target) return;
  target.classList.remove('test-fail', 'test-success', 'empty-tip');
  if (!message) {
    target.classList.add('empty-tip');
    target.textContent = isQuick ? I18N.warpQuickInit : I18N.warpHintInit;
    return;
  }
  target.classList.add(isError ? 'test-fail' : 'test-success');
  target.textContent = message;
}

function setWarpQuickButtonsDisabled(disabled) {
  warpQuickButtons.forEach(btn => {
    if (btn) {
      btn.disabled = disabled;
    }
  });
}

function syncWarpOptionSelectionState() {
  const stackValue = String(warpStackArgEl?.value || 'd').trim() || 'd';
  warpStackOptionEls.forEach(btn => {
    if (!btn) return;
    const active = String(btn.dataset.stack || '') === stackValue;
    btn.classList.toggle('chip-selected', active);
    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
  });

  const profileValue = String(warpProfileArgEl?.value || 'full').trim() || 'full';
  warpProfileOptionEls.forEach(btn => {
    if (!btn) return;
    const active = String(btn.dataset.profile || '') === profileValue;
    btn.classList.toggle('chip-selected', active);
    btn.setAttribute('aria-pressed', active ? 'true' : 'false');
  });
}

function initWarpOptionSelectors() {
  warpStackOptionEls.forEach(btn => {
    if (!btn) return;
    btn.addEventListener('click', () => {
      const value = String(btn.dataset.stack || '').trim() || 'd';
      warpStackArgEl.value = value;
      syncWarpOptionSelectionState();
      const hint = I18N.warpStackSelected.replace('{stack}', value);
      setWarpHint(hint, false, true);
    });
  });

  warpProfileOptionEls.forEach(btn => {
    if (!btn) return;
    btn.addEventListener('click', () => {
      const profile = String(btn.dataset.profile || '').trim() || 'full';
      warpProfileArgEl.value = profile;
      const recommendStack = WARP_PROFILE_STACK_HINT[profile] || 'd';
      warpStackArgEl.value = recommendStack;
      syncWarpOptionSelectionState();
      const hint = I18N.warpProfileSelected
          .replace('{profile}', profile)
          .replace('{stack}', recommendStack);
      setWarpHint(hint, false, true);
    });
  });

  syncWarpOptionSelectionState();
}

function renderWarpRuntimeStatus(status) {
  if (!status) {
    return;
  }
  setNodeTextIfChanged(warpCliInstalledEl, boolText(status.warpCliInstalled, I18N.warpStateInstalled, I18N.warpStateMissing));
  setNodeTextIfChanged(warpServiceRunningEl, boolText(status.serviceRunning, I18N.warpStateActive, I18N.warpStateInactive));
  setNodeTextIfChanged(warpConnectedEl, boolText(status.connected, I18N.warpStateConnected, I18N.warpStateDisconnected));
  setNodeTextIfChanged(warpDaemonStatusEl, status.daemonStatus || '-');
  setNodeTextIfChanged(warpConnectivityPhaseEl, status.connectivityPhase || '-');
  setNodeTextIfChanged(warpConnectivityReasonEl, status.connectivityReason || '-');
  setNodeTextIfChanged(warpAccountTypeEl, status.accountType || '-');
  setNodeTextIfChanged(warpRegistrationIdEl, status.registrationId || '-');
  setNodeTextIfChanged(warpModeEl, status.mode || '-');
  setNodeTextIfChanged(warpProtocolEl, status.tunnelProtocol || '-');
  const pmtudRaw = status.pmtudEnabled;
  const pmtudText = pmtudRaw == null
      ? `${status.pmtudStatus || 'Unknown'} (${I18N.warpStateNA})`
      : `${status.pmtudStatus || '-'} (${boolText(Boolean(pmtudRaw), I18N.warpStateYes, I18N.warpStateNo)})`;
  setNodeTextIfChanged(warpPmtudStatusEl, pmtudText);
  setNodeTextIfChanged(
      warpFirewallPrecheckEl,
      boolText(Boolean(status.firewallPrecheckPass), I18N.warpDiagOk, I18N.warpDiagFail));
  setNodeTextIfChanged(warpFirewallTcpEl, status.firewallTcpStatus || '-');
  setNodeTextIfChanged(warpFirewallUdpEl, status.firewallUdpStatus || '-');
  setNodeTextIfChanged(warpOrganizationEl, status.organization || '-');
  const traceState = String(status.warpTraceState || '-').trim();
  setNodeTextIfChanged(warpTraceStateEl, traceState === '-' ? '-' : `warp=${traceState}`);
  setNodeTextIfChanged(
      warpPlusAccountEl,
      boolText(Boolean(status.warpPlusAccount), I18N.warpStateYes, I18N.warpStateNo));
  const plusVerifiedText = status.warpPlusAccount
      ? boolText(Boolean(status.warpPlusVerified), I18N.warpDiagOk, I18N.warpDiagFail)
      : I18N.warpStateNA;
  setNodeTextIfChanged(warpPlusVerifiedEl, plusVerifiedText);
  setNodeTextIfChanged(warpIpv4El, status.publicIpv4 || '-');
  setNodeTextIfChanged(warpIpv6El, status.publicIpv6 || '-');

  const googleCode = status.googleHttpStatus == null ? '-' : status.googleHttpStatus;
  const googleState = boolText(status.googleReachable, I18N.warpGoogleOk, I18N.warpGoogleFail);
  setNodeTextIfChanged(warpGoogleStatusEl, `${googleState} (${googleCode})`);
  setNodeTextIfChanged(warpDnsStatusEl, boolText(status.dnsReady, I18N.warpDiagOk, I18N.warpDiagFail));
  setNodeTextIfChanged(warpUdpStatusEl, boolText(status.edgeUdp443Ready, I18N.warpDiagOk, I18N.warpDiagFail));
  setNodeTextIfChanged(warpTcpStatusEl, boolText(status.edgeTcp443Ready, I18N.warpDiagOk, I18N.warpDiagFail));
  setNodeTextIfChanged(warpRouteStatusEl, boolText(status.routeReady, I18N.warpDiagOk, I18N.warpDiagFail));
  setNodeTextIfChanged(warpCheckedAtEl, formatTime(status.checkedAt));

  const hasPlusMismatch = Boolean(status.warpPlusAccount) && !Boolean(status.warpPlusVerified);
  setWarpHint(
      status.message || I18N.warpHintInit,
      (!status.connected && !!status.warpCliInstalled) || hasPlusMismatch,
      false);
}

function warpHistoryRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${item?.id || '-'}|${item?.epochMillis || '-'}|${item?.connected ? '1' : '0'}|${item?.connectivityPhase || '-'}|${item?.firewallPrecheckPass ? '1' : '0'}|${item?.pmtudEnabled}`)
      .join('\n');
}

function warpHistorySummaryRenderKey(summary) {
  if (!summary || typeof summary !== 'object') {
    return 'EMPTY';
  }
  return [
    summary.windowMinutes ?? '-',
    summary.total ?? '-',
    summary.connected ?? '-',
    summary.disconnected ?? '-',
    summary.firewallPrecheckFailed ?? '-',
    summary.pmtudDisabled ?? '-',
    summary.pmtudUnknown ?? '-',
    summary.topPhase ?? '-'
  ].join('|');
}

function renderWarpHistoryRows(items) {
  if (!warpHistoryBodyEl) return;
  const list = Array.isArray(items) ? items : [];
  clearNode(warpHistoryBodyEl);
  if (list.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 6;
    td.className = 'empty-tip';
    td.textContent = I18N.warpHistoryEmpty;
    tr.appendChild(td);
    warpHistoryBodyEl.appendChild(tr);
    return;
  }

  list.slice(0, 80).forEach(item => {
    const tr = document.createElement('tr');
    appendCell(tr, formatTime(item?.checkedAt || item?.epochMillis));
    appendCell(tr, boolText(Boolean(item?.connected), I18N.warpStateConnected, I18N.warpStateDisconnected));
    appendCell(tr, String(item?.connectivityPhase || '-'));
    appendCell(tr, boolText(Boolean(item?.firewallPrecheckPass), I18N.warpDiagOk, I18N.warpDiagFail));
    const pmtudText = item?.pmtudEnabled == null
        ? I18N.warpStateNA
        : boolText(Boolean(item?.pmtudEnabled), I18N.warpStateYes, I18N.warpStateNo);
    appendCell(tr, pmtudText);
    appendCell(tr, String(item?.publicIpv4 || '-'));
    warpHistoryBodyEl.appendChild(tr);
  });
}

function renderWarpHistorySummary(summary) {
  if (!warpHistorySummaryEl) return;
  if (!summary || typeof summary !== 'object') {
    setNodeTextIfChanged(warpHistorySummaryEl, I18N.warpHistoryEmpty);
    warpHistorySummaryEl.classList.remove('test-success', 'test-fail');
    warpHistorySummaryEl.classList.add('empty-tip');
    return;
  }
  const text = I18N.warpHistorySummaryFmt
      .replace('{m}', String(summary.windowMinutes ?? '-'))
      .replace('{t}', String(summary.total ?? 0))
      .replace('{c}', String(summary.connected ?? 0))
      .replace('{d}', String(summary.disconnected ?? 0))
      .replace('{f}', String(summary.firewallPrecheckFailed ?? 0))
      .replace('{po}', String(summary.pmtudDisabled ?? 0))
      .replace('{pu}', String(summary.pmtudUnknown ?? 0))
      .replace('{p}', String(summary.topPhase || '-'));
  setNodeTextIfChanged(warpHistorySummaryEl, text);
  warpHistorySummaryEl.classList.remove('empty-tip', 'test-fail');
  warpHistorySummaryEl.classList.add('test-success');
}

function warpAlertRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${item?.id || '-'}|${item?.alertCode || '-'}|${item?.active ? '1' : '0'}|${item?.severity || '-'}|${item?.createdEpochMillis || '-'}|${item?.detail || '-'}`)
      .join('\n');
}

function warpAlertSummaryRenderKey(summary) {
  if (!summary || typeof summary !== 'object') {
    return 'EMPTY';
  }
  return [
    summary.windowMinutes ?? '-',
    summary.total ?? '-',
    summary.active ?? '-',
    summary.resolved ?? '-',
    summary.critical ?? '-',
    summary.high ?? '-',
    summary.medium ?? '-',
    summary.low ?? '-',
    summary.topAlertCode ?? '-'
  ].join('|');
}

function warpAlertActiveRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${item?.alertCode || '-'}|${item?.severity || '-'}|${item?.createdEpochMillis || '-'}`)
      .join('|');
}

function renderWarpAlertSummary(summary) {
  if (!warpAlertSummaryEl) return;
  if (!summary || typeof summary !== 'object') {
    setNodeTextIfChanged(warpAlertSummaryEl, I18N.warpAlertSummaryEmpty);
    warpAlertSummaryEl.classList.remove('test-success', 'test-fail');
    warpAlertSummaryEl.classList.add('empty-tip');
    return;
  }
  const text = I18N.warpAlertSummaryFmt
      .replace('{m}', String(summary.windowMinutes ?? '-'))
      .replace('{t}', String(summary.total ?? 0))
      .replace('{a}', String(summary.active ?? 0))
      .replace('{r}', String(summary.resolved ?? 0))
      .replace('{c}', String(summary.critical ?? 0))
      .replace('{h}', String(summary.high ?? 0))
      .replace('{md}', String(summary.medium ?? 0))
      .replace('{l}', String(summary.low ?? 0))
      .replace('{top}', String(summary.topAlertCode || '-'));
  setNodeTextIfChanged(warpAlertSummaryEl, text);
  warpAlertSummaryEl.classList.remove('empty-tip', 'test-fail');
  warpAlertSummaryEl.classList.add('test-success');
}

function renderWarpAlertActive(items) {
  if (!warpAlertActiveEl) return;
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    warpAlertActiveEl.classList.remove('test-fail', 'test-success');
    warpAlertActiveEl.classList.add('empty-tip');
    setNodeTextIfChanged(warpAlertActiveEl, I18N.warpAlertActiveEmpty);
    return;
  }
  const codes = list.slice(0, 6)
      .map(item => `${item.alertCode || '-'}(${item.severity || '-'})`)
      .join(' | ');
  const text = I18N.warpAlertActiveFmt
      .replace('{n}', String(list.length))
      .replace('{codes}', codes || '-');
  warpAlertActiveEl.classList.remove('empty-tip', 'test-success');
  warpAlertActiveEl.classList.add('test-fail');
  setNodeTextIfChanged(warpAlertActiveEl, text);
}

function renderWarpAlertRows(items) {
  if (!warpAlertBodyEl) return;
  const list = Array.isArray(items) ? items : [];
  clearNode(warpAlertBodyEl);
  if (list.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 5;
    td.className = 'empty-tip';
    td.textContent = I18N.warpAlertSummaryEmpty;
    tr.appendChild(td);
    warpAlertBodyEl.appendChild(tr);
    return;
  }

  list.slice(0, 80).forEach(item => {
    const tr = document.createElement('tr');
    appendCell(tr, formatTime(item?.createdAt || item?.createdEpochMillis));
    appendCell(tr, String(item?.severity || '-'));
    appendCell(tr, String(item?.alertCode || '-'));
    appendCell(tr, item?.active ? I18N.warpAlertStateActive : I18N.warpAlertStateResolved);
    appendCell(tr, String(item?.detail || '-'));
    warpAlertBodyEl.appendChild(tr);
  });
}

function warpAlertRuleRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${item?.alertCode || '-'}|${item?.severity || '-'}|${item?.enabled ? '1' : '0'}|${item?.updatedAt || '-'}`)
      .join('\n');
}

function setWarpAlertRuleHint(message, isError = false) {
  if (!warpAlertRuleHintEl) return;
  warpAlertRuleHintEl.classList.remove('test-fail', 'test-success', 'empty-tip');
  if (!message) {
    warpAlertRuleHintEl.classList.add('empty-tip');
    warpAlertRuleHintEl.textContent = I18N.warpAlertRuleLoading;
    return;
  }
  if (isError) {
    warpAlertRuleHintEl.classList.add('test-fail');
  } else {
    warpAlertRuleHintEl.classList.add('test-success');
  }
  warpAlertRuleHintEl.textContent = message;
}

function renderWarpAlertRules(items) {
  if (!warpAlertRuleBodyEl) return;
  const list = Array.isArray(items) ? items : [];
  clearNode(warpAlertRuleBodyEl);
  if (list.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 5;
    td.className = 'empty-tip';
    td.textContent = I18N.warpAlertRuleEmpty;
    tr.appendChild(td);
    warpAlertRuleBodyEl.appendChild(tr);
    setWarpAlertRuleHint(I18N.warpAlertRuleEmpty, true);
    return;
  }

  list.forEach(item => {
    const tr = document.createElement('tr');
    tr.dataset.code = String(item?.alertCode || '-');
    appendCell(tr, String(item?.alertCode || '-'));
    appendCell(tr, String(item?.title || '-'));

    const severityTd = document.createElement('td');
    const severitySel = document.createElement('select');
    severitySel.className = 'warp-alert-rule-severity';
    ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].forEach(opt => {
      const option = document.createElement('option');
      option.value = opt;
      option.textContent = opt;
      if (String(item?.severity || '').toUpperCase() === opt) {
        option.selected = true;
      }
      severitySel.appendChild(option);
    });
    severityTd.appendChild(severitySel);
    tr.appendChild(severityTd);

    const enabledTd = document.createElement('td');
    const enabledInput = document.createElement('input');
    enabledInput.type = 'checkbox';
    enabledInput.className = 'warp-alert-rule-enabled';
    enabledInput.checked = Boolean(item?.enabled);
    enabledTd.appendChild(enabledInput);
    tr.appendChild(enabledTd);

    appendCell(tr, formatTime(item?.updatedAt));
    warpAlertRuleBodyEl.appendChild(tr);
  });
  setWarpAlertRuleHint(`${I18N.warpAlertRuleTitle}: ${list.length}`, false);
}

function collectWarpAlertRulePayload() {
  if (!warpAlertRuleBodyEl) {
    return [];
  }
  const rows = Array.from(warpAlertRuleBodyEl.querySelectorAll('tr[data-code]'));
  const payload = [];
  rows.forEach(row => {
    const code = String(row.dataset.code || '').trim();
    const severityEl = row.querySelector('.warp-alert-rule-severity');
    const enabledEl = row.querySelector('.warp-alert-rule-enabled');
    if (!code || !severityEl || !enabledEl) {
      return;
    }
    payload.push({
      alertCode: code,
      severity: String(severityEl.value || 'LOW').toUpperCase(),
      enabled: Boolean(enabledEl.checked)
    });
  });
  return payload;
}

async function saveWarpAlertRules() {
  const rules = collectWarpAlertRulePayload();
  if (rules.length === 0) {
    setWarpAlertRuleHint(I18N.warpAlertRuleEmpty, true);
    return;
  }
  btnWarpAlertRuleSave.disabled = true;
  try {
    const next = await fetchJson('/api/v1/tools/warp-alert-rules', {
      method: 'PUT',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({rules})
    });
    warpAlertRules = Array.isArray(next) ? next : [];
    lastWarpAlertRuleKey = warpAlertRuleRenderKey(warpAlertRules);
    renderWarpAlertRules(warpAlertRules);
    setWarpAlertRuleHint(I18N.warpAlertRuleSaveDone, false);
    await refreshWarpAlerts(true);
  } catch (error) {
    setWarpAlertRuleHint(`${I18N.refreshFail}: ${error.message || error}`, true);
  } finally {
    btnWarpAlertRuleSave.disabled = false;
  }
}

async function resetWarpAlertRules() {
  btnWarpAlertRuleReset.disabled = true;
  try {
    const next = await fetchJson('/api/v1/tools/warp-alert-rules/reset', {
      method: 'POST'
    });
    warpAlertRules = Array.isArray(next) ? next : [];
    lastWarpAlertRuleKey = warpAlertRuleRenderKey(warpAlertRules);
    renderWarpAlertRules(warpAlertRules);
    setWarpAlertRuleHint(I18N.warpAlertRuleResetDone, false);
    await refreshWarpAlerts(true);
  } catch (error) {
    setWarpAlertRuleHint(`${I18N.refreshFail}: ${error.message || error}`, true);
  } finally {
    btnWarpAlertRuleReset.disabled = false;
  }
}

async function refreshWarpAlerts(forceRefresh = false) {
  if (!warpAlertEnabledCfg) {
    renderWarpAlertSummary(null);
    renderWarpAlertActive([]);
    renderWarpAlertRows([]);
    renderWarpAlertRules([]);
    return;
  }
  const [summary, active, events, rules] = await Promise.all([
    fetchJson('/api/v1/tools/warp-alerts/summary?minutes=120'),
    fetchJson('/api/v1/tools/warp-alerts/active?limit=12'),
    fetchJson('/api/v1/tools/warp-alerts?limit=120'),
    fetchJson('/api/v1/tools/warp-alert-rules')
  ]);

  const summaryKey = warpAlertSummaryRenderKey(summary);
  if (summaryKey !== lastWarpAlertSummaryKey) {
    lastWarpAlertSummaryKey = summaryKey;
    renderWarpAlertSummary(summary);
  }

  const activeKey = warpAlertActiveRenderKey(active);
  if (activeKey !== lastWarpAlertActiveKey || forceRefresh) {
    lastWarpAlertActiveKey = activeKey;
    renderWarpAlertActive(active);
  }

  const eventKey = warpAlertRenderKey(events);
  if (eventKey !== lastWarpAlertRenderKey || forceRefresh) {
    lastWarpAlertRenderKey = eventKey;
    renderWarpAlertRows(events);
  }

  const rulesKey = warpAlertRuleRenderKey(rules);
  if (rulesKey !== lastWarpAlertRuleKey || forceRefresh) {
    warpAlertRules = Array.isArray(rules) ? rules : [];
    lastWarpAlertRuleKey = rulesKey;
    renderWarpAlertRules(warpAlertRules);
  }
}

async function refreshWarpHistory(forceRefresh = false) {
  const summaryUrl = `/api/v1/tools/warp-runtime-history/summary?minutes=60`;
  const historyUrl = `/api/v1/tools/warp-runtime-history?limit=120`;
  const [summary, history] = await Promise.all([
    fetchJson(summaryUrl),
    fetchJson(historyUrl)
  ]);
  const summaryKey = warpHistorySummaryRenderKey(summary);
  if (summaryKey !== lastWarpHistorySummaryKey) {
    lastWarpHistorySummaryKey = summaryKey;
    renderWarpHistorySummary(summary);
  }

  const historyKey = warpHistoryRenderKey(history);
  if (historyKey !== lastWarpHistoryRenderKey || forceRefresh) {
    lastWarpHistoryRenderKey = historyKey;
    renderWarpHistoryRows(history);
  }
}

async function refreshWarpRuntimeStatus(forceRefresh = false) {
  try {
    const suffix = forceRefresh ? '?refresh=true' : '';
    const status = await fetchJson(`/api/v1/tools/warp-runtime-status${suffix}`);
    renderWarpRuntimeStatus(status);
    try {
      await refreshWarpHistory(forceRefresh);
    } catch (historyError) {
      if (warpHistorySummaryEl) {
        warpHistorySummaryEl.classList.remove('test-success');
        warpHistorySummaryEl.classList.add('test-fail');
        setNodeTextIfChanged(warpHistorySummaryEl, `${I18N.refreshFail}: ${historyError.message || historyError}`);
      }
    }
    try {
      await refreshWarpAlerts(forceRefresh);
    } catch (alertError) {
      if (warpAlertSummaryEl) {
        warpAlertSummaryEl.classList.remove('test-success');
        warpAlertSummaryEl.classList.add('test-fail');
        setNodeTextIfChanged(warpAlertSummaryEl, `${I18N.refreshFail}: ${alertError.message || alertError}`);
      }
    }
  } catch (error) {
    setWarpHint(`${I18N.refreshFail}: ${error.message || error}`, true, false);
  }
}

function localizeMenuOpsError(rawError) {
  const raw = String(rawError || '');
  if (raw.includes('High-risk operation requires confirmRisk=true')) {
    return '\u9AD8\u98CE\u9669\u64CD\u4F5C\u672A\u786E\u8BA4\uff0c\u8BF7\u52FE\u9009\u300C\u6211\u786E\u8BA4\u6267\u884C\u9AD8\u98CE\u9669\u64CD\u4F5C\u300D';
  }
  if (raw.includes('Destructive operations are disabled by configuration')) {
    return '\u5F53\u524D\u73AF\u5883\u5DF2\u7981\u7528\u7834\u574F\u6027\u64CD\u4F5C\uff08menu-ops.allow-destructive=false\uff09';
  }
  if (raw.includes('Remote script operations are disabled by configuration')) {
    return '\u5F53\u524D\u73AF\u5883\u5DF2\u7981\u7528\u8FDC\u7A0B\u811A\u672C\u64CD\u4F5C\uff08menu-ops.allow-remote-scripts=false\uff09';
  }
  return raw || '\u64CD\u4F5C\u5931\u8D25';
}

function hasMenuOperation(operationId) {
  return Array.isArray(menuCatalog) && menuCatalog.some(item => item && item.id === operationId);
}

async function runWarpQuickOperation(operationId, argument = null) {
  if (!menuOpsEnabledCfg || !Array.isArray(menuCatalog) || menuCatalog.length === 0) {
    setWarpHint(I18N.warpQuickDisabled, true, true);
    return;
  }
  if (!hasMenuOperation(operationId)) {
    setWarpHint(`\u5F53\u524D\u64CD\u4F5C\u76EE\u5F55\u4E0D\u5305\u542B ${operationId}`, true, true);
    return;
  }

  setWarpQuickButtonsDisabled(true);
  try {
    const payload = {
      operationId,
      argument,
      stdin: null,
      confirmRisk: true
    };
    const result = await fetchJson('/api/v1/menu-ops/jobs', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    menuSelectedJobId = result.jobId || menuSelectedJobId;
    warpTrackedJobId = String(result.jobId || '');
    warpTrackedOperationId = operationId;
    setWarpHint(`${I18N.warpTrackQueued}: ${localizeMenuOperation(operationId, operationId)} | ${result.jobId || '-'}`, false, true);
    await pollMenuJobs(true, true);
    setTimeout(() => {
      pollWarpStatus(true);
    }, 2000);
  } catch (error) {
    setWarpHint(localizeMenuOpsError(error.message || error), true, true);
  } finally {
    setWarpQuickButtonsDisabled(false);
  }
}

function isTerminalStatus(status) {
  const value = String(status || '').toUpperCase();
  return value === 'SUCCESS' || value === 'FAILED' || value === 'TIMEOUT' || value === 'CANCELED';
}

function extractJobFailureReason(detail) {
  if (!detail) return '';
  if (detail.errorMessage) return String(detail.errorMessage).trim();
  const logs = Array.isArray(detail.logs) ? detail.logs : [];
  for (let i = logs.length - 1; i >= 0; i -= 1) {
    const line = String(logs[i] || '').trim();
    if (!line) continue;
    if (line.startsWith('[error]') || line.startsWith('[timeout]') || line.startsWith('[canceled]')) {
      return line;
    }
    if (line.includes('Step failed:') || line.includes('Execution failed:')) {
      return line;
    }
  }
  return '';
}

async function trackWarpQuickJobProgress() {
  if (!warpTrackedJobId) {
    return;
  }

  const job = menuJobs.find(item => item && item.jobId === warpTrackedJobId);
  if (!job) {
    setWarpHint(`${I18N.warpTrackLost}: ${warpTrackedJobId}`, true, true);
    warpTrackedJobId = '';
    warpTrackedOperationId = '';
    return;
  }

  const opName = localizeMenuOperation(
      warpTrackedOperationId || job.operationId,
      job.operationTitle || job.operationId || '-');
  const statusText = menuStatusText(job.status);
  const durationText = `${job.durationMillis ?? '-'} ms`;

  if (!isTerminalStatus(job.status)) {
    setWarpHint(`${I18N.warpTrackRunning}: ${opName} | ${statusText} | ${durationText} | ${job.jobId}`, false, true);
    return;
  }

  if (String(job.status || '').toUpperCase() === 'SUCCESS') {
    setWarpHint(`${I18N.warpTrackDone}: ${opName} | ${statusText} | ${durationText} | ${job.jobId}`, false, true);
    const operationId = String(warpTrackedOperationId || job.operationId || '').toUpperCase();
    if (operationId === 'COLLECT_WARP_DIAGNOSTICS' || operationId === 'CLEANUP_DIAGNOSTICS') {
      setTimeout(() => refreshDiagnosticsFiles(true), 500);
    }
    warpTrackedJobId = '';
    warpTrackedOperationId = '';
    setTimeout(() => pollWarpStatus(true), 1200);
    return;
  }

  try {
    const detail = await fetchJson(`/api/v1/menu-ops/jobs/${encodeURIComponent(job.jobId)}?includeLogs=true&tail=260`);
    const reason = extractJobFailureReason(detail);
    setWarpHint(`${I18N.warpTrackDone}: ${opName} | ${statusText} | ${durationText}${reason ? ` | ${reason}` : ''}`, true, true);
  } catch (error) {
    const fallback = String(job.errorMessage || error.message || error || '').trim();
    setWarpHint(`${I18N.warpTrackDone}: ${opName} | ${statusText} | ${durationText}${fallback ? ` | ${fallback}` : ''}`, true, true);
  }

  warpTrackedJobId = '';
  warpTrackedOperationId = '';
  setTimeout(() => pollWarpStatus(true), 1200);
}

function saveProbeHistory() {
  try {
    localStorage.setItem(PROBE_HISTORY_KEY, JSON.stringify(probeHistory.slice(0, PROBE_HISTORY_MAX)));
  } catch (_) {
    // Ignore storage failures (private mode or quota issues).
  }
}

function loadProbeHistory() {
  try {
    const raw = localStorage.getItem(PROBE_HISTORY_KEY);
    if (!raw) return;
    const parsed = JSON.parse(raw);
    if (Array.isArray(parsed)) {
      probeHistory = parsed.slice(0, PROBE_HISTORY_MAX);
    }
  } catch (_) {
    probeHistory = [];
  }
}

function addProbeHistory(entry) {
  probeHistory.unshift(entry);
  if (probeHistory.length > PROBE_HISTORY_MAX) {
    probeHistory.length = PROBE_HISTORY_MAX;
  }
  saveProbeHistory();
  renderProbeHistory();
}

function renderProbeHistory() {
  clearNode(probeHistoryBody);
  if (probeHistory.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 6;
    td.className = 'empty-tip';
    td.textContent = I18N.noHistory;
    tr.appendChild(td);
    probeHistoryBody.appendChild(tr);
    return;
  }

  probeHistory.forEach(item => {
    const tr = document.createElement('tr');
    appendCell(tr, formatTime(item.time));
    appendCell(tr, item.mode || '-');
    appendCell(tr, item.success ? I18N.success : I18N.fail);
    appendCell(tr, item.targetUrl || '-');
    appendCell(tr, item.proxyAddress || '-');
    appendCell(tr, `${item.durationMillis ?? '-'} ms`);
    probeHistoryBody.appendChild(tr);
  });
}

function menuStatusText(status) {
  const key = `status_${String(status || '').toUpperCase()}`;
  return I18N[key] || status || '-';
}

function menuStatusBadgeClass(status) {
  const value = String(status || '').toUpperCase();
  if (value === 'SUCCESS') return 'badge-success';
  if (value === 'FAILED') return 'badge-failed';
  if (value === 'TIMEOUT') return 'badge-timeout';
  if (value === 'CANCELED') return 'badge-canceled';
  if (value === 'RUNNING') return 'badge-running';
  if (value === 'PENDING') return 'badge-pending';
  return 'badge-stopped';
}

function createdAtMillis(job) {
  const ts = Date.parse(job?.createdAt || '');
  return Number.isFinite(ts) ? ts : 0;
}

function updatedAtMillis(job) {
  const ts = Date.parse(job?.updatedAt || job?.createdAt || '');
  return Number.isFinite(ts) ? ts : 0;
}

function setMenuHint(message, isError) {
  menuOpHintEl.classList.remove('test-fail', 'test-success', 'empty-tip');
  if (!message) {
    menuOpHintEl.classList.add('empty-tip');
    menuOpHintEl.textContent = I18N.menuOpHintInit;
    return;
  }
  if (isError) {
    menuOpHintEl.classList.add('test-fail');
  } else {
    menuOpHintEl.classList.add('test-success');
  }
  menuOpHintEl.textContent = message;
}

function findMenuOperationById(id) {
  return menuCatalog.find(item => item.id === id);
}

function localizeMenuOperation(operationId, fallbackTitle) {
  const key = String(operationId || '').toUpperCase();
  return MENU_OP_ZH[key] || fallbackTitle || operationId || '-';
}

function renderMenuCatalog() {
  clearNode(menuOpSelectEl);
  if (!Array.isArray(menuCatalog) || menuCatalog.length === 0) {
    const opt = document.createElement('option');
    opt.value = '';
    opt.textContent = I18N.menuOpNoCatalog;
    menuOpSelectEl.appendChild(opt);
    menuOpSelectEl.disabled = true;
    return;
  }

  menuOpSelectEl.disabled = false;
  menuCatalog.forEach(item => {
    const opt = document.createElement('option');
    opt.value = item.id;
    opt.textContent = `${localizeMenuOperation(item.id, item.title)} (${item.id})`;
    menuOpSelectEl.appendChild(opt);
  });
  updateMenuOpMeta();
}

function updateMenuOpMeta() {
  const selected = findMenuOperationById(menuOpSelectEl.value) || menuCatalog[0];
  if (!selected) {
    menuOpRiskEl.value = '-';
    menuOpTokenEl.value = '-';
    menuOpArgEl.disabled = true;
    menuOpStdinEl.disabled = true;
    return;
  }

  if (menuOpSelectEl.value !== selected.id) {
    menuOpSelectEl.value = selected.id;
  }

  menuOpRiskEl.value = selected.riskLevel || '-';
  menuOpTokenEl.value = selected.optionToken || '-';
  menuOpArgEl.disabled = !selected.supportsArgument;
  menuOpStdinEl.disabled = !selected.supportsStdin;
  const hintKey = String(selected.id || '').toUpperCase();
  menuOpArgEl.placeholder = MENU_ARG_HINT_ZH[hintKey] || selected.argumentHint || '\u65E0\u53C2\u6570';
  if (!selected.supportsArgument) {
    menuOpArgEl.value = '';
  }
  if (selected.supportsArgument && selected.defaultArgument && !menuOpArgEl.value) {
    menuOpArgEl.value = selected.defaultArgument;
  }
  if (!selected.supportsStdin) {
    menuOpStdinEl.value = '';
  } else if (!menuOpStdinEl.value && selected.defaultStdin) {
    menuOpStdinEl.value = selected.defaultStdin;
  }
}

function renderMenuJobs() {
  clearNode(menuJobBodyEl);
  if (!Array.isArray(menuJobs) || menuJobs.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 4;
    td.className = 'empty-tip';
    td.textContent = I18N.menuOpNoJob;
    tr.appendChild(td);
    menuJobBodyEl.appendChild(tr);
    return;
  }

  menuJobs.forEach(job => {
    const tr = document.createElement('tr');
    tr.className = job.jobId === menuSelectedJobId ? 'job-row-selected' : '';
    tr.addEventListener('click', () => {
      menuSelectedJobId = job.jobId;
      renderMenuJobs();
      fetchMenuJobDetail(job.jobId, {includeLogs: true});
    });

    appendCell(tr, formatTime(job.createdAt));
    appendCell(tr, localizeMenuOperation(job.operationId, job.operationTitle || job.operationId || '-'));

    const statusTd = document.createElement('td');
    const badge = document.createElement('span');
    badge.className = `badge ${menuStatusBadgeClass(job.status)}`;
    badge.textContent = menuStatusText(job.status);
    statusTd.appendChild(badge);
    tr.appendChild(statusTd);

    appendCell(tr, `${job.durationMillis ?? '-'} ms`);
    menuJobBodyEl.appendChild(tr);
  });
}

async function fetchMenuCatalog() {
  if (!menuOpsEnabledCfg) {
    menuOpSelectEl.disabled = true;
    btnMenuOpRun.disabled = true;
    btnMenuOpCancel.disabled = true;
    btnMenuOpDownload.disabled = true;
    btnMenuOpRefresh.disabled = true;
    btnDiagRefresh.disabled = true;
    setWarpQuickButtonsDisabled(true);
    setWarpHint(I18N.warpQuickDisabled, true, true);
    setMenuHint(I18N.menuOpDisabled, true);
    if (menuAuditHintEl) {
      menuAuditHintEl.classList.remove('test-success', 'empty-tip');
      menuAuditHintEl.classList.add('test-fail');
      menuAuditHintEl.textContent = I18N.menuOpDisabled;
    }
    return;
  }
  const catalog = await fetchJson('/api/v1/menu-ops/catalog');
  menuCatalog = Array.isArray(catalog) ? catalog : [];
  btnDiagRefresh.disabled = false;
  setWarpQuickButtonsDisabled(false);
  renderMenuCatalog();
  if (!menuOpsAuditDbEnabledCfg && menuAuditHintEl) {
    menuAuditHintEl.classList.remove('test-success', 'empty-tip');
    menuAuditHintEl.classList.add('test-fail');
    menuAuditHintEl.textContent = 'menu-ops.audit-db-enabled=false，审计数据库已禁用';
  }
}

async function refreshMenuJobs(options = {}) {
  if (!menuOpsEnabledCfg) return;
  const forceDetailLogs = !!options.forceDetailLogs;
  const forceFull = !!options.forceFull;
  try {
    const useDelta = !forceFull && menuJobsCursorMillis > 0;
    const jobsUrl = useDelta
        ? `/api/v1/menu-ops/jobs/delta?limit=${MENU_JOBS_FETCH_LIMIT}&since=${menuJobsCursorMillis}&compact=true`
        : `/api/v1/menu-ops/jobs?limit=${MENU_JOBS_FETCH_LIMIT}`;
    const jobsPayload = await fetchJson(jobsUrl);
    const fetchedJobs = useDelta
        ? (Array.isArray(jobsPayload?.items) ? jobsPayload.items : [])
        : (Array.isArray(jobsPayload) ? jobsPayload : []);

    if (!useDelta) {
      menuJobs = fetchedJobs;
    } else if (fetchedJobs.length > 0) {
      const merged = new Map();
      fetchedJobs.forEach(item => {
        if (item?.jobId) merged.set(item.jobId, item);
      });
      menuJobs.forEach(item => {
        if (item?.jobId && !merged.has(item.jobId)) {
          merged.set(item.jobId, item);
        }
      });
      menuJobs = Array.from(merged.values())
          .sort((a, b) => createdAtMillis(b) - createdAtMillis(a))
          .slice(0, MENU_JOBS_FETCH_LIMIT);
    }

    if (useDelta && Number.isFinite(Number(jobsPayload?.nextSinceEpochMillis))) {
      menuJobsCursorMillis = Math.max(menuJobsCursorMillis, Number(jobsPayload.nextSinceEpochMillis));
    } else if (fetchedJobs.length > 0) {
      const fetchedMaxCursor = fetchedJobs.reduce((max, item) => Math.max(max, updatedAtMillis(item)), 0);
      menuJobsCursorMillis = Math.max(menuJobsCursorMillis, fetchedMaxCursor);
    } else if (!useDelta && menuJobs.length > 0) {
      const fullMaxCursor = menuJobs.reduce((max, item) => Math.max(max, updatedAtMillis(item)), 0);
      menuJobsCursorMillis = Math.max(menuJobsCursorMillis, fullMaxCursor);
    } else if (!useDelta) {
      menuJobsCursorMillis = 0;
    }

    if (forceFull) {
      menuJobsDeltaRounds = 0;
    } else {
      menuJobsDeltaRounds = Math.min(MENU_JOBS_DELTA_ROUNDS_MAX, menuJobsDeltaRounds + 1);
    }

    if (!menuSelectedJobId && menuJobs.length > 0) {
      menuSelectedJobId = menuJobs[0].jobId;
    }
    if (menuSelectedJobId && !menuJobs.some(item => item.jobId === menuSelectedJobId)) {
      menuSelectedJobId = menuJobs.length > 0 ? menuJobs[0].jobId : '';
    }
    renderMenuJobs();
    if (menuSelectedJobId) {
      const selected = menuJobs.find(item => item && item.jobId === menuSelectedJobId);
      const status = String(selected?.status || '').toUpperCase();
      const running = status === 'RUNNING' || status === 'PENDING';
      const includeLogs = forceDetailLogs || running || menuSelectedJobId === warpTrackedJobId;
      await fetchMenuJobDetail(menuSelectedJobId, {includeLogs, tailLines: running ? 260 : UI_LOG_MAX_LINES});
    } else {
      menuJobLogsEl.textContent = '-';
      menuLogMetaEl.textContent = I18N.menuLogMeta;
      lastRenderedLogJobId = '';
    }
    await trackWarpQuickJobProgress();
  } catch (error) {
    setMenuHint(`${I18N.menuJobLoadFail}: ${error.message || error}`, true);
  }
}

async function fetchMenuJobDetail(jobId, options = {}) {
  if (!jobId) return;
  const includeLogs = options.includeLogs !== false;
  const tailLines = Number.isFinite(Number(options.tailLines))
      ? Math.max(1, Math.min(UI_LOG_MAX_LINES, Number(options.tailLines)))
      : UI_LOG_MAX_LINES;
  try {
    const detail = await fetchJson(
        `/api/v1/menu-ops/jobs/${encodeURIComponent(jobId)}?includeLogs=${includeLogs ? 'true' : 'false'}&tail=${tailLines}`);
    const logs = Array.isArray(detail.logs) ? detail.logs : [];
    if (includeLogs) {
      const clipped = logs.length > UI_LOG_MAX_LINES ? logs.slice(-UI_LOG_MAX_LINES) : logs;
      const prefix = logs.length > UI_LOG_MAX_LINES
          ? [`[日志已裁剪，仅显示最近 ${UI_LOG_MAX_LINES} 行，原始 ${logs.length} 行]`, '']
          : [];
      const digest = `${jobId}|${detail.updatedAt || ''}|${logs.length}|${logs.length > 0 ? String(logs[logs.length - 1]) : ''}`;
      if (digest !== lastRenderedLogDigest) {
        menuJobLogsEl.textContent = clipped.length > 0 ? prefix.concat(clipped).join('\n') : '-';
        lastRenderedLogDigest = digest;
      }
      lastRenderedLogJobId = jobId;
    } else if (lastRenderedLogJobId !== jobId) {
      menuJobLogsEl.textContent = '-';
      lastRenderedLogJobId = '';
      lastRenderedLogDigest = '';
    }
    const opName = localizeMenuOperation(detail.operationId, detail.operationTitle || detail.operationId || '-');
    menuLogMetaEl.textContent = `${opName} | ${menuStatusText(detail.status)} | ${detail.jobId}`;
    const running = String(detail.status || '').toUpperCase() === 'RUNNING';
    btnMenuOpCancel.disabled = !running;
  } catch (error) {
    menuJobLogsEl.textContent = String(error.message || error);
    menuLogMetaEl.textContent = I18N.menuJobLoadFail;
    lastRenderedLogDigest = '';
  }
}

function setDiagHint(message, isError = false) {
  if (!diagHintEl) return;
  diagHintEl.classList.remove('test-fail', 'test-success', 'empty-tip');
  if (!message) {
    diagHintEl.classList.add('empty-tip');
    diagHintEl.textContent = I18N.diagNoFiles;
    return;
  }
  if (isError) {
    diagHintEl.classList.add('test-fail');
  } else {
    diagHintEl.classList.add('test-success');
  }
  diagHintEl.textContent = message;
}

function diagnosticsFilesRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${String(item?.name || '')}|${Number(item?.sizeBytes || 0)}|${String(item?.modifiedAt || '')}`)
      .join('\n');
}

function diagnosticsSummaryRenderKey(summary) {
  if (!summary || typeof summary !== 'object') {
    return 'EMPTY';
  }
  return [
    String(summary.directory || ''),
    summary.exists ? '1' : '0',
    Number(summary.fileCount || 0),
    Number(summary.totalBytes || 0),
    String(summary.latestModifiedAt || ''),
    Number(summary.retentionDays || 0),
    Number(summary.maxFiles || 0),
    Number(summary.maxDownloadBytes || 0)
  ].join('|');
}

function renderDiagnosticsSummary() {
  if (!diagSummaryEl) return;
  if (!diagnosticsSummary || typeof diagnosticsSummary !== 'object') {
    setNodeTextIfChanged(diagSummaryEl, I18N.diagSummaryEmpty);
    diagSummaryEl.classList.remove('test-success', 'test-fail');
    diagSummaryEl.classList.add('empty-tip');
    return;
  }

  const text = I18N.diagSummaryFmt
      .replace('{dir}', String(diagnosticsSummary.directory || menuOpsDiagnosticsDirCfg))
      .replace('{count}', String(Number(diagnosticsSummary.fileCount || 0)))
      .replace('{size}', formatBytes(Number(diagnosticsSummary.totalBytes || 0)))
      .replace('{latest}', diagnosticsSummary.latestModifiedAt ? formatTime(diagnosticsSummary.latestModifiedAt) : '-')
      .replace('{days}', String(Number(diagnosticsSummary.retentionDays || 0)))
      .replace('{maxFiles}', String(Number(diagnosticsSummary.maxFiles || 0)))
      .replace('{maxDownload}', formatBytes(Number(diagnosticsSummary.maxDownloadBytes || 0)));
  setNodeTextIfChanged(diagSummaryEl, text);
  diagSummaryEl.classList.remove('empty-tip', 'test-fail');
  diagSummaryEl.classList.add('test-success');
}

async function downloadDiagnosticFile(fileName) {
  const safeName = String(fileName || '').trim();
  if (!safeName) return;
  try {
    const response = await fetch(`/api/v1/menu-ops/diagnostics/${encodeURIComponent(safeName)}`, {method: 'GET'});
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`${response.status} ${body || 'download failed'}`);
    }
    const blob = await response.blob();
    const filename = parseAttachmentFilename(response.headers.get('content-disposition')) || safeName;
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setDiagHint(`${I18N.menuDownloadDone}: ${filename}`, false);
  } catch (error) {
    setDiagHint(error.message || String(error), true);
  }
}

async function deleteDiagnosticFile(fileName) {
  const safeName = String(fileName || '').trim();
  if (!safeName) return;
  const confirmText = I18N.diagDeleteConfirm.replace('{name}', safeName);
  if (!window.confirm(confirmText)) {
    return;
  }
  try {
    await fetchJson(`/api/v1/menu-ops/diagnostics/${encodeURIComponent(safeName)}`, {method: 'DELETE'});
    setDiagHint(`${I18N.diagDeleteDone}: ${safeName}`, false);
    await refreshDiagnosticsFiles(true);
  } catch (error) {
    setDiagHint(error.message || String(error), true);
  }
}

async function runDiagnosticsCleanup() {
  if (!menuOpsEnabledCfg) {
    setDiagHint(I18N.menuOpDisabled, true);
    return;
  }
  btnDiagCleanup.disabled = true;
  try {
    const result = await fetchJson('/api/v1/menu-ops/jobs', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({
        operationId: 'CLEANUP_DIAGNOSTICS',
        argument: null,
        stdin: null,
        confirmRisk: false
      })
    });
    const jobId = result?.jobId ? String(result.jobId) : '-';
    setDiagHint(`${I18N.diagCleanupSubmit}: ${jobId}`, false);
    await pollMenuJobs(true, true);
    setTimeout(() => refreshDiagnosticsFiles(true), 450);
  } catch (error) {
    setDiagHint(localizeMenuOpsError(error.message || error), true);
  } finally {
    btnDiagCleanup.disabled = false;
  }
}

function renderDiagnosticsFiles() {
  if (!diagFileBodyEl) return;
  clearNode(diagFileBodyEl);
  if (!Array.isArray(diagnosticsFiles) || diagnosticsFiles.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 4;
    td.className = 'empty-tip';
    td.textContent = I18N.diagNoFiles;
    tr.appendChild(td);
    diagFileBodyEl.appendChild(tr);
    setDiagHint(I18N.diagNoFiles, false);
    return;
  }

  diagnosticsFiles.forEach(item => {
    const name = String(item?.name || '').trim();
    const tr = document.createElement('tr');
    appendCell(tr, name || '-');
    appendCell(tr, formatBytes(Number(item?.sizeBytes || 0)));
    appendCell(tr, formatTime(item?.modifiedAt));

    const actionTd = document.createElement('td');
    const actions = document.createElement('div');
    actions.className = 'diag-file-actions';

    const downloadBtn = document.createElement('button');
    downloadBtn.type = 'button';
    downloadBtn.className = 'action-btn action-neutral';
    downloadBtn.textContent = I18N.diagDownload;
    downloadBtn.addEventListener('click', () => downloadDiagnosticFile(name));

    const deleteBtn = document.createElement('button');
    deleteBtn.type = 'button';
    deleteBtn.className = 'action-btn action-warn';
    deleteBtn.textContent = I18N.diagDelete;
    deleteBtn.addEventListener('click', () => deleteDiagnosticFile(name));

    actions.appendChild(downloadBtn);
    actions.appendChild(deleteBtn);
    actionTd.appendChild(actions);
    tr.appendChild(actionTd);
    diagFileBodyEl.appendChild(tr);
  });

  setDiagHint(`${I18N.diagTitle}: ${diagnosticsFiles.length}`, false);
}

async function refreshDiagnosticsFiles(force = false) {
  if (!menuOpsEnabledCfg) return;
  const now = Date.now();
  if (!force && now - lastDiagnosticsRefreshAt < calcRefreshInterval(DIAG_REFRESH_MS_VISIBLE)) {
    return;
  }
  if (isRefreshingDiagnostics) {
    return;
  }
  isRefreshingDiagnostics = true;
  try {
    const [filesPayload, summaryPayload] = await Promise.all([
      fetchJson('/api/v1/menu-ops/diagnostics?limit=120'),
      fetchJson('/api/v1/menu-ops/diagnostics/summary')
    ]);
    const nextFiles = Array.isArray(filesPayload) ? filesPayload : [];
    const nextFilesKey = diagnosticsFilesRenderKey(nextFiles);
    if (nextFilesKey !== lastDiagnosticsRenderKey) {
      diagnosticsFiles = nextFiles;
      lastDiagnosticsRenderKey = nextFilesKey;
      renderDiagnosticsFiles();
    }

    const nextSummaryKey = diagnosticsSummaryRenderKey(summaryPayload);
    if (nextSummaryKey !== lastDiagnosticsSummaryKey) {
      diagnosticsSummary = summaryPayload || null;
      lastDiagnosticsSummaryKey = nextSummaryKey;
      renderDiagnosticsSummary();
    }
  } catch (error) {
    setDiagHint(`${I18N.diagLoadFail}: ${error.message || error}`, true);
    if (diagSummaryEl) {
      diagSummaryEl.classList.remove('test-success');
      diagSummaryEl.classList.add('test-fail');
      setNodeTextIfChanged(diagSummaryEl, `${I18N.diagLoadFail}: ${error.message || error}`);
    }
  } finally {
    lastDiagnosticsRefreshAt = Date.now();
    isRefreshingDiagnostics = false;
  }
}

function menuAuditRecordsRenderKey(items) {
  const list = Array.isArray(items) ? items : [];
  if (list.length === 0) {
    return 'EMPTY';
  }
  return list
      .map(item => `${item?.id || '-'}|${item?.jobId || '-'}|${item?.status || '-'}|${item?.createdEpochMillis || '-'}|${item?.durationMillis || '-'}`)
      .join('\n');
}

function menuAuditSummaryRenderKey(summary) {
  if (!summary || typeof summary !== 'object') {
    return 'EMPTY';
  }
  return [
    summary.windowMinutes ?? '-',
    summary.total ?? '-',
    summary.success ?? '-',
    summary.failed ?? '-',
    summary.timeout ?? '-',
    summary.canceled ?? '-',
    summary.running ?? '-',
    summary.pending ?? '-',
    summary.topOperationId ?? '-'
  ].join('|');
}

function renderMenuAuditSummary() {
  if (!menuAuditSummaryEl) return;
  if (!menuAuditSummary || typeof menuAuditSummary !== 'object') {
    setNodeTextIfChanged(menuAuditSummaryEl, I18N.menuAuditSummaryEmpty);
    menuAuditSummaryEl.classList.remove('test-success', 'test-fail');
    menuAuditSummaryEl.classList.add('empty-tip');
    return;
  }

  const text = I18N.menuAuditSummaryFmt
      .replace('{m}', String(menuAuditSummary.windowMinutes ?? '-'))
      .replace('{t}', String(menuAuditSummary.total ?? 0))
      .replace('{s}', String(menuAuditSummary.success ?? 0))
      .replace('{f}', String(menuAuditSummary.failed ?? 0))
      .replace('{to}', String(menuAuditSummary.timeout ?? 0))
      .replace('{c}', String(menuAuditSummary.canceled ?? 0))
      .replace('{r}', String(menuAuditSummary.running ?? 0))
      .replace('{p}', String(menuAuditSummary.pending ?? 0))
      .replace('{top}', String(menuAuditSummary.topOperationId || '-'));
  setNodeTextIfChanged(menuAuditSummaryEl, text);
  menuAuditSummaryEl.classList.remove('empty-tip', 'test-fail');
  menuAuditSummaryEl.classList.add('test-success');
}

function renderMenuAuditRows() {
  if (!menuAuditBodyEl || !menuAuditHintEl) return;
  clearNode(menuAuditBodyEl);
  if (!Array.isArray(menuAuditRecords) || menuAuditRecords.length === 0) {
    const tr = document.createElement('tr');
    const td = document.createElement('td');
    td.colSpan = 5;
    td.className = 'empty-tip';
    td.textContent = I18N.menuAuditNoData;
    tr.appendChild(td);
    menuAuditBodyEl.appendChild(tr);
    menuAuditHintEl.classList.remove('test-success', 'test-fail');
    menuAuditHintEl.classList.add('empty-tip');
    menuAuditHintEl.textContent = I18N.menuAuditNoData;
    return;
  }

  menuAuditRecords.slice(0, 80).forEach(item => {
    const tr = document.createElement('tr');
    appendCell(tr, formatTime(item?.createdAt || item?.createdEpochMillis));
    appendCell(tr, localizeMenuOperation(item?.operationId, item?.operationTitle || item?.operationId || '-'));
    appendCell(tr, menuStatusText(item?.status));
    appendCell(tr, String(item?.riskLevel || '-'));
    appendCell(tr, `${item?.durationMillis ?? '-'} ms`);
    menuAuditBodyEl.appendChild(tr);
  });

  menuAuditHintEl.classList.remove('empty-tip', 'test-fail');
  menuAuditHintEl.classList.add('test-success');
  menuAuditHintEl.textContent = I18N.menuAuditHintFmt
      .replace('{n}', String(menuAuditRecords.length))
      .replace('{db}', menuOpsAuditDbPathCfg || '-');
}

async function refreshMenuAudit(force = false) {
  if (!menuOpsEnabledCfg || !menuOpsAuditDbEnabledCfg) {
    return;
  }
  const now = Date.now();
  if (!force && now - lastMenuAuditRefreshAt < calcRefreshInterval(DIAG_REFRESH_MS_VISIBLE)) {
    return;
  }
  if (isRefreshingMenuAudit) {
    return;
  }
  isRefreshingMenuAudit = true;
  try {
    const [recordsPayload, summaryPayload] = await Promise.all([
      fetchJson('/api/v1/menu-ops/audit?limit=120'),
      fetchJson('/api/v1/menu-ops/audit/summary?minutes=240')
    ]);
    const nextRecords = Array.isArray(recordsPayload) ? recordsPayload : [];
    const recordsKey = menuAuditRecordsRenderKey(nextRecords);
    if (recordsKey !== lastMenuAuditRenderKey) {
      menuAuditRecords = nextRecords;
      lastMenuAuditRenderKey = recordsKey;
      renderMenuAuditRows();
    }

    const summaryKey = menuAuditSummaryRenderKey(summaryPayload);
    if (summaryKey !== lastMenuAuditSummaryKey) {
      menuAuditSummary = summaryPayload || null;
      lastMenuAuditSummaryKey = summaryKey;
      renderMenuAuditSummary();
    }
  } catch (error) {
    if (menuAuditHintEl) {
      menuAuditHintEl.classList.remove('test-success', 'empty-tip');
      menuAuditHintEl.classList.add('test-fail');
      menuAuditHintEl.textContent = `${I18N.refreshFail}: ${error.message || error}`;
    }
    if (menuAuditSummaryEl) {
      menuAuditSummaryEl.classList.remove('test-success');
      menuAuditSummaryEl.classList.add('test-fail');
      setNodeTextIfChanged(menuAuditSummaryEl, `${I18N.refreshFail}: ${error.message || error}`);
    }
  } finally {
    lastMenuAuditRefreshAt = Date.now();
    isRefreshingMenuAudit = false;
  }
}

async function runMenuOperation() {
  if (!menuOpsEnabledCfg) {
    setMenuHint(I18N.menuOpDisabled, true);
    return;
  }
  const operationId = String(menuOpSelectEl.value || '').trim();
  if (!operationId) {
    setMenuHint(I18N.menuOpSelectRequired, true);
    return;
  }
  const selected = findMenuOperationById(operationId);
  if (selected && String(selected.riskLevel || '').toUpperCase() === 'HIGH' && !menuOpConfirmEl.checked) {
    setMenuHint('\u9AD8\u98CE\u9669\u64CD\u4F5C\u9700\u8981\u5148\u52FE\u9009\u300C\u6211\u786E\u8BA4\u6267\u884C\u9AD8\u98CE\u9669\u64CD\u4F5C\u300D', true);
    return;
  }

  const payload = {
    operationId,
    argument: String(menuOpArgEl.value || '').trim() || null,
    stdin: String(menuOpStdinEl.value || '').trim() || null,
    confirmRisk: !!menuOpConfirmEl.checked
  };

  btnMenuOpRun.disabled = true;
  btnMenuOpRun.textContent = I18N.menuOpSubmitRunning;
  try {
    const result = await fetchJson('/api/v1/menu-ops/jobs', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    menuSelectedJobId = result.jobId || '';
    setMenuHint(I18N.menuOpSubmitDone.replace('{id}', menuSelectedJobId), false);
    await pollMenuJobs(true, true);
  } catch (error) {
    setMenuHint(localizeMenuOpsError(error.message || error), true);
  } finally {
    btnMenuOpRun.disabled = false;
    btnMenuOpRun.textContent = I18N.menuOpRun;
  }
}

async function cancelMenuOperation() {
  if (!menuSelectedJobId) {
    setMenuHint(I18N.menuNoActiveJob, true);
    return;
  }
  try {
    await fetchJson(`/api/v1/menu-ops/jobs/${encodeURIComponent(menuSelectedJobId)}/cancel`, {method: 'POST'});
    setMenuHint(I18N.menuCancelDone, false);
    await pollMenuJobs(true, true);
  } catch (error) {
    setMenuHint(error.message || String(error), true);
  }
}

function parseAttachmentFilename(disposition) {
  const header = String(disposition || '');
  if (!header) return '';
  const utf8Match = header.match(/filename\*=UTF-8''([^;]+)/i);
  if (utf8Match && utf8Match[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim());
    } catch (_) {
      // ignore malformed encoded filename and fallback to plain filename
    }
  }
  const plainMatch = header.match(/filename=\"?([^\";]+)\"?/i);
  return plainMatch && plainMatch[1] ? plainMatch[1].trim() : '';
}

async function downloadMenuJobLogs() {
  if (!menuSelectedJobId) {
    setMenuHint(I18N.menuNoSelectedJob, true);
    return;
  }
  try {
    const response = await fetch(
        `/api/v1/menu-ops/jobs/${encodeURIComponent(menuSelectedJobId)}/logs.txt?tail=3000`,
        {method: 'GET'});
    if (!response.ok) {
      const body = await response.text();
      throw new Error(`${response.status} ${body || 'download failed'}`);
    }
    const blob = await response.blob();
    const filename = parseAttachmentFilename(response.headers.get('content-disposition'))
        || `menu-job-${menuSelectedJobId}.log.txt`;
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
    setMenuHint(`${I18N.menuDownloadDone}: ${filename}`, false);
  } catch (error) {
    setMenuHint(error.message || String(error), true);
  }
}

function renderProxyTestResult(result, isError) {
  testResultBoxEl.classList.remove('test-success', 'test-fail', 'empty-tip');
  if (isError) {
    testResultBoxEl.classList.add('test-fail');
    testResultBoxEl.textContent = `${I18N.probeError}: ${result}`;
    return;
  }

  const lines = [];
  const localizedMessage = localizeProbeMessage(result.message || '-');
  lines.push(`${I18N.resultMode}: ${result.mode || '-'}`);
  lines.push(`${I18N.resultStatus}: ${result.success ? I18N.success : I18N.fail}`);
  lines.push(`${I18N.resultMessage}: ${localizedMessage}`);
  lines.push(`${I18N.resultTarget}: ${result.targetUrl || '-'}`);
  lines.push(`${I18N.resultProxy}: ${result.proxyAddress || '-'}`);
  lines.push(`${I18N.resultCost}: ${result.durationMillis ?? '-'} ms`);
  if (result.httpStatus != null) {
    lines.push(`${I18N.resultHttp}: ${result.httpStatus}`);
  }
  if (result.responseSnippet) {
    lines.push(`${I18N.resultSnippet}: ${result.responseSnippet}`);
  }

  testResultBoxEl.classList.add(result.success ? 'test-success' : 'test-fail');
  testResultBoxEl.textContent = lines.join('\n');
}

function renderScenarioResult(lines, isError) {
  scenarioResultBoxEl.classList.remove('test-success', 'test-fail', 'empty-tip');
  scenarioResultBoxEl.classList.add(isError ? 'test-fail' : 'test-success');
  scenarioResultBoxEl.textContent = lines.join('\n');
}

function localizeProbeMessage(message) {
  const msg = String(message || '').trim();
  if (!msg) return '-';
  if (msg === 'SOCKS5 proxy probe passed') return '代理探测成功（SOCKS5）';
  if (msg === 'HTTP proxy probe passed') return '代理探测成功（HTTP）';
  if (msg.startsWith('HTTP proxy returned status ')) {
    return '代理返回 HTTP 状态码: ' + msg.substring('HTTP proxy returned status '.length);
  }
  if (msg === 'Only http/https target URL is supported') return '目标 URL 仅支持 http/https';
  if (msg.startsWith('Invalid target URL:')) {
    return '目标 URL 格式错误: ' + msg.substring('Invalid target URL:'.length).trim();
  }
  if (msg.startsWith('Unsupported test mode:')) {
    return '不支持的测试模式: ' + msg.substring('Unsupported test mode:'.length).trim();
  }
  if (msg.startsWith('Probe exception:')) {
    return '测试异常: ' + msg.substring('Probe exception:'.length).trim();
  }
  if (msg === 'SOCKS5 requires username/password') return 'SOCKS5 代理需要用户名密码';
  if (msg === 'SOCKS5 CONNECT ok, ChatGPT 443 reachable') return 'SOCKS5 CONNECT 成功，ChatGPT 443 端口可达';
  if (msg === 'SOCKS5 CONNECT ok, Netflix 443 reachable') return 'SOCKS5 CONNECT 成功，Netflix 443 端口可达';
  if (msg === 'SOCKS5 CONNECT ok, Google Scholar 443 reachable') return 'SOCKS5 CONNECT 成功，Google Scholar 443 端口可达';
  if (msg === 'SOCKS5 CONNECT ok, Telegram API 443 reachable') return 'SOCKS5 CONNECT 成功，Telegram API 443 端口可达';
  if (msg === 'SOCKS5 CONNECT ok, IPv4 egress path reachable') return 'SOCKS5 CONNECT 成功，IPv4 出口链路可达';
  if (msg === 'Google risk/captcha likely triggered, rotate egress IP and retry') return '可能触发 Google 验证码或风控，请切换出口 IP 后重试';
  if (msg === 'Netflix reachable but response status is abnormal, possible geo restriction') return 'Netflix 可达但状态异常，可能存在区域限制';
  if (msg === 'ChatGPT reachable but access is restricted, try another region/egress') return 'ChatGPT 可达但受限，请尝试切换区域或出口';
  if (msg.startsWith('Reachable, HTTP status ')) return '连通成功，HTTP 状态码 ' + msg.substring('Reachable, HTTP status '.length);
  if (msg === 'Reachable') return '连通成功';
  return msg;
}
async function runProxyTest() {
  const targetUrl = String(testTargetUrlEl.value || '').trim();
  if (!targetUrl) {
    renderProxyTestResult(I18N.targetEmpty, true);
    return;
  }

  const payload = {
    mode: testModeEl.value,
    targetUrl,
    proxyHost: String(testProxyHostEl.value || '').trim() || null,
    proxyPort: String(testProxyPortEl.value || '').trim() ? Number(testProxyPortEl.value) : null,
    username: String(testUsernameEl.value || '').trim() || null,
    password: String(testPasswordEl.value || '').trim() || null,
    timeoutMillis: String(testTimeoutEl.value || '').trim() ? Number(testTimeoutEl.value) : 8000
  };

  btnRunProxyTest.disabled = true;
  btnRunProxyTest.textContent = I18N.probeRunning;
  try {
    const result = await fetchJson('/api/v1/tools/proxy-test', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    addProbeHistory({
      time: new Date().toISOString(),
      mode: result.mode || payload.mode,
      success: !!result.success,
      targetUrl: result.targetUrl || payload.targetUrl,
      proxyAddress: result.proxyAddress || '-',
      durationMillis: result.durationMillis ?? null
    });
    renderProxyTestResult(result, false);
  } catch (error) {
    addProbeHistory({
      time: new Date().toISOString(),
      mode: payload.mode,
      success: false,
      targetUrl: payload.targetUrl,
      proxyAddress: `${payload.proxyHost || '-'}:${payload.proxyPort || '-'}`,
      durationMillis: null
    });
    renderProxyTestResult(error.message || String(error), true);
  } finally {
    btnRunProxyTest.disabled = false;
    btnRunProxyTest.textContent = I18N.probeBtn;
  }
}

async function runScenarioTest() {
  const selected = scenarioCheckEls
      .filter(item => item.checked)
      .map(item => String(item.value || '').trim())
      .filter(Boolean);
  if (selected.length === 0) {
    renderScenarioResult([I18N.scenarioSelectRequired], true);
    return;
  }

  const payload = {
    mode: testModeEl.value,
    proxyHost: String(testProxyHostEl.value || '').trim() || null,
    proxyPort: String(testProxyPortEl.value || '').trim() ? Number(testProxyPortEl.value) : null,
    username: String(testUsernameEl.value || '').trim() || null,
    password: String(testPasswordEl.value || '').trim() || null,
    timeoutMillis: String(testTimeoutEl.value || '').trim() ? Number(testTimeoutEl.value) : 8000,
    scenarios: selected
  };

  btnRunScenarioTest.disabled = true;
  btnRunScenarioTest.textContent = I18N.scenarioRunning;
  try {
    const result = await fetchJson('/api/v1/tools/scenario-test', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify(payload)
    });
    const items = Array.isArray(result.items) ? result.items : [];
    const successCount = items.filter(item => item && item.success).length;
    const lines = [];
    lines.push(`${I18N.scenarioSummary}: ${successCount}/${items.length}`);
    lines.push(`${I18N.resultMode}: ${result.mode || payload.mode}`);
    lines.push(`${I18N.resultProxy}: ${result.proxyAddress || '-'}`);
    lines.push(`${I18N.resultCost}: ${result.durationMillis ?? '-'} ms`);
    lines.push('');

    items.forEach(item => {
      const name = I18N[`scenario_${String(item.scenario || '').toUpperCase()}`] || item.scenario || '-';
      const statusText = item.success ? I18N.success : I18N.fail;
      const statusCode = item.httpStatus == null ? '-' : item.httpStatus;
      const message = localizeProbeMessage(item.message || '-');
      lines.push(`[${name}] ${statusText} | HTTP: ${statusCode} | ${item.durationMillis ?? '-'} ms`);
      lines.push(`  ${message}`);
    });

    renderScenarioResult(lines, successCount !== items.length);
  } catch (error) {
    renderScenarioResult([`${I18N.probeError}: ${error.message || error}`], true);
  } finally {
    btnRunScenarioTest.disabled = false;
    btnRunScenarioTest.textContent = I18N.scenarioBtn;
  }
}

async function refresh() {
  try {
    const [runtimeResult, overviewResult, selfCheckResult] = await Promise.allSettled([
      fetchJson('/api/v1/runtime/status'),
      fetchJson('/api/v1/metrics/overview'),
      fetchJson('/api/v1/runtime/self-check')
    ]);
    if (runtimeResult.status !== 'fulfilled') {
      throw runtimeResult.reason;
    }
    if (overviewResult.status !== 'fulfilled') {
      throw overviewResult.reason;
    }
    const runtime = runtimeResult.value;
    const overview = overviewResult.value;
    const selfCheck = selfCheckResult.status === 'fulfilled' ? selfCheckResult.value : null;

    setRuntimeState(runtime.running);
    renderListeners(runtime);
    renderSelfCheck(selfCheck);
    renderFailureReasons(overview.failureReasons);

    setNodeTextIfChanged(mActive, overview.activeConnections ?? 0);
    setNodeTextIfChanged(mTotal, overview.totalConnections ?? 0);
    setNodeTextIfChanged(mBlocked, overview.blockedConnections ?? 0);
    setNodeTextIfChanged(mAuthFail, overview.authFailures ?? 0);
    setNodeTextIfChanged(mConnectFail, overview.connectFailures ?? 0);
    setNodeTextIfChanged(mInBytes, formatBytes(overview.totalBytesFromClient ?? 0));
    setNodeTextIfChanged(mOutBytes, formatBytes(overview.totalBytesFromTarget ?? 0));

    drawProtocolChart(overview.socksConnections, overview.httpConnections, overview.httpsTunnelConnections);
    renderSessions(overview);
    renderEvents(overview);

    setNodeTextIfChanged(lastUpdateEl, `${I18N.lastUpdate}: ${formatTime(new Date().toISOString())}`);
  } catch (error) {
    setRuntimeState(false, error.message);
    runtimeMetaEl.textContent = buildRuntimeMeta();
    setNodeTextIfChanged(lastUpdateEl, `${I18N.lastUpdate}: ${I18N.refreshFail}`);
  }
}

async function runAction(url) {
  setActionButtonsDisabled(true);
  try {
    await fetchJson(url, {method: 'POST'});
    await pollRuntime(true);
  } finally {
    setActionButtonsDisabled(false);
  }
}

function calcRefreshInterval(visibleMs) {
  return document.hidden ? HIDDEN_REFRESH_MS : visibleMs;
}

async function pollRuntime(force = false) {
  const now = Date.now();
  if (!force && now - lastRuntimeRefreshAt < calcRefreshInterval(BASE_REFRESH_MS)) {
    return;
  }
  if (isRefreshingRuntime) {
    return;
  }
  isRefreshingRuntime = true;
  try {
    await refresh();
  } finally {
    lastRuntimeRefreshAt = Date.now();
    isRefreshingRuntime = false;
  }
}

async function pollMenuJobs(force = false, forceDetailLogs = false) {
  if (!menuOpsEnabledCfg) {
    return;
  }
  const now = Date.now();
  if (!force && now - lastMenuRefreshAt < calcRefreshInterval(MENU_REFRESH_MS_VISIBLE)) {
    return;
  }
  if (isRefreshingMenuJobs) {
    return;
  }
  isRefreshingMenuJobs = true;
  try {
    const forceFull = force
        || forceDetailLogs
        || !!warpTrackedJobId
        || menuJobsCursorMillis <= 0
        || menuJobsDeltaRounds >= MENU_JOBS_DELTA_ROUNDS_MAX;
    await refreshMenuJobs({forceDetailLogs, forceFull});
  } finally {
    lastMenuRefreshAt = Date.now();
    isRefreshingMenuJobs = false;
  }
}

async function pollWarpStatus(force = false) {
  if (!WARP_FEATURE_ENABLED) {
    return;
  }
  const now = Date.now();
  if (!force && now - lastWarpRefreshAt < calcRefreshInterval(WARP_REFRESH_MS_VISIBLE)) {
    return;
  }
  if (isRefreshingWarp) {
    return;
  }
  isRefreshingWarp = true;
  try {
    await refreshWarpRuntimeStatus(force);
  } finally {
    lastWarpRefreshAt = Date.now();
    isRefreshingWarp = false;
  }
}

async function pollMenuAudit(force = false) {
  if (!menuOpsEnabledCfg || !menuOpsAuditDbEnabledCfg) {
    return;
  }
  const now = Date.now();
  if (!force && now - lastMenuAuditRefreshAt < calcRefreshInterval(DIAG_REFRESH_MS_VISIBLE)) {
    return;
  }
  await refreshMenuAudit(force);
}

function schedulerTick() {
  pollRuntime();
  pollMenuJobs();
  pollWarpStatus();
  refreshDiagnosticsFiles();
  pollMenuAudit();
}

function startScheduler() {
  if (schedulerTimer != null) {
    clearInterval(schedulerTimer);
  }
  schedulerTimer = setInterval(schedulerTick, 1000);
}

btnStart.addEventListener('click', () => runAction('/api/v1/runtime/start'));
btnStop.addEventListener('click', () => runAction('/api/v1/runtime/stop'));
btnRestart.addEventListener('click', () => runAction('/api/v1/runtime/restart'));
btnRunProxyTest.addEventListener('click', runProxyTest);
btnRunScenarioTest.addEventListener('click', runScenarioTest);
btnFillProxyConfig.addEventListener('click', () => {
  fillProxyConfig(true);
  testResultBoxEl.classList.remove('test-fail', 'test-success', 'empty-tip');
  testResultBoxEl.classList.add('test-success');
  testResultBoxEl.textContent = I18N.fillDone;
  scenarioResultBoxEl.classList.remove('test-fail', 'test-success', 'empty-tip');
  scenarioResultBoxEl.classList.add('empty-tip');
  scenarioResultBoxEl.textContent = I18N.scenarioInit;
});
testModeEl.addEventListener('change', () => {
  setProbeDefaultPortByMode();
  fillProxyConfig(false);
});
menuOpSelectEl.addEventListener('change', updateMenuOpMeta);
btnMenuOpRun.addEventListener('click', runMenuOperation);
btnMenuOpCancel.addEventListener('click', cancelMenuOperation);
btnMenuOpDownload.addEventListener('click', downloadMenuJobLogs);
btnMenuOpRefresh.addEventListener('click', () => pollMenuJobs(true, true));
btnDiagRefresh.addEventListener('click', () => refreshDiagnosticsFiles(true));
btnDiagCleanup.addEventListener('click', runDiagnosticsCleanup);
if (WARP_FEATURE_ENABLED) {
  btnWarpStatusRefresh.addEventListener('click', () => pollWarpStatus(true));
  btnWarpInstallClientWarp.addEventListener('click', () => runWarpQuickOperation('INSTALL_CLIENT_WARP'));
  btnWarpInstallClientProxy.addEventListener('click', () => runWarpQuickOperation('INSTALL_CLIENT_PROXY'));
  btnWarpInterfaceToggle.addEventListener('click', () => runWarpQuickOperation('TOGGLE_WARP'));
  btnWarpPlusStart.addEventListener('click', () => runWarpQuickOperation('START_WARP_PLUS'));
  btnWarpPlusStop.addEventListener('click', () => runWarpQuickOperation('STOP_WARP_PLUS'));
  btnWarpClientToggle.addEventListener('click', () => runWarpQuickOperation('TOGGLE_CLIENT'));
  btnWarpClientModeWarp.addEventListener('click', () => runWarpQuickOperation('SET_CLIENT_MODE', 'warp'));
  btnWarpClientModeProxy.addEventListener('click', () => runWarpQuickOperation('SET_CLIENT_MODE', 'proxy:40000'));
  btnWarpRepair.addEventListener('click', () => runWarpQuickOperation('REPAIR_WARP_NETWORK'));
  btnWarpTuneMtu.addEventListener('click', () => runWarpQuickOperation('TUNE_WARP_MTU'));
  btnWarpSetStackPriority.addEventListener('click', () => runWarpQuickOperation('SET_STACK_PRIORITY', String(warpStackArgEl.value || 'd')));
  btnWarpSetEndpoint.addEventListener('click', () => {
    const endpoint = String(warpEndpointArgEl.value || '').trim();
    if (!endpoint) {
      setWarpHint(I18N.warpQuickNeedEndpoint, true, true);
      return;
    }
    runWarpQuickOperation('SET_WARP_ENDPOINT', endpoint);
  });
  btnWarpShowAccount.addEventListener('click', () => runWarpQuickOperation('SHOW_WARP_ACCOUNT'));
  btnWarpVerifyPlus.addEventListener('click', () => runWarpQuickOperation('VERIFY_WARP_PLUS'));
  btnWarpPmtudOn.addEventListener('click', () => runWarpQuickOperation('SET_PMTUD_MODE', 'on'));
  btnWarpPmtudOff.addEventListener('click', () => runWarpQuickOperation('SET_PMTUD_MODE', 'off'));
  btnWarpFirewallPrecheck.addEventListener('click', () => runWarpQuickOperation('WARP_FIREWALL_PRECHECK'));
  btnWarpDiagnostics.addEventListener('click', () => runWarpQuickOperation('COLLECT_WARP_DIAGNOSTICS'));
  btnWarpSetLicense.addEventListener('click', () => {
    const license = String(warpLicenseArgEl.value || '').trim();
    if (!license) {
      setWarpHint(I18N.warpQuickNeedLicense, true, true);
      return;
    }
    runWarpQuickOperation('SET_WARP_LICENSE', license);
  });
  btnWarpReregister.addEventListener('click', () => runWarpQuickOperation('REREGISTER_WARP_ACCOUNT'));
  btnWarpApplyProfile.addEventListener('click', () => runWarpQuickOperation('APPLY_WARP_PROFILE', String(warpProfileArgEl.value || 'full')));
  btnWarpAutoRecover.addEventListener('click', () => runWarpQuickOperation('AUTO_RECOVER_WARP'));
  btnWarpAutoSwitch.addEventListener('click', () => runWarpQuickOperation('AUTO_SWITCH_POLICY', String(warpProfileArgEl.value || 'auto')));
  btnWarpTeamsSet.addEventListener('click', () => {
    const arg = String(warpTeamsArgEl.value || '').trim();
    if (!arg) {
      setWarpHint(I18N.warpQuickNeedArg, true, true);
      return;
    }
    runWarpQuickOperation('SET_WARP_TEAMS_ACCOUNT', arg);
  });
  btnWarpTeamsLeave.addEventListener('click', () => runWarpQuickOperation('LEAVE_WARP_TEAMS_ACCOUNT'));
  if (btnWarpAlertRuleSave) {
    btnWarpAlertRuleSave.addEventListener('click', saveWarpAlertRules);
  }
  if (btnWarpAlertRuleReset) {
    btnWarpAlertRuleReset.addEventListener('click', resetWarpAlertRules);
  }
}

applyI18n();
if (WARP_FEATURE_ENABLED) {
  initWarpOptionSelectors();
}
loadProbeHistory();
renderProbeHistory();
fillProxyConfig(false);
setProbeDefaultPortByMode();
fetchMenuCatalog()
    .then(() => pollMenuJobs(true, true))
    .catch(error => setMenuHint(`${I18N.menuJobLoadFail}: ${error.message || error}`, true));
pollRuntime(true);
if (WARP_FEATURE_ENABLED) {
  pollWarpStatus(true);
}
refreshDiagnosticsFiles(true);
pollMenuAudit(true);
startScheduler();
document.addEventListener('visibilitychange', () => {
  if (!document.hidden) {
    pollRuntime(true);
    pollMenuJobs(true, false);
    if (WARP_FEATURE_ENABLED) {
      pollWarpStatus(true);
    }
    refreshDiagnosticsFiles(true);
    pollMenuAudit(true);
  }
});

