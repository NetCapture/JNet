package com.jnet.cloudflare;

import com.jnet.core.Interceptor;
import com.jnet.core.Request;
import com.jnet.core.Response;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 请求时序拦截器
 * 模拟人类行为延迟，避免被识别为机器人
 */
public class RequestTimingInterceptor implements Interceptor {
    private final long minDelay;
    private final long maxDelay;
    private final Object timingLock = new Object();
    private long queuedDelayMillis;
    private long lastReservationNanos;
    private boolean hasReservation;

    /**
     * 创建请求时序拦截器
     * @param minDelay 最小延迟（毫秒）
     * @param maxDelay 最大延迟（毫秒）
     */
    public RequestTimingInterceptor(long minDelay, long maxDelay) {
        if (minDelay < 0 || maxDelay < minDelay) {
            throw new IllegalArgumentException("Invalid delay range: [" + minDelay + ", " + maxDelay + "]");
        }
        this.minDelay = minDelay;
        this.maxDelay = maxDelay;
    }

    /**
     * 创建默认时序拦截器（500-2000ms）
     */
    public RequestTimingInterceptor() {
        this(500, 2000);
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        if (chain.isCanceled()) {
            throw new IOException("Request canceled");
        }
        sleep(chain, reserveDelay());
        return chain.proceed(chain.request());
    }

    private long reserveDelay() {
        synchronized (timingLock) {
            long now = System.nanoTime();
            if (!hasReservation) {
                hasReservation = true;
                lastReservationNanos = now;
                queuedDelayMillis = 0;
                return 0;
            }

            long elapsedNanos = now - lastReservationNanos;
            long elapsedMillis = elapsedNanos <= 0 ? 0 : elapsedNanos / 1_000_000L;
            long previousSlotFromNow = queuedDelayMillis - elapsedMillis;
            long delay = calculateDelay();
            long reserved = previousSlotFromNow > Long.MAX_VALUE - delay
                    ? Long.MAX_VALUE : previousSlotFromNow + delay;
            queuedDelayMillis = Math.max(0L, reserved);
            lastReservationNanos = now;
            return queuedDelayMillis;
        }
    }

    /**
     * 计算随机延迟
     */
    private long calculateDelay() {
        if (minDelay == maxDelay) {
            return minDelay;
        }
        if (maxDelay == Long.MAX_VALUE) {
            return ThreadLocalRandom.current().nextLong(minDelay, maxDelay);
        }
        return ThreadLocalRandom.current().nextLong(minDelay, maxDelay + 1);
    }

    private void sleep(Chain chain, long millis) throws IOException {
        long remaining = millis;
        while (remaining > 0) {
            if (chain.isCanceled()) {
                throw new IOException("Request canceled");
            }
            long chunk = Math.min(remaining, 100L);
            try {
                Thread.sleep(chunk);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while applying request timing", e);
            }
            remaining -= chunk;
        }
        if (chain.isCanceled()) {
            throw new IOException("Request canceled");
        }
    }

    /**
     * 重置时序状态
     */
    public void reset() {
        synchronized (timingLock) {
            queuedDelayMillis = 0;
            lastReservationNanos = 0;
            hasReservation = false;
        }
    }
}
