# JNet - 零依赖高性能HTTP客户端

[![Maven Central](https://img.shields.io/maven-central/v/com.netcapture/jnt.svg)](https://maven.pkg.github.com/NetCapture/JNet)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![CI](https://github.com/NetCapture/JNet/actions/workflows/ci.yml/badge.svg)](https://github.com/NetCapture/JNet/actions)

> 🚀 基于 JDK 11+ HttpClient 的极简HTTP客户端，零第三方依赖，API 设计参考 Python requests。

## ✨ 核心特性

- ✅ **零依赖** - 仅使用 JDK 11+ 标准库
- ✅ **HTTP/2 原生支持** - 基于 `java.net.http.HttpClient`
- ✅ **线程安全** - 不可变对象设计
- ✅ **极简 API** - Python requests 风格
- ✅ **完整功能** - 拦截器、缓存、重试、SSL、SSE、异步

## 📦 安装

### Maven
```xml
<dependency>
    <groupId>com.netcapture</groupId>
    <artifactId>jnt</artifactId>
    <version>3.4.3</version>
</dependency>
```

### Gradle
```groovy
implementation 'com.netcapture:jnt:3.4.1'
```

**要求：Java 11+**

## 🚀 快速开始

### 基础请求
```java
// GET
String data = JNet.get("https://api.example.com/data");

// POST JSON
String result = JNet.post("https://api.example.com/users",
    JNet.json().put("name", "Alice").put("age", 25));

// 带参数和头部
String data = JNet.get("https://api.example.com/search",
    JNet.params("q", "java"),
    JNet.headers("Authorization", "Bearer token"));
```

### 客户端配置
```java
JNetClient client = JNetClient.newBuilder()
    .connectTimeout(5000)
    .readTimeout(10000)
    .proxy("127.0.0.1", 8080)
    .build();

Response response = client.newGet("https://api.example.com").build().newCall().execute();
```

### 拦截器
```java
// 日志拦截器
JNetClient client = JNetClient.newBuilder()
    .addInterceptor(new Interceptor.LoggingInterceptor())
    .addInterceptor(new Interceptor.RetryInterceptor(3, 1000))
    .build();

// 自定义拦截器
client = JNetClient.newBuilder()
    .addInterceptor(chain -> {
        Request original = chain.request();
        Request authenticated = original.newBuilder()
            .addHeader("Authorization", "Bearer " + getToken())
            .build();
        return chain.proceed(authenticated);
    })
    .build();
```

### SSE (Server-Sent Events)
```java
SSEClient sse = new SSEClient();
sse.connect("https://api.example.com/events", new SSEClient.SSEListener() {
    @Override
    public void onData(String data) {
        System.out.println("Event data: " + data);
    }

    @Override
    public void onError(Exception e) {
        System.err.println("Error: " + e.getMessage());
    }
});
```

### 异步请求
```java
// CompletableFuture
CompletableFuture<String> future = JNet.getAsync("https://api.example.com/data");
String data = future.get();

// Callback
Request request = client.newGet("https://api.example.com").build();
request.newCall().enqueue(new Call.Callback() {
    @Override
    public void onSuccess(Response response) {
        System.out.println("Success: " + response.getBody());
    }

    @Override
    public void onFailure(Exception e) {
        System.err.println("Error: " + e.getMessage());
    }
});
```

### SSL 配置
```java
// 开发环境（不推荐生产）
SSLConfig sslConfig = new SSLConfig().trustAllCertificates();

// 生产环境
SSLConfig sslConfig = new SSLConfig()
    .addTrustCertificate(caCertFile)
    .clientCertificate(clientPfxFile, "password");

JNetClient client = JNetClient.newBuilder()
    .sslConfig(sslConfig)
    .build();
```

## 🏗️ 架构概览

```
用户代码 → JNet (静态工具) → JNetClient (客户端管理)
                    ↓
              Request (请求对象) → Call (执行器)
                    ↓
              Interceptor Chain (拦截器链)
                    ↓
              JDK HttpClient (实际请求)
                    ↓
              Response (响应对象)
```

**核心组件：**
- `JNet` - 静态工具类，快速发起请求
- `JNetClient` - 客户端管理，支持自定义配置
- `Request`/`Response` - 不可变请求/响应对象
- `Call` - 请求执行器，支持同步/异步
- `Interceptor` - 拦截器链，类似 OkHttp
- `SSEClient` - Server-Sent Events 流式处理
- `ResponseCache` - 响应缓存
- `SSLConfig` - SSL/TLS 配置

## 🧪 测试

```bash
# 运行所有测试
./build.sh test

# 打包项目
./build.sh package
```

**测试覆盖：**
- ✅ 核心功能测试 (TestJNetUtils, TestPair, TestRequest, TestResponse, TestJNetClient, TestConcurrency)
- ✅ 拦截器测试 (TestInterceptorFull - 31 个测试用例)
- ✅ SSE 测试 (SSERealTimeAPITest)

## 📊 性能对比

| 特性 | JNet | OkHttp | Apache HttpClient |
|------|------|--------|-------------------|
| 依赖 | 0 | 3+ | 5+ |
| 代码量 | ~6K 行 | ~30K 行 | ~50K 行 |
| HTTP/2 | ✅ | ✅ | ⚠️ |
| 拦截器 | ✅ | ✅ | ⚠️ |
| SSE | ✅ | ❌ | ❌ |
| 线程安全 | ✅ | ✅ | ✅ |

## 📁 项目结构

```
src/main/java/com/jnet/core/
├── JNet.java              # 静态工具类
├── JNetClient.java        # 客户端管理
├── Request.java           # 请求对象
├── Response.java          # 响应对象
├── Call.java              # 请求执行器
├── Interceptor.java       # 拦截器接口与实现
├── SSEClient.java         # SSE 客户端
├── ResponseCache.java     # 响应缓存
├── SSLConfig.java         # SSL 配置
└── ...
```

## 🔧 开发

```bash
# 构建
./build.sh package

# 测试
./build.sh test

# 清理
mvn clean
```

## 📄 文档

- **API 文档**: [GitHub Pages](https://netcapture.github.io/JNet/)
- **更新日志**: [CHANGELOG.md](CHANGELOG.md)
- **贡献指南**: [CONTRIBUTING.md](CONTRIBUTING.md)
- **安全策略**: [SECURITY.md](SECURITY.md)

## 🤝 贡献

欢迎提交 Issue 和 Pull Request！

请阅读 [CONTRIBUTING.md](CONTRIBUTING.md) 了解开发流程。

## 📄 许可证

Apache 2.0 - 详见 [LICENSE](LICENSE)

## 📞 联系

- **作者**: sanbo
- **Email**: sanbo.xyz@gmail.com
- **GitHub**: https://github.com/NetCapture/JNet

---

**JNet** © 2020-2026 NetCapture Group
