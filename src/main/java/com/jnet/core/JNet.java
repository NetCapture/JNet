package com.jnet.core;

import com.jnet.tcp.TcpClient;
import com.jnet.tcp.TcpRequest;
import com.jnet.tcp.TcpSession;
import com.jnet.udp.UdpClient;
import com.jnet.udp.UdpPacket;
import com.jnet.hls.HlsClient;
import com.jnet.rtsp.RtspClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JNet - 极简HTTP/TCP/UDP客户端（基于JDK 11+原生实现）
 * 特点:
 * - 无第三方依赖
 * - 支持HTTP/2、SSE、WebSocket、Socket.IO、HLS、RTSP、TCP、UDP
 * - 类似Python requests风格的API
 * - 支持服务器功能（一键启动）
 *
 * <h2>API对比 - JNet vs Python Requests</h2>
 *
 * <table>
 * <tr><th>功能</th><th>Python Requests</th><th>JNet</th></tr>
 * <tr><td>基础GET</td><td>requests.get(url)</td><td>JNet.get(url)</td></tr>
 * <tr><td>GET + 参数</td><td>requests.get(url, params)</td><td>JNet.get(url, params)</td></tr>
 * <tr><td>POST JSON</td><td>requests.post(url, json)</td><td>JNet.post(url, json)</td></tr>
 * <tr><td>POST + Headers</td><td>requests.post(url, headers)</td><td>JNet.post(url, headers)</td></tr>
 * <tr><td>异步请求</td><td>requests.get_async(url)</td><td>JNet.getAsync(url)</td></tr>
 * <tr><td>TCP Socket</td><td>socket.create().send(data)</td><td>JNet.tcp(host, port).send(data)</td></tr>
 * <tr><td>UDP Datagram</td><td>--</td><td>JNet.udp(host, port).send(data)</td></tr>
 * <tr><td>持久TCP会话</td><td>--</td><td>JNet.tcpSession(host, port).send()</td></tr>
 * <tr><td>HLS播放</td><td>--</td><td>JNet.hls(url).downloadSegments()</td></tr>
 * <tr><td>RTSP流</td><td>--</td><td>JNet.rtsp(url).play()</td></tr>
 * <tr><td>WebSocket</td><td>--</td><td>JNet.websocket(ws).connect()</td><td>JNet.websocket(wsUrl).connect()</td></tr>
 * <tr><td>Socket.IO</td><td>--</td><td>JNet.socketio(wsUrl).connect()</td><td>JNet.socketio(wsUrl).connect()</td></tr>
 * <tr><td>一键服务</td><td>--</td><td>JNet.serve(8080).start()</td></tr>
 * </table>
 * </p>
 *
 * <h3>快速开始</h3>
 *
 * <p>最简洁的方式 - 就像Python requests一样！</p>
 * <pre>{@code
 * // 1. 基础GET
 * String html = JNet.get("https://api.example.com/page");
 *
 * // 2. 带参数
 * String data = JNet.get("https://api.example.com/data", JNet.params("key", "value"));
 *
 * // 3. POST JSON
 * String result = JNet.post("https://api.example.com/users", JNet.json().put("name", "Alice"));
 *
 * // 4. 异步
 * CompletableFuture<String> future = JNet.getAsync("https://api.example.com/data");
 * String data = future.get();
 * }</pre>
 *
 * <h3>TCP Socket (新增)</h3>
 *
 * <p>类似Python socket的简洁API</p>
 * <pre>{@code
 * // 单次发送和接收
 * String response = JNet.tcp("example.com", 80).send("Hello, Server!");
 *
 * // 发送UTF-8字符串
 * String response = JNet.tcp("example.com", 80).send("你好，服务器！");
 *
 * // 发送二进制数据
 * byte[] data = "二进制数据".getBytes(StandardCharsets.UTF_8);
 * String response = JNet.tcp("example.com", 80).send(data);
 *
 * // 带超时
 * String response = JNet.tcp("example.com", 80).timeout(5000).send("Hello!");
 *
 * // 持久TCP会话
 * TcpSession session = JNet.tcp("example.com", 80).newSession();
 * session.send("第一条消息");
 * String first = session.receive();
 * session.send("第二条消息");
 * String second = session.receive();
 * session.close();
 * }</pre>
 *
 * <h3>UDP Datagram (新增)</h3>
 *
 * <p>类似Python socket的简洁API</p>
 * <pre>{@code
 * // 发送UDP包
 * JNet.udp("example.com", 1234).send("Hello, UDP!");
 *
 * // 接收UDP包
 * UdpPacket received = JNet.udp("example.com", 1234).receive();
 * System.out.println(received.getDataAsString());
 * }</pre>
 *
 * <h3>HLS 播放 (新增)</h3>
 *
 * <p>m3u8 格式HTTP Live Streaming</p>
 * <pre>{@code
 * // 获取播放列表并下载所有段
 * HlsClient hls = JNet.hls("https://live.example.com/stream.m3u8");
 * hls.getPlaylist().getSegments().forEach(segment -> {
 *     String url = segment.getUrl();
 *     System.out.println("下载: " + url);
 * });
 * hls.downloadSegments("/tmp/video/");
 *
 * // 流式播放（Live）
 * HlsClient liveHls = JNet.hls.builder()
 *     .url("https://live.example.com/stream.m3u8")
 *     .refreshInterval(3)
 *     .build();
 *
 * liveHls.streamLive(new HlsClient.LiveStreamListener() {
 *     @Override
 *     public void onSegment(HlsSegment segment) {
 *         System.out.println("播放中: " + segment.getUrl());
 *     }
 *
 *     @Override
 *     public void onPlaylistRefreshed(HlsMediaPlaylist playlist) {
 *         System.out.println("播放列表已刷新");
 *     }
 * });</pre>
 *
 * <h3>RTSP 流媒体 (新增)</h3>
 *
 * <p>RTSP协议控制 - rtsp://</p>
 * <pre>{@code
 * RtspClient rtsp = RtspClient.newBuilder()
 *     .url("rtsp://192.168.1.100:554/stream")
 *     .credentials("admin", "password")
 *     .build();
 *
 * // 连接并播放
 * rtsp.connect();
 * rtsp.setup(0, "RTP/AVP;unicast;client_port=5000");
 * rtsp.play();
 * rtsp.pause();
 * rtsp.teardown();
 * }</pre>
 *
 * <h3>WebSocket (已有支持)</h3>
 *
 * <p>原生WebSocket协议</p>
 * <pre>{@code
 * // 连接
 * JNet.websocket("ws://echo.websocket.org").connect();
 * String msg = "Hello WebSocket!";
 * ws.sendText(msg);
 * String reply = ws.receiveString();
 * ws.close();
 * }</pre>
 *
 * <h3>Socket.IO (已有支持)</h3>
 *
 * <p>WebSocket封装</p>
 * <pre>{@code
 * JNet.socketio("wss://echo.socket.io").connect();
 * socketio.emit("chat message", "Hello!");
 * socketio.on("chat message", args -> {
 *     System.out.println("收到: " + Arrays.toString(args));
 * });</pre>
 *
 * <h3>一键启动服务 (新增)</h3>
 *
 * <p>类似Python httpone - 一键启动服务</p>
 * <pre>{@code
 * // 启动HTTP服务器（8080端口）
 * Server server = JNet.serve(8080);
 * server.start();
 *
 * // 启动TCP服务器
 * com.jnet.tcp.Server tcpServer = com.jnet.tcp.Server
 * tcpServer.port(9000).handler(request -> {
 *     // 处理请求并返回
 *     return "HTTP/1.1 200 OK\n";
 * });
 * tcpServer.start();
 *
 * // 启动UDP服务器
 * com.jnet.udp.Server udpServer = com.jnet.udp.Server
 * udpServer.port(9001).handler(packet -> {
 *     // 处理UDP数据包
 *     return "ACK";
 * });
 * udpServer.start();
 *
 * // 启动RTSP服务器
 * com.jnet.rtsp.Server rtspServer = com.jnet.rtsp.Server
 * rtspServer.port(554).handler(request -> {
 *     return "RTSP/1.0 200 OK\n";
 * });
 * rtspServer.start();
 * }</pre>
 *
 * <h3>Server支持</h3>
 *
 * <pre>{@code
 * Package: com.jnet.server
 *
 *   // TCP Server
 * Server tcp = Server.builder()
 *     .port(8080)
 *     .route("/api", request -> {
 *         // 处理 /api 路径
 *         com.jnet.tcp.TcpClient tcp = new com.jnet.tcp.TcpClient();
 *         try {
 *             String path = request.getUrl().split("/", 2)[1];
 *             if (path.startsWith("api/")) {
 *                 // 转发到内部HTTP服务
 *                 String innerUrl = "http://localhost:8080" + path;
 *                 return JNet.get(innerUrl).getBody();
 *             } else {
 *                 return "404 Not Found\n";
 *         }
 *     })
 *     .start();
 *
 *   // UDP Server
 * Server udp = Server.builder()
 *     .port(9001)
 *     .handler(packet -> {
 *         byte[] data = packet.getData();
 *         // 返回ACK
 *         return UdpPacket.Builder()
 *                 .address(packet.getAddress())
                 .port(packet.getPort())
                 .data("ACK")
                 .build();
 *     })
 *     .start();
 *
 * // RTSP Server
 * Server rtspServer = com.jnet.rtsp.Server.builder()
 *     .port(554)
 *     .handler(request -> {
 *         // 解析RTSP请求并返回RTSP响应
 *         String body = request.getBody();
 *         if (body.startsWith("OPTIONS")) {
 *             return "RTSP/1.0 200 OK\nCSeq: 1\nPublic: OPTIONS, DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN\n";
 * }
 *         return "RTSP/1.0 200 OK\n";
 *     })
 *     .start();
 * }</pre>
 *
 * <h2>版本信息</h2>
 * <p>
 * <strong>v3.5.0</strong> - TCP/UDP Core Release<br>
 * <strong>v3.6.0</strong> - HLS Support<br>
 * <strong>v3.7.0</strong> - RTSP Support<br>
 * <strong>v3.8.0</strong> - Server Support
 * </p>
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class JNet {

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
        // No-op
    }

    /**
     * 获取默认HTTP客户端实例
     * 用于需要共享客户端的场景（如SSEClient）
     */
    public static HttpClient getDefaultHttpClient() {
        return JNetClient.getInstance().getHttpClient();
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
            map.put(keyValues[i], keyValues[i + 1]);
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
        String auth = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Bearer Token
     */
    public static String bearerToken(String token) {
        return "Bearer " + token;
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
        return post(url, body, headers);
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
        String finalUrl = JNetUtils.buildUrl(url, params);
        return ExceptionMapper.executeWithMapping(() -> {
            Request request = JNetClient.getInstance()
                    .newGet(finalUrl)
                    .method(method)
                    .headers(headers)
                    .body(body)
                    .build();
            Response response = request.newCall().execute();
            return response.getBody();
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
     * 异步通用请求 - 整整参数
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body,
                                       Map<String, String> headers,
                                       Map<String, String> params) {
        CompletableFuture<String> future = new CompletableFuture<>();
        try {
            String finalUrl = JNetUtils.buildUrl(url, params);

            Request request = JNetClient.getInstance()
                    .newGet(finalUrl)
                    .method(method)
                    .headers(headers)
                    .body(body)
                    .build();

            request.newCall().enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    future.complete(response.getBody());
                }

                @Override
                public void onFailure(Exception e) {
                    future.completeExceptionally(ExceptionMapper.map(e, method, finalUrl));
                }
            });
        } catch (Exception e) {
            future.completeExceptionally(ExceptionMapper.map(e, method, url));
        }
        return future;
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
        return TcpClient.send(host, port, data.getBytes(StandardCharsets.UTF_8));
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
     * @param port 监听端口
     * @param timeout 超时（毫秒，默认10秒）
     * @return 接收到的数据包，超时返回null
     */
    public static UdpPacket udp(int port) throws IOException {
        return UdpClient.getInstance().receiveOnPort(port);
    }

    /**
     * UDP 请求 - 接收数据包（指定超时）
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
        return HlsClient.newBuilder()
                .url(url)
                .readTimeout(timeout)
                .refreshInterval(refreshInterval)
                .build();
    }

    /**
     * 从URL创建Live HLS播放客户端
     */
    public static HlsClient liveHls(String url, Duration readTimeout, int refreshInterval) {
        return HlsClient.newBuilder()
                .url(url)
                .readTimeout(readTimeout)
                .refreshInterval(refreshInterval)
                .build();
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
     * 创建HTTP服务器（一键启动，类似 pyhon httpone）
     *
     * @param port 监听端口（默认8080）
     * @return 服务器对象
     */
    public static com.jnet.tcp.Server serve(int port) {
        return com.jnet.tcp.Server.newBuilder()
                .port(port)
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
                .handler(handler)
                .build();
    }

    /**
     * 创建TCP服务器
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
        return JNetClient.getInstance().getHttpClient();
    }

    /**
     * 获取JNetClient实例（外部使用）
     */
    public static JNetClient getClient() {
        return JNetClient.getInstance();
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

    /**
     * 构建URL（内部使用）
     */
    private static String buildUrl(String url, Map<String, String> params) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        try {
            URI uri = URI.create(url);
            if (uri.getQuery() != null && !uri.getQuery().isEmpty()) {
                // URL already has parameters, just return as is
                return url;
            }

            if (params == null || params.isEmpty()) {
                return url;
            }

            // Add query parameters
            StringBuilder sb = new StringBuilder(url);
            sb.append(url.contains("?") ? "&" : "?");
            boolean first = true;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first) {
                    sb.append("&");
                }
                sb.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                sb.append("=");
                String value = entry.getValue() != null ? entry.getValue() : "";
                sb.append(URLEncoder.encode(value, StandardCharsets.UTF_8));
                first = false;
            }
            return sb.toString();
        } catch (Exception e) {
            // 失败时返回原始URL
            return url;
        }
    }
}
