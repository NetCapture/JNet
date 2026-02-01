package com.jnet.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 请求执行接口 - 负责实际的网络请求
 * 线程安全，支持同步和异步执行
 * 支持拦截器
 * 
 * <p>
 * 基于JDK 11 HttpClient实现
 * </p>
 *
 * @author sanbo
 * @version 3.0.0
 */
public interface Call {
    /**
     * 获取关联的请求
     */
    Request request();

    /**
     * 同步执行请求
     *
     * @return Response
     * @throws IOException 网络异常
     */
    Response execute() throws IOException;

    /**
     * 异步执行请求
     *
     * @param callback 回调接口
     */
    void enqueue(Callback callback);

    /**
     * 取消请求
     */
    void cancel();

    /**
     * 判断是否已执行
     */
    boolean isExecuted();

    /**
     * 判断是否已取消
     */
    boolean isCanceled();

    /**
     * Call实现类
     */
    class RealCall implements Call {
        private final Request request;
        private final JNetClient client;
        private final List<Interceptor> interceptors;
        private volatile boolean executed;
        private volatile boolean canceled;
        // JDK HttpClient的Future，用于取消异步请求
        private volatile CompletableFuture<?> pendingFuture;

        public RealCall(Request request, JNetClient client) {
            this(request, client, null);
        }

        public RealCall(Request request, JNetClient client, List<Interceptor> interceptors) {
            this.request = request;
            this.client = client;
            this.interceptors = interceptors;
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response execute() throws IOException {
            synchronized (this) {
                if (executed) {
                    throw new IllegalStateException("Call already executed");
                }
                executed = true;
            }

            if (canceled) {
                throw new IOException("Request canceled");
            }

            try {
                return executeWithInterceptors();
            } catch (IOException e) {
                throw enhanceException(e);
            } catch (Exception e) {
                throw enhanceException(new IOException(e));
            }
        }

        @Override
        public void enqueue(Callback callback) {
            synchronized (this) {
                if (executed) {
                    throw new IllegalStateException("Call already executed");
                }
                executed = true;
            }

            if (canceled) {
                callback.onFailure(new IOException("Request canceled"));
                return;
            }

            // 异步执行
            if (interceptors != null && !interceptors.isEmpty()) {
                // 有拦截器的情况
                CompletableFuture.runAsync(() -> {
                    try {
                        Response response = executeWithInterceptors();
                        if (canceled) {
                            callback.onFailure(new IOException("Request canceled"));
                        } else {
                            callback.onSuccess(response);
                        }
                    } catch (Exception e) {
                        callback.onFailure(enhanceException(e));
                    }
                });
            } else {
                // 无拦截器，直接使用HttpClient异步
                try {
                    HttpRequest jdkRequest = buildJdkRequest(request);
                    CompletableFuture<HttpResponse<String>> future = client.getHttpClient()
                            .sendAsync(jdkRequest, HttpResponse.BodyHandlers.ofString());
                    this.pendingFuture = future;

                    future.whenComplete((httpResponse, throwable) -> {
                        if (throwable != null) {
                            callback.onFailure(enhanceException(toException(throwable)));
                        } else {
                            if (canceled) {
                                callback.onFailure(new IOException("Request canceled"));
                            } else {
                                callback.onSuccess(toJNetResponse(httpResponse, request, 0));
                            }
                        }
                    });
                } catch (Exception e) {
                    callback.onFailure(enhanceException(e));
                }
            }
        }

        @Override
        public void cancel() {
            canceled = true;
            if (pendingFuture != null) {
                pendingFuture.cancel(true);
            }
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        private Response executeWithInterceptors() throws IOException {
            if (interceptors == null || interceptors.isEmpty()) {
                return executeInternal();
            }

            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request, this);
            return chain.proceed(request);
        }

        /**
         * 执行实际的网络请求（供拦截器链调用）
         */
        Response executeNetworkRequest(Request req) throws IOException {
            return executeInternalWithRequest(req);
        }

        private Response executeInternalWithRequest(Request req) throws IOException {
            long startTime = System.currentTimeMillis();
            try {
                HttpRequest jdkRequest = buildJdkRequest(req);
                HttpResponse<String> httpResponse;
                try {
                    httpResponse = client.getHttpClient().send(jdkRequest, HttpResponse.BodyHandlers.ofString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Request interrupted", e);
                }

                if (canceled) {
                    throw new IOException("Request canceled");
                }

                long duration = System.currentTimeMillis() - startTime;
                return toJNetResponse(httpResponse, req, duration);
            } catch (Exception e) {
                if (e instanceof IOException)
                    throw (IOException) e;
                throw new IOException(e);
            }
        }

        private Response executeInternal() throws IOException {
            long startTime = System.currentTimeMillis();

            try {
                HttpRequest jdkRequest = buildJdkRequest(request);

                HttpResponse<String> httpResponse;
                try {
                    httpResponse = client.getHttpClient().send(jdkRequest, HttpResponse.BodyHandlers.ofString());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Request interrupted", e);
                }

                if (canceled) {
                    throw new IOException("Request canceled");
                }

                long duration = System.currentTimeMillis() - startTime;
                return toJNetResponse(httpResponse, request, duration);
            } catch (Exception e) {
                if (e instanceof IOException)
                    throw (IOException) e;
                throw new IOException(e);
            }
        }

        private HttpRequest buildJdkRequest(Request request) {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(request.getUri());

            if (client.getReadTimeout() > 0) {
                builder.timeout(Duration.ofMillis(client.getReadTimeout()));
            }

            // Headers
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                builder.header(entry.getKey(), entry.getValue());
            }

            // Method & Body
            HttpRequest.BodyPublisher bodyPublisher = request.getBodyPublisher() != null
                    ? request.getBodyPublisher()
                    : HttpRequest.BodyPublishers.noBody();

            builder.method(request.getMethod(), bodyPublisher);

            return builder.build();
        }

        private Response toJNetResponse(HttpResponse<String> httpResponse, Request request, long duration) {
            boolean isSuccess = httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300;
            Response.Builder builder = isSuccess ? Response.success(request) : Response.failure(request);

            builder.code(httpResponse.statusCode())
                    .body(httpResponse.body())
                    .duration(duration);

            for (Map.Entry<String, List<String>> entry : httpResponse.headers().map().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    builder.header(entry.getKey(), entry.getValue().get(0));
                }
            }

            return builder.build();
        }

        private IOException enhanceException(Exception e) {
            if (e instanceof IOException) {
                return (IOException) e;
            }
            return new IOException(e);
        }

        private Exception toException(Throwable t) {
            if (t instanceof Exception)
                return (Exception) t;
            return new Exception(t);
        }
    }

    /**
     * 回调接口
     */
    interface Callback {
        /**
         * 请求成功
         */
        void onSuccess(Response response);

        /**
         * 请求失败
         */
        void onFailure(Exception e);
    }
}
