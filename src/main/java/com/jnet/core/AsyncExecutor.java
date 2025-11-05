package com.jnet.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步执行器
 * 统一管理线程池，避免资源泄露
 *
 * @author sanbo
 * @version 3.0.0
 */
public class AsyncExecutor {
    private static final int CORE_THREADS = Runtime.getRuntime().availableProcessors() * 2;
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(CORE_THREADS);

    private static volatile boolean shutdown = false;

    private AsyncExecutor() {
        // 防止实例化
    }

    /**
     * 获取线程池实例
     */
    public static ExecutorService getExecutor() {
        if (shutdown) {
            synchronized (AsyncExecutor.class) {
                if (shutdown) {
                    // 重新创建线程池
                    shutdown = false;
                }
            }
        }
        return EXECUTOR;
    }

    /**
     * 关闭线程池
     */
    public static void shutdown() {
        synchronized (AsyncExecutor.class) {
            if (!shutdown) {
                shutdown = true;
                EXECUTOR.shutdown();
                try {
                    if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                        EXECUTOR.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    EXECUTOR.shutdownNow();
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    /**
     * 检查是否已关闭
     */
    public static boolean isShutdown() {
        synchronized (AsyncExecutor.class) {
            return shutdown;
        }
    }
}
