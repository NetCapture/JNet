package com.jnet.tcp;

import java.time.Duration;

/**
 * TCP Socket Configuration
 * Immutable configuration for TCP client (Builder pattern)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpConfig {
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final boolean keepAlive;
    private final boolean tcpNoDelay;
    private final int sendBufferSize;
    private final int receiveBufferSize;
    private final boolean autoReconnect;
    private final int maxReconnectAttempts;
    private final long reconnectDelay;
    private final int soTimeout;
    private final boolean soReuseAddress;
    private final int trafficClass;
    private final boolean useNio;

    private TcpConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.keepAlive = builder.keepAlive;
        this.tcpNoDelay = builder.tcpNoDelay;
        this.sendBufferSize = builder.sendBufferSize;
        this.receiveBufferSize = builder.receiveBufferSize;
        this.autoReconnect = builder.autoReconnect;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelay = builder.reconnectDelay;
        this.soTimeout = builder.soTimeout;
        this.soReuseAddress = builder.soReuseAddress;
        this.trafficClass = builder.trafficClass;
        this.useNio = builder.useNio;
    }

    // ========== Factory Methods ==========

    /**
     * Default TCP configuration
     */
    public static TcpConfig defaultConfig() {
        return newBuilder().build();
    }

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public Duration getWriteTimeout() {
        return writeTimeout;
    }

    public boolean isKeepAlive() {
        return keepAlive;
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public boolean isAutoReconnect() {
        return autoReconnect;
    }

    public int getMaxReconnectAttempts() {
        return maxReconnectAttempts;
    }

    public long getReconnectDelay() {
        return reconnectDelay;
    }

    public int getSoTimeout() {
        return soTimeout;
    }

    public boolean isSoReuseAddress() {
        return soReuseAddress;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public boolean isUseNio() {
        return useNio;
    }

    // ========== Builder ==========

    /**
     * TCP Configuration Builder
     */
    public static class Builder {
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private boolean keepAlive = true;
        private boolean tcpNoDelay = false;
        private int sendBufferSize = 8192;
        private int receiveBufferSize = 8192;
        private boolean autoReconnect = false;
        private int maxReconnectAttempts = 3;
        private long reconnectDelay = 1000;
        private int soTimeout = 0;
        private boolean soReuseAddress = false;
        private int trafficClass = 0; // IPTOS_THROUGHPUT
        private boolean useNio = false;

        /**
         * Set connection timeout
         */
        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout (SO_RCVTIMEO)
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set write timeout (SO_SNDTIMEO)
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        /**
         * Enable/disable TCP keep-alive
         */
        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        /**
         * Enable/disable TCP_NODELAY (Nagle's algorithm)
         */
        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        /**
         * Set send buffer size
         */
        public Builder sendBufferSize(int size) {
            this.sendBufferSize = size;
            return this;
        }

        /**
         * Set receive buffer size
         */
        public Builder receiveBufferSize(int size) {
            this.receiveBufferSize = size;
            return this;
        }

        /**
         * Enable/disable automatic reconnection
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /**
         * Set maximum reconnection attempts
         */
        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        /**
         * Set reconnection delay (milliseconds)
         */
        public Builder reconnectDelay(long delayMs) {
            this.reconnectDelay = delayMs;
            return this;
        }

        /**
         * Set socket timeout (SO_TIMEOUT, in milliseconds, 0 = infinite)
         */
        public Builder soTimeout(int timeoutMs) {
            this.soTimeout = timeoutMs;
            return this;
        }

        /**
         * Enable/disable address reuse (SO_REUSEADDR)
         */
        public Builder soReuseAddress(boolean reuse) {
            this.soReuseAddress = reuse;
            return this;
        }

        /**
         * Set traffic class (IPTOS)
         * 0=throughput, 1=lowcost, 2=reliability, 3=throughput+reliability, 4=bulk
         */
        public Builder trafficClass(int trafficClass) {
            this.trafficClass = trafficClass;
            return this;
        }

        /**
         * Common traffic class constants
         */
        public static final int TC_IPTOS_THROUGHPUT = 0;
        public static final int TC_IPTOS_LOWCOST = 1;
        public static final int TC_IPTOS_RELIABILITY = 2;
        public static final int TC_IPTOS_THROUGHPUT_RELIABILITY = 3;
        public static final int TC_IPTOS_BULK = 4;

        /**
         * Use NIO (non-blocking) mode
         */
        public Builder useNio(boolean useNio) {
            this.useNio = useNio;
            return this;
        }

        /**
         * Build immutable configuration
         */
        public TcpConfig build() {
            return new TcpConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TcpConfig{connectTimeout=%s, readTimeout=%s, keepAlive=%s, autoReconnect=%s}",
                connectTimeout, readTimeout, keepAlive, autoReconnect);
    }
}
