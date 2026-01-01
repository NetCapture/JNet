package com.jnet.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
            this.maxRetries = maxRetries;
            this.delayMs = delayMs;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();
            IOException lastException = null;

            for (int i = 0; i <= maxRetries; i++) {
                try {
                    return chain.proceed(request);
                } catch (IOException e) {
                    lastException = e;
                    if (i < maxRetries) {
                        try {
                            Thread.sleep(delayMs * (i + 1));
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            throw new IOException("Interrupted during retry", ie);
                        }
                    }
                }
            }
            throw lastException;
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
            Request newRequest = Request.newBuilder()
                    .client(request.getClient())
                    .url(request.getUrlString())
                    .method(request.getMethod())
                    .headers(request.getHeaders())
                    .header(name, value)
                    .body(request.getBody())
                    .tag(request.getTag())
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
            this.cache = cache;
            this.maxAge = maxAge;
        }

        @Override
        public Response intercept(Chain chain) throws IOException {
            Request request = chain.request();

            // 只缓存GET请求
            if ("GET".equals(request.getMethod())) {
                Response cached = cache.get(request);
                if (cached != null && !isExpired(cached)) {
                    return cached;
                }
            }

            Response response = chain.proceed(request);

            // 缓存成功的GET请求
            if ("GET".equals(request.getMethod()) && response.isOk() && cache != null) {
                cache.put(request, response);
            }

            return response;
        }

        private boolean isExpired(Response response) {
            // 简单实现，可以根据Cache-Control头判断
            return false;
        }
    }
}
