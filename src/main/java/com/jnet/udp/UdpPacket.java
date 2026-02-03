package com.jnet.udp;

import java.net.DatagramPacket;
import java.net.InetAddress;

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
    private final String dataAsString;
    private final long timestamp;
    private final int ttl; // Time To Live

    private UdpPacket(Builder builder) {
        this.address = builder.address;
        this.port = builder.port;
        this.data = builder.data;
        this.dataAsString = builder.dataAsString;
        this.timestamp = System.currentTimeMillis();
        this.ttl = builder.ttl;
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
        return data;
    }

    public String getDataAsString() {
        return dataAsString;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public int getTtl() {
        return ttl;
    }

    public int getDataLength() {
        return data != null ? data.length : 0;
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
            try {
                this.address = InetAddress.getByName(host);
            } catch (Exception e) {
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
            return this;
        }

        /**
         * Set string data (will be UTF-8 encoded)
         */
        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
            return this;
        }

        /**
         * Set Time To Live (TTL)
         */
        public Builder ttl(int ttl) {
            this.ttl = ttl;
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
