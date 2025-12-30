package com.jnet.core;

import java.io.IOException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * SSE (Server-Sent Events) 客户端
 * 基于 JDK11 HttpClient 的 Reactive Streams 实现 (Flow API)
 * 真正非阻塞，无需为每个连接占用独立线程
 *
 * @author sanbo
 * @version 3.0.0
 */
public class SSEClient {

    private final HttpClient httpClient;
    private final Duration readTimeout;

    public SSEClient() {
        this.httpClient = JNetClient.getInstance().getHttpClient();
        this.readTimeout = Duration.ofSeconds(30);
    }

    public SSEClient(JNetClient client) {
        this.httpClient = client.getHttpClient();
        this.readTimeout = Duration.ofMillis(client.getReadTimeout());
    }

    /**
     * SSE 事件监听器
     */
    public interface SSEListener {
        void onData(String data);

        void onEvent(String event, String data);

        void onComplete();

        void onError(Exception e);
    }

    /**
     * 发送 SSE 请求
     */
    public void stream(String url, Map<String, String> headers, SSEListener listener) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET();

        if (headers != null) {
            headers.forEach(builder::header);
        }

        execute(builder.build(), listener);
    }

    /**
     * 发送 POST SSE 请求
     */
    public void streamPost(String url, String data, Map<String, String> headers, SSEListener listener) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache");

        if (headers != null) {
            headers.forEach(builder::header);
        }

        builder.POST(data != null ? HttpRequest.BodyPublishers.ofString(data) : HttpRequest.BodyPublishers.noBody());

        execute(builder.build(), listener);
    }

    private void execute(HttpRequest request, SSEListener listener) {
        httpClient.sendAsync(request, HttpResponse.BodyHandlers.fromLineSubscriber(new SSESubscriber(listener)))
                .whenComplete((response, throwable) -> {
                    if (throwable != null) {
                        listener.onError(toException(throwable));
                    } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        listener.onError(new IOException("HTTP " + response.statusCode()));
                    }
                });
    }

    // 兼容旧API
    public void close() {
        // Reactive implementation assumes client cancels subscription or connection
        // closes.
        // There is no explicit "executor" to shutdown anymore.
    }

    private static Exception toException(Throwable t) {
        if (t instanceof Exception)
            return (Exception) t;
        return new Exception(t);
    }

    /**
     * 处理 SSE 流的 Subscriber
     */
    private static class SSESubscriber implements Flow.Subscriber<String> {
        private final SSEListener listener;
        private Flow.Subscription subscription;
        private final StringBuilder eventData = new StringBuilder();
        private String currentEvent = null;

        public SSESubscriber(SSEListener listener) {
            this.listener = listener;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE); // 请求所有数据
        }

        @Override
        public void onNext(String line) {
            try {
                if (line.isEmpty()) {
                    // 空行表示事件结束
                    if (eventData.length() > 0) {
                        String data = eventData.toString();
                        listener.onData(data);
                        if (currentEvent != null) {
                            listener.onEvent(currentEvent, data);
                        }
                        eventData.setLength(0);
                        currentEvent = null; // 重置 Event
                    }
                    return;
                }

                if (line.startsWith("data:")) {
                    String cleanData = line.substring(5).trim(); // remove "data:"
                    // trim leading space usually. Standard says "If the value starts with a space,
                    // remove it."
                    // line.substring(5) includes space if "data: foo".
                    // Strict parsing:
                    // int colonIndex = line.indexOf(':'); String field = line.substring(0,
                    // colonIndex); String value = line.substring(colonIndex+1);
                    // if (value.startsWith(" ")) value = value.substring(1);

                    // Simple parsing compatible with previous logic:
                    if (eventData.length() > 0) {
                        eventData.append("\n");
                    }
                    eventData.append(cleanData);
                } else if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith("id:")) {
                    // ignore for now
                } else if (line.startsWith(":")) {
                    // comment
                }
            } catch (Exception e) {
                listener.onError(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            listener.onError(toException(throwable));
        }

        @Override
        public void onComplete() {
            // 发送剩余数据 (Standard SSE says only dispatch on empty line, but if stream ends?)
            // Usually stream ends means connection closed.
            listener.onComplete();
        }
    }
}
