package com.jnet.core;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import javax.net.ssl.SSLHandshakeException;

/**
 * 异常映射器 - 统一处理HTTP请求异常
 * 将各种底层异常转换为统一的JNetException
 *
 * 优化目标: 消除150+行重复的异常处理代码
 *
 * @author sanbo
 * @version 3.5.0
 */
final class ExceptionMapper {

    private ExceptionMapper() {
        // 工具类，防止实例化
    }

    /**
     * 将异常映射为JNetException
     *
     * @param e 原始异常
     * @param method HTTP方法
     * @param url 请求URL
     * @return JNetException
     */
    static JNetException map(Exception e, String method, String url) {
        JNetException.Builder builder = new JNetException.Builder()
                .cause(e)
                .requestUrl(url)
                .requestMethod(method);

        if (e instanceof ConnectException) {
            return builder
                    .message("Connection refused: " + url)
                    .errorType(JNetException.ErrorType.CONNECTION_REFUSED)
                    .build();
        }

        if (e instanceof SocketTimeoutException) {
            return builder
                    .message("Request timeout: " + url)
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .build();
        }

        if (e instanceof UnknownHostException) {
            return builder
                    .message("Unknown host: " + e.getMessage())
                    .errorType(JNetException.ErrorType.NETWORK_UNAVAILABLE)
                    .build();
        }

        if (e instanceof SSLHandshakeException) {
            return builder
                    .message("SSL handshake failed: " + url)
                    .errorType(JNetException.ErrorType.SSL_HANDSHAKE_FAILED)
                    .build();
        }

        if (e instanceof InterruptedException) {
            Thread.currentThread().interrupt();
            return builder
                    .message("Request interrupted: " + url)
                    .errorType(JNetException.ErrorType.INTERRUPTED)
                    .build();
        }

        if (e instanceof IOException) {
            return builder
                    .message("IO error during " + method + " request: " + url)
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .build();
        }

        if (e instanceof IllegalArgumentException) {
            return builder
                    .message("Invalid request configuration: " + e.getMessage())
                    .errorType(JNetException.ErrorType.REQUEST_BUILD_ERROR)
                    .build();
        }

        // 默认情况
        return builder
                .message(method + " request failed: " + url)
                .errorType(JNetException.ErrorType.UNKNOWN)
                .build();
    }

    /**
     * 执行HTTP请求并统一处理异常
     *
     * @param <T> 返回类型
     * @param supplier 请求执行函数
     * @param method HTTP方法
     * @param url 请求URL
     * @return 请求结果
     * @throws JNetException 映射后的异常
     */
    static <T> T executeWithMapping(RequestSupplier<T> supplier, String method, String url) {
        try {
            return supplier.execute();
        } catch (Exception e) {
            throw map(e, method, url);
        }
    }

    /**
     * 请求执行函数接口
     */
    @FunctionalInterface
    interface RequestSupplier<T> {
        T execute() throws Exception;
    }
}
