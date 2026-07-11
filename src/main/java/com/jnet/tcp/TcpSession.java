package com.jnet.tcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;

/**
 * Persistent TCP Session
 * Maintains a socket connection for multiple send/receive operations
 * Thread-safe operations through synchronization
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpSession implements AutoCloseable {
    private static final int DEFAULT_MAX_RECEIVE_BYTES = 64 * 1024 * 1024;

    private volatile Socket socket;
    private final String host;
    private final int port;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final String sessionId;
    private final boolean keepAlive;
    private final boolean tcpNoDelay;
    private final int sendBufferSize;
    private final int receiveBufferSize;
    private final int soTimeout;
    private final boolean soReuseAddress;
    private final int trafficClass;
    private final boolean autoReconnect;
    private final int maxReconnectAttempts;
    private final long reconnectDelay;

    private volatile boolean connected = false;
    private volatile boolean closed = false;
    private boolean connecting;
    private volatile int reconnectCount = 0;
    private final Object lock = new Object();
    private final Object readLock = new Object();
    private final Object writeLock = new Object();

    private TcpSession(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.sessionId = builder.sessionId;
        this.keepAlive = builder.keepAlive;
        this.tcpNoDelay = builder.tcpNoDelay;
        this.sendBufferSize = builder.sendBufferSize;
        this.receiveBufferSize = builder.receiveBufferSize;
        this.soTimeout = builder.soTimeout;
        this.soReuseAddress = builder.soReuseAddress;
        this.trafficClass = builder.trafficClass;
        this.autoReconnect = builder.autoReconnect;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelay = builder.reconnectDelay;
        this.socket = new Socket();
    }

    // ========== Factory Methods ==========

    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Public API ==========

    /**
     * Send byte array data
     */
    public void send(byte[] data) throws IOException {
        ensureConnected();
        if (data == null || data.length == 0) {
            return;
        }
        synchronized (writeLock) {
            Socket current = requireConnectedSocket();
            try {
                OutputStream out = current.getOutputStream();
                out.write(data);
                out.flush();
            } catch (IOException error) {
                invalidate(current);
                throw error;
            }
        }
    }

    /**
     * Send string data (UTF-8 encoded)
     */
    public void send(String data) throws IOException {
        send(data != null ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8) : null);
    }

    /**
     * Send data with automatic line termination
     */
    public void sendLine(String data) throws IOException {
        send(data + "\r\n");
    }

    /**
     * Receive one chunk with a single blocking read. Protocol framing is left to callers.
     */
    public byte[] receive() throws IOException {
        return receive(0);
    }

    /**
     * Receive data with timeout
     */
    public byte[] receive(int timeoutMs) throws IOException {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        synchronized (readLock) {
            ensureConnected();
            Socket current = requireConnectedSocket();
            current.setSoTimeout(timeoutMs > 0 ? timeoutMs : configuredReadTimeoutMillis());
            try {
                byte[] data = readOnce(current.getInputStream());
                if (data.length == 0) {
                    invalidate(current);
                }
                return data;
            } catch (IOException error) {
                if (!(error instanceof SocketTimeoutException)) {
                    invalidate(current);
                }
                throw error;
            }
        }
    }

    /**
     * Receive string (UTF-8)
     */
    public String receiveString() throws IOException {
        return receiveString(0);
    }

    /**
     * Receive string with timeout
     */
    public String receiveString(int timeoutMs) throws IOException {
        byte[] data = receive(timeoutMs);
        return data != null ? new String(data, java.nio.charset.StandardCharsets.UTF_8) : null;
    }

    /**
     * Receive until connection closed
     */
    public byte[] receiveAll() throws IOException {
        return receiveAll(DEFAULT_MAX_RECEIVE_BYTES);
    }

    /**
     * Receive until the peer closes its output, rejecting responses larger than {@code maxBytes}.
     */
    public byte[] receiveAll(int maxBytes) throws IOException {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Maximum response size must be positive");
        }
        synchronized (readLock) {
            ensureConnected();
            Socket current = requireConnectedSocket();
            current.setSoTimeout(configuredReadTimeoutMillis());
            try {
                byte[] data = readUntilClosed(current.getInputStream(), maxBytes);
                invalidate(current);
                return data;
            } catch (IOException error) {
                // receiveAll consumes an unknown prefix before failing. The stream can no longer
                // be framed safely, including after a timeout, so never expose it for reuse.
                invalidate(current);
                throw error;
            }
        }
    }

    /**
     * Get input stream for streaming
     */
    public InputStream getInputStream() throws IOException {
        ensureConnected();
        return requireConnectedSocket().getInputStream();
    }

    /**
     * Get output stream for streaming
     */
    public OutputStream getOutputStream() throws IOException {
        ensureConnected();
        return requireConnectedSocket().getOutputStream();
    }

    /**
     * Check if connection is alive
     */
    public boolean isConnected() {
        synchronized (lock) {
            return connected && !closed && socket != null
                    && socket.isConnected() && !socket.isClosed();
        }
    }

    /**
     * Check if session is closed
     */
    public boolean isClosed() {
        synchronized (lock) {
            return closed || socket == null || socket.isClosed();
        }
    }

    /**
     * Get session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get remote address
     */
    public SocketAddress getRemoteAddress() {
        Socket current = socket;
        return current != null ? current.getRemoteSocketAddress() : null;
    }

    /**
     * Get local port
     */
    public int getLocalPort() {
        Socket current = socket;
        return current != null ? current.getLocalPort() : -1;
    }

    /**
     * Get socket
     */
    public Socket getSocket() {
        return socket;
    }

    /**
     * Close session and socket
     */
    @Override
    public void close() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                connected = false;
                closeSocketQuietly(socket);
                lock.notifyAll();
            }
        }
    }

    /**
     * Abort session immediately (no graceful shutdown)
     */
    public void abort() {
        close();
    }

    /**
     * Get reconnect count
     */
    public int getReconnectCount() {
        return reconnectCount;
    }

    // ========== Internal Methods ==========

    /**
     * Connect to server
     */
    void connect() throws IOException {
        synchronized (lock) {
            while (connecting && !closed && !hasConnectedSocket()) {
                try {
                    lock.wait();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Connection interrupted", e);
                }
            }
            if (closed) {
                throw new IOException("Session is closed");
            }
            if (hasConnectedSocket()) {
                return; // Already connected
            }
            connecting = true;
            reconnectCount = 0;
        }

        IOException lastFailure = null;
        try {
            InetSocketAddress address = new InetSocketAddress(host, port);
            int retryLimit = autoReconnect ? maxReconnectAttempts : 0;

            for (int attempt = 0; attempt <= retryLimit; attempt++) {
                if (attempt > 0) {
                    synchronized (lock) {
                        reconnectCount = attempt;
                    }
                    waitBeforeReconnect();
                }

                Socket candidate = new Socket();
                try {
                    applySocketConfig(candidate);
                    synchronized (lock) {
                        if (closed) {
                            throw new IOException("Session is closed");
                        }
                        Socket previous = socket;
                        socket = candidate;
                        connected = false;
                        if (previous != candidate) {
                            closeSocketQuietly(previous);
                        }
                    }
                    candidate.connect(address, durationToMillis(connectTimeout));
                    synchronized (lock) {
                        if (closed || socket != candidate) {
                            throw new IOException("Session is closed");
                        }
                        connected = true;
                    }
                    return;
                } catch (IOException e) {
                    lastFailure = e;
                    synchronized (lock) {
                        if (socket == candidate) {
                            connected = false;
                        }
                        closeSocketQuietly(candidate);
                        if (closed) {
                            throw new IOException("Session is closed", e);
                        }
                    }
                }
            }

            throw lastFailure != null
                    ? lastFailure
                    : new IOException("Failed to connect to " + host + ":" + port);
        } finally {
            synchronized (lock) {
                connecting = false;
                lock.notifyAll();
            }
        }
    }

    /**
     * Check connection state
     */
    private void checkConnection() throws IOException {
        if (closed) {
            throw new IOException("Session is closed");
        }
        Socket current = socket;
        if (!connected || current == null || !current.isConnected() || current.isClosed()) {
            throw new IOException("Not connected to " + host + ":" + port);
        }
    }

    private void ensureConnected() throws IOException {
        if (closed) {
            throw new IOException("Session is closed");
        }
        Socket current = socket;
        if (!connected || current == null || current.isClosed()) {
            connect();
        }
    }

    private Socket requireConnectedSocket() throws IOException {
        checkConnection();
        return socket;
    }

    void shutdownOutput() throws IOException {
        ensureConnected();
        synchronized (writeLock) {
            Socket current = requireConnectedSocket();
            if (current.isOutputShutdown()) {
                return;
            }
            try {
                current.shutdownOutput();
            } catch (IOException error) {
                invalidate(current);
                throw error;
            }
        }
    }

    /**
     * Perform exactly one blocking read. Message framing belongs to the protocol layer.
     */
    private byte[] readOnce(InputStream in) throws IOException {
        byte[] buffer = new byte[8192];
        int bytesRead = in.read(buffer);
        if (bytesRead == -1) {
            return new byte[0];
        }
        return Arrays.copyOf(buffer, bytesRead);
    }

    private byte[] readUntilClosed(InputStream in, int maxBytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(8192, maxBytes));
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            if (bytesRead > maxBytes - out.size()) {
                throw new IOException("TCP response exceeds maximum size of " + maxBytes + " bytes");
            }
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }

    private void applySocketConfig(Socket target) throws IOException {
        target.setKeepAlive(keepAlive);
        target.setTcpNoDelay(tcpNoDelay);
        target.setReuseAddress(soReuseAddress);
        if (sendBufferSize > 0) {
            target.setSendBufferSize(sendBufferSize);
        }
        if (receiveBufferSize > 0) {
            target.setReceiveBufferSize(receiveBufferSize);
        }
        if (trafficClass > 0) {
            target.setTrafficClass(trafficClass);
        }
        target.setSoTimeout(configuredReadTimeoutMillis());
    }

    private int configuredReadTimeoutMillis() {
        return soTimeout > 0 ? soTimeout : durationToMillis(readTimeout);
    }

    private static int durationToMillis(Duration duration) {
        if (duration == null || duration.isZero()) {
            return 0;
        }
        long millis;
        try {
            millis = duration.toMillis();
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
        if (millis <= 0) {
            return 1;
        }
        return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) millis;
    }

    private void waitBeforeReconnect() throws IOException {
        synchronized (lock) {
            if (closed) {
                throw new IOException("Session is closed");
            }
            if (reconnectDelay > 0) {
                try {
                    lock.wait(reconnectDelay);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Reconnect interrupted", e);
                }
            }
            if (closed) {
                throw new IOException("Session is closed");
            }
        }
    }

    private boolean hasConnectedSocket() {
        Socket current = socket;
        return connected && current != null && current.isConnected() && !current.isClosed();
    }

    private static void closeSocketQuietly(Socket target) {
        if (target == null || target.isClosed()) {
            return;
        }
        try {
            target.close();
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    private void invalidate(Socket target) {
        synchronized (lock) {
            if (socket == target) {
                connected = false;
                closeSocketQuietly(target);
            }
        }
    }

    // ========== Builder ==========

    public static class Builder {
        private String host;
        private int port;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private String sessionId;
        private boolean keepAlive = true;
        private boolean tcpNoDelay = false;
        private int sendBufferSize = 8192;
        private int receiveBufferSize = 8192;
        private int soTimeout = 0;
        private boolean soReuseAddress = false;
        private int trafficClass = 0;
        private boolean autoReconnect = false;
        private int maxReconnectAttempts = 3;
        private long reconnectDelay = 1000;

        /**
         * Set host and port
         */
        public Builder host(String host, int port) {
            this.host = host;
            this.port = port;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        /**
         * Set read timeout
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Retained for API compatibility; blocking {@link Socket} has no portable write timeout.
         * @deprecated Configure application-level framing and cancellation instead.
         */
        @Deprecated
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
            return this;
        }

        public Builder keepAlive(boolean keepAlive) {
            this.keepAlive = keepAlive;
            return this;
        }

        public Builder tcpNoDelay(boolean tcpNoDelay) {
            this.tcpNoDelay = tcpNoDelay;
            return this;
        }

        public Builder sendBufferSize(int size) {
            this.sendBufferSize = size;
            return this;
        }

        public Builder receiveBufferSize(int size) {
            this.receiveBufferSize = size;
            return this;
        }

        public Builder soTimeout(int timeoutMs) {
            this.soTimeout = timeoutMs;
            return this;
        }

        public Builder soReuseAddress(boolean reuse) {
            this.soReuseAddress = reuse;
            return this;
        }

        /** Set the raw 8-bit IPv4 TOS / IPv6 traffic-class value (0-255). */
        public Builder trafficClass(int trafficClass) {
            this.trafficClass = trafficClass;
            return this;
        }

        /**
         * Set session ID
         */
        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        /**
         * Enable/disable auto reconnect
         */
        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        /**
         * Set max reconnect attempts
         */
        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        /**
         * Set reconnect delay (milliseconds)
         */
        public Builder reconnectDelay(long delayMs) {
            this.reconnectDelay = delayMs;
            return this;
        }

        /**
         * Build TcpSession
         */
        public TcpSession build() {
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Host must be set");
            }
            if (port < 1 || port > 65_535) {
                throw new IllegalStateException("Port must be between 1 and 65535");
            }
            if (connectTimeout != null && connectTimeout.isNegative()) {
                throw new IllegalStateException("Connect timeout cannot be negative");
            }
            if (readTimeout != null && readTimeout.isNegative()) {
                throw new IllegalStateException("Read timeout cannot be negative");
            }
            if (maxReconnectAttempts < 0) {
                throw new IllegalStateException("Max reconnect attempts cannot be negative");
            }
            if (reconnectDelay < 0) {
                throw new IllegalStateException("Reconnect delay cannot be negative");
            }
            if (soTimeout < 0) {
                throw new IllegalStateException("SO_TIMEOUT cannot be negative");
            }
            if (sendBufferSize <= 0 || receiveBufferSize <= 0) {
                throw new IllegalStateException("Socket buffer sizes must be positive");
            }
            if (trafficClass < 0 || trafficClass > 255) {
                throw new IllegalStateException("Traffic class must be between 0 and 255");
            }
            return new TcpSession(this);
        }
    }
}
