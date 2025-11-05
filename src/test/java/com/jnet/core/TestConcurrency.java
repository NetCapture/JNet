package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 并发测试
 * 验证线程安全性
 *
 * @author sanbo
 * @version 3.0
 */
public class TestConcurrency {

    @Test
    @DisplayName("测试 JNetClient 单例并发访问")
    void testConcurrentClientAccess() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        final boolean[] allPassed = {true};

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // 测试获取单例
                    JNetClient client1 = JNetClient.getInstance();
                    assertNotNull(client1, "客户端不应该为空");

                    // 测试创建请求
                    Request.Builder builder = client1.newGet("https://httpbin.org/get");
                    assertNotNull(builder, "请求构建器不应该为空");

                } catch (Exception e) {
                    allPassed[0] = false;
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有线程应该在超时时间内完成");
        assertTrue(allPassed[0], "所有线程的测试都应该通过");
    }

    @Test
    @DisplayName("测试并发工具类使用")
    void testConcurrentUtils() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        final boolean[] allPassed = {true};

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    // 测试 Base64
                    String text = "Thread-" + index;
                    String encoded = JNetUtils.encodeBase64(text);
                    String decoded = JNetUtils.decodeBase64(encoded);
                    assertEquals(text, decoded, "Base64 编码解码应该正确");

                    // 测试 MD5
                    String md5 = JNetUtils.md5(text);
                    assertNotNull(md5, "MD5 计算应该成功");

                } catch (Exception e) {
                    allPassed[0] = false;
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "所有线程应该在超时时间内完成");
        assertTrue(allPassed[0], "所有线程的测试都应该通过");
    }
}
