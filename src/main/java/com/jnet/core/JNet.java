package com.jnet.core;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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

    // ========== 静态配置 ==========

    private static volatile HttpClient defaultClient;
    private static volatile Duration defaultTimeout = Duration.ofSeconds(10);

    private JNet() {
        // 防止实例化
    }

    /**
     * 创建默认HTTP客户端 (单例)
     */
    private static HttpClient getDefaultClient() {
        if (defaultClient == null) {
            synchronized (JNet.class) {
                if (defaultClient == null) {
                    defaultClient = HttpClient.newBuilder()
                            .version(HttpClient.Version.HTTP_2)  // 支持HTTP/2
                            .connectTimeout(defaultTimeout)
                            .build();
                }
            }
        }
        return defaultClient;
    }

    /**
     * 设置默认超时时间
     */
    public static void setDefaultTimeout(Duration timeout) {
        defaultTimeout = timeout;
    }

    // ========== 工具方法 ==========

    /**
     * 构建查询参数
     */
    public static Map<String, String> params(String... keyValues) {
        if (keyValues == null || keyValues.length == 0) return new HashMap<>();
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("keyValues must be even");
        }
        Map<String, String> map = new HashMap<>();
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
        try {
            String finalUrl = buildUrl(url, params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .GET();

            applyHeaders(builder, headers);

            HttpClient client = getDefaultClient();
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Network error during GET request: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted: " + url, e);
        } catch (Exception e) {
            throw new RuntimeException("GET request failed: " + url, e);
        }
    }

    /**
     * GET请求 - 带认证
     */
    public static String get(String url, String auth) {
        Map<String, String> headers = auth != null ?
                headers("Authorization", auth) : null;
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
        String jsonStr = toJsonString(json);
        Map<String, String> h = new HashMap<>();
        if (headers != null) h.putAll(headers);
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
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(defaultTimeout);

            // 设置HTTP方法
            switch (method.toUpperCase()) {
                case "GET":
                    builder.GET();
                    break;
                case "POST":
                    builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                    break;
                case "PUT":
                    builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                case "HEAD":
                    builder.GET(); // HEAD使用GET但忽略body
                    break;
                case "PATCH":
                    // PATCH需要特殊处理（某些JDK版本不支持）
                    if (body != null) {
                        builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        builder.method("PATCH", HttpRequest.BodyPublishers.ofString(""));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported method: " + method);
            }

            applyHeaders(builder, headers);

            HttpClient client = getDefaultClient();
            HttpResponse<String> response = client.send(builder.build(),
                    HttpResponse.BodyHandlers.ofString());

            return response.body();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Network error during " + method + " request: " + url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted: " + url, e);
        } catch (Exception e) {
            throw new RuntimeException(method + " request failed: " + url, e);
        }
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
    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers, Map<String, String> params) {
        try {
            String finalUrl = buildUrl(url, params);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(finalUrl))
                    .GET();

            applyHeaders(builder, headers);

            HttpClient client = getDefaultClient();
            return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body);
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
        String jsonStr = toJsonString(json);
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
    public static CompletableFuture<String> requestAsync(String method, String url, String body, Map<String, String> headers) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(defaultTimeout);

            switch (method.toUpperCase()) {
                case "GET":
                    builder.GET();
                    break;
                case "POST":
                    builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                    break;
                case "PUT":
                    builder.PUT(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
                    break;
                case "DELETE":
                    builder.DELETE();
                    break;
                case "HEAD":
                    builder.GET();
                    break;
                case "PATCH":
                    // PATCH需要特殊处理
                    if (body != null) {
                        builder.method("PATCH", HttpRequest.BodyPublishers.ofString(body));
                    } else {
                        builder.method("PATCH", HttpRequest.BodyPublishers.ofString(""));
                    }
                    break;
                default:
                    throw new IllegalArgumentException("Unsupported method: " + method);
            }

            applyHeaders(builder, headers);

            HttpClient client = getDefaultClient();
            return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString())
                    .thenApply(HttpResponse::body);
        } catch (Exception e) {
            CompletableFuture<String> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    // ========== 私有辅助方法 ==========

    /**
     * 构建带参数的URL
     */
    private static String buildUrl(String url, Map<String, String> params) {
        if (params == null || params.isEmpty()) return url;

        StringBuilder sb = new StringBuilder(url);
        boolean hasQuery = url.contains("?");
        if (!hasQuery) sb.append("?");
        else if (!url.endsWith("&") && !url.endsWith("?")) sb.append("&");

        boolean first = true;
        for (Map.Entry<String, String> entry : params.entrySet()) {
            if (!first) sb.append("&");
            first = false;
            try {
                sb.append(entry.getKey())
                  .append("=")
                  .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                // 编码失败时使用原始值
                sb.append(entry.getKey()).append("=").append(entry.getValue());
            }
        }

        return sb.toString();
    }

    /**
     * 应用Headers到请求
     */
    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) return;
        // 手动构建headers数组
        List<String> headerList = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerList.add(entry.getKey());
            headerList.add(entry.getValue());
        }
        builder.headers(headerList.toArray(new String[0]));
    }

    /**
     * 将对象转换为JSON字符串
     */
    private static String toJsonString(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof String) return "\"" + escapeJson((String) obj) + "\"";
        if (obj instanceof Number || obj instanceof Boolean) return obj.toString();
        if (obj instanceof Map) {
            StringBuilder sb = new StringBuilder("{");
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) obj).entrySet()) {
                if (!first) sb.append(",");
                first = false;
                sb.append("\"").append(entry.getKey()).append("\":")
                  .append(toJsonString(entry.getValue()));
            }
            sb.append("}");
            return sb.toString();
        }
        if (obj instanceof Iterable) {
            StringBuilder sb = new StringBuilder("[");
            boolean first = true;
            for (Object item : (Iterable<?>) obj) {
                if (!first) sb.append(",");
                first = false;
                sb.append(toJsonString(item));
            }
            sb.append("]");
            return sb.toString();
        }
        return "\"" + escapeJson(obj.toString()) + "\"";
    }

    /**
     * JSON字符串转义
     */
    private static String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}
