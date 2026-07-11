package com.jnet.protocol;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
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
    private volatile String dataAsString;
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
        this.bytesRead = builder.bytesReadSet
                ? builder.bytesRead
                : builder.data == null ? 0 : builder.data.length;
        this.successful = builder.successful;
        this.duration = builder.duration;
        this.errorMessage = builder.errorMessage;
        this.statusCode = builder.statusCode;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
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
        return copy(data);
    }

    byte[] dataUnsafe() {
        return data;
    }

    public String getDataAsString() {
        String value = dataAsString;
        if (value == null && data != null) {
            value = new String(data, StandardCharsets.UTF_8);
            dataAsString = value;
        }
        return value;
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
        private boolean bytesReadSet;
        private boolean successful = false;
        private long duration = 0;
        private String errorMessage;
        private int statusCode = 0;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private ProtocolRequest request;

        private Builder() {}

        private Builder(boolean isSuccess) {
            this.successful = isSuccess;
        }

        public Builder data(byte[] data) {
            this.data = copy(data);
            this.dataAsString = null;
            return this;
        }

        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }

        public Builder host(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        public Builder bytesRead(int bytesRead) {
            if (bytesRead < 0) {
                throw new IllegalArgumentException("bytesRead must be non-negative");
            }
            this.bytesRead = bytesRead;
            this.bytesReadSet = true;
            return this;
        }

        public Builder duration(long duration) {
            if (duration < 0) {
                throw new IllegalArgumentException("duration must be non-negative");
            }
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
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    header(entry.getKey(), entry.getValue());
                }
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

    private static byte[] copy(byte[] value) {
        return value == null ? null : Arrays.copyOf(value, value.length);
    }
}
