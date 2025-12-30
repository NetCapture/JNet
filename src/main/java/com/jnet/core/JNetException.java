package com.jnet.core;

/**
 * JNet异常基类
 * 提供详细的错误信息和错误类型
 *
 * @author sanbo
 * @version 3.0.0
 */
public class JNetException extends RuntimeException {
    private final ErrorType errorType;
    private final int statusCode;
    private final String requestUrl;
    private final String requestMethod;

    public JNetException(String message) {
        super(message);
        this.errorType = ErrorType.UNKNOWN;
        this.statusCode = -1;
        this.requestUrl = null;
        this.requestMethod = null;
    }

    public JNetException(String message, Throwable cause) {
        super(message, cause);
        this.errorType = ErrorType.UNKNOWN;
        this.statusCode = -1;
        this.requestUrl = null;
        this.requestMethod = null;
    }

    private JNetException(Builder builder) {
        super(builder.message, builder.cause);
        this.errorType = builder.errorType;
        this.statusCode = builder.statusCode;
        this.requestUrl = builder.requestUrl;
        this.requestMethod = builder.requestMethod;
    }

    public ErrorType getErrorType() {
        return errorType;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public String getRequestMethod() {
        return requestMethod;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("JNetException{");

        if (errorType != ErrorType.UNKNOWN) {
            sb.append("type=").append(errorType).append(", ");
        }

        if (statusCode != -1) {
            sb.append("status=").append(statusCode).append(", ");
        }

        if (requestMethod != null) {
            sb.append("method=").append(requestMethod).append(", ");
        }

        if (requestUrl != null) {
            sb.append("url=").append(requestUrl).append(", ");
        }

        sb.append("message=").append(getMessage());

        if (getCause() != null) {
            sb.append(", cause=").append(getCause().getMessage());
        }

        sb.append("}");
        return sb.toString();
    }

    /**
     * 创建异常构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 异常构建器
     */
    public static class Builder {
        private String message;
        private Throwable cause;
        private ErrorType errorType = ErrorType.UNKNOWN;
        private int statusCode = -1;
        private String requestUrl;
        private String requestMethod;

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public Builder errorType(ErrorType errorType) {
            this.errorType = errorType;
            return this;
        }

        public Builder statusCode(int statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder requestUrl(String requestUrl) {
            this.requestUrl = requestUrl;
            return this;
        }

        public Builder requestMethod(String requestMethod) {
            this.requestMethod = requestMethod;
            return this;
        }

        public JNetException build() {
            return new JNetException(this);
        }
    }

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        NETWORK_UNAVAILABLE("网络不可用"),
        CONNECTION_REFUSED("连接被拒绝"),
        CONNECTION_TIMEOUT("连接超时"),
        READ_TIMEOUT("读取超时"),
        SSL_HANDSHAKE_FAILED("SSL握手失败"),
        HTTP_PROTOCOL_ERROR("HTTP协议错误"),
        HTTP_CLIENT_ERROR("HTTP客户端错误"),
        HTTP_SERVER_ERROR("HTTP服务器错误"),
        RESPONSE_PARSING_ERROR("响应解析错误"),
        REQUEST_BUILD_ERROR("请求构建错误"),
        IO_ERROR("IO错误"),
        INTERRUPTED("请求被中断"),
        UNKNOWN("未知错误");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }
}
