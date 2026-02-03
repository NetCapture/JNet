package com.jnet.udp;

import java.time.Duration;

/**
 * UDP Datagram Configuration
 * Immutable configuration for UDP client (Builder pattern)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class UdpConfig {
    private final Duration timeout;
    private final int sendBufferSize;
    private final int receiveBufferSize;
    private final boolean broadcast;
    private final int timeToLive;
    private final boolean loopbackMode;
    private final int trafficClass;
    private final int multicastTtl;

    private UdpConfig(Builder builder) {
        this.timeout = builder.timeout;
        this.sendBufferSize = builder.sendBufferSize;
        this.receiveBufferSize = builder.receiveBufferSize;
        this.broadcast = builder.broadcast;
        this.timeToLive = builder.timeToLive;
        this.loopbackMode = builder.loopbackMode;
        this.trafficClass = builder.trafficClass;
        this.multicastTtl = builder.multicastTtl;
    }

    // ========== Factory Methods ==========

    /**
     * Default UDP configuration
     */
    public static UdpConfig defaultConfig() {
        return newBuilder().build();
    }

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public Duration getTimeout() {
        return timeout;
    }

    public int getSendBufferSize() {
        return sendBufferSize;
    }

    public int getReceiveBufferSize() {
        return receiveBufferSize;
    }

    public boolean isBroadcast() {
        return broadcast;
    }

    public int getTimeToLive() {
        return timeToLive;
    }

    public boolean isLoopbackMode() {
        return loopbackMode;
    }

    public int getTrafficClass() {
        return trafficClass;
    }

    public int getMulticastTtl() {
        return multicastTtl;
    }

    // ========== Builder ==========

    /**
     * UDP Configuration Builder
     */
    public static class Builder {
        private Duration timeout = Duration.ofSeconds(5);
        private int sendBufferSize = 65536; // Max UDP packet size
        private int receiveBufferSize = 65536;
        private boolean broadcast = false;
        private int timeToLive = 1; // 1 hop by default
        private boolean loopbackMode = false;
        private int trafficClass = 0; // IPTOS_LOWCOST
        private int multicastTtl = 1; // Default TTL

        /**
         * Set receive timeout
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set timeout in milliseconds
         */
        public Builder timeout(int timeoutMs) {
            this.timeout = Duration.ofMillis(timeoutMs);
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
         * Enable broadcast mode
         */
        public Builder broadcast(boolean broadcast) {
            this.broadcast = broadcast;
            return this;
        }

        /**
         Set time to live (hop count for multicast)
         */
        public Builder timeToLive(int ttl) {
            this.timeToLive = ttl;
            return this;
        }

        /**
         * Enable/disable loopback mode
         */
        public Builder loopbackMode(boolean loopback) {
            this.loopbackMode = loopback;
            return this;
        }

        /**
         * Set traffic class (IPTOS)
         * 0=throughput, 1=lowcost, 2=reliability, 3=bulk
         */
        public Builder trafficClass(int trafficClass) {
            this.trafficClass = trafficClass;
            return this;
        }

        /**
         * Common traffic class constants
         */
        public static final int UC_IPTOS_THROUGHPUT = 0;
        public static final int UC_IPTOS_LOWCOST = 1;
        public static final int UC_IPTOS_RELIABILITY = 2;
        public static final int UC_IPTOS_BULK = 3;

        /**
         * Set multicast TTL
         */
        public Builder multicastTtl(int ttl) {
            this.multicastTtl = ttl;
            return this;
        }

        /**
         * Build immutable configuration
         */
        public UdpConfig build() {
            return new UdpConfig(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "UdpConfig{timeout=%s, broadcast=%s, ttl=%d, loopback=%s}",
                timeout, broadcast, timeToLive, loopbackMode);
    }
}
