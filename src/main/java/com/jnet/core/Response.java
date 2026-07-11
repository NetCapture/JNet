package com.jnet.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 不可变响应对象
 * 线程安全，状态不可更改
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class Response {
    private final int code;
    private final String message;
    private final String body;
    private final Map<String, String> headers;
    private final Map<String, List<String>> headerValues;
    private final long duration;
    private final Request request;
    private final boolean successful;

    private Response(Builder builder) {
        this.code = builder.code;
        this.message = builder.message;
        this.body = builder.body;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        Map<String, List<String>> copiedHeaderValues = new HashMap<>();
        for (Map.Entry<String, List<String>> entry : builder.headerValues.entrySet()) {
            copiedHeaderValues.put(entry.getKey(),
                    Collections.unmodifiableList(new ArrayList<>(entry.getValue())));
        }
        this.headerValues = Collections.unmodifiableMap(copiedHeaderValues);
        this.duration = builder.duration;
        this.request = builder.request;
        this.successful = builder.successful;
    }

    /**
     * 创建成功的响应Builder
     */
    public static Builder success(Request request) {
        return new Builder(request, true);
    }

    /**
     * 创建失败的响应Builder
     */
    public static Builder failure(Request request) {
        return new Builder(request, false);
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        String value = headers.get(name);
        if (value != null) {
            return value;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public Map<String, List<String>> getHeaderValues() {
        return headerValues;
    }

    public List<String> getHeaderValues(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }

        List<String> values = headerValues.get(name);
        if (values != null) {
            return values;
        }

        for (Map.Entry<String, List<String>> entry : headerValues.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    public long getDuration() {
        return duration;
    }

    public Request getRequest() {
        return request;
    }

    public boolean isSuccessful() {
        return successful;
    }

    public boolean isOk() {
        return successful && code >= 200 && code < 300;
    }

    /**
     * 判断是否为客户端错误
     */
    public boolean isClientError() {
        return code >= 400 && code < 500;
    }

    /**
     * 判断是否为服务器错误
     */
    public boolean isServerError() {
        return code >= 500;
    }

    /**
     * 响应Builder
     */
    public static class Builder {
        private final Request request;
        private final boolean successful;
        private int code;
        private String message = "";
        private String body;
        private Map<String, String> headers = new HashMap<>();
        private Map<String, List<String>> headerValues = new HashMap<>();
        private long duration = -1;

        private Builder(Request request, boolean successful) {
            this.request = request;
            this.successful = successful;
        }

        /**
         * 设置响应码
         */
        public Builder code(int code) {
            this.code = code;
            return this;
        }

        /**
         * 设置响应消息
         */
        public Builder message(String message) {
            this.message = message == null ? "" : message;
            return this;
        }

        /**
         * 设置响应体
         */
        public Builder body(String body) {
            this.body = body;
            return this;
        }

        /**
         * 添加响应头
         */
        public Builder header(String name, String value) {
            HttpValidation.requireToken(name, "header name");
            String normalized = HttpValidation.normalizeHeaderValue(value);
            HttpValidation.putCaseInsensitive(this.headers, name, normalized);
            HttpValidation.putCaseInsensitive(
                    this.headerValues, name, Collections.singletonList(normalized));
            return this;
        }

        public Builder headerValues(String name, List<String> values) {
            HttpValidation.requireToken(name, "header name");
            if (values != null && !values.isEmpty()) {
                List<String> normalizedValues = new ArrayList<>(values.size());
                for (String value : values) {
                    normalizedValues.add(HttpValidation.normalizeHeaderValue(value));
                }
                List<String> immutableValues = Collections.unmodifiableList(normalizedValues);
                HttpValidation.putCaseInsensitive(this.headerValues, name, immutableValues);
                HttpValidation.putCaseInsensitive(this.headers, name, immutableValues.get(0));
            }
            return this;
        }

        /**
         * 批量添加响应头
         */
        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    header(entry.getKey(), entry.getValue());
                }
            }
            return this;
        }

        /**
         * 设置请求耗时（毫秒）
         */
        public Builder duration(long duration) {
            this.duration = duration;
            return this;
        }

        /**
         * 构建不可变Response对象
         */
        public Response build() {
            return new Response(this);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Response{code=%d, successful=%s, bodyLength=%d, duration=%dms}",
                code, successful, body == null ? 0 : body.length(), duration);
    }
}
