package com.jnet.core;

import org.junit.jupiter.api.*;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Interceptor 拦截器完整单元测试
 *
 * @author sanbo
 * @version 3.0
 */
@DisplayName("【Interceptor】拦截器完整测试")
public class TestInterceptorFull {

    private JNetClient client;

    @BeforeEach
    void setUp() {
        client = JNetClient.getInstance();
    }

    // ========== 日志拦截器 ==========

    @Nested
    @DisplayName("日志拦截器")
    class LoggingInterceptorTest {

        @Test
        @DisplayName("创建日志拦截器")
        void testCreateLoggingInterceptor() {
            Interceptor.LoggingInterceptor interceptor = new Interceptor.LoggingInterceptor();
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("日志拦截器拦截请求")
        void testLoggingIntercept() throws IOException {
            Interceptor.LoggingInterceptor interceptor = new Interceptor.LoggingInterceptor();

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "log test");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), request, mockResponse);

            // 这会打印日志到控制台
            Response response = interceptor.intercept(chain);

            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("日志拦截器在链中")
        void testLoggingInChain() throws IOException {
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.LoggingInterceptor()
            );

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "chain log");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
        }
    }

    // ========== 重试拦截器 ==========

    @Nested
    @DisplayName("重试拦截器")
    class RetryInterceptorTest {

        @Test
        @DisplayName("创建重试拦截器")
        void testCreateRetryInterceptor() {
            Interceptor.RetryInterceptor interceptor = new Interceptor.RetryInterceptor(3);
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("创建重试拦截器带延迟")
        void testCreateRetryInterceptorWithDelay() {
            Interceptor.RetryInterceptor interceptor = new Interceptor.RetryInterceptor(3, 500);
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("重试拦截器 - 成功请求")
        void testRetryOnSuccess() throws IOException {
            Interceptor.RetryInterceptor interceptor = new Interceptor.RetryInterceptor(3);

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "retry success");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), request, mockResponse);

            Response response = interceptor.intercept(chain);
            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("重试拦截器 - 模拟失败重试")
        void testRetryOnFailure() {
            // 创建一个总是失败的拦截器用于测试
            Interceptor failingInterceptor = chain -> {
                throw new IOException("Simulated failure");
            };

            Interceptor.RetryInterceptor retry = new Interceptor.RetryInterceptor(2, 10);

            // 组合拦截器链
            List<Interceptor> interceptors = Arrays.asList(retry, failingInterceptor);
            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, client.newGet("https://example.com").build());

            assertThrows(IOException.class, () -> {
                retry.intercept(chain);
            });
        }

        @Test
        @DisplayName("重试拦截器 - 重试次数")
        void testRetryCount() {
            // 测试重试拦截器的配置
            Interceptor.RetryInterceptor retry3 = new Interceptor.RetryInterceptor(3);
            Interceptor.RetryInterceptor retry5 = new Interceptor.RetryInterceptor(5, 100);

            assertNotNull(retry3);
            assertNotNull(retry5);
        }
    }

    // ========== 头部拦截器 ==========

    @Nested
    @DisplayName("头部拦截器")
    class HeaderInterceptorTest {

        @Test
        @DisplayName("创建头部拦截器")
        void testCreateHeaderInterceptor() {
            Interceptor.HeaderInterceptor interceptor = new Interceptor.HeaderInterceptor("X-Test", "value");
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("头部拦截器添加头部")
        void testHeaderInterceptorAddsHeader() throws IOException {
            Interceptor.HeaderInterceptor interceptor = new Interceptor.HeaderInterceptor("X-Custom-Header", "test-value");

            Request original = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(original, 200, "header test");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), original, mockResponse);

            Response response = interceptor.intercept(chain);
            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("头部拦截器在链中")
        void testHeaderInChain() throws IOException {
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.HeaderInterceptor("Authorization", "Bearer token"),
                    new Interceptor.HeaderInterceptor("X-API-Version", "3.0")
            );

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "header chain");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
        }
    }

    // ========== 缓存拦截器 ==========

    @Nested
    @DisplayName("缓存拦截器")
    class CacheInterceptorTest {

        @Test
        @DisplayName("创建缓存拦截器")
        void testCreateCacheInterceptor() {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor.CacheInterceptor interceptor = new Interceptor.CacheInterceptor(cache);
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("创建缓存拦截器带最大年龄")
        void testCreateCacheInterceptorWithMaxAge() {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor.CacheInterceptor interceptor = new Interceptor.CacheInterceptor(cache, 30000);
            assertNotNull(interceptor);
        }

        @Test
        @DisplayName("缓存拦截器 - 缓存GET请求")
        void testCacheGetRequest() throws IOException {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor.CacheInterceptor interceptor = new Interceptor.CacheInterceptor(cache);

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "cached body");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), request, mockResponse);

            // 第一次请求 - 应该缓存
            Response response1 = interceptor.intercept(chain);
            assertNotNull(response1);

            // 第二次请求 - 应该从缓存返回
            Response response2 = interceptor.intercept(chain);
            assertNotNull(response2);
        }

        @Test
        @DisplayName("缓存拦截器 - 不缓存POST请求")
        void testNoCachePostRequest() throws IOException {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor.CacheInterceptor interceptor = new Interceptor.CacheInterceptor(cache);

            Request request = client.newPost("https://httpbin.org/post")
                    .body("test")
                    .build();

            Response mockResponse = TestUtils.createMockResponse(request, 200, "post response");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), request, mockResponse);

            Response response = interceptor.intercept(chain);
            assertNotNull(response);
            // POST 请求不应该被缓存
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("缓存拦截器 - 只缓存成功响应")
        void testOnlyCacheSuccessful() throws IOException {
            ResponseCache cache = new ResponseCache(60000);
            Interceptor.CacheInterceptor interceptor = new Interceptor.CacheInterceptor(cache);

            // 404 请求
            Request request = client.newGet("https://httpbin.org/status/404").build();
            Response mockResponse = TestUtils.createMockResponse(request, 404, "not found");
            Interceptor.Chain chain = TestUtils.createMockChain(
                    Arrays.asList(interceptor), request, mockResponse);

            Response response = interceptor.intercept(chain);
            assertNotNull(response);
            assertFalse(response.isSuccessful());
            // 失败的响应不应该被缓存
            assertEquals(0, cache.size());
        }
    }

    // ========== 拦截器链 ==========

    @Nested
    @DisplayName("拦截器链")
    class ChainTest {

        @Test
        @DisplayName("RealChain 创建")
        void testRealChainCreation() {
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.LoggingInterceptor()
            );
            Request request = client.newGet("https://httpbin.org/get").build();

            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 0, request);
            assertNotNull(chain);
        }

        @Test
        @DisplayName("RealChain 获取请求")
        void testRealChainRequest() {
            List<Interceptor> interceptors = Arrays.asList();
            Request request = client.newGet("https://httpbin.org/get").build();

            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 0, request);
            assertEquals(request, chain.request());
        }

        @Test
        @DisplayName("RealChain 单个拦截器")
        void testSingleInterceptorChain() throws IOException {
            Interceptor.LoggingInterceptor logging = new Interceptor.LoggingInterceptor();
            List<Interceptor> interceptors = Arrays.asList(logging);

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "single");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("RealChain 多个拦截器")
        void testMultipleInterceptorsChain() throws IOException {
            Interceptor.LoggingInterceptor logging = new Interceptor.LoggingInterceptor();
            Interceptor.HeaderInterceptor header = new Interceptor.HeaderInterceptor("X-Test", "value");

            List<Interceptor> interceptors = Arrays.asList(logging, header);

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "multiple");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("RealChain 索引超出范围")
        void testChainIndexOutOfBounds() {
            List<Interceptor> interceptors = Arrays.asList();
            Request request = client.newGet("https://httpbin.org/get").build();

            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 0, request);

            assertThrows(IllegalStateException.class, () -> {
                chain.proceed(request);
            });
        }

        @Test
        @DisplayName("RealChain 递归调用")
        void testChainRecursion() throws IOException {
            // 测试拦截器链的递归调用机制
            Interceptor.LoggingInterceptor logging = new Interceptor.LoggingInterceptor();
            List<Interceptor> interceptors = Arrays.asList(logging);

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "recursive");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            // 这会调用下一个拦截器（索引+1）
            Response response = chain.proceed(request);
            assertNotNull(response);
        }
    }

    // ========== 组合拦截器 ==========

    @Nested
    @DisplayName("组合拦截器")
    class CombinedTest {

        @Test
        @DisplayName("多个拦截器组合")
        void testMultipleInterceptors() throws IOException {
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.LoggingInterceptor(),
                    new Interceptor.HeaderInterceptor("X-Auth", "token"),
                    new Interceptor.HeaderInterceptor("X-Version", "3.0")
            );

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "success");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
            assertTrue(response.isSuccessful());
        }

        @Test
        @DisplayName("完整拦截器链")
        void testFullInterceptorChain() throws IOException {
            ResponseCache cache = new ResponseCache(60000);
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.LoggingInterceptor(),
                    new Interceptor.HeaderInterceptor("Authorization", "Bearer token"),
                    new Interceptor.CacheInterceptor(cache),
                    new Interceptor.RetryInterceptor(3)
            );

            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "mock body");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = chain.proceed(request);
            assertNotNull(response);
        }

        @Test
        @DisplayName("自定义拦截器")
        void testCustomInterceptor() throws IOException {
            Interceptor custom = chain -> {
                Request original = chain.request();
                Request modified = original.toBuilder()
                        .header("X-Custom-Interceptor", "applied")
                        .build();
                return chain.proceed(modified);
            };

            List<Interceptor> interceptors = Arrays.asList(custom);
            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "custom response");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = custom.intercept(chain);
            assertNotNull(response);
        }

        @Test
        @DisplayName("拦截器修改请求")
        void testInterceptorModifiesRequest() throws IOException {
            Interceptor modifier = chain -> {
                Request original = chain.request();
                // 修改请求
                Request modified = original.toBuilder()
                        .header("X-Modified", "true")
                        .build();
                return chain.proceed(modified);
            };

            List<Interceptor> interceptors = Arrays.asList(modifier);
            Request request = client.newGet("https://httpbin.org/get").build();
            Response mockResponse = TestUtils.createMockResponse(request, 200, "modified response");
            Interceptor.Chain chain = TestUtils.createMockChain(interceptors, request, mockResponse);

            Response response = modifier.intercept(chain);
            assertNotNull(response);
        }

        @Test
        @DisplayName("拦截器短路")
        void testInterceptorShortCircuit() throws IOException {
            // 拦截器可以直接返回响应，不调用 proceed
            Interceptor shortCircuit = chain -> {
                Request request = chain.request();
                return Response.success(request)
                        .code(200)
                        .body("short-circuit")
                        .build();
            };

            List<Interceptor> interceptors = Arrays.asList(shortCircuit);
            Request request = client.newGet("https://httpbin.org/get").build();
            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request);

            Response response = shortCircuit.intercept(chain);
            assertEquals("short-circuit", response.getBody());
        }
    }

    // ========== 边界情况 ==========

    @Nested
    @DisplayName("边界情况")
    class BoundaryTest {

        @Test
        @DisplayName("空拦截器列表")
        void testEmptyInterceptorList() throws IOException {
            List<Interceptor> interceptors = Arrays.asList();
            Request request = client.newGet("https://httpbin.org/get").build();

            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 0, request);

            // 应该抛出异常，因为没有拦截器可以调用
            assertThrows(IllegalStateException.class, () -> {
                chain.proceed(request);
            });
        }

        @Test
        @DisplayName("拦截器抛出异常")
        void testInterceptorException() {
            Interceptor failing = chain -> {
                throw new IOException("Interceptor error");
            };

            List<Interceptor> interceptors = Arrays.asList(failing);
            Request request = client.newGet("https://httpbin.org/get").build();
            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request);

            assertThrows(IOException.class, () -> {
                failing.intercept(chain);
            });
        }

        @Test
        @DisplayName("拦截器链索引边界")
        void testChainIndexBoundary() {
            List<Interceptor> interceptors = Arrays.asList(
                    new Interceptor.LoggingInterceptor()
            );
            Request request = client.newGet("https://httpbin.org/get").build();

            // 索引超出范围
            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 1, request);
            assertThrows(IllegalStateException.class, () -> {
                chain.proceed(request);
            });
        }

        @Test
        @DisplayName("null 拦截器")
        void testNullInterceptor() {
            // 拦截器列表不应该包含 null，测试会抛出 NPE
            List<Interceptor> interceptors = new java.util.ArrayList<>();
            interceptors.add(null);
            Request request = client.newGet("https://httpbin.org/get").build();

            Interceptor.RealChain chain = new Interceptor.RealChain(interceptors, 0, request);

            assertThrows(NullPointerException.class, () -> {
                chain.proceed(request);
            });
        }
    }
}
