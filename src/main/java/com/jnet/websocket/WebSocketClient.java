package com.jnet.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket 客户端 - 基于 JDK 11 原生 API
 * Phase 5 功能：连接管理、Ping/Pong 心跳、自动重连
 */
public class WebSocketClient {
    
    private final HttpClient httpClient;
    private final Duration connectTimeout;
    private volatile WebSocket webSocket;
    private volatile String currentUrl;
    
    // Phase 5.2: Ping/Pong heartbeat
    private final long pingInterval;
    private final ScheduledExecutorService pingExecutor;
    private final AtomicLong lastPongTime = new AtomicLong(System.currentTimeMillis());
    
    // Phase 5.3: Auto-reconnection
    private final int maxReconnectAttempts;
    private final long reconnectDelay;
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private volatile int reconnectAttempts = 0;
    
    private final WebSocketListener listener;

    private WebSocketClient(Builder builder) {
        this.httpClient = builder.httpClient != null ? builder.httpClient : HttpClient.newHttpClient();
        this.connectTimeout = builder.connectTimeout;
        this.listener = builder.listener;
        this.pingInterval = builder.pingInterval;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelay = builder.reconnectDelay;
        this.pingExecutor = builder.pingInterval > 0 ? 
            Executors.newSingleThreadScheduledExecutor() : null;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * WebSocket 事件监听器
     */
    public interface WebSocketListener {
        default void onOpen(WebSocket webSocket) {}
        default void onMessage(String message) {}
        default void onBinaryMessage(byte[] data) {}
        default void onPing(ByteBuffer data) {}
        default void onPong(ByteBuffer data) {}
        default void onClose(int statusCode, String reason) {}
        default void onError(Throwable error) {}
        default void onReconnecting(int attempt) {}
    }

    /**
     * 连接到 WebSocket 服务器
     */
    public CompletableFuture<WebSocket> connect(String url) {
        this.currentUrl = url;
        return connectInternal(url);
    }

    private CompletableFuture<WebSocket> connectInternal(String url) {
        return httpClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(URI.create(url), new WebSocket.Listener() {
                    
                    @Override
                    public void onOpen(WebSocket webSocket) {
                        WebSocketClient.this.webSocket = webSocket;
                        reconnectAttempts = 0; // Reset on successful connection
                        
                        if (listener != null) {
                            listener.onOpen(webSocket);
                        }
                        
                        // Phase 5.2: Start ping/pong heartbeat
                        if (pingExecutor != null) {
                            startPingPong();
                        }
                        
                        webSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        if (listener != null) {
                            listener.onMessage(data.toString());
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                        if (listener != null) {
                            byte[] bytes = new byte[data.remaining()];
                            data.get(bytes);
                            listener.onBinaryMessage(bytes);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onPing(WebSocket webSocket, ByteBuffer message) {
                        if (listener != null) {
                            listener.onPing(message);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onPong(WebSocket webSocket, ByteBuffer message) {
                        lastPongTime.set(System.currentTimeMillis());
                        if (listener != null) {
                            listener.onPong(message);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        if (listener != null) {
                            listener.onClose(statusCode, reason);
                        }
                        
                        // Phase 5.3: Auto-reconnection
                        if (shouldReconnect.get() && reconnectAttempts < maxReconnectAttempts) {
                            scheduleReconnect();
                        }
                        
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        if (listener != null) {
                            listener.onError(error);
                        }
                        
                        // Phase 5.3: Auto-reconnection on error
                        if (shouldReconnect.get() && reconnectAttempts < maxReconnectAttempts) {
                            scheduleReconnect();
                        }
                    }
                });
    }

    /**
     * Phase 5.2: Start ping/pong heartbeat monitoring
     */
    private void startPingPong() {
        if (pingExecutor == null) return;
        
        pingExecutor.scheduleAtFixedRate(() -> {
            if (webSocket != null && !webSocket.isOutputClosed()) {
                // Send ping
                webSocket.sendPing(ByteBuffer.allocate(0));
                
                // Check if pong response is overdue
                long timeSinceLastPong = System.currentTimeMillis() - lastPongTime.get();
                if (timeSinceLastPong > pingInterval * 3) {
                    // No pong received for 3 ping intervals - connection may be dead
                    if (listener != null) {
                        listener.onError(new Exception("Ping timeout - no pong received"));
                    }
                    // Trigger reconnection
                    if (shouldReconnect.get()) {
                        webSocket.abort();
                        scheduleReconnect();
                    }
                }
            }
        }, pingInterval, pingInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 5.3: Schedule reconnection attempt
     */
    private void scheduleReconnect() {
        reconnectAttempts++;
        
        if (reconnectAttempts > maxReconnectAttempts) {
            return;
        }
        
        if (listener != null) {
            listener.onReconnecting(reconnectAttempts);
        }
        
        CompletableFuture.delayedExecutor(reconnectDelay * reconnectAttempts, TimeUnit.MILLISECONDS)
                .execute(() -> {
                    if (shouldReconnect.get() && currentUrl != null) {
                        connectInternal(currentUrl);
                    }
                });
    }

    /**
     * 发送文本消息
     */
    public CompletableFuture<WebSocket> sendText(String text) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected"));
        }
        return webSocket.sendText(text, true);
    }

    /**
     * 发送二进制消息
     */
    public CompletableFuture<WebSocket> sendBinary(byte[] data) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected"));
        }
        return webSocket.sendBinary(ByteBuffer.wrap(data), true);
    }

    /**
     * 发送 Ping 帧
     */
    public CompletableFuture<WebSocket> sendPing(ByteBuffer data) {
        if (webSocket == null || webSocket.isOutputClosed()) {
            return CompletableFuture.failedFuture(
                new IllegalStateException("WebSocket not connected"));
        }
        return webSocket.sendPing(data);
    }

    /**
     * 关闭连接
     */
    public CompletableFuture<WebSocket> close() {
        return close(WebSocket.NORMAL_CLOSURE, "");
    }

    /**
     * 关闭连接并指定状态码和原因
     */
    public CompletableFuture<WebSocket> close(int statusCode, String reason) {
        shouldReconnect.set(false); // Disable auto-reconnection
        
        if (pingExecutor != null) {
            pingExecutor.shutdown();
        }
        
        if (webSocket == null) {
            return CompletableFuture.completedFuture(null);
        }
        
        return webSocket.sendClose(statusCode, reason);
    }

    /**
     * 强制断开连接
     */
    public void abort() {
        shouldReconnect.set(false);
        
        if (pingExecutor != null) {
            pingExecutor.shutdown();
        }
        
        if (webSocket != null) {
            webSocket.abort();
        }
    }

    /**
     * 检查连接状态
     */
    public boolean isConnected() {
        return webSocket != null && !webSocket.isOutputClosed();
    }

    /**
     * 获取重连次数
     */
    public int getReconnectAttempts() {
        return reconnectAttempts;
    }

    public static class Builder {
        private HttpClient httpClient;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private WebSocketListener listener;
        private long pingInterval = 30000; // 30 seconds
        private int maxReconnectAttempts = 5;
        private long reconnectDelay = 1000; // 1 second base delay

        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder listener(WebSocketListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder pingInterval(long millis) {
            this.pingInterval = millis;
            return this;
        }

        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        public Builder reconnectDelay(long millis) {
            this.reconnectDelay = millis;
            return this;
        }

        public Builder disablePing() {
            this.pingInterval = 0;
            return this;
        }

        public Builder disableReconnect() {
            this.maxReconnectAttempts = 0;
            return this;
        }

        public WebSocketClient build() {
            return new WebSocketClient(this);
        }
    }
}
