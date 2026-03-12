# Proxy Hub

`Proxy Hub` 是一个基于 Spring Boot 3、Netty 与可选 Squid 引擎构建的代理网关项目，提供以下能力：

- SOCKS5 代理
- HTTP 代理
- HTTPS CONNECT 隧道
- 可视化控制台
- 运行态自检与指标统计
- ACL 访问控制
- 客户端认证
- 代理连通性探测

它适合用作自建代理出口、联调测试代理、统一管理 SOCKS5 与 HTTP 代理的中间层服务。

## 项目概览

项目采用“管理面 + 数据面”分离的结构：

- 管理面：Spring Boot Web、Thymeleaf 控制台、运行控制 API、自检与探测接口
- 数据面：Netty SOCKS5 监听、HTTP 代理监听，或通过外部 Squid 提供 HTTP 代理能力

默认采用双端口设计：

- `proxy.socks.port=1080`：SOCKS5 监听端口
- `proxy.http.port=8080`：HTTP/HTTPS 代理监听端口
- `server.port=9090`：管理控制台与管理 API 端口

## 核心功能

- 支持 SOCKS5 `CONNECT`
- 支持 HTTP 代理与 HTTPS `CONNECT` 隧道转发
- 支持 SOCKS5 用户名密码认证
- 支持 HTTP Basic 代理认证
- 支持客户端 CIDR 白名单
- 支持目标主机黑名单与目标端口黑名单
- 支持单客户端最大并发连接数限制
- 提供运行时启停、重启、重载接口
- 提供活动连接、失败原因、流量、协议分布等指标
- 提供代理探测与场景探测接口
- 支持 `java -jar` 直接运行
- 提供 CentOS7 / Ubuntu 打包与部署脚本

## 当前实现状态

为避免文档与代码脱节，下面按当前仓库中的实际实现状态说明：

| 模块 | 状态 | 说明 |
| --- | --- | --- |
| SOCKS5 代理 | 已实现 | 基于 Netty，支持 `CONNECT` 和可选用户名密码认证 |
| HTTP 代理 | 已实现 | 可使用内置 Netty 引擎或外部 Squid 引擎 |
| HTTPS CONNECT 隧道 | 已实现 | 透传隧道，不做 MITM 解密 |
| ACL 访问控制 | 已实现 | 支持客户端 CIDR 白名单、目标主机/端口拦截 |
| 管理控制台 | 已实现 | 支持运行态查看、指标展示、自检、代理探测 |
| 运行时控制 API | 已实现 | 支持 `start / stop / restart / reload / status / self-check` |
| 代理探测接口 | 已实现 | 支持普通代理探测与场景探测 |
| Squid 外部引擎 | 已实现 | 可托管生成 `squid.conf`，并解析 access log 汇总指标 |
| MenuOps 诊断文件接口 | 已实现 | 支持诊断目录汇总、文件列表、下载、删除 |
| MenuOps 审计查询 | 已实现 | 支持读取 SQLite 审计记录与汇总 |
| MenuOps 作业目录/提交/取消 | 预留中 | 当前服务实现为简化占位版，目录为空，作业提交返回不支持 |
| WARP 相关快捷操作 | 预留中 | 前端脚本中保留了相关结构，但当前版本未真正启用 |

如果你只是想要一个可运行的 SOCKS5 / HTTP 代理网关，当前项目已经具备可用基础能力；如果你希望直接使用 README 中历史版本提到的完整 WARP / MenuOps 作业编排能力，请先以当前代码为准进行二次实现或补全。

## 目录结构

```text
.
├─bin/                         启停脚本、打包脚本、systemd 服务文件
├─conf/                        生产配置与 squid 配置
├─src/main/java/               核心代码
│  ├─config/                   配置对象
│  ├─core/                     运行时管理、ACL、指标、网络桥接
│  ├─socks/                    SOCKS5 代理实现
│  ├─http/                     HTTP 代理与 Squid 外部引擎实现
│  └─management/               控制台、API、探测、自检、安全控制
├─src/main/resources/
│  ├─application.yml           默认配置
│  ├─templates/dashboard.html  控制台页面
│  └─static/                   前端样式与脚本
└─pom.xml                      Maven 构建配置
```

## 技术栈

- Java 17
- Spring Boot 3.3.6
- Netty 4.1.115.Final
- Thymeleaf
- SQLite JDBC
- 可选外部组件：Squid

## 构建要求

- JDK 17 或更高版本
- Maven 3.8 或更高版本

构建命令：

```bash
mvn clean package -DskipTests
```

运行测试：

```bash
mvn test
```

构建完成后，可执行 Jar 通常位于：

```text
target/proxy-hub-1.0.0.jar
```

## 快速开始

### 1. 本地开发推荐启动方式

项目默认配置文件 `src/main/resources/application.yml` 中，HTTP 引擎为：

```yaml
proxy:
  http:
    engine: squid
```

这意味着如果你的机器没有安装 `squid`，HTTP 监听器可能无法正常启动。对于本地开发，尤其是 Windows 环境，推荐显式切换到内置 Netty HTTP 引擎：

```bash
java -jar target/proxy-hub-1.0.0.jar --proxy.http.engine=legacy
```

如果你当前只需要 SOCKS5，也可以直接关闭 HTTP 监听：

```bash
java -jar target/proxy-hub-1.0.0.jar --proxy.http.enabled=false
```

### 2. 使用外部生产配置启动

```bash
java -jar target/proxy-hub-1.0.0.jar \
  --spring.config.location=file:./conf/application-prod.yml
```

如果 `conf/application-prod.yml` 里仍使用 `squid` 引擎，请确保目标机器已安装 Squid，或者同时覆盖为：

```bash
java -jar target/proxy-hub-1.0.0.jar \
  --spring.config.location=file:./conf/application-prod.yml \
  --proxy.http.engine=legacy
```

### 3. 打开控制台

应用启动后可访问：

- 控制台：`http://127.0.0.1:9090/dashboard`
- 健康检查：`http://127.0.0.1:9090/actuator/health`

说明：

- 应用启动后会自动尝试启动所有已启用的代理监听器
- 即使某个监听器启动失败，Spring Boot 管理面仍可能继续存活，可通过控制台和日志排查原因

## HTTP 引擎说明

`proxy.http.engine` 支持两种模式：

### `legacy`

内置 Netty HTTP 代理实现，特点：

- 无需安装外部依赖
- 适合本地开发、测试环境、Windows 环境
- 启动简单，易于排障

### `squid`

通过外部 Squid 进程提供 HTTP 代理能力，特点：

- 适合已有 Squid 运维习惯的 Linux 环境
- 支持由项目根据 ACL 自动生成 `squid.conf`
- 控制台会解析 Squid access log 汇总指标

使用 `squid` 模式时请确保：

- `proxy.http.squid.executable` 可执行
- `proxy.http.squid.config-path` 可写
- `proxy.http.squid.workdir` 可写
- `proxy.http.squid.access-log-path` 所在目录可写

注意：

- 当前 `proxy.http.auth.*` 不会自动映射到 Squid 认证助手，因此若使用 Squid 模式，不应假定 HTTP 代理认证已经完整接入外部引擎

## 关键配置说明

### 监听与认证

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `proxy.socks.enabled` | `true` | 是否启用 SOCKS5 监听 |
| `proxy.socks.bind-host` | `0.0.0.0` | SOCKS5 绑定地址 |
| `proxy.socks.port` | `1080` | SOCKS5 端口 |
| `proxy.socks.auth.enabled` | `false` | 是否启用 SOCKS5 用户名密码认证 |
| `proxy.http.enabled` | `true` | 是否启用 HTTP 监听 |
| `proxy.http.bind-host` | `0.0.0.0` | HTTP 绑定地址 |
| `proxy.http.port` | `8080` | HTTP 端口 |
| `proxy.http.engine` | `squid` | HTTP 引擎，默认配置文件中为 `squid` |
| `proxy.http.auth.enabled` | `false` | 是否启用内置 HTTP Basic 代理认证 |

### ACL 与连接限制

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `proxy.acl.enabled` | `false` | 是否启用 ACL |
| `proxy.acl.allow-client-cidrs` | `[]` | 客户端 CIDR 白名单，空表示不限制 |
| `proxy.acl.deny-target-hosts` | `localhost, *.internal.local` | 目标主机黑名单 |
| `proxy.acl.deny-target-ports` | `25, 3306, 6379` | 目标端口黑名单 |
| `proxy.performance.max-connections-per-client` | `0` | 单客户端最大活动连接数，`0` 表示不限 |

### 管理面访问保护

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `proxy.management.enabled` | `false` | 是否启用管理面保护 |
| `proxy.management.protect-actuator` | `false` | 是否将 `/actuator/**` 也纳入保护 |
| `proxy.management.allow-cidrs` | `[]` | 允许访问管理面的客户端 CIDR |
| `proxy.management.allow-basic-auth` | `true` | 是否允许 Basic Auth |
| `proxy.management.allow-token-auth` | `false` | 是否允许 Token 认证 |
| `proxy.management.access-token` | `""` | 管理 Token，对应请求头 `X-ProxyHub-Token` |
| `proxy.management.basic.username` | `admin` | 管理 Basic Auth 用户名 |
| `proxy.management.basic.password` | `admin` | 管理 Basic Auth 密码 |

### 控制台与运维附加配置

| 配置项 | 默认值 | 说明 |
| --- | --- | --- |
| `server.port` | `9090` | 管理控制台与 API 端口 |
| `proxy.dashboard.refresh-seconds` | `2` | 控制台刷新周期 |
| `menu-ops.enabled` | `true` | 是否暴露 MenuOps 区域配置与接口 |
| `menu-ops.diagnostics-dir` | `./logs/diagnostics` | 诊断文件目录 |
| `menu-ops.audit-db-path` | `./logs/menu-ops-audit.db` | MenuOps 审计 SQLite 文件 |

## 一个更接近生产的配置示例

下面是一份更适合实际使用的示例，重点是开启认证、ACL 和管理面保护：

```yaml
server:
  port: 9090

proxy:
  socks:
    enabled: true
    bind-host: 0.0.0.0
    port: 1080
    auth:
      enabled: true
      username: proxy_user
      password: strong_password

  http:
    enabled: true
    bind-host: 0.0.0.0
    port: 8080
    engine: legacy
    auth:
      enabled: true
      username: proxy_user
      password: strong_password

  acl:
    enabled: true
    allow-client-cidrs:
      - 127.0.0.1/32
      - 10.0.0.0/8
      - 192.168.0.0/16
    deny-target-hosts:
      - localhost
      - "*.internal.local"
    deny-target-ports:
      - 22
      - 3306
      - 6379

  performance:
    max-connections-per-client: 200

  management:
    enabled: true
    protect-actuator: true
    allow-cidrs:
      - 127.0.0.1/32
      - 192.168.0.0/16
    allow-basic-auth: true
    basic:
      enabled: true
      username: admin_ops
      password: change_me_now
```

## 控制台与 API

### 控制台页面

- `GET /dashboard`

控制台可查看：

- 代理整体运行状态
- 当前监听器状态
- 活动连接数、累计连接数、拦截数、认证失败数、连接失败数
- 入站/出站流量
- 失败原因统计
- 活动会话
- 最近事件
- 运行态自检信息
- 代理探测与场景探测结果
- MenuOps 审计与诊断文件信息

### 运行时控制 API

- `GET /api/v1/runtime/status`
- `GET /api/v1/runtime/self-check`
- `POST /api/v1/runtime/start`
- `POST /api/v1/runtime/stop`
- `POST /api/v1/runtime/restart`
- `POST /api/v1/runtime/reload`

### 指标 API

- `GET /api/v1/metrics/overview`

### 代理探测 API

- `POST /api/v1/tools/proxy-test`
- `POST /api/v1/tools/scenario-test`

场景探测当前内置目标包括：

- `CHATGPT`
- `NETFLIX`
- `GOOGLE_SCHOLAR`
- `TELEGRAM`
- `IPV4_EGRESS`

### 当前可用的 MenuOps 相关接口

- `GET /api/v1/menu-ops/diagnostics`
- `GET /api/v1/menu-ops/diagnostics/summary`
- `GET /api/v1/menu-ops/diagnostics/{fileName}`
- `DELETE /api/v1/menu-ops/diagnostics/{fileName}`
- `GET /api/v1/menu-ops/audit`
- `GET /api/v1/menu-ops/audit/summary`

### 已暴露但暂未完整实现的接口

以下接口在控制器中已存在，但当前 `MenuOpsService` 为简化实现，调用结果可能为空或直接返回不支持：

- `GET /api/v1/menu-ops/catalog`
- `GET /api/v1/menu-ops/jobs`
- `GET /api/v1/menu-ops/jobs/delta`
- `GET /api/v1/menu-ops/jobs/{jobId}`
- `GET /api/v1/menu-ops/jobs/{jobId}/logs.txt`
- `POST /api/v1/menu-ops/jobs`
- `POST /api/v1/menu-ops/jobs/{jobId}/cancel`

如果你打算继续开发这部分，建议从 `src/main/java/com/zqzqq/proxyhub/management/service/MenuOpsService.java` 开始补齐实际执行逻辑。

## 使用示例

### 1. 验证 SOCKS5 代理

无认证：

```bash
curl --proxy socks5h://127.0.0.1:1080 https://httpbin.org/ip
```

带认证：

```bash
curl --proxy socks5h://user:pass@127.0.0.1:1080 https://httpbin.org/ip
```

### 2. 验证 HTTP 代理

无认证：

```bash
curl -x http://127.0.0.1:8080 http://httpbin.org/get
```

带认证：

```bash
curl -x http://user:pass@127.0.0.1:8080 http://httpbin.org/get
```

### 3. 验证 HTTPS CONNECT 隧道

```bash
curl -x http://127.0.0.1:8080 https://httpbin.org/ip
```

### 4. 调用代理探测接口

```json
{
  "mode": "SOCKS5",
  "targetUrl": "https://httpbin.org/ip",
  "proxyHost": "127.0.0.1",
  "proxyPort": 1080,
  "username": null,
  "password": null,
  "timeoutMillis": 8000
}
```

### 5. 调用场景探测接口

```json
{
  "mode": "HTTP",
  "proxyHost": "127.0.0.1",
  "proxyPort": 8080,
  "username": null,
  "password": null,
  "timeoutMillis": 8000,
  "scenarios": [
    "CHATGPT",
    "NETFLIX",
    "GOOGLE_SCHOLAR"
  ]
}
```

## Linux 部署与发布

### 1. 直接使用脚本启动

```bash
chmod +x bin/*.sh
bin/start.sh
bin/status.sh
bin/stop.sh
```

`bin/start.sh` 的默认行为：

- 从 `target/` 中寻找可执行 Jar
- 使用 `conf/application-prod.yml` 作为外部配置
- 生成 PID 文件到 `run/proxy-hub.pid`
- 控制台日志输出到 `logs/console.out`
- 默认 JVM 参数为 `-Xms512m -Xmx1024m -XX:+UseG1GC -XX:+HeapDumpOnOutOfMemoryError`

### 2. CentOS7 发布包

Linux 下打包：

```bash
chmod +x bin/build-release.sh
bin/build-release.sh
```

跳过构建阶段：

```bash
bin/build-release.sh --skip-build
```

输出文件：

```text
dist/proxy-hub-<version>-centos7.tar.gz
```

### 3. Ubuntu 发布包

Linux 下打包：

```bash
chmod +x bin/build-release-ubuntu.sh
bin/build-release-ubuntu.sh
```

跳过构建阶段：

```bash
bin/build-release-ubuntu.sh --skip-build
```

输出文件：

```text
dist/proxy-hub-<version>-ubuntu.tar.gz
```

### 4. Ubuntu 安装脚本

```bash
tar -xzf proxy-hub-<version>-ubuntu.tar.gz -C /opt
cd /opt/proxy-hub-<version>-ubuntu
chmod +x bin/*.sh
sudo bash bin/install-ubuntu.sh
```

安装脚本会：

- 创建 `proxyhub` 系统用户和用户组
- 安装 `proxy-hub.service`
- 创建 `/opt/proxy-hub` 软链接
- 启用 systemd 服务

## 生产环境建议

- 不要使用默认账号密码
- 对 SOCKS5 和 HTTP 监听都开启认证
- 开启 ACL，限制允许访问代理的客户端网段
- 将管理端口仅暴露给可信网络
- 如启用管理 Basic Auth，请使用强密码
- 如果使用 `squid` 模式，请确认可执行文件、日志目录、配置目录权限正确
- 配置 JVM 内存和日志留存策略
- 使用 `/api/v1/runtime/self-check` 和 `/api/v1/metrics/overview` 做日常巡检

## 已知限制

- SOCKS5 当前只支持 `CONNECT`
- HTTPS 仅支持 `CONNECT` 隧道透传，不支持 TLS 中间人解密
- ACL 的客户端 CIDR 匹配当前以 IPv4 规则为主
- 默认配置下 HTTP 引擎为 `squid`，本地环境若未安装 Squid 需手动切换到 `legacy`
- MenuOps 作业中心和 WARP 自动化能力在当前版本中尚未完整落地

## 开发建议

如果你准备继续扩展这个项目，建议优先关注以下几个方向：

1. 完成 `MenuOpsService` 的真实作业执行链路
2. 为 Squid 模式补齐 HTTP 认证对接
3. 明确 WARP 相关功能的服务端能力边界，并与前端脚本保持一致
4. 为 README 中仍然属于“预留”的模块补齐自动化测试

## 许可证

本项目采用 [LICENSE](./LICENSE) 中声明的许可证。
