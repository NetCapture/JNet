package com.jnet.core;

import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JNet客户端 - 单例模式
 * 全局HTTP客户端配置和请求调度器
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class JNetClient {
    private static final int DEFAULT_TIMEOUT = 10_000; // 10秒
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 64 * 1024 * 1024;
    private static final int DEFAULT_MAX_REDIRECTS = 10;
    private static volatile JNetClient instance;

    private final HttpClient httpClient;
    private final int connectTimeout; // 保留供SSEClient等可能需要的地方查看
    private final int readTimeout;
    private final int writeTimeout;
    private final int maxResponseBytes;
    private final java.net.Proxy proxy;
    private final boolean followRedirects;
    private final com.jnet.auth.Auth auth; // 默认认证
    private final List<Interceptor> interceptors;

    private JNetClient(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.maxResponseBytes = builder.maxResponseBytes;
        this.proxy = builder.proxy;
        this.followRedirects = builder.followRedirects;
        this.auth = builder.auth;
        this.interceptors = Collections.unmodifiableList(new ArrayList<>(builder.interceptors));

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(builder.connectTimeout))
                // Redirects are handled by JNet so credentials can be removed on origin changes.
                .followRedirects(HttpClient.Redirect.NEVER)
                .executor(builder.executor != null ? builder.executor : HttpExecutorHolder.EXECUTOR);

        if (builder.cookieHandler != null) {
            clientBuilder.cookieHandler(builder.cookieHandler);
        }

        if (builder.proxy != null) {
            // 验证代理类型并配置
            if (builder.proxy.type() == java.net.Proxy.Type.HTTP) {
                // JDK HttpClient only supports HTTP proxies.
                if (builder.proxy.address() instanceof java.net.InetSocketAddress) {
                    clientBuilder.proxy(new JNetProxySelector(builder.proxy));
                } else {
                    throw new IllegalArgumentException(
                            "Proxy address must be InetSocketAddress, got: " +
                                    (builder.proxy.address() != null ? builder.proxy.address().getClass().getName()
                                            : "null"));
                }
            } else if (builder.proxy.type() == java.net.Proxy.Type.SOCKS) {
                throw new IllegalArgumentException(
                        "SOCKS proxies are not supported by the JDK HttpClient transport");
            } else if (builder.proxy.type() == java.net.Proxy.Type.DIRECT) {
                // Explicit DIRECT must override any JVM-wide default ProxySelector.
                clientBuilder.proxy(new JNetProxySelector(java.net.Proxy.NO_PROXY));
            } else {
                throw new IllegalArgumentException("Unsupported proxy type: " + builder.proxy.type());
            }
        }

        this.httpClient = clientBuilder.build();
    }

    /**
     * 获取单例实例
     */
    public static JNetClient getInstance() {
        if (instance == null) {
            synchronized (JNetClient.class) {
                if (instance == null) {
                    instance = new Builder().build();
                }
            }
        }
        return instance;
    }

    /**
     * 创建新客户端实例
     */
    public static JNetClient create() {
        return new Builder().build();
    }

    /**
     * 创建 Builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 获取底层的JDK HttpClient。
     * 该传输层固定不自动重定向；需要安全重定向处理时请通过 Request/Call 执行。
     */
    public HttpClient getHttpClient() {
        return httpClient;
    }

    /**
     * 获取默认认证
     */
    public com.jnet.auth.Auth getAuth() {
        return auth;
    }

    public List<Interceptor> getInterceptors() {
        return interceptors;
    }

    /**
     * 创建GET请求
     */
    public Request.Builder newGet(String url) {
        return new Request.Builder().client(this).url(url).method("GET").auth(auth);
    }

    /**
     * 创建POST请求
     */
    public Request.Builder newPost(String url) {
        return new Request.Builder().client(this).url(url).method("POST").auth(auth);
    }

    /**
     * 创建PUT请求
     */
    public Request.Builder newPut(String url) {
        return new Request.Builder().client(this).url(url).method("PUT").auth(auth);
    }

    /**
     * 创建DELETE请求
     */
    public Request.Builder newDelete(String url) {
        return new Request.Builder().client(this).url(url).method("DELETE").auth(auth);
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    /** @deprecated JDK HttpClient does not expose a write timeout. */
    @Deprecated
    public int getWriteTimeout() {
        return writeTimeout;
    }

    public int getMaxResponseBytes() {
        return maxResponseBytes;
    }

    public java.net.Proxy getProxy() {
        return proxy;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    int getMaxRedirects() {
        return DEFAULT_MAX_REDIRECTS;
    }

    /**
     * 客户端配置构建器
     */
    public static class Builder {
        private int connectTimeout = DEFAULT_TIMEOUT;
        private int readTimeout = DEFAULT_TIMEOUT;
        private int writeTimeout = DEFAULT_TIMEOUT;
        private int maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES;
        private java.net.Proxy proxy;
        private boolean followRedirects = true;
        private com.jnet.auth.Auth auth;
        private final List<Interceptor> interceptors = new ArrayList<>();
        // Stateless by default. Stateful sessions must opt in with cookieHandler(...).
        private CookieHandler cookieHandler;
        private Executor executor;

        /**
         * 设置连接超时时间
         */
        public Builder connectTimeout(int timeout, TimeUnit unit) {
            this.connectTimeout = checkedTimeoutMillis(timeout, unit, false, "Connect timeout");
            return this;
        }

        /**
         * 设置读取超时时间
         * 注意：JDK HttpClient的connectTimeout是连接超时，
         * request timeout是在Request级别设置的，或者全局无默认读取超时。
         * JNetClient这里保留字段用于Request构建时默认设置。
         */
        public Builder readTimeout(int timeout, TimeUnit unit) {
            this.readTimeout = checkedTimeoutMillis(timeout, unit, true, "Read timeout");
            return this;
        }

        /**
         * 设置写入超时时间 (已废弃，JDK HttpClient自动管理)
         * @deprecated JDK HttpClient does not expose a write timeout.
         */
        @Deprecated
        public Builder writeTimeout(int timeout, TimeUnit unit) {
            this.writeTimeout = checkedTimeoutMillis(timeout, unit, true, "Write timeout");
            return this;
        }

        /** Sets the maximum response body aggregated in memory by ordinary calls. */
        public Builder maxResponseBytes(int maxResponseBytes) {
            if (maxResponseBytes <= 0) {
                throw new IllegalArgumentException("Maximum response size must be positive");
            }
            this.maxResponseBytes = maxResponseBytes;
            return this;
        }

        private static int checkedTimeoutMillis(int timeout, TimeUnit unit, boolean allowZero, String name) {
            if (unit == null) {
                throw new IllegalArgumentException("Time unit cannot be null");
            }
            if (timeout < 0 || (!allowZero && timeout == 0)) {
                throw new IllegalArgumentException(name + (allowZero ? " must be non-negative" : " must be positive"));
            }
            long millis = unit.toMillis(timeout);
            if ((timeout > 0 && millis == 0) || millis > Integer.MAX_VALUE) {
                throw new IllegalArgumentException(name + " is outside the supported millisecond range");
            }
            return (int) millis;
        }

        /**
         * 设置代理
         */
        public Builder proxy(java.net.Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * 设置 Request/Call 是否跟随重定向。底层 JDK HttpClient 始终为 NEVER，
         * 以便 JNet 在跨源跳转时移除敏感请求头。
         */
        public Builder followRedirects(boolean follow) {
            this.followRedirects = follow;
            return this;
        }

        /**
         * 设置默认认证
         */
        public Builder auth(com.jnet.auth.Auth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * 设置Cookie处理器。客户端默认无状态；需要会话 Cookie 时必须显式传入。
         * @param cookieHandler 自定义Cookie处理器，传null保持无状态
         */
        public Builder cookieHandler(CookieHandler cookieHandler) {
            this.cookieHandler = cookieHandler;
            return this;
        }

        /**
         * Uses a caller-owned executor for JDK HTTP transport work. When omitted,
         * all JNet clients share a bounded daemon executor owned by the library.
         */
        public Builder executor(Executor executor) {
            this.executor = Objects.requireNonNull(executor, "executor");
            return this;
        }

        public Builder addInterceptor(Interceptor interceptor) {
            if (interceptor != null) {
                this.interceptors.add(interceptor);
            }
            return this;
        }

        public Builder interceptors(List<Interceptor> interceptors) {
            this.interceptors.clear();
            if (interceptors != null) {
                for (Interceptor interceptor : interceptors) {
                    addInterceptor(interceptor);
                }
            }
            return this;
        }

        /**
         * 构建客户端实例
         */
        public JNetClient build() {
            return new JNetClient(this);
        }
    }

    private static final class HttpExecutorHolder {
        private static final int THREADS = Math.max(2,
                Math.min(32, Runtime.getRuntime().availableProcessors() * 2));
        private static final int QUEUED_TASKS = THREADS * 256;
        private static final AtomicInteger THREAD_IDS = new AtomicInteger();
        private static final ThreadPoolExecutor EXECUTOR = createExecutor();

        private static ThreadPoolExecutor createExecutor() {
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    THREADS,
                    THREADS,
                    30L,
                    TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(QUEUED_TASKS),
                    task -> {
                        Thread thread = new Thread(task,
                                "JNet-Http-" + THREAD_IDS.incrementAndGet());
                        thread.setDaemon(true);
                        return thread;
                    },
                    new ThreadPoolExecutor.AbortPolicy());
            executor.allowCoreThreadTimeOut(true);
            return executor;
        }
    }
}
