package ff.jnezha.jnt.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 请求执行接口 - 负责实际的网络请求
 * 线程安全，支持同步和异步执行
 *
 * @author JNet Team
 * @version 3.0
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
        private volatile boolean executed;
        private volatile boolean canceled;

        public RealCall(Request request, JNetClient client) {
            this.request = request;
            this.client = client;
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
                return executeInternal();
            } finally {
                // 清理资源
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
            new Thread(() -> {
                try {
                    Response response = executeInternal();
                    callback.onSuccess(response);
                } catch (Exception e) {
                    callback.onFailure(e);
                }
            }).start();
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

        private Response executeInternal() throws IOException {
            long startTime = System.currentTimeMillis();

            HttpURLConnection connection = null;
            try {
                // 创建连接
                URL url = request.getUrl();
                connection = (HttpURLConnection) url.openConnection(
                        request.getClient().getProxy()
                );

                // 设置请求方法
                connection.setRequestMethod(request.getMethod());

                // 设置超时
                connection.setConnectTimeout(request.getClient().getConnectTimeout());
                connection.setReadTimeout(request.getClient().getReadTimeout());

                // 设置是否跟随重定向
                connection.setInstanceFollowRedirects(request.getClient().isFollowRedirects());

                // 设置请求头
                for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                    connection.setRequestProperty(entry.getKey(), entry.getValue());
                }

                // 设置请求体
                if (request.getBody() != null && !request.getMethod().equals("GET")) {
                    connection.setDoOutput(true);
                    connection.getOutputStream().write(request.getBody().getBytes(StandardCharsets.UTF_8));
                }

                // 获取响应
                int responseCode = connection.getResponseCode();
                String responseMessage = connection.getResponseMessage();

                // 读取响应头
                Map<String, String> responseHeaders = new java.util.HashMap<>();
                for (Map.Entry<String, java.util.List<String>> entry : connection.getHeaderFields().entrySet()) {
                    if (entry.getKey() != null && !entry.getValue().isEmpty()) {
                        responseHeaders.put(entry.getKey(), entry.getValue().get(0));
                    }
                }

                // 读取响应体
                String responseBody = readResponseBody(connection);

                long duration = System.currentTimeMillis() - startTime;

                // 构建响应
                Response.Builder builder = Response.success(request)
                        .code(responseCode)
                        .message(responseMessage)
                        .headers(responseHeaders)
                        .body(responseBody)
                        .duration(duration);

                return builder.build();

            } finally {
                if (connection != null) {
                    connection.disconnect();
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

                reader = new BufferedReader(
                        new InputStreamReader(inputStream, StandardCharsets.UTF_8)
                );

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
