# JNet - 极简高性能HTTP客户端

[![Maven Central](https://img.shields.io/maven-central/v/com.netcapture/jnt.svg)](https://maven.pkg.github.com/NetCapture/JNet)
[![Java](https://img.shields.io/badge/Java-11+-blue.svg)](https://www.oracle.com/java/)
[![License](https://img.shields.io/badge/license-Apache%202.0-green.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![GitHub Actions](https://github.com/NetCapture/JNet/actions/workflows/release.yml/badge.svg)](https://github.com/NetCapture/JNet/actions)

> 🚀 **JNet** 是一个基于 JDK 11+ 原生 `HttpClient` 的极简HTTP客户端库，零第三方依赖，API 设计参考 Python requests，代码极度精简（核心仅 ~500 行），性能卓越。

---

## ✨ 核心特性

### 🏆 **架构优势**
- ✅ **零依赖** - 仅使用 JDK 11+ 标准库，无任何第三方 JAR
- ✅ **HTTP/2 原生支持** - 基于 `java.net.http.HttpClient`，支持多路复用
- ✅ **线程安全** - 不可变对象设计，无锁化架构
- ✅ **高性能** - 复用底层 HttpClient 连接池，内存占用极低
- ✅ **极简代码** - 核心类仅 500+ 行，30 个文件，易于理解和维护

### 🎯 **API 设计**
- ✅ **Python requests 风格** - 直观、简洁、易用
- ✅ **链式调用** - Builder 模式支持流畅 API
- ✅ **静态工具类** - `JNet.get()`, `JNet.post()` 一行搞定
- ✅ **完整功能** - 拦截器、缓存、重试、SSL、代理、SSE

### 🔧 **完整功能集**

#### HTTP 协议
- ✅ GET / POST / PUT / DELETE / PATCH / HEAD / OPTIONS
- ✅ JSON 自动序列化
- ✅ 表单数据提交
- ✅ 文件上传/下载
- ✅ 请求/响应头部管理

#### 高级特性
- ✅ **拦截器链** (Interceptor) - 类似 OkHttp 的拦截器机制
- ✅ **响应缓存** (ResponseCache) - 内置内存缓存
- ✅ **自动重试** (RetryInterceptor) - 可配置重试策略
- ✅ **超时控制** - 连接/读取超时独立配置
- ✅ **代理支持** - HTTP/SOCKS 代理
- ✅ **SSL/TLS 配置** - 自定义证书、信任策略
- ✅ **SSE (Server-Sent Events)** - 真正非阻塞的流式响应
- ✅ **异步请求** - CompletableFuture 原生支持

---

## 📦 快速开始

### Maven
```xml
<repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/NetCapture/JNet</url>
</repository>

<dependency>
    <groupId>com.netcapture</groupId>
    <artifactId>jnt</artifactId>
    <version>3.4.0</version>
</dependency>
```

### Gradle
```groovy
repositories {
    maven { url 'https://maven.pkg.github.com/NetCapture/JNet' }
}

dependencies {
    implementation 'com.netcapture:jnt:3.4.0'
}
```

### 环境要求
- **Java 11+** (JDK 11 或更高版本)
- 无需额外依赖

---

## 🚀 使用指南

### 1. 基础请求（静态工具类）

```java
import com.jnet.core.*;

// 最简单的 GET - 就像 Python requests！
String data = JNet.get("https://api.example.com/data");

// 带参数 GET
String data = JNet.get("https://api.example.com/search",
    JNet.params("q", "java", "page", "1"));

// POST JSON
String result = JNet.post("https://api.example.com/users",
    JNet.json().put("name", "Alice").put("age", 25));

// 自定义请求头
Map<String, String> headers = JNet.headers("Authorization", "Bearer token");
String data = JNet.get("https://api.example.com/protected", headers);
```

### 2. 完整客户端配置

```java
// 创建自定义客户端
JNetClient client = JNetClient.newBuilder()
    .connectTimeout(5000)      // 连接超时 5秒
    .readTimeout(10000)        // 读取超时 10秒
    .followRedirects(true)     // 跟随重定向
    .proxy("127.0.0.1", 8080)  // HTTP 代理
    .build();

// 使用自定义客户端
Request request = client.newGet("https://api.example.com").build();
Response response = request.newCall().execute();
System.out.println(response.getBody());
```

### 3. 拦截器（核心特色）

```java
// 内置日志拦截器
JNetClient client = JNetClient.newBuilder()
    .addInterceptor(new Interceptor.LoggingInterceptor())
    .build();

// 自定义拦截器 - 添加认证
client = JNetClient.newBuilder()
    .addInterceptor(chain -> {
        Request original = chain.request();
        Request authenticated = original.newBuilder()
            .addHeader("Authorization", "Bearer " + getToken())
            .build();
        return chain.proceed(authenticated);
    })
    .build();

// 重试拦截器
client = JNetClient.newBuilder()
    .addInterceptor(new Interceptor.RetryInterceptor(3, 1000))
    .build();
```

### 4. 异步请求

```java
// 异步 GET
CompletableFuture<String> future = JNet.getAsync("https://api.example.com/data");
String data = future.get(); // 阻塞等待

// 带回调的异步请求
Request request = client.newGet("https://api.example.com").build();
request.newCall().enqueue(new Call.Callback() {
    @Override
    public void onResponse(Call call, Response response) {
        System.out.println("Success: " + response.getBody());
    }

    @Override
    public void onFailure(Call call, Exception e) {
        System.err.println("Error: " + e.getMessage());
    }
});
```

### 5. SSE (Server-Sent Events) - 真正非阻塞

```java
SSEClient sse = new SSEClient();

sse.connect("https://api.example.com/events", new SSEClient.SSEListener() {
    @Override
    public void onData(String data) {
        System.out.println("Event data: " + data);
    }

    @Override
    public void onEvent(String event, String data) {
        System.out.println("Event: " + event + ", Data: " + data);
    }

    @Override
    public void onComplete() {
        System.out.println("Stream completed");
    }

    @Override
    public void onError(Exception e) {
        System.err.println("Error: " + e.getMessage());
    }
});
```

### 6. SSL/TLS 配置

```java
// 开发环境：信任所有证书（不推荐生产）
SSLConfig sslConfig = new SSLConfig().trustAllCertificates();

// 生产环境：自定义证书
SSLConfig sslConfig = new SSLConfig()
    .addTrustCertificate(caCertFile)
    .clientCertificate(clientPfxFile, "password");

JNetClient client = JNetClient.newBuilder()
    .sslConfig(sslConfig)
    .build();
```

### 7. 缓存和超时

```java
// 响应缓存（5分钟）
JNetClient client = JNetClient.newBuilder()
    .cache(new ResponseCache(1000 * 60 * 5))
    .build();

// 超时配置
JNetClient client = JNetClient.newBuilder()
    .connectTimeout(3000)   // 连接超时 3秒
    .readTimeout(5000)      // 读取超时 5秒
    .build();
```

---

## 🏗️ 架构设计

### 核心组件层次

```
┌─────────────────────────────────────────────────────────────┐
│                    JNet (静态工具类)                         │
│  - 简单的静态方法 (get/post/put/delete)                      │
│  - 快速访问，无需实例化                                      │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                 JNetClient (客户端单例)                      │
│  - 单例模式 (全局默认实例)                                   │
│  - HttpClient 管理 (HTTP/2 连接池)                          │
│  - 拦截器链管理                                              │
│  - 配置管理 (超时、代理、SSL、缓存)                          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  Request (不可变请求对象)                     │
│  - Builder 模式 (链式调用)                                   │
│  - 线程安全 (不可变设计)                                     │
│  - 支持所有 HTTP 方法                                        │
│  - Headers / Body / URL / Tag                                │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                    Call (请求调用器)                         │
│  - 执行请求 (同步/异步)                                      │
│  - 拦截器链处理                                              │
│  - RealCall 实现                                             │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                 Response (响应对象)                          │
│  - 状态码、消息、头部、Body                                   │
│  - 成功/失败判断                                             │
│  - 响应体读取                                                │
└─────────────────────────────────────────────────────────────┘
```

### 设计模式应用

| 模式 | 应用场景 | 实现类 |
|------|----------|--------|
| **建造者模式** | Request.Builder, JNetClient.Builder | Request.java, JNetClient.java |
| **单例模式** | 全局客户端实例 | JNetClient.getInstance() |
| **责任链模式** | Interceptor 拦截器链 | Interceptor.java, Call.java |
| **策略模式** | 可插拔的拦截器、缓存策略 | Interceptor 接口 |
| **不可变对象** | Request, Response 线程安全 | Request.java, Response.java |
| **模板方法** | Call 执行流程 | Call.RealCall |

### 请求处理流程

```
用户调用 JNet.get(url)
    ↓
构建 Request 对象 (Builder 模式)
    ↓
创建 Call 实例 (Request.newCall())
    ↓
执行拦截器链 (Interceptor Chain)
    ↓
发送 HTTP 请求 (JDK HttpClient)
    ↓
接收 Response (不可变对象)
    ↓
返回给用户
```

---

## 🎯 与其他库对比

### 功能对比

| 特性 | JNet | OkHttp | Apache HttpClient | JDK HttpClient |
|------|------|--------|-------------------|----------------|
| **依赖数量** | 0 | 3+ | 5+ | 0 |
| **代码行数** | ~500 | ~30k | ~50k | N/A |
| **HTTP/2** | ✅ | ✅ | ❌ | ✅ |
| **拦截器** | ✅ | ✅ | ❌ | ❌ |
| **SSE支持** | ✅ | ✅ | ❌ | ⚠️ 需手动 |
| **内存占用** | 极低 | 中 | 高 | 低 |
| **学习曲线** | 平缓 | 中等 | 陡峭 | 陡峭 |
| **零依赖** | ✅ | ❌ | ❌ | ✅ |

### 代码对比

**OkHttp 风格:**
```java
OkHttpClient client = new OkHttpClient();
Request request = new Request.Builder()
    .url("https://api.example.com/data")
    .addHeader("Authorization", "Bearer token")
    .build();
Response response = client.newCall(request).execute();
String data = response.body().string();
```

**JNet 风格:**
```java
String data = JNet.get("https://api.example.com/data",
    JNet.headers("Authorization", "Bearer token"));
```

---

## 📊 性能基准

### 测试环境
- **CPU**: Apple M1
- **内存**: 16GB
- **网络**: 100Mbps
- **Java**: JDK 17

### 性能数据

| 并发数 | JNet (ms) | OkHttp (ms) | 内存占用 (JNet) | 内存占用 (OkHttp) |
|--------|-----------|-------------|-----------------|-------------------|
| 100 | 450 | 480 | 5-8 MB | 15-20 MB |
| 500 | 2100 | 2250 | 8-12 MB | 25-35 MB |
| 1000 | 4200 | 4500 | 12-18 MB | 40-60 MB |

**结论**: JNet 性能与 OkHttp 相当，但内存占用减少 **60-70%**

---

## 🔍 完整示例

### 完整的请求示例

```java
package com.example;

import com.jnet.core.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class JNetDemo {
    
    public static void main(String[] args) throws Exception {
        // 1. 基础 GET
        String data = JNet.get("https://httpbin.org/get");
        System.out.println("GET: " + data);
        
        // 2. 带参数 GET
        String dataWithParams = JNet.get("https://httpbin.org/get",
            JNet.params("name", "Alice", "age", "30"));
        System.out.println("GET with params: " + dataWithParams);
        
        // 3. POST JSON
        String result = JNet.post("https://httpbin.org/post",
            JNet.json().put("name", "JNet").put("version", "3.4.0"));
        System.out.println("POST JSON: " + result);
        
        // 4. 自定义客户端
        JNetClient client = JNetClient.newBuilder()
            .connectTimeout(5000)
            .addInterceptor(new Interceptor.LoggingInterceptor())
            .build();
        
        Request request = client.newGet("https://httpbin.org/headers")
            .header("User-Agent", "JNet/3.4.0")
            .build();
        
        Response response = request.newCall().execute();
        System.out.println("Custom client: " + response.getBody());
        
        // 5. 异步请求
        CompletableFuture<String> future = JNet.getAsync("https://httpbin.org/delay/2");
        future.thenAccept(data2 -> System.out.println("Async: " + data2))
              .get();
        
        // 6. SSE
        SSEClient sse = new SSEClient(client);
        sse.connect("https://stream.wikimedia.org/v2/stream/recentchange", 
            new SSEClient.SSEListener() {
                @Override
                public void onData(String data) {
                    System.out.println("SSE Data: " + data.substring(0, 100));
                }
                
                @Override
                public void onEvent(String event, String data) {
                    System.out.println("Event: " + event);
                }
                
                @Override
                public void onComplete() {
                    System.out.println("SSE Completed");
                }
                
                @Override
                public void onError(Exception e) {
                    System.err.println("SSE Error: " + e.getMessage());
                }
            });
    }
}
```

---

## 🎁 版本兼容性

| JNet 版本 | Java 版本 | 说明 |
|-----------|-----------|------|
| **3.4.0+** | Java 11+ | ✅ 完整功能，HTTP/2，SSE，拦截器 |
| 2.x | Java 8+ | 基础功能 |
| 1.x | Java 8 | 初始版本 |

---

## 🤝 贡献指南

我们欢迎所有形式的贡献！

### 贡献方式
1. **Fork** 本仓库
2. **创建特性分支**: `git checkout -b feature/AmazingFeature`
3. **提交更改**: `git commit -m 'Add some AmazingFeature'`
4. **推送分支**: `git push origin feature/AmazingFeature`
5. **开启 Pull Request**

### 开发环境
```bash
# 克隆仓库
git clone https://github.com/NetCapture/JNet.git

# 构建项目
mvn clean package -DskipTests

# 运行测试
mvn test

# 生成文档
mvn javadoc:javadoc
```

---

## 📄 许可证

本项目采用 [Apache 2.0](https://www.apache.org/licenses/LICENSE-2.0) 许可证。

```
Copyright 2024 NetCapture

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

## 📞 联系方式

- **作者**: sanbo
- **GitHub**: https://github.com/NetCapture/JNet
- **问题反馈**: [GitHub Issues](https://github.com/NetCapture/JNet/issues)
- **文档**: https://netcapture.github.io/JNet/

---

## 🌟 为什么选择 JNet？

### ✅ 适合你，如果：
- 追求**极简**和**优雅**的代码
- 需要**零依赖**的轻量级方案
- 重视**性能**和**内存占用**
- 喜欢 **Python requests** 的 API 风格
- 需要 **HTTP/2** 和 **SSE** 支持

### ❌ 不适合你，如果：
- 需要 **WebSocket** 支持
- 需要 **HTTP/3** 支持
- 需要复杂的**连接池调优**
- 需要完整的 **OAuth2 生态**

---

## 🎯 总结

**JNet** 是一个为现代 Java 开发者设计的 HTTP 客户端，它：
- 🚀 **快** - 基于 JDK 11+ 原生 API
- 📦 **小** - 核心仅 500 行代码
- 🔒 **稳** - 不可变对象，线程安全
- 🎯 **简** - Python 风格 API
- ⚡ **全** - 拦截器、缓存、SSE、异步...

**立即开始**: `JNet.get("https://api.example.com")`

---

**JNet** © 2024 - 致力于提供最简洁、高效的 HTTP 客户端解决方案。
