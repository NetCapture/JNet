package com.jnet.udp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

/**
 * UDP Datagram Wrapper
 * Immutable wrapper for UDP datagram packets
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class UdpPacket {
    private final InetAddress address;
    private final int port;
    private final byte[] data;
    private volatile String dataAsString;
    private final long timestamp;
    private final int ttl; // Time To Live
    private final boolean ttlExplicit;

    private UdpPacket(Builder builder) {
        this.address = builder.address;
        this.port = builder.port;
        this.data = builder.data != null ? builder.data.clone() : new byte[0];
        this.dataAsString = builder.dataAsString;
        this.timestamp = System.currentTimeMillis();
        this.ttl = builder.ttl;
        this.ttlExplicit = builder.ttlExplicit;
    }

    // ========== Factory Methods ==========

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public InetAddress getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public byte[] getData() {
        return data.clone();
    }

    public String getDataAsString() {
        String value = dataAsString;
        if (value == null) {
            value = new String(data, StandardCharsets.UTF_8);
            dataAsString = value;
        }
        return value;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getTtl() {
        return ttl;
    }

    public int getDataLength() {
        return data.length;
    }

    byte[] dataUnsafe() {
        return data;
    }

    boolean hasExplicitTtl() {
        return ttlExplicit;
    }

    /**
     * Get socket address as string
     */
    public String getSocketAddress() {
        return address.getHostAddress() + ":" + port;
    }

    // ========== Builder ==========

    /**
     * UDP Packet Builder
     */
    public static class Builder {
        private InetAddress address;
        private int port;
        private byte[] data;
        private String dataAsString;
        private int ttl = 1;
        private boolean ttlExplicit;

        /**
         * Set target address
         */
        public Builder address(InetAddress address) {
            this.address = address;
            return this;
        }

        /**
         * Set target address and port
         */
        public Builder address(InetAddress address, int port) {
            this.address = address;
            this.port = port;
            return this;
        }
        /**
         * Set target host and port
         */
        public Builder address(String host, int port) {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalArgumentException("Host cannot be null or empty");
            }
            try {
                this.address = InetAddress.getByName(host);
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Invalid host: " + host, e);
            }
            this.port = port;
            return this;
        }

        /**
         * Set binary data
         */
        public Builder data(byte[] data) {
            this.data = data;
            this.dataAsString = null;
            return this;
        }

        /**
         * Set string data (will be UTF-8 encoded)
         */
        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }

        /**
         * Set Time To Live (TTL)
         */
        public Builder ttl(int ttl) {
            if (ttl < 0 || ttl > 255) {
                throw new IllegalArgumentException("TTL must be between 0 and 255");
            }
            this.ttl = ttl;
            this.ttlExplicit = true;
            return this;
        }

        /**
         * Build immutable UdpPacket
         */
        public UdpPacket build() {
            if (address == null) {
                throw new IllegalStateException("Address must be set");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalStateException("Port must be between 1 and 65535");
            }
            return new UdpPacket(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "UdpPacket{address='%s:%d', dataLength=%d, ttl=%d}",
                address.getHostAddress(), port, getDataLength(), ttl);
    }
}
