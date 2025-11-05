package com.jnet.core;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * SSE (Server-Sent Events) 客户端
 * 支持接收服务器推送的实时数据流，类似于 OpenAI 的流式响应
 *
 * @author sanbo
 * @version 3.0.0
 */
public class SSEClient {

    private final JNetClient client;
    private ExecutorService executor;

    public SSEClient() {
        this.client = JNetClient.getInstance();
        this.executor = Executors.newCachedThreadPool();
    }

    /**
     * SSE 事件监听器
     */
    public interface SSEListener {
        /**
         * 收到数据
         */
        void onData(String data);

        /**
         * 收到完整事件
         */
        void onEvent(String event, String data);

        /**
         * 流结束
         */
        void onComplete();

        /**
         * 发生错误
         */
        void onError(Exception e);
    }

    /**
     * 发送 SSE 请求
     *
     * @param url 请求 URL
     * @param headers 请求头
     * @param listener 事件监听器
     */
    public void stream(String url, Map<String, String> headers, SSEListener listener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                // 创建连接
                java.net.URL sseUrl = new java.net.URL(url);
                connection = (HttpURLConnection) sseUrl.openConnection();

                // 设置请求方法
                connection.setRequestMethod("GET");

                // 设置 SSE 所需的头
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Connection", "keep-alive");

                // 设置自定义请求头
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }

                // 设置超时
                connection.setConnectTimeout(client.getConnectTimeout());
                connection.setReadTimeout(client.getReadTimeout());

                // 读取响应
                reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                StringBuilder eventData = new StringBuilder();
                String currentEvent = null;

                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 空行表示事件结束
                        if (eventData.length() > 0) {
                            String data = eventData.toString();
                            listener.onData(data);
                            if (currentEvent != null) {
                                listener.onEvent(currentEvent, data);
                            }
                            eventData.setLength(0);
                        }
                    } else if (line.startsWith("event:")) {
                        // 事件类型
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        // 事件数据
                        String data = line.substring(5).trim();
                        if (!eventData.isEmpty()) {
                            eventData.append("\n");
                        }
                        eventData.append(data);
                    } else if (line.startsWith("id:")) {
                        // 事件 ID (可用于重连)
                        String id = line.substring(3).trim();
                        // 可以实现重连逻辑
                    } else if (line.startsWith(":")) {
                        // 注释行，忽略
                    }
                }

                // 发送完成事件
                listener.onComplete();

            } catch (Exception e) {
                listener.onError(e);
            } finally {
                // 清理资源
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // 忽略
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * 发送 POST SSE 请求
     *
     * @param url 请求 URL
     * @param data POST 数据
     * @param headers 请求头
     * @param listener 事件监听器
     */
    public void streamPost(String url, String data, Map<String, String> headers, SSEListener listener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            BufferedReader reader = null;
            try {
                // 创建连接
                java.net.URL sseUrl = new java.net.URL(url);
                connection = (HttpURLConnection) sseUrl.openConnection();

                // 设置请求方法
                connection.setRequestMethod("POST");

                // 设置请求头
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("Accept", "text/event-stream");
                connection.setRequestProperty("Cache-Control", "no-cache");
                connection.setRequestProperty("Connection", "keep-alive");

                // 设置自定义请求头
                if (headers != null) {
                    for (Map.Entry<String, String> entry : headers.entrySet()) {
                        connection.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }

                // 发送 POST 数据
                if (data != null) {
                    connection.setDoOutput(true);
                    connection.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
                }

                // 设置超时
                connection.setConnectTimeout(client.getConnectTimeout());
                connection.setReadTimeout(client.getReadTimeout());

                // 读取响应
                reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                StringBuilder eventData = new StringBuilder();
                String currentEvent = null;

                while ((line = reader.readLine()) != null) {
                    if (line.isEmpty()) {
                        // 空行表示事件结束
                        if (eventData.length() > 0) {
                            String dataStr = eventData.toString();
                            listener.onData(dataStr);
                            if (currentEvent != null) {
                                listener.onEvent(currentEvent, dataStr);
                            }
                            eventData.setLength(0);
                        }
                    } else if (line.startsWith("event:")) {
                        // 事件类型
                        currentEvent = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        // 事件数据
                        String dataStr = line.substring(5).trim();
                        if (!eventData.isEmpty()) {
                            eventData.append("\n");
                        }
                        eventData.append(dataStr);
                    } else if (line.startsWith("id:")) {
                        // 事件 ID
                        String id = line.substring(3).trim();
                    } else if (line.startsWith(":")) {
                        // 注释行，忽略
                    }
                }

                listener.onComplete();

            } catch (Exception e) {
                listener.onError(e);
            } finally {
                // 清理资源
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (IOException e) {
                        // 忽略
                    }
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    /**
     * 关闭 SSE 客户端
     */
    public void close() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 使用示例 (类似 OpenAI 的流式响应)
     */
    public static void main(String[] args) {
        SSEClient sseClient = new SSEClient();

        // 示例：请求 OpenAI 风格的流式响应
        String url = "https://api.example.com/stream";
        String apiKey = "your-api-key";

        sseClient.streamPost(url, "{\"model\":\"gpt-3.5-turbo\",\"stream\":true}",
            Map.of("Authorization", "Bearer " + apiKey),
            new SSEListener() {
                @Override
                public void onData(String data) {
                    System.out.print(data);
                    System.out.flush();
                }

                @Override
                public void onEvent(String event, String data) {
                    if ("delta".equals(event) || "message".equals(event)) {
                        System.out.print(data);
                        System.out.flush();
                    }
                }

                @Override
                public void onComplete() {
                    System.out.println("\n=== Stream Complete ===");
                }

                @Override
                public void onError(Exception e) {
                    System.err.println("Error: " + e.getMessage());
                }
            }
        );

        // 注意：在实际应用中，应该适当时候调用 close()
        // sseClient.close();
    }
}
