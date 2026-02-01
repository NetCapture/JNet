package com.jnet.cloudflare;

import com.jnet.core.Interceptor;
import com.jnet.core.Request;
import com.jnet.core.Response;

import java.io.IOException;
import java.util.Random;

/**
 * 请求时序拦截器
 * 模拟人类行为延迟，避免被识别为机器人
 */
public class RequestTimingInterceptor implements Interceptor {
    private final Random random = new Random();
    private final long minDelay;
    private final long maxDelay;
    private volatile long lastRequestTime = 0;

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
        // 计算需要延迟的时间
        long currentTime = System.currentTimeMillis();
        long timeSinceLastRequest = currentTime - lastRequestTime;
        
        long delay = calculateDelay();
        
        // 如果距离上次请求时间过短，额外延迟
        if (lastRequestTime > 0 && timeSinceLastRequest < delay) {
            long additionalDelay = delay - timeSinceLastRequest;
            sleep(additionalDelay);
        } else if (lastRequestTime > 0) {
            // 即使时间够了，也随机增加一点延迟
            sleep(random.nextInt((int)(delay / 2)));
        }

        lastRequestTime = System.currentTimeMillis();
        return chain.proceed(chain.request());
    }

    /**
     * 计算随机延迟
     */
    private long calculateDelay() {
        if (minDelay == maxDelay) {
            return minDelay;
        }
        return minDelay + random.nextLong() % (maxDelay - minDelay);
    }

    private void sleep(long millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Sleep interrupted", e);
        }
    }

    /**
     * 重置时序状态
     */
    public void reset() {
        this.lastRequestTime = 0;
    }
}
