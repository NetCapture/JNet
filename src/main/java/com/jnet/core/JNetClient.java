package com.jnet.core;

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

    private JNetClient(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;

        HttpClient.Builder clientBuilder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_2)
                .connectTimeout(Duration.ofMillis(builder.connectTimeout))
                .followRedirects(builder.followRedirects ? HttpClient.Redirect.NORMAL : HttpClient.Redirect.NEVER);

        if (builder.proxy != null) {
            clientBuilder.proxy(ProxySelector.of((java.net.InetSocketAddress) builder.proxy.address()));
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
     * 创建GET请求
     */
    public Request.Builder newGet(String url) {
        return new Request.Builder().client(this).url(url).method("GET");
    }

    /**
     * 创建POST请求
     */
    public Request.Builder newPost(String url) {
        return new Request.Builder().client(this).url(url).method("POST");
    }

    /**
     * 创建PUT请求
     */
    public Request.Builder newPut(String url) {
        return new Request.Builder().client(this).url(url).method("PUT");
    }

    /**
     * 创建DELETE请求
     */
    public Request.Builder newDelete(String url) {
        return new Request.Builder().client(this).url(url).method("DELETE");
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
         * 构建客户端实例
         */
        public JNetClient build() {
            return new JNetClient(this);
        }
    }
}
