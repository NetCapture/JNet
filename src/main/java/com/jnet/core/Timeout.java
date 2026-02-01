package com.jnet.core;

import java.time.Duration;

/**
 * 超时配置类
 * 支持细粒度的超时控制：连接超时、读超时、写超时
 */
public class Timeout {
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final Duration totalTimeout;

    private Timeout(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.totalTimeout = builder.totalTimeout;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 创建默认超时配置 (30秒)
     */
    public static Timeout defaultTimeout() {
        return newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .readTimeout(Duration.ofSeconds(30))
                .writeTimeout(Duration.ofSeconds(30))
                .build();
    }

    /**
     * 创建无限超时配置
     */
    public static Timeout infinite() {
        return newBuilder()
                .connectTimeout(Duration.ZERO)
                .readTimeout(Duration.ZERO)
                .writeTimeout(Duration.ZERO)
                .build();
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public Duration getTotalTimeout() {
        return totalTimeout;
    }

    public static class Builder {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(30);
        private Duration totalTimeout = Duration.ZERO; // 0 = no total timeout

        /**
         * 设置连接超时
         */
        public Builder connectTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("Connect timeout must be non-negative");
            }
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * 设置读超时
         */
        public Builder readTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("Read timeout must be non-negative");
            }
            this.readTimeout = timeout;
            return this;
        }

        /**
         * 设置写超时
         */
        public Builder writeTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("Write timeout must be non-negative");
            }
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * 设置总超时（整个请求-响应周期）
         */
        public Builder totalTimeout(Duration timeout) {
            if (timeout == null || timeout.isNegative()) {
                throw new IllegalArgumentException("Total timeout must be non-negative");
            }
            this.totalTimeout = timeout;
            return this;
        }

        /**
         * 设置所有超时为相同值
         */
        public Builder allTimeouts(Duration timeout) {
            connectTimeout(timeout);
            readTimeout(timeout);
            writeTimeout(timeout);
            return this;
        }

        public Timeout build() {
            return new Timeout(this);
        }
    }

    @Override
    public String toString() {
        return String.format("Timeout{connect=%s, read=%s, write=%s, total=%s}",
                connectTimeout, readTimeout, writeTimeout, totalTimeout);
    }
}
