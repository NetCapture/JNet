package com.jnet.core;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * 拦截器接口
 * 用于拦截和处理请求/响应
 *
 * @author sanbo
 * @version 3.0.0
 */
public interface Interceptor {

    /**
     * 拦截请求并处理响应
     */
    Response intercept(Chain chain) throws IOException;

    /**
     * 拦截器链
     */
    interface Chain {
        Request request();
        Response proceed(Request request) throws IOException;

        /** Returns whether the owning call has been canceled, if the chain has one. */
        default boolean isCanceled() {
            return false;
        }
    }

    /**
     * 拦截器链实现
     */
    class RealChain implements Chain {
        private final List<Interceptor> interceptors;
        private final int index;
        private final Request request;
        private final Call.RealCall call;

        /**
         * 构造函数 - 用于实际请求执行
         */
        public RealChain(List<Interceptor> interceptors, int index, Request request, Call.RealCall call) {
            this.interceptors = interceptors;
            this.index = index;
            this.request = request;
            this.call = call;
        }

        /**
         * 构造函数 - 用于测试（向后兼容）
         * 注意：测试时需要手动处理 chain.proceed() 的最后一环
         */
        public RealChain(List<Interceptor> interceptors, int index, Request request) {
            this(interceptors, index, request, null);
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public boolean isCanceled() {
            return call != null && call.isCanceled();
        }

        @Override
        public Response proceed(Request request) throws IOException {
            if (index >= interceptors.size()) {
                if (call == null) {
                    // 测试模式：抛出异常让测试知道已经到达链的末端
                    throw new IllegalStateException("No more interceptors - chain ended at index " + index);
                }
                // 实际模式：调用网络请求
                return call.executeNetworkRequest(request);
            }
            RealChain next = new RealChain(interceptors, index + 1, request, call);
            return interceptors.get(index).intercept(next);
        }
    }

    /**
     * 日志拦截器
     */
    class LoggingInterceptor implements Interceptor {
        @Override
        public Response intercept(Chain chain) throws IOException {
            long start = System.currentTimeMillis();
            Request request = chain.request();

            System.out.println("--> " + request.getMethod() + " " + request.getUrlString());

            Response response = chain.proceed(request);

            long duration = System.currentTimeMillis() - start;
            System.out.println("<-- " + response.getCode() + " " + response.getMessage()
                    + " (" + duration + "ms)");

            return response;
        }
    }

    /**
     * 重试拦截器
     */
    class RetryInterceptor implements Interceptor {
        private final int maxRetries;
        private final long delayMs;

        public RetryInterceptor(int maxRetries) {
            this(maxRetries, 1000);
        }

        public RetryInterceptor(int maxRetries, long delayMs) {
            if (maxRetries < 0 || delayMs < 0) {
                throw new IllegalArgumentException("Retry count and delay must be non-negative");
            }
            this.maxRetries = maxRetries;
            this.delayMs = delayMs;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            if (!isIdempotent(request.getMethod())
                    || (request.getBodyPublisher() != null && request.getBody() == null)) {
                return chain.proceed(request);
            }
            IOException lastException = null;

            for (int i = 0; i <= maxRetries; i++) {
                if (chain.isCanceled()) {
                    throw new IOException("Request canceled");
                }
                try {
                    return chain.proceed(request);
                } catch (IOException e) {
                    if (chain.isCanceled()) {
                        throw new IOException("Request canceled", e);
                    }
                    lastException = e;
                    if (i < maxRetries) {
                        sleepBeforeRetry(chain, saturatingMultiply(delayMs, i + 1L));
                    }
                }
            }
            throw lastException;
        }

        private static boolean isIdempotent(String method) {
            return "GET".equals(method) || "HEAD".equals(method) || "PUT".equals(method)
                    || "DELETE".equals(method) || "OPTIONS".equals(method) || "TRACE".equals(method);
        }

        private static long saturatingMultiply(long value, long multiplier) {
            return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
        }

        private static void sleepBeforeRetry(Chain chain, long delay) throws IOException {
            long remaining = delay;
            while (remaining > 0) {
                if (chain.isCanceled()) {
                    throw new IOException("Request canceled");
                }
                long chunk = Math.min(remaining, 100L);
                try {
                    Thread.sleep(chunk);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted during retry", e);
                }
                remaining -= chunk;
            }
        }
    }

    /**
     * 头部拦截器
     */
    class HeaderInterceptor implements Interceptor {
        private final String name;
        private final String value;

        public HeaderInterceptor(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            Request newRequest = request.toBuilder()
                    .header(name, value)
                    .build();
            return chain.proceed(newRequest);
        }
    }

    /**
     * 响应缓存拦截器
     */
    class CacheInterceptor implements Interceptor {
        private final ResponseCache cache;
        private final long maxAge;

        public CacheInterceptor(ResponseCache cache) {
            this(cache, 60_000); // 默认1分钟
        }

        public CacheInterceptor(ResponseCache cache, long maxAge) {
            this.cache = Objects.requireNonNull(cache, "cache");
            if (maxAge < 0) {
                throw new IllegalArgumentException("maxAge must be non-negative");
            }
            this.maxAge = maxAge;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // 只缓存GET请求
            if ("GET".equals(request.getMethod())) {
                Response cached = cache.get(request);
                if (cached != null) {
                    return cached;
                }
            }

            Response response = chain.proceed(request);

            // 缓存成功的GET请求
            if ("GET".equals(request.getMethod()) && response.isOk() && cache != null) {
                cache.put(request, response, maxAge);
            }

            return response;
        }
    }
}
