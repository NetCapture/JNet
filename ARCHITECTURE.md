# JNet 3.0 架构设计文档

## 概述

JNet 3.0 是一个轻量级、高性能的 HTTP 客户端库，参考 OKHttp 设计理念，专为 Java 和 Android 环境优化。提供简洁的 API、不可变对象和建造者模式，确保代码的简洁性和可维护性。

## 核心特性

- ✅ **不可变对象**: Request、Response 不可变，线程安全
- ✅ **建造者模式**: 优雅的对象构建方式
- ✅ **单例客户端**: 统一配置管理
- ✅ **轻量级**: 移除重型依赖，代码精简
- ✅ **兼容性强**: 支持 Java 8+ 和 Android
- ✅ **零 JSON 依赖**: 替代 org.json，提供轻量级工具

## 架构设计

### 1. 核心组件

#### 1.1 JNetClient (客户端)
- **职责**: 全局 HTTP 客户端配置和请求调度
- **设计模式**: 单例模式
- **核心功能**:
  - 统一超时配置
  - 代理设置
  - 重定向控制
  - 请求方法快捷创建

**设计亮点**:
```java
// 单例获取
JNetClient client = JNetClient.getInstance();

// 自定义配置
JNetClient custom = JNetClient.create()
    .connectTimeout(5, TimeUnit.SECONDS)
    .readTimeout(10, TimeUnit.SECONDS)
    .build();
```

#### 1.2 Request (请求对象)
- **职责**: 不可变 HTTP 请求表示
- **设计模式**: 建造者模式
- **核心特性**:
  - 不可变状态
  - 链式构建
  - 自动 URL 验证
  - 便捷方法 (json(), form())

**设计亮点**:
```java
Request request = client.newPost("https://api.example.com")
    .json()
    .body(jsonData)
    .header("Authorization", "Bearer token")
    .build();
```

#### 1.3 Response (响应对象)
- **职责**: 不可变 HTTP 响应表示
- **设计模式**: 工厂模式 + 建造者模式
- **核心特性**:
  - 不可变状态
  - 状态码判断
  - 错误类型检查
  - 性能指标

**设计亮点**:
```java
Response response = call.execute();
if (response.isOk()) {
    // 处理成功响应
} else if (response.isClientError()) {
    // 处理客户端错误
} else if (response.isServerError()) {
    // 处理服务器错误
}
```

#### 1.4 Call (执行接口)
- **职责**: 请求执行和调度
- **设计模式**: 策略模式
- **核心功能**:
  - 同步执行 (execute)
  - 异步执行 (enqueue)
  - 请求取消
  - 状态查询

**设计亮点**:
```java
// 同步执行
Response response = call.execute();

// 异步执行
call.enqueue(new Call.Callback() {
    @Override
    public void onSuccess(Response response) {
        // 处理成功
    }

    @Override
    public void onFailure(Exception e) {
        // 处理失败
    }
});
```

#### 1.5 JNetUtils (工具类)
- **职责**: 提供常用工具方法
- **设计模式**: 工具类模式
- **核心功能**:
  - 字符串操作
  - Base64 编解码
  - MD5 哈希
  - 轻量级 JSON 构建器
  - URL 编解码
  - 数字转换
  - 性能计时

## 设计原则

### 1. 单一职责原则 (SRP)

每个类都有明确的职责:
- `JNetClient`: 配置管理
- `Request`: 请求表示
- `Response`: 响应表示
- `Call`: 执行调度
- `JNetUtils`: 工具方法

### 2. 开放封闭原则 (OCP)

核心类通过接口抽象，支持扩展:
- `Call` 接口支持不同实现
- `JNetClient` 可扩展配置
- `Request.Builder` 可扩展构建方法

### 3. 里氏替换原则 (LSP)

实现类可以替换接口使用:
- `RealCall` 可替换 `Call` 接口
- 所有不可变对象保证行为一致

### 4. 接口隔离原则 (ISP)

接口功能单一:
- `Call` 只关注执行
- `Callback` 只处理结果

### 5. 依赖倒置原则 (DIP)

依赖抽象而非具体:
- 依赖 `Call` 接口而非 `RealCall`
- 依赖 `JNetClient` 抽象配置

## 性能优化

### 1. 内存优化
- 不可变对象减少内存占用
- 内部使用 `HashMap` 而非 `ConcurrentHashMap`
- 及时释放连接资源

### 2. 线程安全
- 不可变对象天然线程安全
- 最小化同步范围
- 异步执行使用独立线程

### 3. 快速失败
- 构建时验证 URL 格式
- 重复执行检测
- 早期状态检查

## 兼容性

### Java 版本
- 最低要求: Java 8
- 推荐: Java 11+

### Android 兼容
- API Level 24+ (Android 7.0+)
- 使用标准库，无额外依赖

### 依赖库
- **零第三方依赖**
- 仅使用 JDK 标准库
- 移除 `org.json` 包

## 使用示例

### 基本用法

```java
// 1. 获取客户端
JNetClient client = JNetClient.getInstance();

// 2. 创建请求
Request request = client.newGet("https://api.example.com/data")
    .header("Accept", "application/json")
    .build();

// 3. 执行请求
Call call = request.newCall();
Response response = call.execute();

// 4. 处理响应
if (response.isOk()) {
    String data = response.getBody();
    System.out.println(data);
}
```

### 高级用法

```java
// 自定义客户端配置
JNetClient client = JNetClient.create()
    .connectTimeout(10, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build();

// POST JSON 请求
String json = JNetUtils.json()
    .add("name", "JNet")
    .add("version", 3.0)
    .add("features", new String[]{"fast", "lightweight"})
    .build();

Request request = client.newPost("https://api.example.com")
    .json()
    .body(json)
    .build();

// 异步执行
request.newCall().enqueue(new Call.Callback() {
    @Override
    public void onSuccess(Response response) {
        // 处理响应
    }

    @Override
    public void onFailure(Exception e) {
        // 处理错误
    }
});
```

## 迁移指南

### 从 JNet 2.x 迁移

1. **API 变化**
   ```java
   // 旧版本
   String result = Jnt.get("https://api.example.com");

   // 新版本
   Response response = JNetClient.getInstance()
       .newGet("https://api.example.com")
       .build()
       .newCall()
       .execute();

   String result = response.getBody();
   ```

2. **JSON 处理**
   ```java
   // 旧版本 (需要 org.json)
   JSONObject json = new JSONObject(response);

   // 新版本 (使用 JNetUtils)
   String json = JNetUtils.json()
       .add("key", "value")
       .build();
   ```

## 最佳实践

### 1. 使用单例客户端
```java
// 推荐
JNetClient client = JNetClient.getInstance();

// 不推荐：每次都创建新实例
// JNetClient client = new JNetClient.Builder().build();
```

### 2. 复用 Request 对象
```java
// 构建一次，多次使用
Request request = client.newGet("https://api.example.com")
    .build();

Call call = request.newCall();
Response response = call.execute();
```

### 3. 错误处理
```java
try {
    Response response = call.execute();
    if (!response.isSuccessful()) {
        // 处理业务错误
    }
} catch (IOException e) {
    // 处理网络错误
}
```

### 4. 异步优先
```java
// 在 UI 线程中，优先使用异步
call.enqueue(new Call.Callback() {
    // 在子线程中执行
    public void onSuccess(Response response) {
        // 更新 UI
    }
});
```

## 未来规划

- [ ] 支持 HTTPS 证书验证
- [ ] 添加拦截器机制
- [ ] 支持缓存策略
- [ ] HTTP/2 支持
- [ ] 响应流式读取
- [ ] 连接池优化
- [ ] WebSocket 支持
- [ ] 响应拦截器

## 总结

JNet 3.0 架构设计遵循 SOLID 原则，提供简洁、高效、线程安全的 HTTP 客户端解决方案。通过不可变对象、建造者模式和单例模式，显著提升代码质量和开发体验。轻量级设计使其成为 Java 和 Android 项目的理想选择。
