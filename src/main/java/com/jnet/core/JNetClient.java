package com.jnet.core;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.ProxySelector;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * JNet客户端 - 单例模式
 * 全局HTTP客户端配置和请求调度器
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class JNetClient {
    private static final int DEFAULT_TIMEOUT = 10_000; // 10秒
    private static volatile JNetClient instance;

    private final HttpClient httpClient;
    private final int connectTimeout; // 保留供SSEClient等可能需要的地方查看
    private final int readTimeout;
    private final com.jnet.auth.Auth auth; // 默认认证

    private JNetClient(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.auth = builder.auth;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(builder.connectTimeout))
                .followRedirects(builder.followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER)
                .cookieHandler(builder.cookieHandler);

        if (builder.proxy != null) {
            // 验证代理类型并配置
            if (builder.proxy.type() == java.net.Proxy.Type.HTTP ||
                    builder.proxy.type() == java.net.Proxy.Type.SOCKS) {
                // HTTP 或 SOCKS 代理 - 验证地址类型
                if (builder.proxy.address() instanceof java.net.InetSocketAddress) {
                    clientBuilder.proxy(new JNetProxySelector(builder.proxy));
                } else {
                    throw new IllegalArgumentException(
                            "Proxy address must be InetSocketAddress, got: " +
                                    (builder.proxy.address() != null ? builder.proxy.address().getClass().getName()
                                            : "null"));
                }
            } else if (builder.proxy.type() == java.net.Proxy.Type.DIRECT) {
                // DIRECT 类型表示不使用代理,忽略
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
     * 获取底层的JDK HttpClient
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

    /**
     * 客户端配置构建器
     */
    public static class Builder {
        private int connectTimeout = DEFAULT_TIMEOUT;
        private int readTimeout = DEFAULT_TIMEOUT;
        private java.net.Proxy proxy;
        private boolean followRedirects = true;
        private com.jnet.auth.Auth auth;
        // 默认启用Cookie管理 (类似 Python requests.Session)
        private CookieHandler cookieHandler = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);

        /**
         * 设置连接超时时间
         */
        public Builder connectTimeout(int timeout, TimeUnit unit) {
            this.connectTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        /**
         * 设置读取超时时间
         * 注意：JDK HttpClient的connectTimeout是连接超时，
         * request timeout是在Request级别设置的，或者全局无默认读取超时。
         * JNetClient这里保留字段用于Request构建时默认设置。
         */
        public Builder readTimeout(int timeout, TimeUnit unit) {
            this.readTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        /**
         * 设置写入超时时间 (已废弃，JDK HttpClient自动管理)
         */
        public Builder writeTimeout(int timeout, TimeUnit unit) {
            // this.writeTimeout = (int) unit.toMillis(timeout);
            return this;
        }

        /**
         * 设置代理
         */
        public Builder proxy(java.net.Proxy proxy) {
            this.proxy = proxy;
            return this;
        }

        /**
         * 设置是否跟随重定向
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
         * 设置Cookie处理器
         * @param cookieHandler 自定义Cookie处理器, 传null禁用Cookie
         */
        public Builder cookieHandler(CookieHandler cookieHandler) {
            this.cookieHandler = cookieHandler;
            return this;
        }

        /**
         * 构建客户端实例
         */
        public JNetClient build() {
            return new JNetClient(this);
        }
    }
}
