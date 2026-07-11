package com.jnet.rtsp;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable RTSP request. */
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
        method = builder.method;
        url = builder.url;
        sessionId = builder.sessionId;
        contentRange = builder.contentRange;
        userAgent = builder.userAgent;
        authorization = builder.authorization;
        cseq = builder.cseq;
        data = builder.data;
        headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
    }

    public static Builder newBuilder() {
        return new Builder();
    }

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

    public Builder toBuilder() {
        return new Builder()
                .method(method)
                .url(url)
                .sessionId(sessionId)
                .range(contentRange)
                .userAgent(userAgent)
                .authorization(authorization)
                .cseq(cseq)
                .headers(headers)
                .data(data);
    }

    public String toRequestString() {
        StringBuilder result = new StringBuilder(128);
        result.append(method.getMethod()).append(' ').append(url).append(" RTSP/1.0\r\n")
                .append("CSeq: ").append(cseq).append("\r\n");
        appendHeader(result, "User-Agent", userAgent);
        appendHeader(result, "Session", sessionId);
        appendHeader(result, "Authorization", authorization);
        for (Map.Entry<String, String> header : headers.entrySet()) {
            result.append(header.getKey()).append(": ")
                    .append(header.getValue()).append("\r\n");
        }
        appendHeader(result, "Range", contentRange);
        if (data != null && !data.isEmpty()) {
            appendHeader(result, "Content-Length",
                    String.valueOf(data.getBytes(StandardCharsets.UTF_8).length));
        }
        result.append("\r\n");
        if (data != null && !data.isEmpty()) {
            result.append(data);
        }
        return result.toString();
    }

    private static void appendHeader(StringBuilder target, String name, String value) {
        if (value != null && !value.isEmpty()) {
            target.append(name).append(": ").append(value).append("\r\n");
        }
    }

    public static class Builder {
        private RtspMethod method = RtspMethod.OPTIONS;
        private String url;
        private String sessionId;
        private String contentRange;
        private String userAgent = "JNet/3.5.2 (JNet RTSP Client)";
        private String authorization;
        private String cseq = "1";
        private String data = "";
        private final Map<String, String> headers = new LinkedHashMap<>();

        public Builder method(RtspMethod method) {
            if (method == null) {
                throw new IllegalArgumentException("method cannot be null");
            }
            this.method = method;
            return this;
        }

        public Builder url(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            this.url = requireSingleLine(url, "URL");
            return this;
        }

        public Builder sessionId(String sessionId) {
            this.sessionId = requireSingleLine(sessionId, "sessionId");
            return this;
        }

        public Builder contentRange(String contentRange) {
            this.contentRange = requireSingleLine(contentRange, "contentRange");
            return this;
        }

        public Builder range(String range) {
            contentRange = requireSingleLine(range, "range");
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = requireSingleLine(userAgent, "userAgent");
            return this;
        }

        public Builder authorization(String authorization) {
            this.authorization = requireSingleLine(authorization, "authorization");
            return this;
        }

        public Builder cseq(String cseq) {
            this.cseq = requireSingleLine(cseq, "cseq");
            return this;
        }

        public Builder cseq(int cseq) {
            this.cseq = String.valueOf(cseq);
            return this;
        }

        public Builder data(String data) {
            this.data = data == null ? "" : data;
            return this;
        }

        public Builder header(String key, String value) {
            String name = requireHeaderName(key);
            if (isManagedHeader(name)) {
                throw new IllegalArgumentException("Use the dedicated builder method for header: " + name);
            }
            java.util.Iterator<String> iterator = headers.keySet().iterator();
            while (iterator.hasNext()) {
                if (iterator.next().equalsIgnoreCase(name)) {
                    iterator.remove();
                }
            }
            headers.put(name, requireSingleLine(value, "header value"));
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    header(header.getKey(), header.getValue());
                }
            }
            return this;
        }

        public RtspRequest build() {
            if (url == null || url.isEmpty()) {
                throw new IllegalStateException("URL must be set");
            }
            if (method == null) {
                throw new IllegalStateException("Method must be set");
            }
            if (cseq == null || cseq.isEmpty()) {
                throw new IllegalStateException("CSeq must be set");
            }
            return new RtspRequest(this);
        }

        private static String requireSingleLine(String value, String label) {
            if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
                throw new IllegalArgumentException(label + " must not contain CR/LF");
            }
            return value;
        }

        private static String requireHeaderName(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("header name cannot be null or empty");
            }
            for (int index = 0; index < value.length(); index++) {
                char current = value.charAt(index);
                if (!isTokenCharacter(current)) {
                    throw new IllegalArgumentException("Invalid RTSP header name");
                }
            }
            return value;
        }

        private static boolean isManagedHeader(String name) {
            return "CSeq".equalsIgnoreCase(name) || "User-Agent".equalsIgnoreCase(name)
                    || "Session".equalsIgnoreCase(name) || "Authorization".equalsIgnoreCase(name)
                    || "Range".equalsIgnoreCase(name) || "Content-Length".equalsIgnoreCase(name);
        }

        private static boolean isTokenCharacter(char value) {
            return value >= '0' && value <= '9' || value >= 'A' && value <= 'Z'
                    || value >= 'a' && value <= 'z'
                    || "!#$%&'*+-.^_`|~".indexOf(value) >= 0;
        }
    }
}
