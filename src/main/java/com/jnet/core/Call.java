package com.jnet.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 请求执行接口 - 负责实际的网络请求
 * 线程安全，支持同步和异步执行
 * 支持连接池、拦截器、重试等高级功能
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
        private final ConnectionPool connectionPool;
        private volatile boolean executed;
        private volatile boolean canceled;

        public RealCall(Request request, JNetClient client) {
            this(request, client, null, null);
        }

        public RealCall(Request request, JNetClient client, List<Interceptor> interceptors,
                       ConnectionPool connectionPool) {
            this.request = request;
            this.client = client;
            this.interceptors = interceptors;
            this.connectionPool = connectionPool;
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

            try {
                return executeWithInterceptors();
            } catch (IOException e) {
                throw enhanceException(e);
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

            // 异步执行
            AsyncExecutor.getExecutor().submit(
                    () -> {
                        try {
                            Response response = executeWithInterceptors();
                            callback.onSuccess(response);
                        } catch (Exception e) {
                            callback.onFailure(enhanceException(e));
                        }
                    });
        }

        @Override
        public void cancel() {
            canceled = true;
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

            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request);
            return chain.proceed(request);
        }

        private Response executeInternal() throws IOException {
            long startTime = System.currentTimeMillis();

            HttpURLConnection connection = null;
            try {
                // 获取连接（使用连接池或直接创建）
                if (connectionPool != null) {
                    connection = connectionPool.get(request.getUrlString());
                } else {
                    URL url = request.getUrl();
                    java.net.Proxy proxy=client.getProxy();
                    if (proxy == null) {
                        connection = (HttpURLConnection) url.openConnection();
                    }else{
                        connection = (HttpURLConnection) url.openConnection(proxy);
                    }
                    
                }

                // 设置请求方法
                connection.setRequestMethod(request.getMethod());

                // 设置超时
                connection.setConnectTimeout(client.getConnectTimeout());
                connection.setReadTimeout(client.getReadTimeout());

                // 设置是否跟随重定向
                connection.setInstanceFollowRedirects(client.isFollowRedirects());

                // 设置请求头
                for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }

                // 设置请求体
                if (request.getBody() != null && !request.getMethod().equals("GET")) {
                    connection.setDoOutput(true);
                    connection
                            .getOutputStream()
                            .write(request.getBody().getBytes(StandardCharsets.UTF_8));
                }

                // 检查是否已取消
                if (canceled) {
                    throw new IOException("Request canceled");
                }

                // 获取响应
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                // 读取响应头
                Map<String, String> responseHeaders = new java.util.HashMap<>();
                for (Map.Entry<String, java.util.List<String>> entry :
                        connection.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                        responseHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }
                }

                // 读取响应体
                String responseBody = readResponseBody(connection);

                // 检查是否已取消
                if (canceled) {
                    throw new IOException("Request canceled");
                }

                long duration = System.currentTimeMillis() - startTime;

                // 构建响应
                Response.Builder builder =
                        responseCode >= 200 && responseCode < 300
                                ? Response.success(request)
                                : Response.failure(request);
                builder.code(responseCode)
                        .message(responseMessage)
                        .headers(responseHeaders)
                        .body(responseBody)
                        .duration(duration);

                return builder.build();

            } finally {
                if (connection != null) {
                    // 根据是否使用连接池来决定是否释放或断开连接
                    if (connectionPool != null) {
                        connectionPool.release(connection);
                    } else {
                        connection.disconnect();
                    }
                }
            }
        }

        private String readResponseBody(HttpURLConnection connection) throws IOException {
            InputStream inputStream = null;
            BufferedReader reader = null;
            try {
                // 判断是否为错误响应
                boolean isError = connection.getResponseCode() >= 400;
                inputStream = isError ? connection.getErrorStream() : connection.getInputStream();

                if (inputStream == null) {
                    return null;
                }

                reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

                StringBuilder result = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }

                return result.length() > 0 ? result.toString() : null;
            } finally {
                if (reader != null) {
                    reader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        }

        private JNetException enhanceException(Exception e) {
            if (e instanceof JNetException) {
                return (JNetException) e;
            }

            JNetException.Builder builder = JNetException.builder()
                    .message(e.getMessage())
                    .cause(e)
                    .requestUrl(request.getUrlString())
                    .requestMethod(request.getMethod());

            if (e instanceof IOException) {
                if (e.getMessage() != null) {
                    String msg = e.getMessage().toLowerCase();
                    if (msg.contains("timeout")) {
                        builder.errorType(JNetException.ErrorType.CONNECTION_TIMEOUT);
                    } else if (msg.contains("ssl")) {
                        builder.errorType(JNetException.ErrorType.SSL_HANDSHAKE_FAILED);
                    } else {
                        builder.errorType(JNetException.ErrorType.NETWORK_UNAVAILABLE);
                    }
                }
            } else {
                builder.errorType(JNetException.ErrorType.UNKNOWN);
            }

            return builder.build();
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
