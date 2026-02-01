package com.jnet.core;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JNet - 极简HTTP客户端 (基于JDK11原生HTTP Client)
 * 特点:
 * - 无第三方依赖
 * - 支持HTTP/2
 * - 支持多平台 (JDK11+)
 * - API参考Python requests风格
 * - 极度精简代码
 *
 * <h2>快速开始</h2>
 *
 * <pre>{@code
 * // 最简洁的方式 - 就像Python requests一样！
 * String data = JNet.get("https://api.example.com/data");
 *
 * // 带参数
 * String data = JNet.get("https://api.example.com/data", JNet.params("key", "value"));
 *
 * // POST JSON
 * String result = JNet.post("https://api.example.com/users", JNet.json().put("name", "Alice"));
 *
 * // 异步请求
 * CompletableFuture<String> future = JNet.getAsync("https://api.example.com/data");
 * String data = future.get();
 *
 * // 完整参数
 * Map<String, String> headers = JNet.headers("Authorization", "Bearer token");
 * Map<String, String> params = JNet.params("key1", "value1");
 * String result = JNet.get("https://api.example.com", headers, params);
 * }</pre>
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class JNet {

    private JNet() {
        // 防止实例化
    }

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

    // ========== GET 请求 ==========

    /**
     * GET请求 - 最简单方式
     */
    public static String get(String url) {
        return get(url, (Map<String, String>) null);
    }

    /**
     * GET请求 - 带参数
     */
    public static String get(String url, Map<String, String> params) {
        return get(url, params, null);
    }

    /**
     * GET请求 - 带Headers
     */
    public static String get(String url, Map<String, String> headers, Map<String, String> params) {
        return ExceptionMapper.executeWithMapping(() -> {
            String finalUrl = JNetUtils.buildUrl(url, params);

            // 使用JNetClient统一处理
            Request request = JNetClient.getInstance().newGet(finalUrl)
                    .headers(headers)
                    .build();

            return request.newCall().execute().getBody();
        }, "GET", url);
    }

    /**
     * GET请求 - 带认证
     */
    public static String get(String url, String auth) {
        Map<String, String> headers = auth != null ? headers("Authorization", auth) : null;
        return get(url, headers, null);
    }

    // ========== POST 请求 ==========

    /**
     * POST请求 - 最简单方式
     */
    public static String post(String url, String body) {
        return request("POST", url, body);
    }

    /**
     * POST请求 - 带Headers
     */
    public static String post(String url, String body, Map<String, String> headers) {
        return request("POST", url, body, headers);
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
        Map<String, String> h = new HashMap<>();
        if (headers != null)
            h.putAll(headers);
        h.put("Content-Type", "application/json");
        return post(url, jsonStr, h);
    }

    // ========== 其他HTTP方法 ==========

    /**
     * PUT请求
     */
    public static String put(String url, String body) {
        return request("PUT", url, body);
    }

    /**
     * DELETE请求
     */
    public static String delete(String url) {
        return request("DELETE", url, null);
    }

    /**
     * PATCH请求
     */
    public static String patch(String url, String body) {
        return request("PATCH", url, body);
    }

    /**
     * HEAD请求
     */
    public static String head(String url) {
        return request("HEAD", url, null);
    }

    /**
     * 通用请求方法
     */
    public static String request(String method, String url, String body) {
        return request(method, url, body, null);
    }

    /**
     * 通用请求方法 - 完整参数
     */
    public static String request(String method, String url, String body, Map<String, String> headers) {
        return ExceptionMapper.executeWithMapping(() -> {
            Request.Builder builder = new Request.Builder()
                    .client(JNetClient.getInstance())
                    .url(url)
                    .method(method)
                    .headers(headers)
                    .body(body);

            return builder.build().newCall().execute().getBody();
        }, method, url);
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
        return getAsync(url, params, null);
    }

    /**
     * 异步GET请求 - 完整参数
     */
    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers,
            Map<String, String> params) {
        try {
            String finalUrl = JNetUtils.buildUrl(url, params);

            Request request = JNetClient.getInstance().newGet(finalUrl)
                    .headers(headers)
                    .build();

            CompletableFuture<String> future = new CompletableFuture<>();
            request.newCall().enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    future.complete(response.getBody());
                }

                @Override
                public void onFailure(Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    /**
     * 异步POST请求
     */
    public static CompletableFuture<String> postAsync(String url, String body) {
        return requestAsync("POST", url, body);
    }

    /**
     * 异步POST JSON请求
     */
    public static CompletableFuture<String> postJsonAsync(String url, Object json) {
        String jsonStr = JNetUtils.toJsonString(json);
        return postAsync(url, jsonStr);
    }

    /**
     * 异步通用请求
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body) {
        return requestAsync(method, url, body, null);
    }

    /**
     * 异步通用请求 - 完整参数
     */
    public static CompletableFuture<String> requestAsync(String method, String url, String body,
            Map<String, String> headers) {
        try {
            Request.Builder builder = new Request.Builder()
                    .client(JNetClient.getInstance())
                    .url(url)
                    .method(method)
                    .headers(headers)
                    .body(body);

            CompletableFuture<String> future = new CompletableFuture<>();
            builder.build().newCall().enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    future.complete(response.getBody());
                }

                @Override
                public void onFailure(Exception e) {
                    future.completeExceptionally(e);
                }
            });
            return future;
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }
}