package com.jnet.tcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Duration;

/**
 * Persistent TCP Session
 * Maintains a socket connection for multiple send/receive operations
 * Thread-safe operations through synchronization
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class TcpSession implements AutoCloseable {
    private final Socket socket;
    private final String host;
    private final int port;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final String sessionId;
    private final boolean autoReconnect;
    private final int maxReconnectAttempts;
    private final long reconnectDelay;

    private volatile boolean connected = false;
    private volatile boolean closed = false;
    private int reconnectCount = 0;
    private final Object lock = new Object();

    private TcpSession(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.sessionId = builder.sessionId;
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
        synchronized (lock) {
            checkConnection();
            OutputStream out = socket.getOutputStream();
            out.write(data);
            out.flush();
        }
    }

    /**
     * Send string data (UTF-8 encoded)
     */
    public void send(String data) throws IOException {
        if (data != null) {
            send(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    /**
     * Send data with automatic line termination
     */
    public void sendLine(String data) throws IOException {
        send(data + "\r\n");
    }

    /**
     * Receive data
     */
    public byte[] receive() throws IOException {
        return receive(0);
    }

    /**
     * Receive data with timeout
     */
    public byte[] receive(int timeoutMs) throws IOException {
        checkConnection();
        InputStream in = socket.getInputStream();
        socket.setSoTimeout(timeoutMs);
        return readAll(in);
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
        checkConnection();
        InputStream in = socket.getInputStream();
        return readAll(in);
    }

    /**
     * Get input stream for streaming
     */
    public InputStream getInputStream() throws IOException {
        checkConnection();
        return socket.getInputStream();
    }

    /**
     * Get output stream for streaming
     */
    public OutputStream getOutputStream() throws IOException {
        checkConnection();
        return socket.getOutputStream();
    }

    /**
     * Check if connection is alive
     */
    public boolean isConnected() {
        synchronized (lock) {
            return connected && !closed && !socket.isClosed();
        }
    }

    /**
     * Check if session is closed
     */
    public boolean isClosed() {
        synchronized (lock) {
            return closed || socket.isClosed();
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
        return socket.getRemoteSocketAddress();
    }

    /**
     * Get local port
     */
    public int getLocalPort() {
        return socket.getLocalPort();
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
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ignored) {
                    // Ignore close errors
                }
            }
        }
    }

    /**
     * Abort session immediately (no graceful shutdown)
     */
    public void abort() {
        synchronized (lock) {
            if (!closed) {
                closed = true;
                connected = false;
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException ignored) {
                    // Ignore close errors
                }
            }
        }
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
            if (connected && !socket.isClosed()) {
                return; // Already connected
            }

            InetSocketAddress address = new InetSocketAddress(host, port);

            try {
                if (readTimeout != null && !readTimeout.isZero()) {
                    socket.setSoTimeout((int) readTimeout.toMillis());
                }
                if (writeTimeout != null && !writeTimeout.isZero()) {
                    socket.setSoTimeout((int) writeTimeout.toMillis());
                }
                socket.connect(address);
                connected = true;
                reconnectCount = 0;
            } catch (IOException e) {
                if (autoReconnect) {
                    attemptReconnect();
                }
                throw e;
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
        if (!connected || socket.isClosed()) {
            throw new IOException("Not connected to " + host + ":" + port);
        }
    }

    /**
     * Attempt to reconnect
     */
    private void attemptReconnect() {
        if (reconnectCount >= maxReconnectAttempts) {
            throw new IllegalStateException("Max reconnection attempts reached: " + maxReconnectAttempts);
        }

        try {
            socket.close();
            Thread.sleep(reconnectDelay);
            connect();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reconnect interrupted", e);
        } catch (IOException e) {
            // Ignore connect errors, will retry
        }
    }

    /**
     * Read all available data from stream
     */
    private byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
        }
        return out.toByteArray();
    }

    // ========== Builder ==========

    public static class Builder {
        private String host;
        private int port;
        private Duration readTimeout = Duration.ofSeconds(30);
        private Duration writeTimeout = Duration.ofSeconds(10);
        private String sessionId;
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

        /**
         * Set read timeout
         */
        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Set write timeout
         */
        public Builder writeTimeout(Duration timeout) {
            this.writeTimeout = timeout;
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
            if (host == null) {
                throw new IllegalStateException("Host must be set");
            }
            if (port == 0) {
                throw new IllegalStateException("Port must be set");
            }
            return new TcpSession(this);
        }
    }
}
