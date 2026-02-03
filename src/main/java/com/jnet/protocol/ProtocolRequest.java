package com.jnet.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Protocol Request
 * Generic request for any protocol
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class ProtocolRequest {
    private final String host;
    private final int port;
    private final byte[] data;
    private final TransportType transport;
    private final Map<String, String> headers;
    private final String method;
    private final Map<String, String> metadata;

    public enum TransportType {
        TCP,
        UDP
    }

    private ProtocolRequest(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.data = builder.data;
        this.transport = builder.transport;
        this.headers = builder.headers;
        this.method = builder.method;
        this.metadata = builder.metadata;
    }

    // ========== Factory ==========

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

    public TransportType getTransport() {
        return transport;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getMethod() {
        return method;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public String getDataAsString() {
        return data != null ? new String(data, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    public int getDataLength() {
        return data != null ? data.length : 0;
    }

    // ========== Builder ==========

    public static class Builder {
        private String host;
        private int port;
        private byte[] data;
        private TransportType transport = TransportType.TCP;
        private final Map<String, String> headers = new HashMap<>();
        private String method = "GET";
        private final Map<String, String> metadata = new HashMap<>();

        public Builder host(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder data(String data) {
            this.data = data != null ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
            return this;
        }

        public Builder transport(TransportType transport) {
            this.transport = transport;
            return this;
        }

        public Builder header(String key, String value) {
            if (key != null && !key.isEmpty()) {
                this.headers.put(key, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder method(String method) {
            this.method = method;
            return this;
        }

        public Builder metadata(String key, String value) {
            if (key != null && !key.isEmpty()) {
                this.metadata.put(key, value);
            }
            return this;
        }

        public ProtocolRequest build() {
            if (host == null) {
                throw new IllegalStateException("Host must be set");
            }
            return new ProtocolRequest(this);
        }
    }
}
