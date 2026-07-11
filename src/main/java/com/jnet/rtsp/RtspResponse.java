package com.jnet.rtsp;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** Immutable RTSP response. */
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
        successful = builder.successful;
        statusCode = builder.statusCode;
        statusText = builder.statusText;
        body = builder.body;
        errorMessage = builder.errorMessage;
        bytesRead = builder.bytesRead;
        duration = builder.duration;
        Map<String, String> copy = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        copy.putAll(builder.headers);
        headers = Collections.unmodifiableMap(copy);
        request = builder.request;
    }

    public static Builder success() {
        return new Builder(true);
    }

    public static Builder failure() {
        return new Builder(false);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

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
        return body == null ? 0 : body.getBytes(StandardCharsets.UTF_8).length;
    }

    public boolean hasError() {
        return !successful || errorMessage != null;
    }

    public InputStream getInputStream() {
        return body == null ? null : new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8));
    }

    public String getHeader(String name) {
        return headers.get(name);
    }

    /** Parses one complete RTSP response string without trimming its body. */
    public static RtspResponse parse(String response) {
        if (response == null || response.trim().isEmpty()) {
            return invalid("Empty response", 0, 0, null);
        }

        int headerEnd = response.indexOf("\r\n\r\n");
        int separatorLength = 4;
        if (headerEnd < 0) {
            headerEnd = response.indexOf("\n\n");
            separatorLength = 2;
        }
        String headers = headerEnd < 0 ? response : response.substring(0, headerEnd);
        String body = headerEnd < 0 ? "" : response.substring(headerEnd + separatorLength);
        return parseParts(headers, body, body.getBytes(StandardCharsets.UTF_8).length,
                response.getBytes(StandardCharsets.UTF_8).length, 0, null);
    }

    static RtspResponse parse(byte[] headerBytes, byte[] bodyBytes, long duration, RtspRequest request) {
        String headers = new String(headerBytes, StandardCharsets.ISO_8859_1);
        int headerEnd = headers.indexOf("\r\n\r\n");
        if (headerEnd >= 0) {
            headers = headers.substring(0, headerEnd);
        }
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        return parseParts(headers, body, bodyBytes.length, headerBytes.length + bodyBytes.length,
                duration, request);
    }

    private static RtspResponse parseParts(String headerBlock, String body, int bodyBytes,
                                           int bytesRead, long duration, RtspRequest request) {
        String[] lines = headerBlock.split("\r?\n", -1);
        Status status = Status.parse(lines.length == 0 ? "" : lines[0]);
        if (status == null) {
            return invalid("Invalid response", bytesRead, duration, request);
        }

        Map<String, String> headers = parseHeaders(lines);
        String contentLength = headers.get("Content-Length");
        if (contentLength != null) {
            final long expected;
            try {
                expected = Long.parseLong(contentLength);
            } catch (NumberFormatException e) {
                return invalidLength(status, headers, bytesRead, duration, request);
            }
            if (expected < 0 || bodyBytes < expected) {
                String message = expected < 0 ? "Invalid Content-Length" : "Incomplete RTSP body";
                return failure().statusCode(status.code).statusText(status.text)
                        .headers(headers).errorMessage(message).bytesRead(bytesRead)
                        .duration(duration).request(request).build();
            }
        }

        boolean successful = status.code >= 200 && status.code < 300;
        return newBuilder()
                .successful(successful)
                .statusCode(status.code)
                .statusText(status.text)
                .body(body)
                .headers(headers)
                .errorMessage(successful ? null : status.code + " " + status.text)
                .bytesRead(bytesRead)
                .duration(duration)
                .request(request)
                .build();
    }

    private static Map<String, String> parseHeaders(String[] lines) {
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (int index = 1; index < lines.length; index++) {
            String line = lines[index];
            int separator = line.indexOf(':');
            if (separator > 0) {
                headers.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
            }
        }
        return headers;
    }

    private static RtspResponse invalidLength(Status status, Map<String, String> headers,
                                              int bytesRead, long duration, RtspRequest request) {
        return failure()
                .statusCode(status.code)
                .statusText(status.text)
                .headers(headers)
                .errorMessage("Invalid Content-Length")
                .bytesRead(bytesRead)
                .duration(duration)
                .request(request)
                .build();
    }

    private static RtspResponse invalid(String message, int bytesRead, long duration,
                                        RtspRequest request) {
        return failure()
                .statusCode(0)
                .errorMessage(message)
                .bytesRead(bytesRead)
                .duration(duration)
                .request(request)
                .build();
    }

    private static final class Status {
        private final int code;
        private final String text;

        private Status(int code, String text) {
            this.code = code;
            this.text = text;
        }

        private static Status parse(String line) {
            if (!line.startsWith("RTSP/")) {
                return null;
            }
            String[] parts = line.split(" +", 3);
            if (parts.length < 2) {
                return null;
            }
            try {
                int code = Integer.parseInt(parts[1]);
                if (code < 100 || code > 999) {
                    return null;
                }
                return new Status(code, parts.length == 3 ? parts[2] : "");
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }

    public static class Builder {
        private boolean successful;
        private int statusCode;
        private String statusText;
        private String body;
        private String errorMessage;
        private int bytesRead;
        private long duration;
        private final Map<String, String> headers = new HashMap<>();
        private RtspRequest request;

        private Builder() {
        }

        private Builder(boolean successful) {
            this.successful = successful;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder statusText(String statusText) {
            this.statusText = statusText;
            return this;
        }

        public Builder successful(boolean successful) {
            this.successful = successful;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
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

        public Builder header(String key, String value) {
            if (key != null && !key.isEmpty()) {
                headers.put(key, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        public Builder request(RtspRequest request) {
            this.request = request;
            return this;
        }

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
