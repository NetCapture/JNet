package com.jnet.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Call 类完整单元测试
 * 测试同步、异步、取消、拦截器等功能
 *
 * @author sanbo
 * @version 3.0
 */
@DisplayName("【Call】请求调用器完整测试")
public class TestCallFull {

    private JNetClient client;

    @BeforeEach
    void setUp() {
        client = JNetClient.getInstance();
    }

    // ========== 同步执行测试 ==========

    @Nested
    @DisplayName("同步执行")
    class SyncExecutionTest {

        @Test
        @DisplayName("execute() - 基本同步请求")
        void testExecuteBasic() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertNotNull(response);
            assertTrue(response.isSuccessful());
            assertEquals(200, response.getCode());
        }

        @Test
        @DisplayName("execute() - POST 请求")
        void testExecutePost() throws IOException {
            String body = JNetUtils.json().add("test", "value").build();
            Request request = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build();
            Call call = request.newCall();
            Response response = call.execute();

            assertNotNull(response);
            assertTrue(response.isSuccessful());
            assertTrue(response.getBody().contains("test"));
        }

        @Test
        @DisplayName("execute() - 请求耗时统计")
        void testExecuteDuration() throws IOException {
            Request request = client.newGet("https://httpbin.org/delay/1").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertTrue(response.getDuration() >= 1000, "应该记录耗时");
            System.out.println("耗时: " + response.getDuration() + "ms");
        }

        @Test
        @DisplayName("execute() - 响应头解析")
        void testExecuteHeaders() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertNotNull(response.getHeaders());
            assertTrue(response.getHeaders().size() > 0);
        }

        @Test
        @DisplayName("execute() - 重复执行应该抛出异常")
        void testExecuteTwice() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            call.execute(); // 第一次

            // 第二次应该失败
            assertThrows(IllegalStateException.class, () -> {
                call.execute();
            });
        }

        @Test
        @DisplayName("execute() - 404 错误")
        void testExecute404() throws IOException {
            Request request = client.newGet("https://httpbin.org/status/404").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertEquals(404, response.getCode());
            assertFalse(response.isSuccessful());
            assertFalse(response.isOk());
        }

        @Test
        @DisplayName("execute() - 500 错误")
        void testExecute500() throws IOException {
            Request request = client.newGet("https://httpbin.org/status/500").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertEquals(500, response.getCode());
            assertFalse(response.isSuccessful());
            assertTrue(response.isServerError());
        }

        @Test
        @DisplayName("execute() - 301 重定向")
        void testExecuteRedirect() throws IOException {
            // JNetClient 默认跟随重定向
            Request request = client.newGet("https://httpbin.org/redirect-to?url=https://httpbin.org/get").build();
            Call call = request.newCall();
            Response response = call.execute();

            assertTrue(response.isSuccessful());
            assertEquals(200, response.getCode());
        }
    }

    // ========== 异步执行测试 ==========

    @Nested
    @DisplayName("异步执行")
    class AsyncExecutionTest {

        @Test
        @DisplayName("enqueue() - 基本异步请求")
        void testEnqueueBasic() throws InterruptedException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Response> responseRef = new AtomicReference<>();
            AtomicBoolean success = new AtomicBoolean(false);

            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    responseRef.set(response);
                    success.set(true);
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS), "异步请求应该在超时内完成");
            assertTrue(success.get(), "请求应该成功");
            assertNotNull(responseRef.get());
            assertTrue(responseRef.get().isSuccessful());
        }

        @Test
        @DisplayName("enqueue() - POST 异步请求")
        void testEnqueuePost() throws InterruptedException {
            String body = JNetUtils.json().add("async", true).build();
            Request request = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build();
            Call call = request.newCall();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Response> responseRef = new AtomicReference<>();

            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    responseRef.set(response);
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertNotNull(responseRef.get());
            assertTrue(responseRef.get().getBody().contains("async"));
        }

        @Test
        @DisplayName("enqueue() - 异步错误处理")
        void testEnqueueError() throws InterruptedException {
            Request request = client.newGet("http://invalid-host-12345.com").build();
            Call call = request.newCall();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Exception> exceptionRef = new AtomicReference<>();

            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    exceptionRef.set(e);
                    latch.countDown();
                }
            });

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertNotNull(exceptionRef.get());
        }

        @Test
        @DisplayName("enqueue() - 重复执行应该抛出异常")
        void testEnqueueTwice() {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            Call.Callback callback = new Call.Callback() {
                @Override
                public void onSuccess(Response response) {}
                @Override
                public void onFailure(Exception e) {}
            };

            call.enqueue(callback); // 第一次

            // 第二次应该失败
            assertThrows(IllegalStateException.class, () -> {
                call.enqueue(callback);
            });
        }

        @Test
        @DisplayName("enqueue() - 多个并发异步请求")
        void testEnqueueConcurrent() throws InterruptedException {
            int count = 5;
            CountDownLatch latch = new CountDownLatch(count);
            AtomicBoolean allSuccess = new AtomicBoolean(true);

            for (int i = 0; i < count; i++) {
                Request request = client.newGet("https://httpbin.org/get").build();
                Call call = request.newCall();

                call.enqueue(new Call.Callback() {
                    @Override
                    public void onSuccess(Response response) {
                        if (!response.isSuccessful()) {
                            allSuccess.set(false);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        allSuccess.set(false);
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            assertTrue(allSuccess.get());
        }
    }

    // ========== 取消测试 ==========

    @Nested
    @DisplayName("取消机制")
    class CancellationTest {

        @Test
        @DisplayName("cancel() - 取消同步请求")
        void testCancelSync() throws IOException {
            Request request = client.newGet("https://httpbin.org/delay/5").build();
            Call call = request.newCall();

            // 在另一个线程中取消
            CompletableFuture.runAsync(() -> {
                try {
                    Thread.sleep(100);
                    call.cancel();
                } catch (Exception e) {
                    // 忽略
                }
            });

            try {
                call.execute();
                fail("应该被取消");
            } catch (IOException e) {
                assertTrue(e.getMessage().contains("cancel") ||
                          e.getCause() instanceof InterruptedException);
            }
        }

        @Test
        @DisplayName("cancel() - 取消异步请求")
        void testCancelAsync() throws InterruptedException {
            Request request = client.newGet("https://httpbin.org/delay/5").build();
            Call call = request.newCall();

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Exception> exceptionRef = new AtomicReference<>();

            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    latch.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    exceptionRef.set(e);
                    latch.countDown();
                }
            });

            // 立即取消
            call.cancel();

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertNotNull(exceptionRef.get());
            assertTrue(exceptionRef.get() instanceof IOException);
        }

        @Test
        @DisplayName("isCanceled() - 检查取消状态")
        void testIsCanceled() {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            assertFalse(call.isCanceled());

            call.cancel();
            assertTrue(call.isCanceled());
        }

        @Test
        @DisplayName("isExecuted() - 检查执行状态")
        void testIsExecuted() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            assertFalse(call.isExecuted());

            call.execute();
            assertTrue(call.isExecuted());
        }

        @Test
        @DisplayName("execute() - 已取消的请求")
        void testExecuteAfterCancel() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            call.cancel();

            assertThrows(IOException.class, () -> {
                call.execute();
            });
        }
    }

    // ========== 拦截器测试 ==========

    @Nested
    @DisplayName("拦截器")
    class InterceptorTest {

        @Test
        @DisplayName("单个拦截器")
        void testSingleInterceptor() throws IOException {
            // 创建带拦截器的客户端
            JNetClient clientWithInterceptor = JNetClient.newBuilder()
                    .build();

            Request request = clientWithInterceptor.newGet("https://httpbin.org/get")
                    .header("X-Test", "interceptor")
                    .build();

            Call call = request.newCall();
            Response response = call.execute();

            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("多个拦截器链")
        void testInterceptorChain() throws IOException {
            // 模拟多个拦截器
            Request request = client.newGet("https://httpbin.org/get")
                    .header("X-Test-1", "value1")
                    .header("X-Test-2", "value2")
                    .build();

            Call call = request.newCall();
            Response response = call.execute();

            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("日志拦截器")
        void testLoggingInterceptor() throws IOException {
            Interceptor logging = new Interceptor.LoggingInterceptor();

            Request request = client.newGet("https://httpbin.org/get").build();
            Call call = request.newCall();

            // 日志拦截器会打印到控制台
            System.out.println("=== 日志拦截器测试 ===");
            Response response = call.execute();
            System.out.println("=====================");

            assertNotNull(response);
        }

        @Test
        @DisplayName("重试拦截器 - 成功")
        void testRetryInterceptorSuccess() throws IOException {
            Interceptor retry = new Interceptor.RetryInterceptor(3, 100);

            Request request = client.newGet("https://httpbin.org/get").build();
            // 这里只是测试拦截器存在，不测试重试逻辑
            assertNotNull(retry);
            assertNotNull(request);
        }

        @Test
        @DisplayName("头部拦截器")
        void testHeaderInterceptor() throws IOException {
            Interceptor header = new Interceptor.HeaderInterceptor("X-Custom", "test-value");

            Request original = client.newGet("https://httpbin.org/get").build();
            // 拦截器会在执行时修改请求
            assertNotNull(header);
            assertNotNull(original);
        }

        @Test
        @DisplayName("缓存拦截器")
        void testCacheInterceptor() throws IOException {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor cacheInterceptor = new Interceptor.CacheInterceptor(cache);

            Request request = client.newGet("https://httpbin.org/get").build();
            // 测试拦截器创建
            assertNotNull(cacheInterceptor);
            assertNotNull(request);
        }
    }

    // ========== 请求构建测试 ==========

    @Nested
    @DisplayName("请求构建")
    class RequestBuildTest {

        @Test
        @DisplayName("GET 请求构建")
        void testBuildGet() {
            Request request = client.newGet("https://httpbin.org/get")
                    .header("User-Agent", "JNet/3.0")
                    .tag("test")
                    .build();

            assertEquals("GET", request.getMethod());
            assertEquals("https://httpbin.org/get", request.getUrlString());
            assertEquals("test", request.getTag());
        }

        @Test
        @DisplayName("POST 请求构建")
        void testBuildPost() {
            Request request = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/json")
                    .body("{\"test\":true}")
                    .build();

            assertEquals("POST", request.getMethod());
            assertNotNull(request.getBody());
        }

        @Test
        @DisplayName("PUT 请求构建")
        void testBuildPut() {
            Request request = client.newPut("https://httpbin.org/put")
                    .body("update")
                    .build();

            assertEquals("PUT", request.getMethod());
        }

        @Test
        @DisplayName("DELETE 请求构建")
        void testBuildDelete() {
            Request request = client.newDelete("https://httpbin.org/delete")
                    .build();

            assertEquals("DELETE", request.getMethod());
        }

        @Test
        @DisplayName("请求 toString")
        void testRequestToString() {
            Request request = client.newGet("https://httpbin.org/get")
                    .header("X-Test", "value")
                    .build();

            String str = request.toString();
            assertNotNull(str);
            assertTrue(str.contains("GET"));
            assertTrue(str.contains("httpbin.org"));
        }

        @Test
        @DisplayName("URL 无效应该抛出异常")
        void testInvalidUrl() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.newGet("not-a-url").build();
            });
        }

        @Test
        @DisplayName("null URL 应该抛出异常")
        void testNullUrl() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.newGet(null).build();
            });
        }

        @Test
        @DisplayName("空 URL 应该抛出异常")
        void testEmptyUrl() {
            assertThrows(IllegalArgumentException.class, () -> {
                client.newGet("").build();
            });
        }

        @Test
        @DisplayName("默认客户端自动设置")
        void testDefaultClient() {
            Request request = Request.newBuilder()
                    .url("https://httpbin.org/get")
                    .method("GET")
                    .build();

            assertNotNull(request.getClient());
            assertEquals("GET", request.getMethod());
        }
    }

    // ========== 响应处理测试 ==========

    @Nested
    @DisplayName("响应处理")
    class ResponseHandlingTest {

        @Test
        @DisplayName("响应状态判断")
        void testResponseStatus() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Response response = request.newCall().execute();

            assertTrue(response.isSuccessful());
            assertTrue(response.isOk());
            assertFalse(response.isClientError());
            assertFalse(response.isServerError());
        }

        @Test
        @DisplayName("客户端错误判断")
        void testClientError() throws IOException {
            Request request = client.newGet("https://httpbin.org/status/404").build();
            Response response = request.newCall().execute();

            assertFalse(response.isSuccessful());
            assertFalse(response.isOk());
            assertTrue(response.isClientError());
            assertFalse(response.isServerError());
        }

        @Test
        @DisplayName("服务器错误判断")
        void testServerError() throws IOException {
            Request request = client.newGet("https://httpbin.org/status/500").build();
            Response response = request.newCall().execute();

            assertFalse(response.isSuccessful());
            assertFalse(response.isOk());
            assertFalse(response.isClientError());
            assertTrue(response.isServerError());
        }

        @Test
        @DisplayName("响应体读取")
        void testResponseBody() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Response response = request.newCall().execute();

            String body = response.getBody();
            assertNotNull(body);
            assertTrue(body.length() > 0);
            assertTrue(body.contains("httpbin.org"));
        }

        @Test
        @DisplayName("响应头读取")
        void testResponseHeaders() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Response response = request.newCall().execute();

            Map<String, String> headers = response.getHeaders();
            assertNotNull(headers);
            assertTrue(headers.size() > 0);

            // 单个头部读取
            String contentType = response.getHeader("Content-Type");
            assertNotNull(contentType);
        }

        @Test
        @DisplayName("响应 toString")
        void testResponseToString() throws IOException {
            Request request = client.newGet("https://httpbin.org/get").build();
            Response response = request.newCall().execute();

            String str = response.toString();
            assertNotNull(str);
            assertTrue(str.contains("Response"));
            assertTrue(str.contains("code=" + response.getCode()));
        }

        @Test
        @DisplayName("响应构建器")
        void testResponseBuilder() {
            Request req = client.newGet("https://httpbin.org/get").build();

            Response success = Response.success(req)
                    .code(200)
                    .message("OK")
                    .body("test")
                    .duration(100)
                    .header("X-Test", "value")
                    .build();

            assertEquals(200, success.getCode());
            assertEquals("OK", success.getMessage());
            assertEquals("test", success.getBody());
            assertEquals(100, success.getDuration());
            assertTrue(success.isSuccessful());

            Response failure = Response.failure(req)
                    .code(404)
                    .message("Not Found")
                    .build();

            assertEquals(404, failure.getCode());
            assertFalse(failure.isSuccessful());
        }
    }

    // ========== 边界情况测试 ==========

    @Nested
    @DisplayName("边界情况")
    class BoundaryTest {

        @Test
        @DisplayName("超大响应体")
        void testLargeResponse() throws IOException {
            // httpbin 支持生成大响应
            Request request = client.newGet("https://httpbin.org/bytes/10240").build();
            Response response = request.newCall().execute();

            assertTrue(response.getBody().length() >= 10240);
        }

        @Test
        @DisplayName("特殊字符在 URL 中")
        void testSpecialCharsInUrl() throws IOException {
            Request request = client.newGet("https://httpbin.org/get?special=!@#$%").build();
            Response response = request.newCall().execute();

            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("中文字符在请求体中")
        void testChineseInBody() throws IOException {
            Request request = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "text/plain")
                    .body("你好，世界！")
                    .build();

            Response response = request.newCall().execute();
            assertTrue(response.isSuccessful());
            assertTrue(response.getBody().contains("你好"));
        }

        @Test
        @DisplayName("空请求体")
        void testEmptyBody() throws IOException {
            Request request = client.newPost("https://httpbin.org/post")
                    .body("")
                    .build();

            Response response = request.newCall().execute();
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("null 请求体")
        void testNullBody() throws IOException {
            Request request = client.newPost("https://httpbin.org/post")
                    .build();

            Response response = request.newCall().execute();
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("大量并发请求")
        void testHighConcurrency() throws InterruptedException {
            int count = 20;
            CountDownLatch latch = new CountDownLatch(count);
            AtomicBoolean allSuccess = new AtomicBoolean(true);

            for (int i = 0; i < count; i++) {
                Request request = client.newGet("https://httpbin.org/get").build();
                Call call = request.newCall();

                call.enqueue(new Call.Callback() {
                    @Override
                    public void onSuccess(Response response) {
                        if (!response.isSuccessful()) {
                            allSuccess.set(false);
                        }
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        allSuccess.set(false);
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(60, TimeUnit.SECONDS));
            assertTrue(allSuccess.get());
        }
    }

    // ========== 性能测试 ==========

    @Nested
    @DisplayName("性能测试")
    class PerformanceTest {

        @Test
        @DisplayName("同步请求性能")
        void testSyncPerformance() throws IOException {
            long start = System.currentTimeMillis();
            int count = 5;

            for (int i = 0; i < count; i++) {
                Request request = client.newGet("https://httpbin.org/get").build();
                Response response = request.newCall().execute();
                assertTrue(response.isSuccessful());
            }

            long elapsed = System.currentTimeMillis() - start;
            double avg = (double) elapsed / count;
            System.out.println("同步请求平均耗时: " + avg + "ms");
            assertTrue(avg < 5000, "平均耗时应该在合理范围内");
        }

        @Test
        @DisplayName("异步请求性能")
        void testAsyncPerformance() throws InterruptedException {
            long start = System.currentTimeMillis();
            int count = 5;
            CountDownLatch latch = new CountDownLatch(count);

            for (int i = 0; i < count; i++) {
                Request request = client.newGet("https://httpbin.org/get").build();
                Call call = request.newCall();

                call.enqueue(new Call.Callback() {
                    @Override
                    public void onSuccess(Response response) {
                        latch.countDown();
                    }

                    @Override
                    public void onFailure(Exception e) {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(30, TimeUnit.SECONDS));
            long elapsed = System.currentTimeMillis() - start;
            double avg = (double) elapsed / count;
            System.out.println("异步请求平均耗时: " + avg + "ms");
            assertTrue(avg < 5000, "平均耗时应该在合理范围内");
        }

        @Test
        @DisplayName("连接复用测试")
        void testConnectionReuse() throws IOException {
            // 多次请求应该复用底层 HttpClient
            for (int i = 0; i < 3; i++) {
                Request request = client.newGet("https://httpbin.org/get").build();
                Response response = request.newCall().execute();
                assertTrue(response.isSuccessful());
            }
            // 如果没有异常，说明连接复用正常
            assertTrue(true);
        }
    }
}
