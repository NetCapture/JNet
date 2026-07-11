package com.jnet.core;

import java.net.URI;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 不可变请求对象
 * 线程安全，状态不可更改
 *
 * @author sanbo
 * @version 3.0.0
 */
public final class Request {
    private final JNetClient client;
    private final String method;
    private final URI uri;
    private final Map<String, String> headers;
    private final String body;
    private final java.net.http.HttpRequest.BodyPublisher bodyPublisher;
    private final String tag;

    private Request(Builder builder) {
        this.client = builder.client;
        this.method = builder.method;
        this.uri = builder.uri;
        this.headers = Collections.unmodifiableMap(new HashMap<>(builder.headers));
        this.body = builder.body;
        this.bodyPublisher = builder.bodyPublisher;
        this.tag = builder.tag;
    }

    /**
     * 创建新的Builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 创建基于当前请求的Builder（用于修改请求）
     */
    public Builder toBuilder() {
        Builder builder = new Builder()
                .client(this.client)
                .url(this.uri.toString())
                .method(this.method)
                .headers(this.headers)
                .body(this.body)
                .tag(this.tag);
        // Note: bodyPublisher cannot be easily copied back to builder if set directly without string body
        // But if body string exists, builder.body(string) will recreate publisher
        if (this.bodyPublisher != null && this.body == null) {
             builder.body(this.bodyPublisher);
        }
        return builder;
    }

    public JNetClient getClient() {
        return client;
    }

    public String getMethod() {
        return method;
    }

    public URI getUri() {
        return uri;
    }

    /** @deprecated Prefer {@link #getUri()}. */
    @Deprecated
    public URL getUrl() {
        try {
            return uri.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalStateException("Request URI cannot be converted to URL", e);
        }
    }

    public String getUrlString() {
        return uri.toString();
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

    public String getBody() {
        return body;
    }

    public java.net.http.HttpRequest.BodyPublisher getBodyPublisher() {
        return bodyPublisher;
    }

    public String getTag() {
        return tag;
    }

    /**
     * 创建Call实例执行此请求
     */
    public Call newCall() {
        return new Call.RealCall(this, client, client != null ? client.getInterceptors() : null);
    }

    /**
     * 构建Request的Builder
     * 使用建造者模式，支持链式调用
     */
    public static class Builder {
        private JNetClient client;
        private String method = "GET";
        private URI uri;
        private Map<String, String> headers = new HashMap<>();
        private String body;
        private java.net.http.HttpRequest.BodyPublisher bodyPublisher;
        private String tag;
        private com.jnet.auth.Auth auth;

        /**
         * 关联客户端
         */
        public Builder client(JNetClient client) {
            this.client = client;
            return this;
        }

        /**
         * 设置请求URL
         */
        public Builder url(String url) {
            if (url == null || url.isEmpty()) {
                throw new IllegalArgumentException("URL cannot be null or empty");
            }
            try {
                this.uri = parseUrl(url);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid URL", e);
            }
            return this;
        }

        /**
         * 设置请求URI
         */
        public Builder uri(URI uri) {
            if (uri == null) {
                throw new IllegalArgumentException("URI cannot be null");
            }
            validateUri(uri);
            this.uri = uri;
            return this;
        }

        /**
         * 设置请求方法
         */
        public Builder method(String method) {
            String normalized = method == null ? null : method.trim();
            this.method = HttpValidation.requireToken(normalized, "HTTP method");
            return this;
        }

        /**
         * 添加请求头
         */
        public Builder header(String name, String value) {
            HttpValidation.requireToken(name, "header name");
            HttpValidation.putCaseInsensitive(
                    this.headers, name, HttpValidation.normalizeHeaderValue(value));
            return this;
        }

        /**
         * 批量添加请求头
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
         * 设置请求体 (String)
         */
        public Builder body(String body) {
            this.body = body;
            this.bodyPublisher = body != null
                    ? java.net.http.HttpRequest.BodyPublishers.ofString(body)
                    : null;
            return this;
        }

        /**
         * 设置请求体 (BodyPublisher)
         * 用于流式传输、文件上传等
         */
        public Builder body(java.net.http.HttpRequest.BodyPublisher bodyPublisher) {
            this.body = null;
            this.bodyPublisher = bodyPublisher;
            return this;
        }

        /**
         * 设置请求标签
         */
        public Builder tag(String tag) {
            this.tag = tag;
            return this;
        }

        /**
         * 设置认证方式
         */
        public Builder auth(com.jnet.auth.Auth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * 添加常用请求头
         */
        public Builder addCommonHeaders() {
            header("User-Agent", "JNet/3.5.2");
            header("Accept", "*/*");
            return this;
        }

        /**
         * 设置JSON内容类型
         */
        public Builder json() {
            header("Content-Type", "application/json");
            return this;
        }

        /**
         * 设置表单内容类型
         */
        public Builder form() {
            header("Content-Type", "application/x-www-form-urlencoded");
            return this;
        }

        /**
         * 构建不可变Request对象
         */
        public Request build() {
            if (uri == null) {
                throw new IllegalStateException("URL must be set");
            }
            if (client == null) {
                client = JNetClient.getInstance();
            }
            Request request = new Request(this);
            if (auth != null) {
                request = auth.apply(request);
            }
            return request;
        }

        private static URI parseUrl(String url) {
            try {
                URI uri = URI.create(url);
                validateUri(uri);
                return uri;
            } catch (IllegalArgumentException e) {
                URI uri = URI.create(sanitizeUrl(url));
                validateUri(uri);
                return uri;
            }
        }

        private static void validateUri(URI uri) {
            if (uri == null || !uri.isAbsolute() || uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("URL must be absolute with a host");
            }

            String scheme = uri.getScheme();
            if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Unsupported URL scheme: " + scheme);
            }
            if (uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("URL user-info is not supported");
            }
            if (uri.getRawFragment() != null) {
                throw new IllegalArgumentException("URL fragments are not sent in HTTP requests");
            }
            if (uri.getPort() > 65535) {
                throw new IllegalArgumentException("URL port is out of range: " + uri.getPort());
            }
        }

        private static String sanitizeUrl(String url) {
            StringBuilder sanitized = new StringBuilder(url.length());
            for (int i = 0; i < url.length(); i++) {
                char current = url.charAt(i);
                if (current == ' ') {
                    sanitized.append("%20");
                    continue;
                }

                if (current == '%') {
                    if (i + 2 < url.length() && isHexDigit(url.charAt(i + 1)) && isHexDigit(url.charAt(i + 2))) {
                        sanitized.append(current);
                    } else {
                        sanitized.append("%25");
                    }
                    continue;
                }

                sanitized.append(current);
            }
            return sanitized.toString();
        }

        private static boolean isHexDigit(char c) {
            return (c >= '0' && c <= '9')
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F');
        }
    }

    @Override
    public String toString() {
        return String.format(
                "Request{method='%s', url='%s', headers=%d, hasBody=%s}",
                method, getUrlString(), headers.size(), bodyPublisher != null);
    }
}
