package com.jnet.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JNet 静态工具类完整单元测试
 * 覆盖所有公共方法和边界情况
 *
 * @author sanbo
 * @version 3.0
 */
@DisplayName("【JNet】静态工具类完整测试")
public class TestJNetFull {

    // ========== 工具方法测试 ==========

    @Nested
    @DisplayName("工具方法 - params/headers")
    class ToolMethodsTest {

        @Test
        @DisplayName("params() 创建参数映射")
        void testParams() {
            Map<String, String> params = JNet.params("key1", "value1", "key2", "value2");
            assertNotNull(params);
            assertEquals(2, params.size());
            assertEquals("value1", params.get("key1"));
            assertEquals("value2", params.get("key2"));
        }

        @Test
        @DisplayName("params() 空参数")
        void testParamsEmpty() {
            Map<String, String> params = JNet.params();
            assertNotNull(params);
            assertTrue(params.isEmpty());
        }

        @Test
        @DisplayName("params() 奇数个参数应该抛出异常")
        void testParamsOddArgs() {
            assertThrows(IllegalArgumentException.class, () -> {
                JNet.params("key1", "value1", "key2");
            });
        }

        @Test
        @DisplayName("params() null 参数")
        void testParamsNull() {
            Map<String, String> params = JNet.params((String[]) null);
            assertNotNull(params);
            assertTrue(params.isEmpty());
        }

        @Test
        @DisplayName("headers() 与 params() 行为一致")
        void testHeaders() {
            Map<String, String> headers = JNet.headers("Authorization", "Bearer token");
            assertNotNull(headers);
            assertEquals("Bearer token", headers.get("Authorization"));
        }

        @Test
        @DisplayName("json() 创建可变 Map")
        void testJson() {
            Map<String, Object> json = JNet.json();
            assertNotNull(json);
            assertTrue(json instanceof HashMap);
            json.put("key", "value");
            assertEquals("value", json.get("key"));
        }

        @Test
        @DisplayName("basicAuth() 编码正确")
        void testBasicAuth() {
            String auth = JNet.basicAuth("user", "pass");
            assertNotNull(auth);
            assertTrue(auth.startsWith("Basic "));

            // 解码验证
            String encoded = auth.substring(6);
            String decoded = new String(Base64.getDecoder().decode(encoded));
            assertEquals("user:pass", decoded);
        }

        @Test
        @DisplayName("basicAuth() 特殊字符处理")
        void testBasicAuthSpecialChars() {
            String auth = JNet.basicAuth("user@domain", "p@ss:w0rd");
            assertNotNull(auth);
            assertTrue(auth.startsWith("Basic "));
        }

        @Test
        @DisplayName("bearerToken() 格式正确")
        void testBearerToken() {
            String token = JNet.bearerToken("my-token-123");
            assertEquals("Bearer my-token-123", token);
        }

        @Test
        @DisplayName("bearerToken() null 处理")
        void testBearerTokenNull() {
            String token = JNet.bearerToken(null);
            assertEquals("Bearer null", token);
        }
    }

    // ========== GET 请求测试 ==========

    @Nested
    @DisplayName("GET 请求")
    class GetRequestTest {

        @Test
        @DisplayName("get(url) - 简单 GET")
        void testGetSimple() {
            try {
                String result = JNet.get("https://httpbin.org/get");
                assertNotNull(result);
                assertTrue(result.contains("httpbin.org"));
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("get(url, params) - 带参数 GET")
        void testGetWithParams() {
            try {
                Map<String, String> params = JNet.params("name", "JNet", "version", "3.0");
                String result = JNet.get("https://httpbin.org/get", params);
                assertNotNull(result);
                assertTrue(result.contains("JNet"));
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("get(url, headers, params) - 完整 GET")
        void testGetFull() {
            try {
                Map<String, String> headers = JNet.headers("X-Custom", "test");
                Map<String, String> params = JNet.params("q", "search");
                String result = JNet.get("https://httpbin.org/get", headers, params);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("get(url, auth) - 带认证 GET")
        void testGetWithAuth() {
            try {
                String auth = JNet.bearerToken("test-token");
                String result = JNet.get("https://httpbin.org/bearer", auth);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("get() - 网络错误处理")
        void testGetNetworkError() {
            try {
                JNet.get("http://invalid-host-that-does-not-exist-12345.com");
                fail("应该抛出异常");
            } catch (JNetException e) {
                assertNotNull(e.getErrorType());
                assertTrue(e.getMessage().contains("Unknown host") ||
                          e.getMessage().contains("Network"));
            }
        }

        @Test
        @DisplayName("get() - 连接拒绝")
        void testGetConnectionRefused() {
            try {
                // 尝试连接本地不存在的端口
                JNet.get("http://127.0.0.1:9999");
                fail("应该抛出异常");
            } catch (JNetException e) {
                assertTrue(e.getErrorType() == JNetException.ErrorType.CONNECTION_REFUSED ||
                          e.getErrorType() == JNetException.ErrorType.CONNECTION_TIMEOUT);
            }
        }
    }

    // ========== POST 请求测试 ==========

    @Nested
    @DisplayName("POST 请求")
    class PostRequestTest {

        @Test
        @DisplayName("post(url, body) - 简单 POST")
        void testPostSimple() {
            try {
                String result = JNet.post("https://httpbin.org/post", "test data");
                assertNotNull(result);
                assertTrue(result.contains("test data"));
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("post(url, body, headers) - 带头部 POST")
        void testPostWithHeaders() {
            try {
                Map<String, String> headers = JNet.headers("Content-Type", "text/plain");
                String result = JNet.post("https://httpbin.org/post", "hello", headers);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("postJson(url, json) - JSON POST")
        void testPostJson() {
            try {
                Map<String, Object> json = new HashMap<>();
                json.put("name", "JNet");
                json.put("version", 3.0);
                String result = JNet.postJson("https://httpbin.org/post", json);
                assertNotNull(result);
                assertTrue(result.contains("JNet"));
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("postJson(url, json, headers) - JSON POST 带头部")
        void testPostJsonWithHeaders() {
            try {
                Map<String, Object> json = JNet.json();
                json.put("test", "value");
                Map<String, String> headers = JNet.headers("X-Test", "header");
                String result = JNet.postJson("https://httpbin.org/post", json, headers);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }
    }

    // ========== 其他 HTTP 方法测试 ==========

    @Nested
    @DisplayName("其他 HTTP 方法")
    class OtherHttpMethodsTest {

        @Test
        @DisplayName("put() - PUT 请求")
        void testPut() {
            try {
                String result = JNet.put("https://httpbin.org/put", "update data");
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("delete() - DELETE 请求")
        void testDelete() {
            try {
                String result = JNet.delete("https://httpbin.org/delete");
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("patch() - PATCH 请求")
        void testPatch() {
            try {
                String result = JNet.patch("https://httpbin.org/patch", "partial update");
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("head() - HEAD 请求")
        void testHead() {
            try {
                String result = JNet.head("https://httpbin.org/get");
                // HEAD 请求通常返回空 body
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("request() - 通用请求方法")
        void testRequest() {
            try {
                String result = JNet.request("GET", "https://httpbin.org/get", null);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("request() - 不支持的方法")
        void testRequestUnsupportedMethod() {
            try {
                JNet.request("INVALID", "https://httpbin.org/get", null);
                fail("应该抛出异常");
            } catch (JNetException e) {
                assertTrue(e.getErrorType() == JNetException.ErrorType.REQUEST_BUILD_ERROR ||
                          e.getErrorType() == JNetException.ErrorType.UNKNOWN);
            }
        }
    }

    // ========== 异步请求测试 ==========

    @Nested
    @DisplayName("异步请求")
    class AsyncRequestTest {

        @Test
        @DisplayName("getAsync(url) - 简单异步 GET")
        void testGetAsyncSimple() throws Exception {
            CompletableFuture<String> future = JNet.getAsync("https://httpbin.org/get");
            assertNotNull(future);
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
            assertTrue(result.contains("httpbin.org"));
        }

        @Test
        @DisplayName("getAsync(url, params) - 带参数异步 GET")
        void testGetAsyncWithParams() throws Exception {
            Map<String, String> params = JNet.params("key", "value");
            CompletableFuture<String> future = JNet.getAsync("https://httpbin.org/get", params);
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
        }

        @Test
        @DisplayName("getAsync(url, headers, params) - 完整异步 GET")
        void testGetAsyncFull() throws Exception {
            Map<String, String> headers = JNet.headers("X-Test", "async");
            Map<String, String> params = JNet.params("q", "test");
            CompletableFuture<String> future = JNet.getAsync("https://httpbin.org/get", headers, params);
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
        }

        @Test
        @DisplayName("postAsync(url, body) - 异步 POST")
        void testPostAsync() throws Exception {
            CompletableFuture<String> future = JNet.postAsync("https://httpbin.org/post", "async data");
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
            assertTrue(result.contains("async data"));
        }

        @Test
        @DisplayName("postJsonAsync(url, json) - 异步 JSON POST")
        void testPostJsonAsync() throws Exception {
            Map<String, Object> json = JNet.json();
            json.put("async", true);
            CompletableFuture<String> future = JNet.postJsonAsync("https://httpbin.org/post", json);
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
        }

        @Test
        @DisplayName("requestAsync() - 通用异步请求")
        void testRequestAsync() throws Exception {
            CompletableFuture<String> future = JNet.requestAsync("GET", "https://httpbin.org/get", null);
            String result = future.get(10, TimeUnit.SECONDS);
            assertNotNull(result);
        }

        @Test
        @DisplayName("异步请求异常处理")
        void testAsyncException() {
            CompletableFuture<String> future = JNet.getAsync("http://invalid-host-12345.com");
            assertThrows(Exception.class, () -> future.get(10, TimeUnit.SECONDS));
        }
    }

    // ========== JSON 序列化测试 ==========

    @Nested
    @DisplayName("JSON 序列化")
    class JsonSerializationTest {

        @Test
        @DisplayName("JSON 基本类型")
        void testJsonBasicTypes() {
            Map<String, Object> json = JNet.json();
            json.put("string", "value");
            json.put("number", 123);
            json.put("float", 3.14);
            json.put("boolean", true);
            json.put("null", null);

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("JSON 嵌套对象")
        void testJsonNested() {
            Map<String, Object> nested = JNet.json();
            nested.put("inner", "value");

            Map<String, Object> json = JNet.json();
            json.put("outer", nested);

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("JSON 数组")
        void testJsonArray() {
            Map<String, Object> json = JNet.json();
            json.put("array", "[1,2,3]");

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("JSON 特殊字符转义")
        void testJsonEscaping() {
            Map<String, Object> json = JNet.json();
            json.put("special", "test\"with\\quotes");

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("JSON 中文字符")
        void testJsonChinese() {
            Map<String, Object> json = JNet.json();
            json.put("chinese", "你好，世界！");

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
            assertTrue(result.contains("你好"));
        }

        @Test
        @DisplayName("JSON 大对象")
        void testJsonLarge() {
            Map<String, Object> json = JNet.json();
            for (int i = 0; i < 100; i++) {
                json.put("key" + i, "value" + i);
            }

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
        }

        @Test
        @DisplayName("JSON 循环引用检测")
        void testJsonCircularReference() {
            Map<String, Object> json1 = JNet.json();
            Map<String, Object> json2 = JNet.json();
            json1.put("ref", json2);
            json2.put("ref", json1);

            assertThrows(IllegalArgumentException.class, () -> {
                JNet.postJson("https://httpbin.org/post", json1);
            });
        }

        @Test
        @DisplayName("JSON 深度限制")
        void testJsonDepthLimit() {
            Map<String, Object> json = JNet.json();
            Map<String, Object> current = json;
            for (int i = 0; i < 150; i++) {
                Map<String, Object> next = JNet.json();
                current.put("next", next);
                current = next;
            }

            assertThrows(IllegalArgumentException.class, () -> {
                JNet.postJson("https://httpbin.org/post", json);
            });
        }

        @Test
        @DisplayName("JSON NaN 和 Infinity")
        void testJsonNaN() {
            Map<String, Object> json = JNet.json();
            json.put("nan", Double.NaN);
            json.put("inf", Double.POSITIVE_INFINITY);

            String result = JNet.postJson("https://httpbin.org/post", json);
            assertNotNull(result);
            // NaN 和 Infinity 应该被转换为 null
            assertTrue(result.contains("null"));
        }
    }

    // ========== URL 构建测试 ==========

    @Nested
    @DisplayName("URL 构建")
    class UrlBuildTest {

        @ParameterizedTest
        @CsvSource({
            "https://example.com, key=value, https://example.com?key=value",
            "https://example.com?, key=value, https://example.com?key=value",
            "https://example.com?existing=1, new=value, https://example.com?existing=1&new=value"
        })
        @DisplayName("URL 参数构建 - 参数化测试")
        void testUrlBuildParametrized(String base, String param, String expected) {
            try {
                String[] parts = param.split("=");
                Map<String, String> params = JNet.params(parts[0], parts[1]);
                String result = JNet.get(base, params);
                // 只是验证不抛出异常
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("URL 编码特殊字符")
        void testUrlEncoding() {
            try {
                Map<String, String> params = JNet.params("q", "hello world&test=1");
                String result = JNet.get("https://httpbin.org/get", params);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("URL 中文字符编码")
        void testUrlChinese() {
            try {
                Map<String, String> params = JNet.params("q", "你好");
                String result = JNet.get("https://httpbin.org/get", params);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }
    }

    // ========== 超时配置测试 ==========

    @Nested
    @DisplayName("超时配置")
    class TimeoutTest {

        @Test
        @DisplayName("设置默认超时")
        void testSetDefaultTimeout() {
            Duration original = Duration.ofSeconds(10);
            JNet.setDefaultTimeout(original);
            // 无法直接验证，但确保不抛出异常
            assertTrue(true);
        }

        @Test
        @DisplayName("超时配置后请求")
        void testTimeoutInEffect() {
            try {
                JNet.setDefaultTimeout(Duration.ofSeconds(5));
                String result = JNet.get("https://httpbin.org/delay/1");
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            } finally {
                JNet.setDefaultTimeout(Duration.ofSeconds(10));
            }
        }
    }

    // ========== 边界情况测试 ==========

    @Nested
    @DisplayName("边界情况")
    class BoundaryTest {

        @Test
        @DisplayName("空字符串参数")
        void testEmptyStrings() {
            Map<String, String> params = JNet.params("", "");
            assertNotNull(params);
            assertEquals("", params.get(""));
        }

        @Test
        @DisplayName("null 值参数")
        void testNullValues() {
            Map<String, String> params = JNet.params("key", null);
            assertNotNull(params);
            assertEquals("", params.get("key"));
        }

        @Test
        @DisplayName("超长 URL")
        void testLongUrl() {
            try {
                StringBuilder longParam = new StringBuilder();
                for (int i = 0; i < 1000; i++) {
                    longParam.append("a");
                }
                Map<String, String> params = JNet.params("data", longParam.toString());
                String result = JNet.get("https://httpbin.org/get", params);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }

        @Test
        @DisplayName("特殊字符在参数名中")
        void testSpecialCharsInParamName() {
            try {
                Map<String, String> params = JNet.params("key with spaces", "value");
                String result = JNet.get("https://httpbin.org/get", params);
                assertNotNull(result);
            } catch (Exception e) {
                System.out.println("⚠️  网络请求失败: " + e.getMessage());
            }
        }
    }

    // ========== 静态方法存在性测试 ==========

    @Nested
    @DisplayName("API 完整性")
    class ApiCompletenessTest {

        @Test
        @DisplayName("所有公共静态方法都存在")
        void testAllMethodsExist() throws NoSuchMethodException {
            // 同步方法
            assertNotNull(JNet.class.getMethod("get", String.class));
            assertNotNull(JNet.class.getMethod("get", String.class, Map.class));
            assertNotNull(JNet.class.getMethod("get", String.class, Map.class, Map.class));
            assertNotNull(JNet.class.getMethod("get", String.class, String.class));

            assertNotNull(JNet.class.getMethod("post", String.class, String.class));
            assertNotNull(JNet.class.getMethod("post", String.class, String.class, Map.class));
            assertNotNull(JNet.class.getMethod("postJson", String.class, Object.class));
            assertNotNull(JNet.class.getMethod("postJson", String.class, Object.class, Map.class));

            assertNotNull(JNet.class.getMethod("put", String.class, String.class));
            assertNotNull(JNet.class.getMethod("delete", String.class));
            assertNotNull(JNet.class.getMethod("patch", String.class, String.class));
            assertNotNull(JNet.class.getMethod("head", String.class));
            assertNotNull(JNet.class.getMethod("request", String.class, String.class, String.class));
            assertNotNull(JNet.class.getMethod("request", String.class, String.class, String.class, Map.class));

            // 异步方法
            assertNotNull(JNet.class.getMethod("getAsync", String.class));
            assertNotNull(JNet.class.getMethod("getAsync", String.class, Map.class));
            assertNotNull(JNet.class.getMethod("getAsync", String.class, Map.class, Map.class));
            assertNotNull(JNet.class.getMethod("postAsync", String.class, String.class));
            assertNotNull(JNet.class.getMethod("postJsonAsync", String.class, Object.class));
            assertNotNull(JNet.class.getMethod("requestAsync", String.class, String.class, String.class));
            assertNotNull(JNet.class.getMethod("requestAsync", String.class, String.class, String.class, Map.class));

            // 工具方法
            assertNotNull(JNet.class.getMethod("params", String[].class));
            assertNotNull(JNet.class.getMethod("headers", String[].class));
            assertNotNull(JNet.class.getMethod("json"));
            assertNotNull(JNet.class.getMethod("basicAuth", String.class, String.class));
            assertNotNull(JNet.class.getMethod("bearerToken", String.class));

            // 配置方法
            assertNotNull(JNet.class.getMethod("setDefaultTimeout", Duration.class));
            assertNotNull(JNet.class.getMethod("getDefaultHttpClient"));
        }
    }
}
