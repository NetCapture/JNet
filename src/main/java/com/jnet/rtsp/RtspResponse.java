package com.jnet.rtsp;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RTSP Response
 * Response object for RTSP protocol communication
 * Thread-safe (immutable after build)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class RtspResponse {
    private final boolean successful;
    private final int statusCode;
    private final String statusText;
    private final String body;
    private final String errorMessage;
    private final int bytesRead;
    private final long duration;
    private final Map<String, String> headers;
    private final RtspRequest request;

    private RtspResponse(Builder builder) {
        this.successful = builder.successful;
        this.statusCode = builder.statusCode;
        this.statusText = builder.statusText;
        this.body = builder.body;
        this.errorMessage = builder.errorMessage;
        this.bytesRead = builder.bytesRead;
        this.duration = builder.duration;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.request = builder.request;
    }

    // ========== Factory Methods ==========

    /**
     * Create a successful response builder
     */
    public static Builder success() {
        return new Builder(true);
    }

    /**
     * Create a failure response builder
     */
    public static Builder failure() {
        return new Builder(false);
    }

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public boolean isSuccessful() {
        return successful;
    }

    public boolean isOk() {
        return successful && statusCode >= 200 && statusCode < 300;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getStatusText() {
        return statusText;
    }

    public String getBody() {
        return body;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public int getBytesRead() {
        return bytesRead;
    }

    public long getDuration() {
        return duration;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public RtspRequest getRequest() {
        return request;
    }

    public int getBodyLength() {
        return body != null ? body.getBytes(StandardCharsets.UTF_8).length : 0;
    }

    /**
     * Check if response is successful
     */
    public boolean hasError() {
        return !successful || errorMessage != null;
    }

    /**
     * Get response as input stream
     */
    public java.io.InputStream getInputStream() {
        return body != null ? new java.io.ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)) : null;
    }

    /**
     * Get header value by name
     */
    public String getHeader(String name) {
        return headers.get(name);
    }

    // ========== Parser ==========

    /**
     * Parse RTSP response string
     */
    public static RtspResponse parse(String responseString) {
        if (responseString == null || responseString.trim().isEmpty()) {
            return RtspResponse.failure()
                    .errorMessage("Empty response")
                    .statusCode(0)
                    .build();
        }

        String[] lines = responseString.split("\r\n");
        if (lines.length == 0) {
            return RtspResponse.failure()
                    .errorMessage("Invalid response")
                    .statusCode(0)
                    .build();
        }

        // Parse status line: "RTSP/1.0 200 OK"
        String statusLine = lines[0].trim();
        int statusCode = 0;
        String statusText = "";
        String[] statusParts = statusLine.split(" ", 3);
        if (statusParts.length >= 2) {
            try {
                statusCode = Integer.parseInt(statusParts[1]);
            } catch (NumberFormatException e) {
                statusCode = 0;
            }
            if (statusParts.length >= 3) {
                statusText = statusParts[2];
            }
        }

        // Parse headers
        Map<String, String> headers = new java.util.HashMap<>();
        StringBuilder bodyBuilder = new StringBuilder();
        boolean inBody = false;

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                inBody = true;
                continue;
            }
            if (!inBody) {
                int colonPos = line.indexOf(':');
                if (colonPos > 0) {
                    String key = line.substring(0, colonPos).trim();
                    String value = line.substring(colonPos + 1).trim();
                    headers.put(key, value);
                }
            } else {
                bodyBuilder.append(line).append("\r\n");
            }
        }

        String body = bodyBuilder.toString();

        return RtspResponse.newBuilder()
                .statusCode(statusCode)
                .statusText(statusText)
                .body(body)
                .headers(headers)
                .successful(statusCode >= 200 && statusCode < 300)
                .bytesRead(body.length())
                .duration(0)
                .build();
    }

    // ========== Builder ==========

    /**
     * RTSP Response Builder
     */
    public static class Builder {
        private boolean successful = false;
        private int statusCode = 0;
        private String statusText;
        private String body;
        private String errorMessage;
        private int bytesRead = 0;
        private long duration = 0;
        private Map<String, String> headers = new HashMap<>();
        private RtspRequest request;

        private Builder() {}

        private Builder(boolean isSuccess) {
            this.successful = isSuccess;
        }

        /**
         * Set status code
         */
        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        /**
         * Set status text
         */
        public Builder statusText(String statusText) {
            this.statusText = statusText;
            return this;
        }

        /**
         * Set successful flag
         */
        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        /**
         * Set response body
         */
        public Builder body(String body) {
            this.body = body;
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
         * Add header
         */
        public Builder header(String key, String value) {
            if (key != null && !key.isEmpty()) {
                this.headers.put(key, value);
            }
            return this;
        }

        /**
         * Add all headers
         */
        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        /**
         * Set associated request
         */
        public Builder request(RtspRequest request) {
            this.request = request;
            return this;
        }

        /**
         * Build immutable RtspResponse
         */
        public RtspResponse build() {
            return new RtspResponse(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "RtspResponse{successful=%s, statusCode=%d, status='%s', bodyLength=%d}",
                successful, statusCode, statusText, body == null ? 0 : body.length());
    }
}
