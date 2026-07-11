package com.jnet.core;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 异步执行器
 * 统一管理线程池，避免资源泄露
 *
 * @author sanbo
 * @version 3.0.0
 */
public class AsyncExecutor {
    private static final int CORE_THREADS = Math.max(2,
            Math.min(32, Runtime.getRuntime().availableProcessors() * 2));
    private static final int MAX_QUEUED_TASKS = CORE_THREADS * 64;
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();

    // 使用静态初始化确保线程池只创建一次
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            CORE_THREADS,
            CORE_THREADS,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(MAX_QUEUED_TASKS),
            r -> {
                Thread t = new Thread(r, "JNet-Async-" + THREAD_IDS.incrementAndGet());
                // SDK worker threads must not keep a host JVM alive on their own.
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.AbortPolicy());

    // 使用原子布尔确保可见性和原子性
    private static final AtomicBoolean SHUTDOWN = new AtomicBoolean(false);

    private AsyncExecutor() {
        // 防止实例化
    }

    /**
     * 获取线程池实例
     * 线程安全，无需同步
     */
    public static ExecutorService getExecutor() {
        if (SHUTDOWN.get() || EXECUTOR.isShutdown()) {
            SHUTDOWN.set(true);
            throw new IllegalStateException("AsyncExecutor has been shut down");
        }
        return EXECUTOR;
    }

    /**
     * Submits work with cancellation that interrupts a running worker, unlike
     * {@link CompletableFuture#supplyAsync(java.util.function.Supplier, java.util.concurrent.Executor)}.
     */
    public static <T> CompletableFuture<T> submit(Callable<T> task) {
        if (task == null) {
            throw new NullPointerException("task");
        }
        InterruptibleFuture<T> result = new InterruptibleFuture<>();
        Future<?> worker = getExecutor().submit(() -> {
            try {
                if (!result.isCancelled()) {
                    result.complete(task.call());
                }
            } catch (Throwable error) {
                if (!result.isCancelled()) {
                    result.completeExceptionally(error);
                }
            } finally {
                result.clearWorker();
            }
        });
        result.attachWorker(worker);
        return result;
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

                EXECUTOR.awaitTermination(5, TimeUnit.SECONDS);
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
        SHUTDOWN.set(true);
        EXECUTOR.shutdownNow();
    }

    /**
     * 检查是否已关闭
     */
    public static boolean isShutdown() {
        return SHUTDOWN.get() || EXECUTOR.isShutdown();
    }

    /**
     * 检查线程池状态
     */
    public static boolean isTerminated() {
        return EXECUTOR.isTerminated();
    }

    private static final class InterruptibleFuture<T> extends CompletableFuture<T> {
        private Future<?> worker;
        private boolean mayInterrupt;

        private synchronized void attachWorker(Future<?> worker) {
            this.worker = worker;
            if (isCancelled()) {
                worker.cancel(mayInterrupt);
            } else if (isDone()) {
                this.worker = null;
            }
        }

        private synchronized void clearWorker() {
            worker = null;
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            Future<?> current;
            synchronized (this) {
                if (!super.cancel(mayInterruptIfRunning)) {
                    return false;
                }
                mayInterrupt = mayInterruptIfRunning;
                current = worker;
                worker = null;
            }
            if (current != null) {
                current.cancel(mayInterruptIfRunning);
            }
            return true;
        }
    }
}
