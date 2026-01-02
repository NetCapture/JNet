# 更新日志

所有 JNet 项目的重大变更都会记录在此文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，并遵循 [Semantic Versioning](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 待定

### Changed
- 待定

### Fixed
- 待定

---

## [3.4.5] - 2026-01-02

### Added
- ✅ **JNetProxySelector** - 自定义代理选择器，支持 HTTP 和 SOCKS 代理
- ✅ **SSLConfig 安全工厂方法** - 5 个安全的 SSL 配置方法
  - `createTrustCertificate()` - 信任指定证书
  - `createCustomTrust()` - 自定义 TrustManager
  - `createDefault()` - 系统默认证书（推荐生产环境）
  - `createClientAuth()` - 客户端证书认证
  - `createFullConfig()` - 完整配置
- ✅ **详细的 SSL 安全警告文档** - 明确标注安全风险和推荐做法
- ✅ **代理配置增强** - 支持类型验证和地址类型检查

### Changed
- 🔄 **JNetClient 代理配置** - 增强类型安全验证
  - 支持 HTTP/SOCKS/DIRECT 代理类型
  - 验证地址必须是 InetSocketAddress
  - 使用自定义 JNetProxySelector
  - 提供明确的错误信息
- 🔄 **SSLConfig 安全性** - 标记不安全方法为 @Deprecated
  - `NOT_VERIFY` - 仅开发/测试使用
  - `getSSLFactory()` - 仅开发/测试使用
- 🔄 **资源关闭处理** - 改进 finally 块异常处理，避免掩盖原始异常
- 🔄 **代码质量** - 优化格式和注释（AsyncExecutor）

### Fixed
- 🐛 **代理配置健壮性** - 修复类型转换和空值处理问题
- 🐛 **资源泄漏风险** - 改进流关闭的异常处理

### Security
- ⚠️ **重要安全改进** - SSLConfig 添加详细安全警告
- ⚠️ **生产环境推荐** - 使用 `createDefault()` 替代不安全方法
- ⚠️ **中间人攻击防护** - 明确标注跳过证书验证的风险

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
