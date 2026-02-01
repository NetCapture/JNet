package com.jnet.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * 增强版 SSE 客户端
 * Phase 3 功能：自动重连、心跳检测、事件过滤、Last-Event-ID
 */
public class SSEClientEnhanced {
    
    private final HttpClient httpClient;
    private final Duration readTimeout;
    
    // Phase 3.1: Auto-reconnection
    private final int maxRetries;
    private final long initialRetryDelay;
    private final double retryBackoffMultiplier;
    
    // Phase 3.2: Heartbeat
    private final long heartbeatInterval;
    private final ScheduledExecutorService heartbeatExecutor;
    private final AtomicLong lastEventTime = new AtomicLong(System.currentTimeMillis());
    
    // Phase 3.3: Event Filtering
    private Predicate<SSEEvent> eventFilter;
    
    // Phase 3.4: Last-Event-ID
    private volatile String lastEventId;
    
    private volatile boolean running = false;
    private final AtomicInteger reconnectCount = new AtomicInteger(0);

    private SSEClientEnhanced(Builder builder) {
        this.httpClient = builder.httpClient != null ? builder.httpClient : JNetClient.getInstance().getHttpClient();
        this.readTimeout = builder.readTimeout;
        this.maxRetries = builder.maxRetries;
        this.initialRetryDelay = builder.initialRetryDelay;
        this.retryBackoffMultiplier = builder.retryBackoffMultiplier;
        this.heartbeatInterval = builder.heartbeatInterval;
        this.heartbeatExecutor = builder.heartbeatInterval > 0 
            ? Executors.newSingleThreadScheduledExecutor() 
            : null;
        this.eventFilter = builder.eventFilter;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * SSE 事件对象
     */
    public static class SSEEvent {
        private final String id;
        private final String event;
        private final String data;
        private final long timestamp;

        public SSEEvent(String id, String event, String data) {
            this.id = id;
            this.event = event;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getEvent() { return event; }
        public String getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    /**
     * 增强版 SSE 监听器
     */
    public interface EnhancedSSEListener {
        void onEvent(SSEEvent event);
        void onError(Exception e);
        void onReconnect(int attempt);
        default void onHeartbeatTimeout() {}
        default void onComplete() {}
    }

    /**
     * 连接 SSE 流（支持自动重连）
     */
    public void connect(String url, Map<String, String> headers, EnhancedSSEListener listener) {
        running = true;
        reconnectCount.set(0);
        connectWithRetry(url, headers, listener, 0);
    }

    private void connectWithRetry(String url, Map<String, String> headers, 
                                   EnhancedSSEListener listener, int attempt) {
        if (!running || attempt >= maxRetries) {
            if (attempt >= maxRetries) {
                listener.onError(new IOException("Max reconnection attempts reached: " + maxRetries));
            }
            return;
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(readTimeout)
                .header("Accept", "text/event-stream")
                .header("Cache-Control", "no-cache")
                .GET();

        // Phase 3.4: Add Last-Event-ID header
        if (lastEventId != null) {
            builder.header("Last-Event-ID", lastEventId);
        }

        if (headers != null) {
            headers.forEach(builder::header);
        }

        // Start heartbeat monitoring
        if (heartbeatExecutor != null) {
            startHeartbeatMonitoring(listener);
        }

        httpClient.sendAsync(builder.build(), 
                HttpResponse.BodyHandlers.fromLineSubscriber(
                    new EnhancedSSESubscriber(listener, this)))
                .whenComplete((response, throwable) -> {
                    if (throwable != null || (response != null && response.statusCode() >= 400)) {
                        handleConnectionFailure(url, headers, listener, attempt);
                    }
                });
    }

    private void handleConnectionFailure(String url, Map<String, String> headers,
                                         EnhancedSSEListener listener, int attempt) {
        if (!running) return;

        int nextAttempt = attempt + 1;
        reconnectCount.incrementAndGet();
        listener.onReconnect(nextAttempt);

        // Exponential backoff
        long delay = (long) (initialRetryDelay * Math.pow(retryBackoffMultiplier, attempt));
        delay = Math.min(delay, 60000); // Max 60 seconds

        CompletableFuture.delayedExecutor(delay, TimeUnit.MILLISECONDS)
                .execute(() -> connectWithRetry(url, headers, listener, nextAttempt));
    }

    /**
     * Phase 3.2: Heartbeat monitoring
     */
    private void startHeartbeatMonitoring(EnhancedSSEListener listener) {
        if (heartbeatExecutor == null) return;

        heartbeatExecutor.scheduleAtFixedRate(() -> {
            long timeSinceLastEvent = System.currentTimeMillis() - lastEventTime.get();
            if (timeSinceLastEvent > heartbeatInterval * 2) {
                listener.onHeartbeatTimeout();
                // Optionally trigger reconnection
            }
        }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
    }

    /**
     * Phase 3.3: Set event filter
     */
    public void setEventFilter(Predicate<SSEEvent> filter) {
        this.eventFilter = filter;
    }

    /**
     * Disconnect and stop reconnection attempts
     */
    public void disconnect() {
        running = false;
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdown();
        }
    }

    public int getReconnectCount() {
        return reconnectCount.get();
    }

    public String getLastEventId() {
        return lastEventId;
    }

    /**
     * Enhanced SSE Subscriber
     */
    private class EnhancedSSESubscriber implements java.util.concurrent.Flow.Subscriber<String> {
        private final EnhancedSSEListener listener;
        private final SSEClientEnhanced client;
        private java.util.concurrent.Flow.Subscription subscription;
        
        private final StringBuilder eventData = new StringBuilder();
        private String currentEvent = null;
        private String currentId = null;

        public EnhancedSSESubscriber(EnhancedSSEListener listener, SSEClientEnhanced client) {
            this.listener = listener;
            this.client = client;
        }

        @Override
        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(String line) {
            try {
                client.lastEventTime.set(System.currentTimeMillis());

                if (line.isEmpty()) {
                    // Event complete
                    if (eventData.length() > 0) {
                        SSEEvent event = new SSEEvent(currentId, currentEvent, eventData.toString());
                        
                        // Phase 3.4: Update last event ID
                        if (currentId != null) {
                            client.lastEventId = currentId;
                        }
                        
                        // Phase 3.3: Apply filter
                        if (client.eventFilter == null || client.eventFilter.test(event)) {
                            listener.onEvent(event);
                        }
                        
                        eventData.setLength(0);
                        currentEvent = null;
                        currentId = null;
                    }
                    return;
                }

                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (eventData.length() > 0) {
                        eventData.append('\n');
                    }
                    eventData.append(data);
                } else if (line.startsWith("event:")) {
                    currentEvent = line.substring(6).trim();
                } else if (line.startsWith("id:")) {
                    currentId = line.substring(3).trim();
                } else if (line.startsWith(":")) {
                    // Comment - ignore
                }
            } catch (Exception e) {
                listener.onError(e);
            }
        }

        @Override
        public void onError(Throwable throwable) {
            listener.onError(throwable instanceof Exception ? 
                (Exception) throwable : new Exception(throwable));
        }

        @Override
        public void onComplete() {
            listener.onComplete();
        }
    }

    public static class Builder {
        private HttpClient httpClient;
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxRetries = 5;
        private long initialRetryDelay = 1000;
        private double retryBackoffMultiplier = 2.0;
        private long heartbeatInterval = 30000; // 30 seconds
        private Predicate<SSEEvent> eventFilter;

        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        public Builder initialRetryDelay(long millis) {
            this.initialRetryDelay = millis;
            return this;
        }

        public Builder retryBackoffMultiplier(double multiplier) {
            this.retryBackoffMultiplier = multiplier;
            return this;
        }

        public Builder heartbeatInterval(long millis) {
            this.heartbeatInterval = millis;
            return this;
        }

        public Builder eventFilter(Predicate<SSEEvent> filter) {
            this.eventFilter = filter;
            return this;
        }

        public SSEClientEnhanced build() {
            return new SSEClientEnhanced(this);
        }
    }
}
