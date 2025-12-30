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
import java.time.temporal.Temporal;

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
                            .version(HttpClient.Version.HTTP_2) // 支持HTTP/2
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

    /**
     * 获取默认HTTP客户端实例
     * 用于需要共享客户端的场景（如SSEClient）
     */
    public static HttpClient getDefaultHttpClient() {
        return getDefaultClient();
    }

    // ========== 工具方法 ==========

    /**
     * 构建查询参数
     */
    public static Map<String, String> params(String... keyValues) {
        if (keyValues == null || keyValues.length == 0)
            return new HashMap<>();
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
        } catch (java.net.ConnectException e) {
            throw new JNetException.Builder()
                    .message("Connection refused: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.CONNECTION_REFUSED)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (java.net.SocketTimeoutException e) {
            throw new JNetException.Builder()
                    .message("Request timeout: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (java.net.UnknownHostException e) {
            throw new JNetException.Builder()
                    .message("Unknown host: " + e.getMessage())
                    .cause(e)
                    .errorType(JNetException.ErrorType.NETWORK_UNAVAILABLE)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (javax.net.ssl.SSLHandshakeException e) {
            throw new JNetException.Builder()
                    .message("SSL handshake failed: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.SSL_HANDSHAKE_FAILED)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (java.io.IOException e) {
            throw new JNetException.Builder()
                    .message("IO error during GET request: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JNetException.Builder()
                    .message("Request interrupted: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.INTERRUPTED)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        } catch (Exception e) {
            throw new JNetException.Builder()
                    .message("GET request failed: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.UNKNOWN)
                    .requestUrl(url)
                    .requestMethod("GET")
                    .build();
        }
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
        String jsonStr = toJsonString(json);
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
        } catch (java.net.ConnectException e) {
            throw new JNetException.Builder()
                    .message("Connection refused: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.CONNECTION_REFUSED)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (java.net.SocketTimeoutException e) {
            throw new JNetException.Builder()
                    .message("Request timeout: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (java.net.UnknownHostException e) {
            throw new JNetException.Builder()
                    .message("Unknown host: " + e.getMessage())
                    .cause(e)
                    .errorType(JNetException.ErrorType.NETWORK_UNAVAILABLE)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (javax.net.ssl.SSLHandshakeException e) {
            throw new JNetException.Builder()
                    .message("SSL handshake failed: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.SSL_HANDSHAKE_FAILED)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (java.io.IOException e) {
            throw new JNetException.Builder()
                    .message("IO error during " + method + " request: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new JNetException.Builder()
                    .message("Request interrupted: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.INTERRUPTED)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (IllegalArgumentException e) {
            throw new JNetException.Builder()
                    .message("Invalid request configuration: " + e.getMessage())
                    .cause(e)
                    .errorType(JNetException.ErrorType.REQUEST_BUILD_ERROR)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
        } catch (Exception e) {
            throw new JNetException.Builder()
                    .message(method + " request failed: " + url)
                    .cause(e)
                    .errorType(JNetException.ErrorType.UNKNOWN)
                    .requestUrl(url)
                    .requestMethod(method)
                    .build();
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
    public static CompletableFuture<String> getAsync(String url, Map<String, String> headers,
            Map<String, String> params) {
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
    public static CompletableFuture<String> requestAsync(String method, String url, String body,
            Map<String, String> headers) {
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
        if (params == null || params.isEmpty())
            return url;

        try {
            // 使用URI类进行安全的URL解析和构建
            URI originalUri = URI.create(url);

            // 构建查询参数
            StringBuilder queryBuilder = new StringBuilder();

            // 保留原有的查询参数
            String existingQuery = originalUri.getQuery();
            if (existingQuery != null) {
                queryBuilder.append(existingQuery);
                if (!existingQuery.endsWith("&")) {
                    queryBuilder.append("&");
                }
            }

            // 添加新参数
            boolean first = existingQuery == null;
            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first)
                    queryBuilder.append("&");
                first = false;

                String key = entry.getKey();
                String value = entry.getValue();

                // 对键值进行URL编码
                String encodedKey = URLEncoder.encode(key, StandardCharsets.UTF_8);
                String encodedValue = value != null ? URLEncoder.encode(value, StandardCharsets.UTF_8) : "";

                queryBuilder.append(encodedKey).append("=").append(encodedValue);
            }

            // 重建URI
            URI resultUri = new URI(
                    originalUri.getScheme(),
                    originalUri.getUserInfo(),
                    originalUri.getHost(),
                    originalUri.getPort(),
                    originalUri.getPath(),
                    queryBuilder.toString(),
                    originalUri.getFragment());

            return resultUri.toString();

        } catch (Exception e) {
            // 如果URI解析失败，回退到StringBuilder方式（但添加更多保护）
            StringBuilder sb = new StringBuilder(url);

            // 检查并添加正确的分隔符
            boolean hasQuery = url.contains("?");
            boolean endsWithAmp = url.endsWith("&");
            boolean endsWithQ = url.endsWith("?");

            if (!hasQuery) {
                sb.append("?");
            } else if (!endsWithAmp && !endsWithQ) {
                sb.append("&");
            } else if (endsWithAmp) {
                // 已经以&结尾，不需要添加
            }

            boolean first = hasQuery && (endsWithAmp || endsWithQ);
            if (!hasQuery)
                first = true;

            for (Map.Entry<String, String> entry : params.entrySet()) {
                if (!first)
                    sb.append("&");
                first = false;

                try {
                    String key = URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8);
                    String value = entry.getValue() != null
                            ? URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8)
                            : "";
                    sb.append(key).append("=").append(value);
                } catch (Exception encodeException) {
                    // 最后防线：使用原始值
                    sb.append(entry.getKey()).append("=").append(entry.getValue());
                }
            }

            return sb.toString();
        }
    }

    /**
     * 应用Headers到请求
     */
    private static void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers == null || headers.isEmpty())
            return;
        // 手动构建headers数组
        List<String> headerList = new ArrayList<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            headerList.add(entry.getKey());
            headerList.add(entry.getValue());
        }
        builder.headers(headerList.toArray(new String[0]));
    }

    /**
     * 将对象转换为JSON字符串 - 安全版本
     * 支持深度限制、循环引用检测、完整转义
     */
    private static String toJsonString(Object obj) {
        return toJsonString(obj, 0, new IdentityHashMap<>());
    }

    /**
     * 将对象转换为JSON字符串 - 内部实现
     *
     * @param obj     要转换的对象
     * @param depth   当前深度
     * @param visited 已访问对象集合（用于检测循环引用）
     * @return JSON字符串
     */
    private static String toJsonString(Object obj, int depth, Map<Object, Boolean> visited) {
        // 深度限制检查
        if (depth > 100) {
            throw new IllegalArgumentException(
                    "JSON serialization depth exceeded (max 100). Possible circular reference.");
        }

        // null检查
        if (obj == null) {
            return "null";
        }

        // 循环引用检测
        if (isPrimitive(obj)) {
            // 基本类型不需要循环引用检查
        } else {
            if (visited.containsKey(obj)) {
                throw new IllegalArgumentException("Circular reference detected in JSON serialization");
            }
            visited.put(obj, true);
        }

        try {
            // 基本类型处理
            if (obj instanceof String) {
                return escapeJsonString((String) obj);
            }
            if (obj instanceof Number) {
                return escapeJsonNumber((Number) obj);
            }
            if (obj instanceof Boolean) {
                return obj.toString();
            }
            if (obj instanceof Character) {
                return escapeJsonString(obj.toString());
            }

            // 复合类型处理
            if (obj instanceof Map) {
                return toJsonStringMap((Map<?, ?>) obj, depth + 1, visited);
            }
            if (obj instanceof Iterable) {
                return toJsonStringIterable((Iterable<?>) obj, depth + 1, visited);
            }
            if (obj instanceof Object[]) {
                return toJsonStringArray((Object[]) obj, depth + 1, visited);
            }

            if (obj instanceof Date) {
                return "\"" + ((Date) obj).toInstant().toString() + "\"";
            }
            if (obj instanceof Temporal) {
                return "\"" + obj.toString() + "\"";
            }

            // 其他对象 - 转换为字符串
            return escapeJsonString(obj.toString());

        } finally {
            // 清理当前对象的访问记录（避免影响同层其他对象）
            if (!isPrimitive(obj)) {
                visited.remove(obj);
            }
        }
    }

    /**
     * 处理Map类型
     */
    private static String toJsonStringMap(Map<?, ?> map, int depth, Map<Object, Boolean> visited) {
        if (map.isEmpty()) {
            return "{}";
        }

        StringBuilder sb = new StringBuilder("{");
        boolean first = true;

        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!first)
                sb.append(",");
            first = false;

            // 键必须是字符串或可转换为字符串
            String key = entry.getKey() == null ? "null" : escapeJsonString(entry.getKey().toString());
            sb.append("\"").append(key).append("\":");

            // 值递归处理
            sb.append(toJsonString(entry.getValue(), depth, visited));
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 处理Iterable类型
     */
    private static String toJsonStringIterable(Iterable<?> iterable, int depth, Map<Object, Boolean> visited) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Object item : iterable) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append(toJsonString(item, depth, visited));
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 处理数组类型
     */
    private static String toJsonStringArray(Object[] array, int depth, Map<Object, Boolean> visited) {
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;

        for (Object item : array) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append(toJsonString(item, depth, visited));
        }

        sb.append("]");
        return sb.toString();
    }

    /**
     * 安全的数字转换
     */
    private static String escapeJsonNumber(Number num) {
        if (num instanceof Double || num instanceof Float) {
            double value = num.doubleValue();
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return "null"; // JSON不支持NaN和Infinity
            }
        }
        return num.toString();
    }

    /**
     * 完整的JSON字符串转义
     */
    private static String escapeJsonString(String str) {
        if (str == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    // 控制字符需要Unicode转义
                    if (c <= 0x1F) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return "\"" + sb.toString() + "\"";
    }

    /**
     * 判断是否为基本类型（不需要循环引用检查）
     */
    private static boolean isPrimitive(Object obj) {
        return obj instanceof String ||
                obj instanceof Number ||
                obj instanceof Boolean ||
                obj instanceof Character ||
                obj == null;
    }
}
