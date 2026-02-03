package com.jnet.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.time.Duration;

/**
 * UDP Client - Send and receive UDP packets
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class UdpClient implements AutoCloseable {
    private static volatile UdpClient instance;

    private final DatagramSocket socket;
    private final UdpConfig config;
    private final String defaultHost;
    private final int defaultPort;
    private volatile boolean closed = false;

    private UdpClient(Builder builder) {
        this.config = builder.configBuilder != null
                ? builder.configBuilder.build()
                : UdpConfig.defaultConfig();
        this.defaultHost = builder.defaultHost;
        this.defaultPort = builder.defaultPort;

        try {
            this.socket = new DatagramSocket();
            applySocketConfig(socket, config);
        } catch (SocketException e) {
            throw new IllegalStateException("Failed to create UDP socket", e);
        }
    }

    // ========== Factory Methods ==========

    public static UdpClient getInstance() {
        if (instance == null) {
            synchronized (UdpClient.class) {
                if (instance == null) {
                    instance = new Builder().build();
                }
            }
        }
        return instance;
    }

    public static UdpClient newInstance(UdpConfig config) {
        return new Builder().config(config).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static UdpClient create() {
        return newBuilder().build();
    }

    // ========== Public API ==========

    /**
     * Send UDP packet
     */
    public UdpPacket send(UdpPacket packet) throws IOException {
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }
        checkClosed();

        byte[] data = packet.getData() != null ? packet.getData() : new byte[0];
        DatagramPacket datagram = new DatagramPacket(
                data,
                data.length,
                packet.getAddress(),
                packet.getPort()
        );
        socket.send(datagram);
        return packet;
    }

    /**
     * Send UDP packet
     */
    public UdpPacket send(byte[] data, String host, int port) throws IOException {
        if (host == null || host.isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        InetAddress address = InetAddress.getByName(host);
        UdpPacket packet = UdpPacket.newBuilder()
                .address(address, port)
                .data(data)
                .build();
        return send(packet);
    }

    /**
     * Send UDP packet
     */
    public UdpPacket send(String data, String host, int port) throws IOException {
        byte[] bytes = data != null ? data.getBytes(java.nio.charset.StandardCharsets.UTF_8) : new byte[0];
        return send(bytes, host, port);
    }

    /**
     * Send UDP packet using default target
     */
    public UdpPacket send(byte[] data) throws IOException {
        ensureDefaultTarget();
        return send(data, defaultHost, defaultPort);
    }

    /**
     * Send UDP packet using default target
     */
    public UdpPacket send(String data) throws IOException {
        ensureDefaultTarget();
        return send(data, defaultHost, defaultPort);
    }

    /**
     * Receive UDP packet using default socket and timeout
     */
    public UdpPacket receive() throws IOException {
        return receiveInternal(socket, getTimeoutMs(config));
    }

    /**
     * Receive UDP packet using default socket
     */
    public UdpPacket receive(int timeoutMs) throws IOException {
        return receiveInternal(socket, timeoutMs);
    }

    /**
     * Receive UDP packet on a specific port
     */
    public UdpPacket receiveOnPort(int port) throws IOException {
        return receiveOnPort(port, getTimeoutMs(config));
    }

    /**
     * Receive UDP packet on a specific port with timeout
     */
    public UdpPacket receiveOnPort(int port, int timeoutMs) throws IOException {
        try (DatagramSocket receiveSocket = new DatagramSocket(port)) {
            applySocketConfig(receiveSocket, config);
            return receiveInternal(receiveSocket, timeoutMs);
        }
    }

    /**
     * Close socket
     */
    @Override
    public void close() {
        closed = true;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    /**
     * Get socket
     */
    public DatagramSocket getSocket() {
        return socket;
    }

    // ========== Internal Methods ==========

    private void checkClosed() throws IOException {
        if (closed) {
            throw new IOException("UdpClient is closed");
        }
    }

    private void ensureDefaultTarget() throws IOException {
        if (defaultHost == null || defaultHost.isEmpty() || defaultPort <= 0) {
            throw new IOException("Default host/port not set");
        }
    }

    private static int getTimeoutMs(UdpConfig config) {
        if (config == null || config.getTimeout() == null) {
            return 0;
        }
        long ms = config.getTimeout().toMillis();
        return ms > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) ms;
    }

    private static void applySocketConfig(DatagramSocket socket, UdpConfig config) throws SocketException {
        if (config == null) {
            return;
        }
        socket.setBroadcast(config.isBroadcast());
        if (config.getSendBufferSize() > 0) {
            socket.setSendBufferSize(config.getSendBufferSize());
        }
        if (config.getReceiveBufferSize() > 0) {
            socket.setReceiveBufferSize(config.getReceiveBufferSize());
        }
        if (config.getTrafficClass() > 0) {
            socket.setTrafficClass(config.getTrafficClass());
        }
    }

    private static UdpPacket receiveInternal(DatagramSocket socket, int timeoutMs) throws IOException {
        if (timeoutMs > 0) {
            socket.setSoTimeout(timeoutMs);
        }

        int bufferSize = Math.max(socket.getReceiveBufferSize(), 1024);
        byte[] buffer = new byte[bufferSize];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());

        return UdpPacket.newBuilder()
                .address(packet.getAddress(), packet.getPort())
                .data(data)
                .build();
    }

    // ========== Builder ==========

    public static class Builder {
        private UdpConfig.Builder configBuilder = UdpConfig.newBuilder();
        private String defaultHost;
        private int defaultPort;

        public Builder config(UdpConfig config) {
            if (config == null) {
                this.configBuilder = UdpConfig.newBuilder();
                return this;
            }
            this.configBuilder = UdpConfig.newBuilder()
                    .timeout(config.getTimeout())
                    .sendBufferSize(config.getSendBufferSize())
                    .receiveBufferSize(config.getReceiveBufferSize())
                    .broadcast(config.isBroadcast())
                    .timeToLive(config.getTimeToLive())
                    .loopbackMode(config.isLoopbackMode())
                    .trafficClass(config.getTrafficClass())
                    .multicastTtl(config.getMulticastTtl());
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.configBuilder.timeout(timeout);
            return this;
        }

        public Builder broadcast(boolean broadcast) {
            this.configBuilder.broadcast(broadcast);
            return this;
        }

        public Builder sendBufferSize(int size) {
            this.configBuilder.sendBufferSize(size);
            return this;
        }

        public Builder receiveBufferSize(int size) {
            this.configBuilder.receiveBufferSize(size);
            return this;
        }

        public Builder host(String host) {
            this.defaultHost = host;
            return this;
        }

        public Builder port(int port) {
            this.defaultPort = port;
            return this;
        }

        public UdpClient build() {
            return new UdpClient(this);
        }
    }
}
