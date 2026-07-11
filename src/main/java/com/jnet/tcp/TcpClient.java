package com.jnet.tcp;

import com.jnet.core.AsyncExecutor;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * TCP Client - Main entry point for TCP operations
 * Similar to JNetClient for HTTP, provides Python requests-style API
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpClient implements AutoCloseable {
    private static volatile TcpClient instance;
    private final TcpConfig config;
    private final AtomicInteger activeSessionCount;
    private final AtomicLong totalRequestsCount;

    private TcpClient(Builder builder) {
        this.config = builder.configBuilder != null
                ? builder.configBuilder.build()
                : TcpConfig.defaultConfig();
        this.activeSessionCount = new AtomicInteger(0);
        this.totalRequestsCount = new AtomicLong(0);
    }

    // ========== Factory Methods ==========

    /**
     * Get singleton instance
     */
    public static TcpClient getInstance() {
        if (instance == null) {
            synchronized (TcpClient.class) {
                if (instance == null) {
                    instance = new Builder().build();
                }
            }
        }
        return instance;
    }

    /**
     * Create a new client instance
     */
    public static TcpClient create() {
        return new Builder().build();
    }

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Public API - Simple Methods ==========

    /**
     * Quick TCP send and receive (like Python socket connect)
     * @param host Target host
     * @param port Target port
     * @return Response data as string
     */
    public static String send(String host, int port) throws IOException {
        return send(host, port, (byte[]) null);
    }

    /**
     * Quick TCP send data and receive response
     * @param host Target host
     * @param port Target port
     * @param data Data to send (will be UTF-8 encoded)
     * @return Response data as string
     */
    public static String send(String host, int port, String data) throws IOException {
        return send(host, port, data != null
                ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8)
                : null);
    }

    /**
     * Quick TCP send byte data and receive response
     * @param host Target host
     * @param port Target port
     * @param data Data to send
     * @return Response data as string
     */
    public static String send(String host, int port, byte[] data) throws IOException {
        TcpRequest request = TcpRequest.newBuilder()
                .host(host)
                .port(port)
                .data(data)
                .build();
        TcpResponse response = send(request);
        return response.getDataAsString();
    }

    /**
     * Send with request object
     */
    public static TcpResponse send(TcpRequest request) throws IOException {
        TcpClient client = TcpClient.getInstance();
        return client.execute(request);
    }

    /**
     * Execute request and get response
     */
    public TcpResponse execute(TcpRequest request) throws IOException {
        return execute(request, null);
    }

    private TcpResponse execute(TcpRequest request, CancellableTcpFuture<?> cancellation)
            throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        long started = System.nanoTime();
        totalRequestsCount.incrementAndGet();
        activeSessionCount.incrementAndGet();
        try {
            TcpSession session = newSessionForRequest(request);
            if (cancellation != null && !cancellation.attachSession(session)) {
                throw new CancellationException("TCP request canceled");
            }
            try (session) {
                session.connect();
                session.send(request.dataUnsafe());
                session.shutdownOutput();
                byte[] response = session.receiveAll();

                return TcpResponse.success()
                        .host(request.getHost(), request.getPort())
                        .bytesRead(response.length)
                        .data(response)
                        .request(request)
                        .duration((System.nanoTime() - started) / 1_000_000L)
                        .build();
            } finally {
                if (cancellation != null) {
                    cancellation.detachSession(session);
                }
            }
        } finally {
            activeSessionCount.decrementAndGet();
        }
    }

    /**
     * Async TCP request
     */
    public static CompletableFuture<String> sendAsync(String host, int port, String data) {
        return sendAsync(() -> TcpRequest.newBuilder()
                .host(host)
                .port(port)
                .data(data)
                .build());
    }

    /**
     * Async TCP request with byte data
     */
    public static CompletableFuture<String> sendAsync(String host, int port, byte[] data) {
        return sendAsync(() -> TcpRequest.newBuilder()
                .host(host)
                .port(port)
                .data(data)
                .build());
    }

    private static CompletableFuture<String> sendAsync(RequestFactory requestFactory) {
        final TcpRequest request;
        try {
            request = requestFactory.create();
        } catch (RuntimeException error) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(error);
            return failed;
        }
        CancellableTcpFuture<String> result = new CancellableTcpFuture<>();
        Future<?> task = AsyncExecutor.getExecutor().submit(() -> {
            try {
                TcpResponse response = TcpClient.getInstance().execute(request, result);
                result.complete(response.getDataAsString());
            } catch (CancellationException ignored) {
                // cancel() already completed the public future and closed the active socket.
            } catch (Throwable error) {
                if (!result.isCancelled()) {
                    result.completeExceptionally(error);
                }
            } finally {
                result.clearTask();
            }
        });
        result.attachTask(task);
        return result;
    }

    @FunctionalInterface
    private interface RequestFactory {
        TcpRequest create();
    }

    private static final class CancellableTcpFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<TcpSession> activeSession = new AtomicReference<>();
        private final AtomicReference<Future<?>> activeTask = new AtomicReference<>();

        private boolean attachSession(TcpSession session) {
            activeSession.set(session);
            if (isCancelled()) {
                if (activeSession.compareAndSet(session, null)) {
                    session.close();
                }
                return false;
            }
            return true;
        }

        private void detachSession(TcpSession session) {
            activeSession.compareAndSet(session, null);
        }

        private void attachTask(Future<?> task) {
            activeTask.set(task);
            if (isCancelled() && activeTask.compareAndSet(task, null)) {
                task.cancel(true);
            } else if (isDone()) {
                activeTask.compareAndSet(task, null);
            }
        }

        private void clearTask() {
            activeTask.set(null);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean canceled = super.cancel(mayInterruptIfRunning);
            if (canceled) {
                TcpSession session = activeSession.getAndSet(null);
                if (session != null) {
                    session.close();
                }
                Future<?> task = activeTask.getAndSet(null);
                if (task != null) {
                    task.cancel(mayInterruptIfRunning);
                }
            }
            return canceled;
        }
    }

    // ========== Public API - Session Management ==========

    /**
     * Create new request builder
     */
    public TcpRequest.Builder newRequest(String host, int port) {
        return TcpRequest.newBuilder()
                .host(host)
                .port(port);
    }

    /**
     * Create new session (persistent connection)
     */
    public TcpSession newSession(String host, int port) {
        return createSessionBuilder(host, port, null)
                .build();
    }

    /**
     * Create new session with timeout
     */
    public TcpSession newSession(String host, int port, Duration timeout) {
        return createSessionBuilder(host, port, timeout)
                .build();
    }

    /**
     * Get statistics
     */
    public int getActiveSessionCount() {
        return activeSessionCount.get();
    }

    public long getTotalRequestsCount() {
        return totalRequestsCount.get();
    }

    @Override
    public void close() {
        // No persistent resources to release; sessions are managed per request.
    }

    // ========== Internal Methods ==========

    /**
     * Create session from request
     */
    private TcpSession newSessionForRequest(TcpRequest request) throws IOException {
        Duration timeout = request.getTimeout() > 0
                ? Duration.ofMillis(request.getTimeout())
                : config.getReadTimeout();

        TcpSession.Builder builder = createSessionBuilder(request.getHost(), request.getPort(), timeout);
        if (request.getSessionId() != null) {
            builder.sessionId(request.getSessionId());
        }
        return builder.build();
    }

    private TcpSession.Builder createSessionBuilder(String host, int port, Duration timeout) {
        Duration readTimeout = timeout != null ? timeout : config.getReadTimeout();

        return TcpSession.newBuilder()
                .host(host, port)
                .connectTimeout(config.getConnectTimeout())
                .readTimeout(readTimeout)
                .keepAlive(config.isKeepAlive())
                .tcpNoDelay(config.isTcpNoDelay())
                .sendBufferSize(config.getSendBufferSize())
                .receiveBufferSize(config.getReceiveBufferSize())
                .soTimeout(config.getSoTimeout())
                .soReuseAddress(config.isSoReuseAddress())
                .trafficClass(config.getTrafficClass())
                .autoReconnect(config.isAutoReconnect())
                .maxReconnectAttempts(config.getMaxReconnectAttempts())
                .reconnectDelay(config.getReconnectDelay());
    }

    // ========== Config Getters ==========

    public TcpConfig getConfig() {
        return config;
    }

    // ========== Builder ==========

    /**
     * TCP Client Builder
     */
    public static class Builder {
        private TcpConfig.Builder configBuilder;

        public Builder() {
            // Config builder
            this.configBuilder = TcpConfig.newBuilder();
        }

        /**
         * Set TCP configuration
         */
        @SuppressWarnings("deprecation")
        public Builder config(TcpConfig config) {
            if (config == null) {
                this.configBuilder = TcpConfig.newBuilder();
                return this;
            }
            this.configBuilder = TcpConfig.newBuilder()
                    .connectTimeout(config.getConnectTimeout())
                    .readTimeout(config.getReadTimeout())
                    .writeTimeout(config.getWriteTimeout())
                    .keepAlive(config.isKeepAlive())
                    .tcpNoDelay(config.isTcpNoDelay())
                    .sendBufferSize(config.getSendBufferSize())
                    .receiveBufferSize(config.getReceiveBufferSize())
                    .autoReconnect(config.isAutoReconnect())
                    .maxReconnectAttempts(config.getMaxReconnectAttempts())
                    .reconnectDelay(config.getReconnectDelay())
                    .soTimeout(config.getSoTimeout())
                    .soReuseAddress(config.isSoReuseAddress())
                    .trafficClass(config.getTrafficClass());
            return this;
        }

        /**
         * Set connect timeout
         */
        public Builder connectTimeout(Duration timeout) {
            this.configBuilder.connectTimeout(timeout);
            return this;
        }

        /**
         * Set read timeout
         */
        public Builder readTimeout(Duration timeout) {
            this.configBuilder.readTimeout(timeout);
            return this;
        }

        /**
         * Retained for API compatibility; blocking {@code Socket} has no portable write timeout.
         * @deprecated Configure application-level framing and cancellation instead.
         */
        @Deprecated
        public Builder writeTimeout(Duration timeout) {
            this.configBuilder.writeTimeout(timeout);
            return this;
        }

        /**
         * Enable/disable keep-alive
         */
        public Builder keepAlive(boolean keepAlive) {
            this.configBuilder.keepAlive(keepAlive);
            return this;
        }

        /**
         * Enable/disable TCP_NODELAY
         */
        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.configBuilder.tcpNoDelay(tcpNoDelay);
            return this;
        }

        /**
         * Enable/disable auto reconnect
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.configBuilder.autoReconnect(autoReconnect);
            return this;
        }

        /**
         * Set max reconnect attempts
         */
        public Builder maxReconnectAttempts(int attempts) {
            this.configBuilder.maxReconnectAttempts(attempts);
            return this;
        }

        /**
         * Set reconnect delay
         */
        public Builder reconnectDelay(long delayMs) {
            this.configBuilder.reconnectDelay(delayMs);
            return this;
        }

        /**
         * Set socket timeout
         */
        public Builder soTimeout(int timeoutMs) {
            this.configBuilder.soTimeout(timeoutMs);
            return this;
        }

        /**
         * Enable/disable address reuse
         */
        public Builder soReuseAddress(boolean reuse) {
            this.configBuilder.soReuseAddress(reuse);
            return this;
        }

        /** Set the raw 8-bit IPv4 TOS / IPv6 traffic-class value (0-255). */
        public Builder trafficClass(int trafficClass) {
            this.configBuilder.trafficClass(trafficClass);
            return this;
        }

        /**
         * Set a unified timeout for connect/read/write.
         */
        @SuppressWarnings("deprecation")
        public Builder timeout(Duration timeout) {
            return connectTimeout(timeout)
                    .readTimeout(timeout)
                    .writeTimeout(timeout);
        }

        /**
         * Create a request builder (convenience).
         */
        public TcpRequest.Builder newRequest(String host, int port) {
            return TcpRequest.newBuilder()
                    .host(host)
                    .port(port);
        }

        /**
         * Build TcpClient
         */
        public TcpClient build() {
            return new TcpClient(this);
        }
    }
}
