# 更新日志

所有 JNet 项目的重大变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [3.4.5] - 2026-02-01

### Added
- ✅ **WebSocket Client** - 基于 JDK 11 原生 WebSocket 的完整实现
  - 支持自动重连（指数退避策略）
  - 内置 Ping/Pong 心跳检测
  - 支持文本和二进制消息
- ✅ **Socket.IO Client** - 完整的 Engine.IO v4 协议支持
  - 支持命名空间（Namespaces）和房间（Rooms）
  - 支持 HTTP Polling 到 WebSocket 的自动升级
  - 事件驱动架构（emit/on）
- ✅ **Cloudflare Bypass** - 反爬虫绕过工具包
  - `UserAgentRotator`: 浏览器 UA 自动轮换
  - `BrowserFingerprint`: 模拟 Chrome/Firefox 指纹头
  - `CloudflareInterceptor`: 自动处理 503 Challenge
  - `RequestTimingInterceptor`: 模拟人类操作延迟
- ✅ **Enhanced SSE** - 增强版 Server-Sent Events
  - 支持断线自动重连和 Last-Event-ID 恢复
  - 支持事件过滤（Event Filtering）
  - 内置心跳超时检测
- ✅ **Authentication Suite** - 标准认证支持
  - `BasicAuth`: HTTP Basic 认证
  - `BearerAuth`: Bearer Token 认证
  - `DigestAuth`: RFC 7616 Digest 认证
- ✅ **Multipart Upload** - 流式文件上传
  - 支持大文件零内存占用上传
  - 支持混合表单字段和文件
- ✅ **Proxy & Security**
  - `JNetProxySelector`: 支持 HTTP/SOCKS 代理链
  - `SSLConfig`: 增强的安全配置工厂方法（证书锁定、自定义信任源）

### Changed
- 🔄 **JNetClient** - 增强代理配置的类型安全验证
- 🔄 **SSLConfig** - 标记不安全方法为 `@Deprecated`，推荐生产环境使用 `createDefault()`
- 🔄 **Resource Management** - 改进流关闭和异常处理机制

### Fixed
- 🐛 **Proxy Configuration** - 修复代理配置中的类型转换和空值处理问题
- 🐛 **Resource Leaks** - 改进异常情况下的资源释放

### Security
- ⚠️ **SSL Hardening** - 添加详细的 SSL 安全配置警告和最佳实践文档
- ⚠️ **MITM Protection** - 明确标注跳过证书验证的风险

---

## [3.4.4] - 2026-01-02

### Changed
- 版本更新

---

## [3.4.0] - 2026-01-01

### Added
- ✅ 完整的拦截器系统（Logging, Retry, Header, Cache）
- ✅ SSE (Server-Sent Events) 流式处理支持
- ✅ 响应缓存机制（ResponseCache）
- ✅ SSL/TLS 自定义配置（SSLConfig）
- ✅ 异步请求支持（CompletableFuture & Callback）
- ✅ 完整的单元测试套件（57+ 测试用例）

### Changed
- 🔄 基于 JDK 11+ HttpClient 重构核心
- 🔄 优化连接池管理，提升性能
- 🔄 简化 API 设计，参考 Python requests

### Fixed
- 🐛 URL 编码问题处理
- 🐛 拦截器链执行逻辑
- 🐛 异步请求取消机制

---

## [3.0.0] - 2024-12-XX

### Added
- 初始版本发布
- 基础 HTTP 客户端功能
- 零依赖设计

---

## 版本说明

- **主版本号 (X.y.z)**: 重大功能变更或不兼容的 API 修改
- **次版本号 (x.Y.z)**: 向后兼容的功能新增
- **修订号 (x.y.Z)**: 向后兼容的问题修复

## 升级指南

### 从 3.0.x 升级到 3.4.0

无需修改代码，3.4.0 完全向后兼容。

新增功能：
- 拦截器系统（可选使用）
- SSE 支持（可选使用）
- 缓存机制（可选使用）

---

## 贡献者

- sanbo (sanbo.xyz@gmail.com)

---

**注意**: 本项目遵循 Apache 2.0 许可证。
