package com.jnet.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 异步执行器
 * 统一管理线程池，避免资源泄露
 *
 * @author sanbo
 * @version 3.0.0
 */
public class AsyncExecutor {
    private static final int CORE_THREADS = Math.max(2, Runtime.getRuntime().availableProcessors() * 2);

    // 使用静态初始化确保线程池只创建一次
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(
            CORE_THREADS,
            r -> {
                Thread t = new Thread(r, "JNet-Async-" + System.identityHashCode(r));
                // 使用非守护线程确保重要任务完成,通过shutdown hook控制超时
                t.setDaemon(false);
                return t;
            });

    // 使用原子布尔确保可见性和原子性
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    // 注册JVM关闭钩子，确保资源释放
    static {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (!SHUTDOWN.get()) {
                gracefulShutdown();
            }
        }, "JNet-Shutdown-Hook"));
    }

    private AsyncExecutor() {
        // 防止实例化
    }

    /**
     * 获取线程池实例
     * 线程安全，无需同步
     */
    public static ExecutorService getExecutor() {
        if (SHUTDOWN.get()) {
            throw new IllegalStateException("AsyncExecutor has been shut down");
        }
        return EXECUTOR;
    }

    /**
     * 优雅关闭线程池
     * 使用CAS确保只执行一次
     */
    public static void shutdown() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            gracefulShutdown();
        }
    }

    /**
     * 内部优雅关闭实现
     */
    private static void gracefulShutdown() {
        try {
            // 停止接受新任务
            EXECUTOR.shutdown();

            // 等待已提交任务完成（最多10秒）
            if (!EXECUTOR.awaitTermination(10, TimeUnit.SECONDS)) {
                // 超时后强制关闭
                EXECUTOR.shutdownNow();

                // 再次等待
                if (!EXECUTOR.awaitTermination(5, TimeUnit.SECONDS)) {
                    System.err.println("JNet AsyncExecutor did not terminate gracefully");
                }
            }
        } catch (InterruptedException e) {
            // 被中断，强制关闭
            EXECUTOR.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * 强制关闭线程池（不等待任务完成）
     */
    public static void shutdownNow() {
        if (SHUTDOWN.compareAndSet(false, true)) {
            EXECUTOR.shutdownNow();
        }
    }

    /**
     * 检查是否已关闭
     */
    public static boolean isShutdown() {
        return SHUTDOWN.get();
    }

    /**
     * 检查线程池状态
     */
    public static boolean isTerminated() {
        return SHUTDOWN.get() && EXECUTOR.isTerminated();
    }
}
