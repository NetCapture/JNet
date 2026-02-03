package com.jnet.tcp;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable TCP Request
 * Similar to HTTP Request but for raw TCP sockets
 * Thread-safe by design (immutable after build)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpRequest {
    private final String host;
    private final int port;
    private final byte[] data;
    private final String dataAsString;
    private final int timeout; // milliseconds
    private final Map<String, String> metadata;
    private final String tag;
    private final String sessionId; // For persistent sessions

    private TcpRequest(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.data = builder.data;
        this.dataAsString = builder.dataAsString;
        this.timeout = builder.timeout;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.tag = builder.tag;
        this.sessionId = builder.sessionId;
    }

    // ========== Factory Methods ==========

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public String getHost() {
        return host;
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

    public int getTimeout() {
        return timeout;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getTag() {
        return tag;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getSocketAddress() {
        return host + ":" + port;
    }

    public int getDataLength() {
        return data != null ? data.length : 0;
    }

    // ========== Builder ==========

    /**
     * TCP Request Builder
     * Uses Builder pattern for flexible request construction
     */
    public static class Builder {
        private String host;
        private int port;
        private byte[] data;
        private String dataAsString;
        private int timeout = 0;
        private Map<String, String> metadata = new HashMap<>();
        private String tag;
        private String sessionId;

        /**
         * Set target host
         */
        public Builder host(String host) {
            if (host == null || host.isEmpty()) {
                throw new IllegalArgumentException("Host cannot be null or empty");
            }
            this.host = host;
            return this;
        }

        /**
         * Set target port
         */
        public Builder port(int port) {
            if (port < 0 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 0 and 65535");
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
         * Set string data (will be converted to bytes with UTF-8)
         */
        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }

        /**
         * Set operation timeout (milliseconds, 0 = no timeout)
         */
        public Builder timeout(int timeoutMs) {
            this.timeout = timeoutMs;
            return this;
        }

        /**
         * Set timeout using Duration
         */
        public Builder timeout(java.time.Duration timeout) {
            this.timeout = (int) timeout.toMillis();
            return this;
        }

        /**
         * Add metadata
         */
        public Builder metadata(String key, String value) {
            if (key != null && !key.isEmpty()) {
                this.metadata.put(key, value);
            }
            return this;
        }

        /**
         * Set all metadata
         */
        public Builder metadata(Map<String, String> metadata) {
            if (metadata != null) {
                this.metadata.putAll(metadata);
            }
            return this;
        }

        /**
         * Set request tag (for tracking/cancellation)
         */
        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        /**
         * Set session ID (for persistent sessions)
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Build immutable TcpRequest
         */
        public TcpRequest build() {
            if (host == null) {
                throw new IllegalStateException("Host must be set");
            }
            if (port == 0) {
                throw new IllegalStateException("Port must be set");
            }
            return new TcpRequest(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TcpRequest{host='%s:%d', dataLength=%d, timeout=%dms, tag='%s'}",
                host, port, getDataLength(), timeout, tag);
    }
}
