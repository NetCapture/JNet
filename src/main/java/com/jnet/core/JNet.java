package com.jnet.core;

import com.jnet.auth.BasicAuth;
import com.jnet.auth.BearerAuth;
import com.jnet.tcp.TcpClient;
import com.jnet.tcp.TcpRequest;
import com.jnet.tcp.TcpSession;
import com.jnet.udp.UdpClient;
import com.jnet.udp.UdpPacket;
import com.jnet.hls.HlsClient;
import com.jnet.rtsp.RtspClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Dependency-free facade for the most common JNet HTTP, TCP, UDP, HLS and RTSP operations.
 * Advanced configuration remains available through the protocol-specific builders.
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class JNet {
    private static volatile JNetClient defaultClient = JNetClient.getInstance();

    private JNet() {
        // 防止实例化
    }

    // ========== HTTP Methods (已有) ==========

    /**
     * 设置默认超时时间
     * @deprecated 请使用 JNetClient.newBuilder().connectTimeout(...) 配置
     */
    @Deprecated
    public static void setDefaultTimeout(Duration timeout) {
        int timeoutMillis = timeoutMillis(timeout);
        defaultClient = JNetClient.newBuilder()
                .connectTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS)
                .build();
    }

    /**
     * 获取默认HTTP客户端实例
     * 用于需要共享客户端的场景（如SSEClient）
     */
    public static HttpClient getDefaultHttpClient() {
        return defaultClient.getHttpClient();
    }

    // ========== 工具方法 ==========

    /**
     * 构建查询参数
     * 优化：预分配HashMap容量，避免rehash
     */
    public static Map<String, String> params(String... keyValues) {
        if (keyValues == null || keyValues.length == 0)
            return new HashMap<>(4);
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be even");
        }
        // 计算精确容量：元素数 / 0.75 + 1
        int elementCount = keyValues.length / 2;
        int capacity = (int) (elementCount / 0.75f) + 1;
        Map<String, String> map = new HashMap<>(capacity);
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1] == null ? "" : keyValues[i + 1]);
        }
        return map;
    }

    /**
     * 构建Headers
     */
    public static Map<String, String> headers(String... keyValues) {
        return params(keyValues);
    }

    /**
     * 构建JSON
     */
    public static Map<String, Object> json() {
        return new LinkedHashMap<>();
    }

    /**
     * Basic Auth
     */
    public static String basicAuth(String username, String password) {
        return BasicAuth.createHeaderValue(username, password);
    }

    /**
     * Bearer Token
     */
    public static String bearerToken(String token) {
        return BearerAuth.createHeaderValue(token);
    }

    // ========== HTTP GET 请求 ==========

    /**
     * GET请求 - 最简单方式
     */
    public static String get(String url) {
        return request("GET", url, null, null, null);
    }

    /**
     * GET请求 - 带参数
     */
    public static String get(String url, Map<String, String> params) {
        return request("GET", url, null, null, params);
    }

    /**
     * GET请求 - 带Headers
     */
    public static String get(String url, Map<String, String> headers, Map<String, String> params) {
        return request("GET", url, null, headers, params);
    }

    /**
     * GET请求 - 带认证
     */
    public static String get(String url, String auth) {
        Map<String, String> headers = auth != null ? headers("Authorization", auth) : null;
        return get(url, headers, (Map<String, String>) null);
    }

    /**
     * GET请求 - 整合参数
     */
    public static String get(String url, String... params) {
        return get(url, params(params));
    }

    /**
     * GET请求 - 整合参数
     */
    public static String get(String url, Map<String, String> headers, String... params) {
        return get(url, headers, params(params));
    }

    // ========== HTTP POST 请求 ==========

    /**
     * POST请求 - 最简单方式
     */
    public static String post(String url, String body) {
        return request("POST", url, body, null, null);
    }

    /**
     * POST请求 - 带Headers
     */
    public static String post(String url, String body, Map<String, String> headers) {
        return request("POST", url, body, headers, null);
    }

    /**
     * POST JSON请求
     */
    public static String postJson(String url, Object json) {
        return postJson(url, json, null);
    }

    /**
     * POST JSON请求 - 带Headers
     */
    public static String postJson(String url, Object json, Map<String, String> headers) {
        String jsonStr = JNetUtils.toJsonString(json);
        Map<String, String> mergedHeaders = mergeHeaders(headers, "Content-Type", "application/json");
        return request("POST", url, jsonStr, mergedHeaders, null);
    }

    /**
     * POST JSON请求 - 整合参数
     */
    public static String postJson(String url, Map<String, String> headers, Map<String, Object> params) {
        return postJson(url, (Object) params, headers);
    }

    /**
     * POST JSON请求 - 兼容整合参数
     */
    public static String post(String url, Map<String, String> headers, Object json) {
        return postJson(url, json, headers);
    }

    /**
     * POST请求 - 兼容多参数
     */
    public static String post(String url, Map<String, String> headers, String body, Object json) {
        return json != null ? postJson(url, json, headers) : post(url, body, headers);
    }

    // ========== 其他 HTTP 方法 ==========

    /**
     * PUT请求
     */
    public static String put(String url, String body) {
        return request("PUT", url, body, null, null);
    }

    /**
     * DELETE请求
     */
    public static String delete(String url) {
        return request("DELETE", url, null, null, null);
    }

    /**
     * PATCH请求
     */
    public static String patch(String url, String body) {
        return request("PATCH", url, body, null, null);
    }

    /**
     * HEAD请求
     */
    public static String head(String url) {
        return request("HEAD", url, null, null, null);
    }

    /**
     * 通用请求方法
     */
    public static String request(String method, String url) {
        return request(method, url, null, null, null);
    }

    /**
     * 通用请求方法
     */
    public static String request(String method, String url, String body) {
        return request(method, url, body, null, null);
    }

    /**
     * 通用请求方法 - 带Headers
     */
    public static String request(String method, String url, String body, Map<String, String> headers) {
        return request(method, url, body, headers, null);
    }

    /**
     * 通用请求方法 - 整合参数
     */
    public static String request(String method, String url, String body, Map<String, String> headers, Map<String, String> params) {
        return requestResponse(method, url, body, headers, params).getBody();
    }

    /**
     * 通用请求方法，保留状态码和响应头。
     */
    public static Response requestResponse(String method, String url, String body) {
        return requestResponse(method, url, body, null, null);
    }

    /**
     * 通用请求方法，保留状态码和响应头。
     */
    public static Response requestResponse(String method, String url, String body,
            Map<String, String> headers) {
        return requestResponse(method, url, body, headers, null);
    }

    /**
     * 通用请求方法，保留状态码、响应头和请求元数据。
     */
    public static Response requestResponse(String method, String url, String body,
            Map<String, String> headers, Map<String, String> params) {
        String finalUrl = JNetUtils.buildUrl(url, params);
        return ExceptionMapper.executeWithMapping(() -> {
            String normalizedMethod = normalizeMethod(method);
            Request request = defaultClient
                    .newGet(finalUrl)
                    .method(normalizedMethod)
                    .headers(headers)
                    .body(body)
                    .build();
            return request.newCall().execute();
        }, method, finalUrl);
    }

    // ========== 异步请求 ==========

    /**
     * 异步GET请求
     */
    public static CompletableFuture<String> getAsync(String url) {
        return getAsync(url, (Map<String, String>) null);
    }

    /**
     * 异步GET请求 - 带参数
     */
    public static CompletableFuture<String> getAsync(String url, Map<String, String> params) {
        return getAsync(url, (Map<String, String>) null, params);
    }

    /**
     * 异步GET请求 - 带Headers
     */
    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers, Map<String, String> params) {
        return requestAsync("GET", url, null, headers, params);
    }

    /**
     * 异步GET请求 - 带认证
     */
    public static CompletableFuture<String> getAsync(String url, String auth) {
        Map<String, String> headers = auth != null ? headers("Authorization", auth) : null;
        return getAsync(url, headers, (Map<String, String>) null);
    }

    /**
     * 异步POST请求
     */
    public static CompletableFuture<String> postAsync(String url, String body) {
        return requestAsync("POST", url, body, null, null);
    }

    /**
     * 异步POST JSON请求
     */
    public static CompletableFuture<String> postJsonAsync(String url, Object json) {
        String jsonStr = JNetUtils.toJsonString(json);
        Map<String, String> mergedHeaders = mergeHeaders(null, "Content-Type", "application/json");
        return requestAsync("POST", url, jsonStr, mergedHeaders, null);
    }

    /**
     * 异步通用请求
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body) {
        return requestAsync(method, url, body, null, null);
    }

    /**
     * 异步通用请求 - 带Headers
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body,
                                                         Map<String, String> headers) {
        return requestAsync(method, url, body, headers, null);
    }


    /**
     * 异步通用请求 - 整整参数
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body,
                                       Map<String, String> headers,
                                       Map<String, String> params) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            String finalUrl = JNetUtils.buildUrl(url, params);
            String normalizedMethod = normalizeMethod(method);

            Request request = defaultClient
                    .newGet(finalUrl)
                    .method(normalizedMethod)
                    .headers(headers)
                    .body(body)
                    .build();

            Call call = request.newCall();
            future.whenComplete((result, error) -> {
                if (future.isCancelled()) {
                    call.cancel();
                }
            });
            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    future.complete(response.getBody());
                }

                @Override
                public void onFailure(Exception e) {
                    future.completeExceptionally(ExceptionMapper.map(e, normalizedMethod, finalUrl));
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(ExceptionMapper.map(e, method, url));
        }
        return future;
    }

    private static String normalizeMethod(String method) {
        String normalized = method == null ? null : method.trim();
        return HttpValidation.requireToken(normalized, "HTTP method");
    }

    private static int timeoutMillis(Duration timeout) {
        Objects.requireNonNull(timeout, "timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Timeout must be positive");
        }
        long millis;
        try {
            millis = timeout.toMillis();
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("Timeout exceeds the supported millisecond range", overflow);
        }
        if (millis <= 0 || millis > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Timeout is outside the supported millisecond range");
        }
        return (int) millis;
    }

    // ========== TCP Socket Methods (新增) ==========

    /**
     * TCP 请求 - 发送并接收（类似Python socket）
     *
     * @param host 目标主机
     * @param port 目标端口
     * @return 响应数据字符串
     */
    public static String tcp(String host, int port) throws IOException {
        return TcpClient.send(host, port, (byte[]) null);
    }

    /**
     * TCP 请求 - 发送字符串（UTF-8编码）
     */
    public static String tcp(String host, int port, String data) throws IOException {
        return TcpClient.send(host, port, data);
    }

    /**
     * TCP 请求 - 发送二进制数据
     */
    public static String tcp(String host, int port, byte[] data) throws IOException {
        return TcpClient.send(host, port, data);
    }

    /**
     * TCP 请求 - 带超时
     */
    public static String tcp(String host, int port, int timeoutMs) throws IOException {
        TcpRequest request = TcpClient.getInstance()
                .newRequest(host, port)
                .timeout(timeoutMs)
                .build();
        return TcpClient.send(request).getDataAsString();
    }

    /**
     * 创建TCP会话（持久连接）
     */
    public static TcpSession tcpSession(String host, int port) {
        return TcpClient.getInstance().newSession(host, port);
    }

    /**
     * 创建TCP会话（带超时）
     */
    public static TcpSession tcpSession(String host, int port, int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        return TcpClient.getInstance().newSession(host, port,
                timeoutMs > 0 ? java.time.Duration.ofMillis(timeoutMs) : null);
    }

    // ========== UDP Datagram Methods (新增) ==========

    /**
     * UDP 请求 - 发送数据包
     *
     * @param host 目标主机
     * @param port 目标端口
     * @param data 要发送的数据（UTF-8编码）
     * @return 发送的数据包
     */
    public static UdpPacket udp(String host, int port, String data) throws IOException {
        return UdpClient.getInstance()
                .send(
                    new UdpPacket.Builder()
                    .address(host, port)
                    .data(data)
                    .build()
                );
    }

    /**
     * UDP 请求 - 发送二进制数据
     */
    public static UdpPacket udp(String host, int port, byte[] data) throws IOException {
        return UdpClient.getInstance()
                .send(
                    new UdpPacket.Builder()
                    .address(host, port)
                    .data(data)
                    .build()
                );
    }

    /**
     * UDP 请求 - 接收数据包
     *
     * 使用默认 UDP 客户端超时；超时会抛出 {@link java.net.SocketTimeoutException}。
     *
     * @param port 监听端口
     * @return 接收到的数据包
     */
    public static UdpPacket udp(int port) throws IOException {
        return UdpClient.getInstance().receiveOnPort(port);
    }

    /**
     * UDP 请求 - 接收数据包（指定超时）
     *
     * @param port 监听端口
     * @param timeoutMs 超时毫秒数，0 表示无限等待
     * @return 接收到的数据包
     */
    public static UdpPacket udp(int port, int timeoutMs) throws IOException {
        return UdpClient.getInstance().receiveOnPort(port, timeoutMs);
    }

    /**
     * UDP请求 - 创建构建器
     */
    public static UdpClient udp(String host, int port) {
        return UdpClient.newBuilder()
                .host(host)
                .port(port)
                .timeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    // ========== HLS HTTP Live Streaming Methods (新增) ==========

    /**
     * HLS 客户端 - 从URL创建
     *
     * @param url HLS播放列表URL
     * @return HLS客户端
     */
    public static HlsClient hls(String url) {
        return HlsClient.fromUrl(url);
    }

    /**
     * HLS 客户端 - 从URL创建（带自定义配置）
     */
    public static HlsClient hls(String url, Duration timeout, int refreshInterval) {
        return configuredHls(url, timeout, refreshInterval);
    }

    /**
     * 从URL创建Live HLS播放客户端
     */
    public static HlsClient liveHls(String url, Duration readTimeout, int refreshInterval) {
        return configuredHls(url, readTimeout, refreshInterval);
    }

    /**
     * RTSP 客户端 - 从URL创建
     *
     * @param url RTSP服务器地址（rtsp://开头）
     * @return RTSP客户端
     */
    public static RtspClient rtsp(String url) {
        return RtspClient.newBuilder()
                .url(url)
                .build();
    }

    /**
     * RTSP 客户端 - 带认证
     *
     * @param url RTSP服务器地址
     * @param username 用户名
     * @param password 密码
     * @return RTSP客户端
     */
    public static RtspClient rtsp(String url, String username, String password) {
        return RtspClient.newBuilder()
                .url(url)
                .credentials(username, password)
                .build();
    }

    /**
     * RTSP 客户端 - 带自定义配置
     *
     * @param url RTSP服务器地址
     * @param timeout 超时时间
     * @param userAgent User-Agent
     * @return RTSP客户端
     */
    public static RtspClient rtsp(String url, java.time.Duration timeout, String userAgent) {
        return RtspClient.newBuilder()
                .url(url)
                .timeout(timeout)
                .userAgent(userAgent)
                .build();
    }

    /**
     * RTSP 客户端 - 完整配置
     *
     * @param url RTSP服务器地址
     * @param timeout 超时时间
     * @param userAgent User-Agent
     * @param username 用户名
     * @param password 密码
     * @return RTSP客户端
     */
    public static RtspClient rtsp(String url, java.time.Duration timeout, String userAgent, String username, String password) {
        return RtspClient.newBuilder()
                .url(url)
                .timeout(timeout)
                .userAgent(userAgent)
                .credentials(username, password)
                .build();
    }

    // ========== Server Methods (新增) ==========

    /**
     * 创建 HTTP/1.1 服务器配置；调用返回对象的 {@code start()} 方法启动。
     *
     * @param port 监听端口（默认8080）
     * @return 服务器对象
     */
    public static com.jnet.tcp.Server serve(int port) {
        return com.jnet.tcp.Server.newBuilder()
                .port(port)
                .handler((method, path, body) -> "")
                .build();
    }

    /**
     * 创建HTTP服务器（带处理器）
     *
     * @param route 路由
     * @param handler 请求处理器
     * @return 服务器对象
     */
    public static com.jnet.tcp.Server serve(String route, com.jnet.tcp.Server.RequestHandler handler) {
        return com.jnet.tcp.Server.newBuilder()
                .port(8080)
                .route(route)
                .handler(handler)
                .build();
    }

    /**
     * 创建 HTTP/1.1 服务器的兼容入口，并非原始字节流 TCP 服务器。
     *
     * @param port 监听端口
     * @param handler 处理器
     * @return TCP服务器对象
     */
    public static com.jnet.tcp.Server tcpServer(int port, com.jnet.tcp.Server.RequestHandler handler) {
        return com.jnet.tcp.Server.newBuilder()
                .port(port)
                .handler(handler)
                .build();
    }

    /**
     * 创建UDP服务器
     *
     * @param port 监听端口
     * @param handler 数据包处理器
     * @return UDP服务器对象
     */
    public static com.jnet.udp.Server udpServer(int port,
                                                com.jnet.udp.Server.PacketHandler handler) {
        return com.jnet.udp.Server.newBuilder()
                .port(port)
                .handler(handler)
                .build();
    }

    // ========== Getters ==========

    /**
     * 获取HTTP客户端实例（外部使用）
     */
    public static HttpClient getHttpClient() {
        return defaultClient.getHttpClient();
    }

    /**
     * 获取JNetClient实例（外部使用）
     */
    public static JNetClient getClient() {
        return defaultClient;
    }

    /**
     * 获取TCP客户端实例
     */
    public static TcpClient getTcpClient() {
        return TcpClient.getInstance();
    }

    /**
     * 获取UDP客户端实例
     */
    public static UdpClient getUdpClient() {
        return UdpClient.getInstance();
    }

    /**
     * 获取HTTP客户端构建器
     */
    public static JNetClient.Builder newBuilder() {
        return JNetClient.newBuilder();
    }

    // ========== 内部方法 ==========

    private static Map<String, String> mergeHeaders(Map<String, String> headers, String key, String value) {
        Map<String, String> merged = new HashMap<>();
        if (headers != null) {
            merged.putAll(headers);
        }
        if (key != null && !key.isEmpty()) {
            merged.put(key, value == null ? "" : value);
        }
        return merged;
    }

    private static HlsClient configuredHls(String url, Duration readTimeout, int refreshInterval) {
        if (refreshInterval <= 0) {
            throw new IllegalArgumentException("refreshInterval must be positive");
        }
        return HlsClient.newBuilder()
                .url(url)
                .readTimeout(readTimeout)
                .build();
    }

}
