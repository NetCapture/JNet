package com.jnet.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ResponseCache 完整单元测试
 *
 * @author sanbo
 * @version 3.0
 */
@DisplayName("【ResponseCache】响应缓存完整测试")
public class TestResponseCacheFull {

    private JNetClient client;

    @BeforeEach
    void setUp() {
        client = JNetClient.getInstance();
    }

    // ========== 基本功能 ==========

    @Nested
    @DisplayName("基本功能")
    class BasicFunctionalityTest {

        @Test
        @DisplayName("创建缓存")
        void testCreateCache() {
            ResponseCache cache = new ResponseCache();
            assertNotNull(cache);
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("创建缓存带TTL")
        void testCreateCacheWithTTL() {
            ResponseCache cache = new ResponseCache(60000);
            assertNotNull(cache);
        }

        @Test
        @DisplayName("缓存存取")
        void testPutAndGet() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request)
                    .code(200)
                    .body("cached data")
                    .build();

            cache.put(request, response);
            assertEquals(1, cache.size());

            Response cached = cache.get(request);
            assertNotNull(cached);
            assertEquals("cached data", cached.getBody());
        }

        @Test
        @DisplayName("缓存不存在返回null")
        void testGetNonExistent() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();
            Response cached = cache.get(request);

            assertNull(cached);
        }

        @Test
        @DisplayName("缓存覆盖")
        void testCacheOverwrite() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();

            Response response1 = Response.success(request).code(200).body("data1").build();
            Response response2 = Response.success(request).code(200).body("data2").build();

            cache.put(request, response1);
            cache.put(request, response2);

            Response cached = cache.get(request);
            assertEquals("data2", cached.getBody());
        }
    }

    // ========== 缓存键生成 ==========

    @Nested
    @DisplayName("缓存键生成")
    class CacheKeyTest {

        @Test
        @DisplayName("相同请求生成相同键")
        void testSameKey() {
            ResponseCache cache = new ResponseCache(60000);

            Request req1 = client.newGet("https://example.com/test").build();
            Request req2 = client.newGet("https://example.com/test").build();

            Response resp1 = Response.success(req1).code(200).body("data").build();

            cache.put(req1, resp1);

            Response cached = cache.get(req2);
            assertNotNull(cached);
        }

        @Test
        @DisplayName("不同URL生成不同键")
        void testDifferentUrl() {
            ResponseCache cache = new ResponseCache(60000);

            Request req1 = client.newGet("https://example.com/test1").build();
            Request req2 = client.newGet("https://example.com/test2").build();

            Response resp1 = Response.success(req1).code(200).body("data1").build();
            Response resp2 = Response.success(req2).code(200).body("data2").build();

            cache.put(req1, resp1);
            cache.put(req2, resp2);

            assertEquals(2, cache.size());
            assertNotNull(cache.get(req1));
            assertNotNull(cache.get(req2));
        }

        @Test
        @DisplayName("不同HTTP方法生成不同键")
        void testDifferentMethod() {
            ResponseCache cache = new ResponseCache(60000);

            Request getReq = client.newGet("https://example.com/test").build();
            Request postReq = client.newPost("https://example.com/test").build();

            Response getResp = Response.success(getReq).code(200).body("get").build();
            Response postResp = Response.success(postReq).code(200).body("post").build();

            cache.put(getReq, getResp);
            cache.put(postReq, postResp);

            assertEquals(2, cache.size());
        }

        @Test
        @DisplayName("不同请求体生成不同键")
        void testDifferentBody() {
            ResponseCache cache = new ResponseCache(60000);

            Request req1 = client.newPost("https://example.com/test").body("data1").build();
            Request req2 = client.newPost("https://example.com/test").body("data2").build();

            Response resp1 = Response.success(req1).code(200).body("response1").build();
            Response resp2 = Response.success(req2).code(200).body("response2").build();

            cache.put(req1, resp1);
            cache.put(req2, resp2);

            assertEquals(2, cache.size());
        }

        @Test
        @DisplayName("null 请求体")
        void testNullBody() {
            ResponseCache cache = new ResponseCache(60000);

            Request req1 = client.newGet("https://example.com/test").build();
            Request req2 = client.newGet("https://example.com/test").body(null).build();

            Response resp1 = Response.success(req1).code(200).body("data").build();

            cache.put(req1, resp1);

            Response cached = cache.get(req2);
            assertNotNull(cached);
        }
    }

    // ========== TTL 过期 ==========

    @Nested
    @DisplayName("TTL过期")
    class TTLTest {

        @Test
        @DisplayName("缓存未过期")
        void testNotExpired() throws InterruptedException {
            ResponseCache cache = new ResponseCache(1000); // 1秒

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            Thread.sleep(500); // 等待500ms
            Response cached = cache.get(request);

            assertNotNull(cached);
            assertEquals("data", cached.getBody());
        }

        @Test
        @DisplayName("缓存已过期")
        void testExpired() throws InterruptedException {
            ResponseCache cache = new ResponseCache(500); // 500ms

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            Thread.sleep(600); // 等待600ms
            Response cached = cache.get(request);

            assertNull(cached);
        }

        @Test
        @DisplayName("不同TTL")
        void testDifferentTTL() throws InterruptedException {
            ResponseCache cache1 = new ResponseCache(100);
            ResponseCache cache2 = new ResponseCache(10000);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache1.put(request, response);
            cache2.put(request, response);

            Thread.sleep(200);

            assertNull(cache1.get(request));
            assertNotNull(cache2.get(request));
        }

        @ParameterizedTest
        @CsvSource({
            "100, 50, true",
            "100, 150, false",
            "1000, 900, true",
            "1000, 1100, false"
        })
        @DisplayName("TTL参数化测试")
        void testTTLParametrized(long ttl, long wait, boolean shouldExist) throws InterruptedException {
            ResponseCache cache = new ResponseCache(ttl);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);
            Thread.sleep(wait);

            Response cached = cache.get(request);

            if (shouldExist) {
                assertNotNull(cached);
            } else {
                assertNull(cached);
            }
        }
    }

    // ========== 清理操作 ==========

    @Nested
    @DisplayName("清理操作")
    class CleanupTest {

        @Test
        @DisplayName("清除缓存")
        void testClear() {
            ResponseCache cache = new ResponseCache(60000);

            for (int i = 0; i < 5; i++) {
                Request request = client.newGet("https://example.com/test" + i).build();
                Response response = Response.success(request).code(200).body("data" + i).build();
                cache.put(request, response);
            }

            assertEquals(5, cache.size());

            cache.clear();
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("清除过期条目")
        void testCleanup() throws InterruptedException {
            ResponseCache cache = new ResponseCache(500);

            // 添加3个条目
            Request req1 = client.newGet("https://example.com/1").build();
            Request req2 = client.newGet("https://example.com/2").build();
            Request req3 = client.newGet("https://example.com/3").build();

            cache.put(req1, Response.success(req1).code(200).body("1").build());
            Thread.sleep(200);
            cache.put(req2, Response.success(req2).code(200).body("2").build());
            Thread.sleep(200);
            cache.put(req3, Response.success(req3).code(200).body("3").build());

            // 现在req1和req2应该过期，req3未过期
            assertEquals(3, cache.size());

            cache.cleanup();

            // 只有req3应该保留
            assertEquals(1, cache.size());
            assertNull(cache.get(req1));
            assertNull(cache.get(req2));
            assertNotNull(cache.get(req3));
        }

        @Test
        @DisplayName("清除空缓存")
        void testCleanupEmpty() {
            ResponseCache cache = new ResponseCache(60000);
            cache.cleanup(); // 不应该抛出异常
            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("清除后缓存大小")
        void testSizeAfterClear() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);
            assertEquals(1, cache.size());

            cache.clear();
            assertEquals(0, cache.size());
        }
    }

    // ========== 并发测试 ==========

    @Nested
    @DisplayName("并发测试")
    class ConcurrencyTest {

        @Test
        @DisplayName("多线程读写")
        void testConcurrentReadWrite() throws InterruptedException {
            ResponseCache cache = new ResponseCache(60000);
            int threadCount = 10;
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                new Thread(() -> {
                    try {
                        Request request = client.newGet("https://example.com/" + index).build();
                        Response response = Response.success(request).code(200).body("data" + index).build();

                        // 写入
                        cache.put(request, response);

                        // 读取
                        Response cached = cache.get(request);
                        assertNotNull(cached);

                    } finally {
                        latch.countDown();
                    }
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(threadCount, cache.size());
        }

        @Test
        @DisplayName("并发读取")
        void testConcurrentRead() throws InterruptedException {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();
            cache.put(request, response);

            int readerCount = 10;
            CountDownLatch latch = new CountDownLatch(readerCount);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < readerCount; i++) {
                new Thread(() -> {
                    Response cached = cache.get(request);
                    if (cached != null && "data".equals(cached.getBody())) {
                        successCount.incrementAndGet();
                    }
                    latch.countDown();
                }).start();
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS));
            assertEquals(readerCount, successCount.get());
        }

        @Test
        @DisplayName("并发清除")
        void testConcurrentCleanup() throws InterruptedException {
            ResponseCache cache = new ResponseCache(100);

            // 填充缓存
            for (int i = 0; i < 10; i++) {
                Request request = client.newGet("https://example.com/" + i).build();
                Response response = Response.success(request).code(200).body("data").build();
                cache.put(request, response);
            }

            Thread.sleep(200); // 等待过期

            // 并发清理和读取
            Thread cleanupThread = new Thread(() -> cache.cleanup());
            Thread readThread = new Thread(() -> {
                for (int i = 0; i < 10; i++) {
                    Request request = client.newGet("https://example.com/" + i).build();
                    cache.get(request);
                }
            });

            cleanupThread.start();
            readThread.start();

            cleanupThread.join();
            readThread.join();

            // 清理后应该为空
            assertEquals(0, cache.size());
        }
    }

    // ========== 边界情况 ==========

    @Nested
    @DisplayName("边界情况")
    class BoundaryTest {

        @Test
        @DisplayName("零TTL")
        void testZeroTTL() {
            ResponseCache cache = new ResponseCache(0);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            // 立即过期
            assertNull(cache.get(request));
        }

        @Test
        @DisplayName("负TTL")
        void testNegativeTTL() {
            ResponseCache cache = new ResponseCache(-100);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            // 应该立即过期
            assertNull(cache.get(request));
        }

        @Test
        @DisplayName("极大TTL")
        void testLargeTTL() {
            ResponseCache cache = new ResponseCache(Long.MAX_VALUE);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            // 不应该过期
            assertNotNull(cache.get(request));
        }

        @Test
        @DisplayName("相同URL不同实例")
        void testSameUrlDifferentInstances() {
            ResponseCache cache = new ResponseCache(60000);

            Request req1 = client.newGet("https://example.com/test").build();
            Request req2 = client.newGet("https://example.com/test").build();

            Response resp1 = Response.success(req1).code(200).body("data1").build();
            Response resp2 = Response.success(req2).code(200).body("data2").build();

            cache.put(req1, resp1);
            cache.put(req2, resp2);

            // 应该覆盖
            assertEquals(1, cache.size());
            Response cached = cache.get(req1);
            assertEquals("data2", cached.getBody());
        }

        @Test
        @DisplayName("超长URL")
        void testLongUrl() {
            ResponseCache cache = new ResponseCache(60000);

            StringBuilder longUrl = new StringBuilder("https://example.com/");
            for (int i = 0; i < 1000; i++) {
                longUrl.append("a");
            }

            Request request = client.newGet(longUrl.toString()).build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);
            Response cached = cache.get(request);

            assertNotNull(cached);
        }

        @Test
        @DisplayName("特殊字符URL")
        void testSpecialCharsUrl() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test?q=hello%20world&special=!@#$%").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);
            Response cached = cache.get(request);

            assertNotNull(cached);
        }

        @Test
        @DisplayName("大响应体")
        void testLargeResponse() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();

            StringBuilder largeBody = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                largeBody.append("x");
            }

            Response response = Response.success(request)
                    .code(200)
                    .body(largeBody.toString())
                    .build();

            cache.put(request, response);
            Response cached = cache.get(request);

            assertNotNull(cached);
            assertEquals(10000, cached.getBody().length());
        }

        @Test
        @DisplayName("多次清理")
        void testMultipleCleanup() {
            ResponseCache cache = new ResponseCache(60000);

            cache.cleanup();
            cache.cleanup();
            cache.cleanup();

            assertEquals(0, cache.size());
        }

        @Test
        @DisplayName("多次清除")
        void testMultipleClear() {
            ResponseCache cache = new ResponseCache(60000);

            Request request = client.newGet("https://example.com/test").build();
            Response response = Response.success(request).code(200).body("data").build();

            cache.put(request, response);

            cache.clear();
            cache.clear();
            cache.clear();

            assertEquals(0, cache.size());
        }
    }

    // ========== 性能测试 ==========

    @Nested
    @DisplayName("性能测试")
    class PerformanceTest {

        @Test
        @DisplayName("大量缓存条目")
        void testManyEntries() {
            ResponseCache cache = new ResponseCache(60000);

            long start = System.currentTimeMillis();

            for (int i = 0; i < 1000; i++) {
                Request request = client.newGet("https://example.com/" + i).build();
                Response response = Response.success(request).code(200).body("data" + i).build();
                cache.put(request, response);
            }

            long putTime = System.currentTimeMillis() - start;

            start = System.currentTimeMillis();

            for (int i = 0; i < 1000; i++) {
                Request request = client.newGet("https://example.com/" + i).build();
                Response cached = cache.get(request);
                assertNotNull(cached);
            }

            long getTime = System.currentTimeMillis() - start;

            System.out.println("Put 1000 entries: " + putTime + "ms");
            System.out.println("Get 1000 entries: " + getTime + "ms");

            assertTrue(putTime < 1000, "Put 应该在1秒内完成");
            assertTrue(getTime < 1000, "Get 应该在1秒内完成");
        }

        @Test
        @DisplayName("缓存命中率")
        void testHitRate() {
            ResponseCache cache = new ResponseCache(60000);

            // 填充缓存
            for (int i = 0; i < 100; i++) {
                Request request = client.newGet("https://example.com/" + i).build();
                Response response = Response.success(request).code(200).body("data").build();
                cache.put(request, response);
            }

            int hits = 0;
            int total = 1000;

            for (int i = 0; i < total; i++) {
                int index = i % 100; // 循环访问
                Request request = client.newGet("https://example.com/" + index).build();
                if (cache.get(request) != null) {
                    hits++;
                }
            }

            double hitRate = (double) hits / total * 100;
            System.out.println("Hit rate: " + hitRate + "%");

            assertTrue(hitRate > 90, "命中率应该很高");
        }

        @Test
        @DisplayName("清理性能")
        void testCleanupPerformance() throws InterruptedException {
            ResponseCache cache = new ResponseCache(100);

            // 填充100个条目
            for (int i = 0; i < 100; i++) {
                Request request = client.newGet("https://example.com/" + i).build();
                Response response = Response.success(request).code(200).body("data").build();
                cache.put(request, response);
            }

            Thread.sleep(200); // 等待过期

            long start = System.currentTimeMillis();
            cache.cleanup();
            long elapsed = System.currentTimeMillis() - start;

            System.out.println("Cleanup 100 entries: " + elapsed + "ms");
            assertTrue(elapsed < 100, "清理应该很快");
        }
    }
}
