package com.jnet.tcp;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

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
        return send(host, port, data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    /**
     * Quick TCP send byte data and receive response
     * @param host Target host
     * @param port Target port
     * @param data Data to send
     * @return Response data as string
     */
    public static String send(String host, int port, byte[] data) throws IOException {
        return send(host, port, data, null);
    }

    /**
     * Send data and receive response (with request)
     */
    private static String send(String host, int port, byte[] data, Duration timeout) throws IOException {
        TcpRequest request = TcpRequest.newBuilder()
                .host(host)
                .port(port)
                .data(data)
                .timeout(timeout != null ? (int) timeout.toMillis() : 0)
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
        try (TcpSession session = newSession(request)) {
            session.connect();
            session.send(request.getData());
            byte[] response = session.receive();
            session.close();

            return TcpResponse.success()
                    .host(request.getHost(), request.getPort())
                    .bytesRead(response.length)
                    .data(response)
                    .request(request)
                    .duration(0)
                    .build();
        } catch (IOException e) {
            return TcpResponse.failure()
                    .errorMessage(e.getMessage())
                    .errorCode(getErrorCode(e))
                    .build();
        }
    }

    /**
     * Async TCP request
     */
    public static CompletableFuture<String> sendAsync(String host, int port, String data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(host, port, data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Async TCP request with byte data
     */
    public static CompletableFuture<String> sendAsync(String host, int port, byte[] data) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return send(host, port, data);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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
        return TcpSession.newBuilder()
                .host(host, port)
                .build();
    }

    /**
     * Create new session with timeout
     */
    public TcpSession newSession(String host, int port, Duration timeout) {
        return TcpSession.newBuilder()
                .host(host, port)
                .readTimeout(timeout)
                .writeTimeout(timeout)
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
    private static TcpSession newSession(TcpRequest request) throws IOException {
        if (request.getSessionId() != null) {
            return TcpSession.newBuilder()
                    .host(request.getHost(), request.getPort())
                    .sessionId(request.getSessionId())
                    .build();
        } else {
            return TcpSession.newBuilder()
                    .host(request.getHost(), request.getPort())
                    .build();
        }
    }

    /**
     * Get error code from exception
     */
    private static int getErrorCode(IOException e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("Connection refused")) {
                return 111; // ECONNREFUSED
            } else if (message.contains("Connection timed out")) {
                return 110; // ETIMEDOUT
            } else if (message.contains("Connection reset")) {
                return 104; // ECONNRESET
            } else if (message.contains("Broken pipe")) {
                return 103; // ECONNABORTED
            }
        }
        return 0; // Unknown error
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
         * Set write timeout
         */
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

        /**
         * Set traffic class
         */
        public Builder trafficClass(int trafficClass) {
            this.configBuilder.trafficClass(trafficClass);
            return this;
        }

        /**
         * Set a unified timeout for connect/read/write.
         */
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
