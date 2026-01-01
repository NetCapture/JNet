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
