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

    /** @deprecated Retained for 3.x compatibility; blocking socket writes are not timed. */
    @Deprecated
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

    /** @deprecated Retained for 3.x compatibility; this client uses blocking sockets. */
    @Deprecated
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
        private int trafficClass = 0;
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
         * Retained for API compatibility; blocking {@code Socket} has no portable write timeout.
         * @deprecated Configure application-level framing and cancellation instead.
         */
        @Deprecated
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
         * Set the raw 8-bit IPv4 TOS / IPv6 traffic-class value (0-255).
         * The operating system may normalize or ignore unsupported bits.
         */
        public Builder trafficClass(int trafficClass) {
            this.trafficClass = trafficClass;
            return this;
        }

        /**
         * Legacy ordinal constants retained because public compile-time constants
         * are inlined into consumer bytecode.
         *
         * @deprecated Use one of the {@code IP_TOS_*} bit values below or pass a
         *             raw 8-bit value to {@link #trafficClass(int)}.
         */
        @Deprecated
        public static final int TC_IPTOS_THROUGHPUT = 0;
        /** @deprecated See {@link #TC_IPTOS_THROUGHPUT}. */
        @Deprecated
        public static final int TC_IPTOS_LOWCOST = 1;
        /** @deprecated See {@link #TC_IPTOS_THROUGHPUT}. */
        @Deprecated
        public static final int TC_IPTOS_RELIABILITY = 2;
        /** @deprecated See {@link #TC_IPTOS_THROUGHPUT}. */
        @Deprecated
        public static final int TC_IPTOS_THROUGHPUT_RELIABILITY = 3;
        /** @deprecated See {@link #TC_IPTOS_THROUGHPUT}. */
        @Deprecated
        public static final int TC_IPTOS_BULK = 4;

        /** Minimize monetary cost (IPTOS_MINCOST). */
        public static final int IP_TOS_LOW_COST = 0x02;
        /** Maximize reliability (IPTOS_RELIABILITY). */
        public static final int IP_TOS_RELIABILITY = 0x04;
        /** Maximize throughput (IPTOS_THROUGHPUT). */
        public static final int IP_TOS_THROUGHPUT = 0x08;
        /** Minimize delay (IPTOS_LOWDELAY). */
        public static final int IP_TOS_LOW_DELAY = 0x10;

        /**
         * Retained for API compatibility; the 3.x client uses blocking sockets only.
         * @deprecated A future transport-specific module will expose NIO explicitly.
         */
        @Deprecated
        public Builder useNio(boolean useNio) {
            this.useNio = useNio;
            return this;
        }

        /**
         * Build immutable configuration
         */
        public TcpConfig build() {
            validateDuration(connectTimeout, "connectTimeout");
            validateDuration(readTimeout, "readTimeout");
            validateDuration(writeTimeout, "writeTimeout");
            if (sendBufferSize <= 0 || receiveBufferSize <= 0) {
                throw new IllegalStateException("Socket buffer sizes must be positive");
            }
            if (maxReconnectAttempts < 0 || reconnectDelay < 0 || soTimeout < 0) {
                throw new IllegalStateException("Reconnect counts, delays and SO_TIMEOUT must be non-negative");
            }
            if (trafficClass < 0 || trafficClass > 255) {
                throw new IllegalStateException("Traffic class must be between 0 and 255");
            }
            return new TcpConfig(this);
        }

        private static void validateDuration(Duration value, String name) {
            if (value == null || value.isNegative()) {
                throw new IllegalStateException(name + " must be non-null and non-negative");
            }
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TcpConfig{connectTimeout=%s, readTimeout=%s, keepAlive=%s, autoReconnect=%s}",
                connectTimeout, readTimeout, keepAlive, autoReconnect);
    }
}
