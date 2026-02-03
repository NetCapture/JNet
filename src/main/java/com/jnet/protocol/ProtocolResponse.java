package com.jnet.protocol;

import java.util.HashMap;
import java.util.Map;

/**
 * Generic Protocol Response
 * Generic response for any protocol
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class ProtocolResponse {
    private final byte[] data;
    private final String dataAsString;
    private final String host;
    private final int port;
    private final int bytesRead;
    private final boolean successful;
    private final long duration;
    private final String errorMessage;
    private final int statusCode;
    private final Map<String, String> headers;
    private final ProtocolRequest request;

    private ProtocolResponse(Builder builder) {
        this.data = builder.data;
        this.dataAsString = builder.dataAsString;
        this.host = builder.host;
        this.port = builder.port;
        this.bytesRead = builder.bytesRead;
        this.successful = builder.successful;
        this.duration = builder.duration;
        this.errorMessage = builder.errorMessage;
        this.statusCode = builder.statusCode;
        this.headers = builder.headers;
        this.request = builder.request;
    }

    // ========== Factory ==========

    public static Builder success() {
        return new Builder(true);
    }

    public static Builder failure() {
        return new Builder(false);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public byte[] getData() {
        return data;
    }

    public String getDataAsString() {
        return dataAsString;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public long getDuration() {
        return duration;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public ProtocolRequest getRequest() {
        return request;
    }

    public int getDataLength() {
        return data != null ? data.length : 0;
    }

    public boolean isOk() {
        return successful && errorMessage == null;
    }

    public java.io.ByteArrayInputStream getInputStream() {
        return data != null ? new java.io.ByteArrayInputStream(data) : null;
    }

    // ========== Builder ==========

    public static class Builder {
        private byte[] data;
        private String dataAsString;
        private String host;
        private int port;
        private int bytesRead = 0;
        private boolean successful = false;
        private long duration = 0;
        private String errorMessage;
        private int statusCode = 0;
        private final Map<String, String> headers = new HashMap<>();
        private ProtocolRequest request;

        private Builder() {}

        private Builder(boolean isSuccess) {
            this.successful = isSuccess;
        }

        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null;
            return this;
        }

        public Builder host(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        public Builder bytesRead(int bytesRead) {
            this.bytesRead = bytesRead;
            return this;
        }

        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
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

        public Builder request(ProtocolRequest request) {
            this.request = request;
            return this;
        }

        public ProtocolResponse build() {
            return new ProtocolResponse(this);
        }
    }
}
