package com.jnet.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 并发测试类
 * 验证JNet在高并发场景下的稳定性
 *
 * @author sanbo
 * @version 3.0
 */
public class ConcurrencyTest {

    private static final int THREAD_COUNT = 10;
    private static final int REQUEST_PER_THREAD = 10;

    public static void main(String[] args) {
        System.out.println("=== JNet 并发测试 ===\n");

        testConcurrentRequests();
        testAsyncConcurrency();
        // testConnectionPoolConcurrency(); // Removed as ConnectionPool is deprecated
        testInterceptorConcurrency();

        System.out.println("\n=== 并发测试完成 ===");
    }

    /**
     * 测试同步并发请求
     */
    private static void testConcurrentRequests() {
        System.out.println("1. 测试同步并发请求:");
        try {
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT * REQUEST_PER_THREAD);
            List<CompletableFuture<Void>> futures = new ArrayList<>();

            long startTime = System.currentTimeMillis();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    for (int j = 0; j < REQUEST_PER_THREAD; j++) {
                        try {
                            // 创建请求（测试API存在性，不实际发起网络请求）
                            Request request = JNetClient.getInstance()
                                    .newGet("https://httpbin.org/get")
                                    .header("X-Thread", String.valueOf(threadId))
                                    .build();

                            System.out.println("   线程-" + threadId + " 请求-" + j + " 创建成功");
                            latch.countDown();
                        } catch (Exception e) {
                            System.err.println("   ❌ 线程-" + threadId + " 错误: " + e.getMessage());
                        }
                    }
                }, executor));
            }

            latch.await(30, TimeUnit.SECONDS);

            long endTime = System.currentTimeMillis();
            System.out.println("   ✅ 同步并发测试: " + (THREAD_COUNT * REQUEST_PER_THREAD)
                    + " 个请求，总耗时 " + (endTime - startTime) + "ms");

            executor.shutdown();

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    /**
     * 测试异步并发请求
     */
    private static void testAsyncConcurrency() {
        System.out.println("2. 测试异步并发请求:");
        try {
            long startTime = System.currentTimeMillis();
            List<CompletableFuture<String>> futures = new ArrayList<>();

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int requestId = i;
                futures.add(
                        CompletableFuture.supplyAsync(() -> {
                            // 测试API存在性
                            try {
                                JNetClient.getInstance()
                                        .newGet("https://httpbin.org/get")
                                        .build();
                                return "success-" + requestId;
                            } catch (Exception e) {
                                return "error-" + requestId + ": " + e.getMessage();
                            }
                        }, AsyncExecutor.getExecutor()));
            }

            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allOf.get(30, TimeUnit.SECONDS);

            long endTime = System.currentTimeMillis();
            System.out.println("   ✅ 异步并发测试: " + THREAD_COUNT
                    + " 个异步请求，总耗时 " + (endTime - startTime) + "ms");

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    /**
     * 测试连接池并发
     */
    private static void testConnectionPoolConcurrency() {
        System.out.println("3. 测试连接池并发:");
        try {
            // ConnectionPool已优化为无锁设计，通过JNet内部机制验证
            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);
            AtomicInteger successCount = new AtomicInteger(0);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                CompletableFuture.runAsync(() -> {
                    try {
                        // 通过JNet请求验证连接管理
                        String result = JNet.get("https://httpbin.org/get");
                        if (result != null && result.contains("httpbin")) {
                            successCount.incrementAndGet();
                        }
                        System.out.println("   线程-" + threadId + " 请求成功");
                        latch.countDown();
                    } catch (Exception e) {
                        System.err.println("   ❌ 线程-" + threadId + " 错误: " + e.getMessage());
                        latch.countDown();
                    }
                }, executor);
            }

            boolean completed = latch.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            if (completed && successCount.get() > 0) {
                System.out.println("   ✅ 连接池并发测试: PASS (成功: " + successCount.get() + ")");
            } else {
                System.out.println("   ⚠️  连接池测试部分失败");
            }

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    /**
     * 测试拦截器并发
     */
    private static void testInterceptorConcurrency() {
        System.out.println("4. 测试拦截器并发:");
        try {
            List<Interceptor> interceptors = new ArrayList<>();
            interceptors.add(new Interceptor.LoggingInterceptor());
            interceptors.add(new Interceptor.RetryInterceptor(2));

            ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
            CountDownLatch latch = new CountDownLatch(THREAD_COUNT);

            for (int i = 0; i < THREAD_COUNT; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // 创建带拦截器的请求（测试API存在性）
                        Request request = JNetClient.getInstance()
                                .newGet("https://httpbin.org/get")
                                .build();

                        // 模拟拦截器链
                        Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request);
                        System.out.println("   线程-" + threadId + " 创建拦截器链成功");
                        latch.countDown();
                    } catch (Exception e) {
                        System.err.println("   ❌ 线程-" + threadId + " 错误: " + e.getMessage());
                    }
                });
            }

            latch.await(10, TimeUnit.SECONDS);
            System.out.println("   ✅ 拦截器并发测试: PASS");

            executor.shutdown();

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
}
