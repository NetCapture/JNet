package com.jnet.core;

import java.net.URI;
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

    public String getUrlString() {
        return uri.toString();
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public String getHeader(String name) {
        return headers.get(name);
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
        return new Call.RealCall(this, client);
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
                this.uri = URI.create(url);
            } catch (Exception e) {
                // 尝试处理未编码的特殊字符
                try {
                    // 简单的 fallback，如果 URI.create 失败，尝试用 URL 构造然后转 URI
                    // 主要是为了兼容一些非标字符，虽然 URI 推荐预先编码
                    this.uri = new java.net.URL(url).toURI();
                } catch (Exception ex) {
                    throw new IllegalArgumentException("Invalid URL: " + url, e);
                }
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
            this.uri = uri;
            return this;
        }

        /**
         * 设置请求方法
         */
        public Builder method(String method) {
            if (method == null || method.isEmpty()) {
                throw new IllegalArgumentException("Method cannot be null or empty");
            }
            this.method = method.toUpperCase();
            return this;
        }

        /**
         * 添加请求头
         */
        public Builder header(String name, String value) {
            if (name == null || name.isEmpty()) {
                return this;
            }
            this.headers.put(name, value == null ? "" : value);
            return this;
        }

        /**
         * 批量添加请求头
         */
        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                this.headers.putAll(headers);
            }
            return this;
        }

        /**
         * 设置请求体 (String)
         */
        public Builder body(String body) {
            this.body = body;
            if (body != null) {
                this.bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofString(body);
            }
            return this;
        }

        /**
         * 设置请求体 (BodyPublisher)
         * 用于流式传输、文件上传等
         */
        public Builder body(java.net.http.HttpRequest.BodyPublisher bodyPublisher) {
            this.bodyPublisher = bodyPublisher;
            // 如果单独设置publisher，body字符串可能为空，用于日志记录的body字段保持null
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
            header("User-Agent", "JNet/3.0");
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
    }

    @Override
    public String toString() {
        return String.format(
                "Request{method='%s', url='%s', headers=%d, hasBody=%s}",
                method, getUrlString(), headers.size(), body != null);
    }
}
