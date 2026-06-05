# java-socket5-proxy 重构计划

> 目标：打造最优网络代理工具，支持 Docker 打包 + Web 界面化配置

---

## 🎯 目标

1. **修复所有高/中优先级问题**（SQLite 连接泄漏、内存泄漏、认证冗余）
2. **新增 Web 界面化配置**（通过界面完成所有配置，无需改配置文件）
3. **添加 Docker 支持**（Dockerfile + docker-compose.yml）
4. **补充测试覆盖率**（JaCoCo + 更多测试）
5. **增强安全**（TLS 支持、CONNECT 端口限制）
6. **性能优化**（连接池优化、缓冲区调优）

---

## 📦 任务清单

### 阶段一：核心修复（高优先级）

- [ ] **T1: SQLite 连接管理修复**
  - 使用 ThreadLocal<Connection> 或 SQLite MUTEX 模式
  - 正确实现 AutoCloseable，连接用完即关闭
  
- [ ] **T2: ProxyMetricsService 内存泄漏修复**
  - 连接断开时清理 sessions map
  - 添加定期清理机制
  
- [ ] **T3: 认证逻辑统一**
  - 消除 AuthService / UserStore 重复逻辑
  - 统一使用 UserStore 作为唯一认证入口
  
- [ ] **T4: 连接池槽位泄漏修复**
  - 确保连接异常时释放槽位（try-finally）

### 阶段二：Web 界面化配置

- [ ] **T5: REST API 配置读写接口**
  - GET /api/v1/config/proxy - 读取代理配置
  - PUT /api/v1/config/proxy - 更新代理配置
  - GET /api/v1/config/users - 读取用户列表
  - POST /api/v1/config/users - 创建/更新用户
  - DELETE /api/v1/config/users/{id} - 删除用户
  - POST /api/v1/config/reload - 热重载配置（无需重启）
  
- [ ] **T6: Vue 3 前端项目**
  - 使用 Vue 3 + Element Plus 构建管理界面
  - 支持：代理配置、用户管理、实时监控、日志查看
  
- [ ] **T7: 配置持久化**
  - 配置修改后自动保存到 application.yml
  - 支持配置版本管理（保留历史版本）
  - 支持配置导入/导出（JSON/YAML）

### 阶段三：Docker 支持

- [ ] **T8: Dockerfile**
  - 多阶段构建（构建阶段 + 运行阶段）
  - 使用 Eclipse Temurin JDK 17 精简镜像
  - 非 root 用户运行
  
- [ ] **T9: docker-compose.yml**
  - 支持代理 + 管理界面部署
  - 数据卷持久化（数据库、配置文件、日志）
  - 环境变量配置（无需改文件）
  
- [ ] **T10: 构建脚本**
  - build.sh: 本地构建
  - docker-build.sh: Docker 构建
  - deploy.sh: 一键部署

### 阶段四：安全增强

- [ ] **T11: TLS/SSL 支持**
  - 管理界面支持 HTTPS
  - 支持 HTTP 代理的 CONNECT TLS 隧道
  
- [ ] **T12: CONNECT 端口限制**
  - 可配置的白名单/黑名单
  - 默认允许: 80, 443, 8080, 8443
  - 默认禁止: 25, 445, 1433, 3306, 3389, 5432
  
- [ ] **T13: 审计日志**
  - 记录所有管理操作（用户创建/删除、配置修改）
  - 连接日志（用户、目标、带宽、时长）
  - 支持日志轮转和导出

### 阶段五：测试与优化

- [ ] **T14: JaCoCo 集成**
  - 设置最低覆盖率 60%
  - CI/CD 流程
  
- [ ] **T15: 补充测试**
  - UserStore 单元测试
  - AuthService 单元测试
  - ProxyMetricsService 单元测试
  - ManagementAccessFilter 单元测试
  - 连接池槽位泄漏测试
  
- [ ] **T16: 压力测试脚本**
  - 并发连接测试
  - 长时间运行稳定性测试

### 阶段六：文档与发布

- [ ] **T17: 完善文档**
  - 快速开始指南
  - Docker 部署指南
  - Web 管理界面使用指南
  - API 文档（Swagger/OpenAPI）
  
- [ ] **T18: 版本发布**
  - 设置版本号（如 1.1.0）
  - CHANGELOG.md
  - Release 标签

---

## 🏗️ 架构变更

### 新增模块

```
src/main/java/com/zqzqq/proxyhub/
├── config/
│   ├── ConfigReloadService.java      # 新：配置热重载
│   └── ConfigPersistenceService.java # 新：配置持久化
├── management/
│   ├── api/
│   │   ├── ConfigController.java     # 新：配置读写 API
│   │   └── AuditController.java      # 新：审计日志 API
│   └── service/
│       ├── AuditService.java         # 新：审计日志服务
│       └── ConfigService.java        # 新：配置管理服务
├── web/                               # 新：前端静态资源
│   └── assets/
│       └── dist/                     # Vue 3 构建产物
└── ...
```

### 新增前端

```
web-ui/
├── package.json
├── vite.config.js
├── index.html
├── src/
│   ├── App.vue
│   ├── main.js
│   ├── api/
│   │   ├── config.js
│   │   ├── user.js
│   │   ├── monitor.js
│   │   └── audit.js
│   ├── views/
│   │   ├── ProxyConfig.vue
│   │   ├── Users.vue
│   │   ├── Monitor.vue
│   │   └── AuditLogs.vue
│   └── router/
│       └── index.js
└── ...
```

### Docker 文件结构

```
docker/
├── Dockerfile
├── docker-compose.yml
├── entrypoint.sh
└── conf/
    └── application-docker.yml

scripts/
├── build.sh
├── docker-build.sh
└── deploy.sh
```

---

## ⚡ 性能目标

| 指标 | 当前 | 目标 |
|------|------|------|
| 单用户并发连接 | 32 槽位 | 支持动态扩展，最高 100 |
| 内存泄漏 | 有（sessions map） | 零泄漏 |
| 配置变更 | 需重启 | 热重载 < 1s |
| 管理界面 | 无 | Vue 3 SPA |

---

## 📅 时间估算

| 阶段 | 任务数 | 预计时间 |
|------|--------|----------|
| 阶段一：核心修复 | 4 | 30min |
| 阶段二：Web 界面 | 3 | 45min |
| 阶段三：Docker 支持 | 3 | 20min |
| 阶段四：安全增强 | 3 | 30min |
| 阶段五：测试优化 | 3 | 25min |
| 阶段六：文档发布 | 2 | 10min |
| **总计** | **18** | **~2.5h** |

---

## 🚀 关键设计决策

1. **前端技术栈**: Vue 3 + Element Plus + Vite（轻量、快速、成熟）
2. **配置存储**: 直接修改 application.yml（与现有一致）
3. **配置热重载**: 监听配置变更 → 更新 ProxyProperties → 重启对应代理服务器
4. **Docker 基础镜像**: eclipse-temurin:17-jre（精简，无 GUI）
5. **构建策略**: Maven 构建 JAR → 复制到 Docker 镜像
