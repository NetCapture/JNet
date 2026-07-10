# 发现与决策

## 需求
- 用户希望分析整个项目，而不是只看单个模块。
- 分析应覆盖技术栈、目录结构、核心链路、成熟度和风险。

## 研究发现
- 主仓库是 Maven Java 11+ 项目，产物为 `com.netcapture:jnt`。
- 生产代码强调零第三方依赖，测试依赖引入 JUnit 5 和 Hamcrest。
- 仓库包含一个独立的 `docs/` 前端站点，使用 TypeScript 构建文档展示与数据交互。
- 当前 `pom.xml` 默认跳过测试，测试需要显式通过 `./build.sh test` 或 Maven 参数启用。
- 核心请求骨架比较清晰：`JNet` 负责静态便捷入口，`JNetClient` 封装 `HttpClient` 配置，`Request` 构建不可变请求，`Call.RealCall` 负责执行和拦截器链。
- `JNet` 对外暴露的静态 API 面非常大，除了 HTTP，还直接挂载了 TCP、UDP、HLS、RTSP、服务端能力，说明它既是 HTTP facade，也是多协议入口聚合器。
- `JNetClient` 本身较轻，当前 Builder 主要支持超时、代理、Cookie、跳转和默认认证，不包含 README 提到的大量高级能力。
- 拦截器机制是项目的关键扩展点，但实现仍偏轻量，内建只包含日志、重试、请求头和简单缓存。
- `SSEClientEnhanced` 是相对独立的增强实现，包含重连、心跳和事件过滤，未完全融入 `Call`/`Request` 主链路。
- 扩展协议模块体量不小，`tcp`、`rtsp`、`websocket`、`socketio` 各自拥有独立 Builder/Client/Response 体系，整体更接近“多协议网络工具箱”而非单一 HTTP client。
- `SocketIOClient` 通过 `JNet.get()` 做 Engine.IO polling 握手，再复用 `WebSocketClient` 升级到 WebSocket，说明模块之间存在轻度复用，但不是统一抽象层。
- `CloudflareInterceptor` 的实现属于启发式重试与文本特征判断，距离真正的浏览器挑战绕过仍有明显差距。
- `DigestAuth` 是相对扎实的独立实现，认证能力比某些宣传中的高级网络能力更接近可复用库组件。
- `docs/` 实际代码已经是 TypeScript 模块化应用，和 `docs/README.md` 中描述的“单页静态文件结构”存在明显不一致，文档维护有滞后。
- 主库在本地环境可通过 `mvn -q -DskipTests -Djavadoc.skip=true compile`。
- 代表性测试 `TestJNetClient`、`TestRequest`、`TestInterceptorFull`、`TestWebSocketClient`、`TestSocketIOClient` 已通过。
- 35 个包含 `@Test` 的文件中，有 8 个文件名不以 `Test` 开头，因此不会被 `pom.xml` 当前的 `**/Test*.java` 默认执行。
- `build.sh test` 不是全量测试，只硬编码执行 3 组测试命令。
- `docs/scripts/build.js` 依赖 `npx tsc --noEmit`，但当前仓库没有 `docs/node_modules`，docs 构建前提未满足。
- 当前工作树已经部分落地阶段 0/3 的改动：`pom.xml` 增加了 `Test*/*Test/*Tests` 发现规则与 JaCoCo `prepare-agent/report`，`build.sh` 增加了 `verify` 入口，CI 增加了 `mvn -B clean verify -DskipTests=false` 和 JaCoCo artifact 上传。
- 现在 36 个带 `@Test` 的测试文件都能被新的 Surefire 命名规则匹配，说明“测试发现规则”本身已基本完成。
- 实际执行 `mvn -B clean verify -DskipTests=false` 时，JaCoCo agent 会正常注入并生成 `target/jacoco.exec`，但由于测试阶段失败，`target/site/jacoco` 报告目录未生成，覆盖率目前还不能稳定产出。
- 当前全量 `verify` 基线并不稳定，已观测到的失败集中在 `TestCallFull`、`TestResponseCacheFull`、`SSERealTimeAPITest`、`TestJNetFull`，其中既有真实语义问题，也有历史测试假设和现实现状不一致的问题。
- CI 当前并不是“仅报告”模式；`.github/workflows/ci.yml` 中的测试和 `verify` 步骤没有 `continue-on-error`，失败会直接让 job 变红。阶段 4 更适合先做测试分层，再逐步收紧，而不是直接再加门槛。
- `SSEClient` 现在补上了 `activeStream/activeSubscription` 句柄并在 `close()` 中取消，但 `SSEClientEnhanced` 仍缺少可重入的生命周期状态机：重复 `connect()` 没有防抖，`disconnect()` 会永久关闭 `heartbeatExecutor`，同实例后续重连不可复用。
- `WebSocketClient` 已经引入 `AtomicInteger reconnectAttempts`、`AtomicBoolean reconnectScheduled` 和 `pingTask` 释放，但 `close()/abort()` 会永久关闭 `pingExecutor`，`connect()` 也不会重置 `shouldReconnect`，因此同实例复连和幂等 close/connect 仍未完成。
- `WebSocketClient.connectInternal()` 只在 listener 的 `onClose/onError` 路径调度重连；如果 `buildAsync()` 在握手阶段直接异常完成，当前代码不会补一个 `exceptionally/whenComplete` 分支接住它，初始连接失败可能直接丢失自动重连。
- `SocketIOClient` 目前只做到了监听器容器改为 `CopyOnWriteArrayList`、关键字段 `volatile` 化和非法握手报错；真正的 connect/reconnect/close 幂等、sid 失效回退握手、任务/资源释放仍未落地。
- 阶段 3 相关改动已部分完成：`ResponseCache` 支持 TTL 和 `varyHeaders` 维度，`Response` 支持多值头保真，`Call` 也会把 JDK 多值响应头写入 `Response.headerValues(...)`。
- 但缓存与响应语义仍未完全收口：`CacheInterceptor.isExpired()` 只识别 `no-cache/no-store`，没有解析 `max-age`/`Expires`；`Response.getHeader()` 和 `Request.getHeader()` 仍是大小写敏感查找，和 HTTP 头语义不一致，也已反映在 `TestCallFull` 的失败上。
- `ResponseCache.get()` 用 `>= expireTime` 判过期，而 `cleanup()` 用 `> expireTime`，边界行为不一致；当前 `TestResponseCacheFull` 的清理失败也说明 TTL 语义变更后旧测试口径需要一起调整。

## 技术决策
| 决策 | 理由 |
|------|------|
| 先分析 `com.jnet.core` 再扩展到其他协议模块 | `core` 明显是主调用链和公共抽象所在 |
| 抽查测试而不是一次性跑完整套 | 当前任务是项目分析，先建立结构认知更高效 |
| 把 `docs/` 当作配套展示系统单独评估 | 它不是主库运行时依赖，但影响项目对外呈现和维护面 |
| 对 README 中宣称的高级能力保持源码核验 | 当前核心类与宣传范围之间存在落差，需要继续验证分布位置 |
| 将测试入口配置作为重点风险单列 | 测试入口直接影响项目质量信号可信度 |

## 遇到的问题
| 问题 | 解决方案 |
|------|---------|
| 未发现现成的规划与学习记录文件 | 本次分析过程中创建并维护 |
| docs 依赖未安装，无法直接运行 TypeScript 类型检查 | 通过读取 `package.json` 与 `build.js` 评估构建前提 |
| 统一 `verify` 运行时间很长且夹带实时网络/压力测试 | 记录为 CI 分层治理问题，而不是继续把所有测试塞进常驻 gate |

## 资源
- `README.md`
- `pom.xml`
- `CLAUDE.md`
- `src/main/java/com/jnet/core/JNet.java`
- `src/main/java/com/jnet/core/JNetClient.java`
- `src/main/java/com/jnet/core/Call.java`
- `src/main/java/com/jnet/core/Request.java`
- `src/main/java/com/jnet/core/Response.java`
- `src/main/java/com/jnet/core/Interceptor.java`
- `src/main/java/com/jnet/core/SSEClientEnhanced.java`
- `src/main/java/com/jnet/websocket/WebSocketClient.java`
- `src/main/java/com/jnet/socketio/SocketIOClient.java`
- `src/main/java/com/jnet/tcp/TcpClient.java`
- `src/main/java/com/jnet/rtsp/RtspClient.java`
- `src/main/java/com/jnet/cloudflare/CloudflareInterceptor.java`
- `src/main/java/com/jnet/auth/DigestAuth.java`
- `docs/package.json`
- `docs/src/main.ts`
- `docs/src/managers/ApplicationManager.ts`
- `docs/README.md`
- `docs/scripts/build.js`
- `src/test/java/com/jnet/core/TestJNetClient.java`
- `src/test/java/com/jnet/core/TestRequest.java`
- `src/test/java/com/jnet/core/TestInterceptorFull.java`
- `src/test/java/com/jnet/core/IntegrationTests.java`
- `src/test/java/com/jnet/core/SSERealTimeAPITest.java`
- `src/test/java/com/jnet/socketio/TestSocketIOClient.java`
- `src/test/java/com/jnet/websocket/TestWebSocketClient.java`

## 视觉/浏览器发现
- 当前仅进行了本地文件与结构查看，无浏览器内容。

---
*每执行2次查看/浏览器/搜索操作后更新此文件*
*防止视觉信息丢失*
