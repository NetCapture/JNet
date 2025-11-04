# JNet 3.0 核心架构实现

## 项目概述

JNet 3.0 是一个轻量级的 HTTP 客户端库，完全参考 OKHttp 的设计理念，使用不可变对象和建造者模式，确保线程安全和代码简洁性。

## 核心设计原则

1. **不可变对象** - 所有核心对象不可变，线程安全
2. **建造者模式** - 优雅的对象构建方式
3. **单例模式** - 客户端统一管理
4. **零依赖** - 仅使用 JDK 标准库
5. **轻量级** - 代码精简，移除 org.json

## 核心类实现

### 1. JNetClient - 客户端核心
**文件**: `src/main/java/ff/jnezha/jnt/core/JNetClient.java`

**特性**:
- 单例模式，全局唯一实例
- 统一配置管理
- 支持超时设置、代理、重定向控制
- 快捷请求方法 (GET/POST/PUT/DELETE)

**关键代码**:
```java
public final class JNetClient {
    private static volatile JNetClient instance;

    public static JNetClient getInstance() { ... }
    public static Builder newBuilder() { ... }

    public Request.Builder newGet(String url) { ... }
    public Request.Builder newPost(String url) { ... }
    public Request.Builder newPut(String url) { ... }
    public Request.Builder newDelete(String url) { ... }
}
```

### 2. Request - 不可变请求
**文件**: `src/main/java/ff/jnezha/jnt/core/Request.java`

**特性**:
- 不可变对象，线程安全
- 建造者模式构建
- 支持任意 HTTP 方法
- 自动 URL 验证
- 便捷方法 (json(), form())

**关键代码**:
```java
public final class Request {
    private final JNetClient client;
    private final String method;
    private final URL url;
    private final Map<String, String> headers;
    private final String body;
    private final String tag;

    public static Builder newBuilder() { ... }
    public Call newCall() { ... }
}
```

### 3. Response - 不可变响应
**文件**: `src/main/java/ff/jnezha/jnt/core/Response.java`

**特性**:
- 不可变对象，线程安全
- 状态码判断
- 错误类型检查
- 性能指标 (耗时)

**关键代码**:
```java
public final class Response {
    private final int code;
    private final String message;
    private final String body;
    private final Map<String, String> headers;
    private final long duration;
    private final Request request;
    private final boolean successful;

    public static Builder success(Request request) { ... }
    public static Builder failure(Request request) { ... }

    public boolean isOk() { ... }
    public boolean isClientError() { ... }
    public boolean isServerError() { ... }
}
```

### 4. Call - 执行接口
**文件**: `src/main/java/ff/jnezha/jnt/core/Call.java`

**特性**:
- 统一执行接口
- 支持同步/异步执行
- 请求取消功能
- 状态查询

**关键代码**:
```java
public interface Call {
    Request request();
    Response execute() throws IOException;
    void enqueue(Callback callback);
    void cancel();
    boolean isExecuted();
    boolean isCanceled();

    class RealCall implements Call {
        // 实际执行逻辑
    }

    interface Callback {
        void onSuccess(Response response);
        void onFailure(Exception e);
    }
}
```

### 5. JNetUtils - 工具类
**文件**: `src/main/java/ff/jnezha/jnt/core/JNetUtils.java`

**特性**:
- 字符串工具
- Base64 编解码
- MD5 哈希
- 轻量级 JSON 构建器
- URL 编解码
- 数字转换
- 性能计时

**关键代码**:
```java
public final class JNetUtils {
    // 字符串工具
    public static boolean isEmpty(CharSequence str) { ... }
    public static boolean isBlank(CharSequence str) { ... }

    // Base64 编解码
    public static String encodeBase64(String str) { ... }
    public static String decodeBase64(String str) { ... }

    // MD5 哈希
    public static String md5(String str) { ... }

    // JSON 构建器
    public static class JsonBuilder { ... }

    // 性能计时
    public static class StopWatch { ... }
}
```

## 快速开始

### 基本用法

```java
// 1. 获取客户端
JNetClient client = JNetClient.getInstance();

// 2. 创建请求
Request request = client.newGet("https://api.example.com/data")
    .header("Accept", "application/json")
    .build();

// 3. 执行请求
Response response = request.newCall().execute();

// 4. 处理响应
if (response.isOk()) {
    System.out.println(response.getBody());
}
```

### POST JSON 请求

```java
String json = JNetUtils.json()
    .add("name", "JNet")
    .add("version", 3.0)
    .build();

Response response = client.newPost("https://api.example.com")
    .json()
    .body(json)
    .build()
    .newCall()
    .execute();
```

### 自定义客户端配置

```java
JNetClient client = JNetClient.newBuilder()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();
```

### 异步请求

```java
request.newCall().enqueue(new Call.Callback() {
    @Override
    public void onSuccess(Response response) {
        // 处理成功响应
    }

    @Override
    public void onFailure(Exception e) {
        // 处理错误
    }
});
```

## 架构优势

### 1. 线程安全
- 所有核心对象不可变
- 天然支持多线程环境
- 无需额外同步机制

### 2. 性能优化
- 减少对象创建
- 快速失败机制
- 及时资源释放

### 3. 可维护性
- 职责单一
- 接口清晰
- 易于扩展

### 4. 兼容性
- Java 8+ 支持
- Android 兼容
- 零第三方依赖

## 文件结构

```
src/main/java/ff/jnezha/jnt/core/
├── JNetClient.java      # 客户端核心 (单例)
├── Request.java         # 不可变请求对象
├── Response.java        # 不可变响应对象
├── Call.java            # 执行接口
├── JNetUtils.java       # 工具类
└── Examples.java        # 使用示例

/Users/sanbo/code/JNet/
├── ARCHITECTURE.md      # 详细架构文档
└── JNET_CORE_README.md  # 核心实现说明
```

## 设计亮点

### 1. 参考 OKHttp 模式
- 类似的 API 设计
- 相同的设计模式
- 熟悉的使用体验

### 2. 代码精简
- 总行数 < 1000 行
- 无冗余代码
- 核心功能完整

### 3. 替代 org.json
```java
// 旧方式 (需要 org.json)
JSONObject obj = new JSONObject();
obj.put("key", "value");

// 新方式 (使用 JNetUtils)
String json = JNetUtils.json()
    .add("key", "value")
    .build();
```

## 编译验证

```bash
mvn clean compile
```

✅ 编译通过，无错误

## 下一步计划

- [ ] 添加 HTTPS 证书验证
- [ ] 实现拦截器机制
- [ ] 支持缓存策略
- [ ] HTTP/2 支持
- [ ] 流式响应读取
- [ ] 连接池优化

## 总结

JNet 3.0 核心架构实现提供了一个轻量、高效、线程安全的 HTTP 客户端解决方案。通过不可变对象、建造者模式和单例模式，显著提升代码质量和开发体验。精简的代码和零依赖特性使其成为 Java 和 Android 项目的理想选择。

---

**设计团队**: JNet Team
**版本**: 3.0
**文档日期**: 2025-11-04
