package com.jnet.tcp;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Immutable TCP Response
 * Thread-safe by design (immutable after build)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpResponse {
    private final byte[] data;
    private final String dataAsString;
    private final String host;
    private final int port;
    private final int bytesRead;
    private final boolean successful;
    private final long duration;
    private final String errorMessage;
    private final int errorCode;
    private final Map<String, String> metadata;
    private final TcpRequest request;

    private TcpResponse(Builder builder) {
        this.data = builder.data;
        this.dataAsString = builder.dataAsString;
        this.host = builder.host;
        this.port = builder.port;
        this.bytesRead = builder.bytesRead;
        this.successful = builder.successful;
        this.duration = builder.duration;
        this.errorMessage = builder.errorMessage;
        this.errorCode = builder.errorCode;
        this.metadata = Collections.unmodifiableMap(new HashMap<>(builder.metadata));
        this.request = builder.request;
    }

    // ========== Factory Methods ==========

    /**
     * Create a successful response builder
     */
    public static Builder success() {
        return new Builder(false);
    }

    /**
     * Create a failure response builder
     */
    public static Builder failure() {
        return new Builder(true);
    }

    /**
     * Create a new builder
     */
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

    public int getErrorCode() {
        return errorCode;
    }

    public Map<String, String> getMetadata() {
        return metadata;
    }

    public TcpRequest getRequest() {
        return request;
    }

    public int getDataLength() {
        return data != null ? data.length : 0;
    }

    /**
     * Check if the response is successful
     */
    public boolean isOk() {
        return successful && errorMessage == null;
    }

    /**
     * Check if there was an error
     */
    public boolean hasError() {
        return !successful || errorMessage != null;
    }

    /**
     * Get response as input stream
     */
    public ByteArrayInputStream getInputStream() {
        return data != null ? new ByteArrayInputStream(data) : null;
    }

    // ========== Builder ==========

    /**
     * TCP Response Builder
     */
    public static class Builder {
        private byte[] data;
        private String dataAsString;
        private String host;
        private int port;
        private int bytesRead = 0;
        private boolean successful = false;
        private long duration = 0;
        private String errorMessage;
        private int errorCode = 0;
        private Map<String, String> metadata = new HashMap<>();
        private TcpRequest request;

        private Builder() {}

        private Builder(boolean isError) {
            this.successful = !isError;
        }

        /**
         * Set response data
         */
        public Builder data(byte[] data) {
            this.data = data;
            return this;
        }

        /**
         * Set response data as string
         */
        public Builder data(String data) {
            this.dataAsString = data;
            this.data = data != null ? data.getBytes(StandardCharsets.UTF_8) : null;
            return this;
        }

        /**
         * Set host info
         */
        public Builder host(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        /**
         * Set bytes read
         */
        public Builder bytesRead(int bytesRead) {
            this.bytesRead = bytesRead;
            return this;
        }

        /**
         * Set duration (milliseconds)
         */
        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        /**
         * Set error message
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Set error code
         */
        public Builder errorCode(int errorCode) {
            this.errorCode = errorCode;
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
         * Set associated request
         */
        public Builder request(TcpRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Build immutable TcpResponse
         */
        public TcpResponse build() {
            return new TcpResponse(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "TcpResponse{successful=%s, bytes=%d, duration=%dms, error='%s'}",
                successful, bytesRead, duration, errorMessage);
    }
}
