package com.jnet.rtsp;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * RTSP Request
 * Request object for RTSP protocol communication
 * Thread-safe (immutable after build)
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class RtspRequest {
    private final RtspMethod method;
    private final String url;
    private final String sessionId;
    private final String contentRange;
    private final String userAgent;
    private final String authorization;
    private final String cseq;
    private final String data;
    private final Map<String, String> headers;

    private RtspRequest(Builder builder) {
        this.method = builder.method;
        this.url = builder.url;
        this.sessionId = builder.sessionId;
        this.contentRange = builder.contentRange;
        this.userAgent = builder.userAgent;
        this.authorization = builder.authorization;
        this.cseq = builder.cseq;
        this.data = builder.data;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
    }

    // ========== Factory Methods ==========

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Getters ==========

    public RtspMethod getMethod() {
        return method;
    }

    public String getUrl() {
        return url;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getContentRange() {
        return contentRange;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getAuthorization() {
        return authorization;
    }

    public String getCseq() {
        return cseq;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getData() {
        return data;
    }

    /**
     * Get request as formatted RTSP request
     */
    public String toRequestString() {
        StringBuilder sb = new StringBuilder();
        sb.append(method.getMethod()).append(" ").append(url).append(" RTSP/1.0\r\n");
        sb.append("Cseq: ").append(cseq).append("\r\n");

        // Add headers
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\r\n");
        }

        // Add content-range if set
        if (contentRange != null && !contentRange.isEmpty()) {
            sb.append("Range: ").append(contentRange).append("\r\n");
        }

        // Add content length if data is set
        if (data != null && !data.isEmpty()) {
            sb.append("Content-Length: ").append(data.getBytes(StandardCharsets.UTF_8).length).append("\r\n");
        }

        sb.append("\r\n");
        if (data != null && !data.isEmpty()) {
            sb.append(data);
        }
        return sb.toString();
    }

    // ========== Builder ==========

    /**
     * RTSP Request Builder
     */
    public static class Builder {
        private RtspMethod method = RtspMethod.OPTIONS;
        private String url;
        private String sessionId;
        private String contentRange;
        private String userAgent = "JNet/3.5.0 (JNet RTSP Client)";
        private String authorization;
        private String cseq = "1";
        private String data = "";

        private final Map<String, String> headers = new HashMap<>();

        /**
         * Set RTSP method
         */
        public Builder method(RtspMethod method) {
            this.method = method;
            return this;
        }

        /**
         * Set RTSP URL
         */
        public Builder url(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            this.url = url;
            return this;
        }

        /**
         * Set session ID
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Set content range
         */
        public Builder contentRange(String contentRange) {
            this.contentRange = contentRange;
            return this;
        }

        /**
         * Set range (alias for contentRange)
         */
        public Builder range(String range) {
            this.contentRange = range;
            return this;
        }

        /**
         * Set user agent
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Set authorization
         */
        public Builder authorization(String authorization) {
            this.authorization = authorization;
            return this;
        }

        /**
         * Set CSeq sequence number
         */
        public Builder cseq(String cseq) {
            this.cseq = cseq;
            return this;
        }

        /**
         * Set CSeq sequence number (int)
         */
        public Builder cseq(int cseq) {
            this.cseq = String.valueOf(cseq);
            return this;
        }

        /**
         * Set request data
         */
        public Builder data(String data) {
            this.data = data != null ? data : "";
            return this;
        }

        /**
         * Add header
         */
        public Builder header(String key, String value) {
            if (key == null || key.isEmpty()) {
                return this;
            }
            this.headers.put(key, value);
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
         * Build immutable RtspRequest
         */
        public RtspRequest build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("URL must be set");
            }
            return new RtspRequest(this);
        }
    }
}
