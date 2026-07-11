package com.jnet.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * UDP client for sending and receiving datagrams.
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class UdpClient implements AutoCloseable {
    private static final int MAX_DATAGRAM_SIZE = 65_535;
    private static volatile UdpClient instance;

    private final MulticastSocket socket;
    private final UdpConfig config;
    private final String defaultHost;
    private final int defaultPort;
    private final byte[] receiveBuffer = new byte[MAX_DATAGRAM_SIZE];
    private final Object sendLock = new Object();
    private final Object receiveLock = new Object();
    private final Object portReceiveLock = new Object();
    private final Set<DatagramSocket> portReceiveSockets = new HashSet<>();
    private volatile boolean closed;

    private UdpClient(Builder builder) {
        this.config = builder.configBuilder.build();
        this.defaultHost = builder.defaultHost;
        this.defaultPort = builder.defaultPort;

        MulticastSocket created = null;
        try {
            created = new MulticastSocket(null);
            applySocketConfig(created, config);
            created.bind(new InetSocketAddress(0));
            this.socket = created;
        } catch (IOException e) {
            if (created != null) {
                created.close();
            }
            throw new IllegalStateException("Failed to create UDP socket", e);
        }
    }

    public static UdpClient getInstance() {
        UdpClient current = instance;
        if (current == null || current.isClosed()) {
            synchronized (UdpClient.class) {
                current = instance;
                if (current == null || current.isClosed()) {
                    current = new Builder().build();
                    instance = current;
                }
            }
        }
        return current;
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

    public UdpPacket send(UdpPacket packet) throws IOException {
        if (packet == null) {
            throw new IllegalArgumentException("Packet cannot be null");
        }
        checkClosed();

        byte[] data = packet.dataUnsafe();
        DatagramPacket datagram = new DatagramPacket(data, data.length, packet.getAddress(), packet.getPort());
        synchronized (sendLock) {
            checkClosed();
            if (!packet.hasExplicitTtl()) {
                socket.send(datagram);
                return packet;
            }

            int previousTtl = socket.getTimeToLive();
            socket.setTimeToLive(packet.getTtl());
            try {
                socket.send(datagram);
            } finally {
                socket.setTimeToLive(previousTtl);
            }
        }
        return packet;
    }

    public UdpPacket send(byte[] data, String host, int port) throws IOException {
        if (host == null || host.trim().isEmpty()) {
            throw new IllegalArgumentException("Host cannot be null or empty");
        }
        return send(UdpPacket.newBuilder()
                .address(InetAddress.getByName(host), port)
                .data(data)
                .build());
    }

    public UdpPacket send(String data, String host, int port) throws IOException {
        byte[] bytes = data != null ? data.getBytes(StandardCharsets.UTF_8) : new byte[0];
        return send(bytes, host, port);
    }

    public UdpPacket send(byte[] data) throws IOException {
        ensureDefaultTarget();
        return send(data, defaultHost, defaultPort);
    }

    public UdpPacket send(String data) throws IOException {
        ensureDefaultTarget();
        return send(data, defaultHost, defaultPort);
    }

    public UdpPacket receive() throws IOException {
        return receive(getTimeoutMs(config));
    }

    public UdpPacket receive(int timeoutMs) throws IOException {
        checkTimeout(timeoutMs);
        checkClosed();
        synchronized (receiveLock) {
            checkClosed();
            return receiveInternal(socket, timeoutMs, receiveBuffer);
        }
    }

    public UdpPacket receiveOnPort(int port) throws IOException {
        return receiveOnPort(port, getTimeoutMs(config));
    }

    public UdpPacket receiveOnPort(int port, int timeoutMs) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        checkTimeout(timeoutMs);
        checkClosed();
        try (DatagramSocket receiveSocket = new DatagramSocket(null)) {
            registerPortReceiveSocket(receiveSocket);
            try {
                applySocketConfig(receiveSocket, config);
                receiveSocket.bind(new InetSocketAddress(port));
                return receiveInternal(receiveSocket, timeoutMs, new byte[MAX_DATAGRAM_SIZE]);
            } finally {
                unregisterPortReceiveSocket(receiveSocket);
            }
        }
    }

    @Override
    public void close() {
        DatagramSocket[] portReceivers;
        synchronized (portReceiveLock) {
            closed = true;
            portReceivers = portReceiveSockets.toArray(new DatagramSocket[0]);
            portReceiveSockets.clear();
        }
        socket.close();
        for (DatagramSocket portReceiver : portReceivers) {
            portReceiver.close();
        }
        if (instance == this) {
            synchronized (UdpClient.class) {
                if (instance == this) {
                    instance = null;
                }
            }
        }
    }

    public boolean isClosed() {
        return closed || socket.isClosed();
    }

    public DatagramSocket getSocket() {
        return socket;
    }

    private void checkClosed() throws IOException {
        if (isClosed()) {
            throw new IOException("UdpClient is closed");
        }
    }

    private void ensureDefaultTarget() throws IOException {
        if (defaultHost == null || defaultPort <= 0) {
            throw new IOException("Default host/port not set");
        }
    }

    private void registerPortReceiveSocket(DatagramSocket receiveSocket) throws IOException {
        synchronized (portReceiveLock) {
            if (closed || socket.isClosed()) {
                throw new IOException("UdpClient is closed");
            }
            portReceiveSockets.add(receiveSocket);
        }
    }

    private void unregisterPortReceiveSocket(DatagramSocket receiveSocket) {
        synchronized (portReceiveLock) {
            portReceiveSockets.remove(receiveSocket);
        }
    }

    private static void checkTimeout(int timeoutMs) {
        if (timeoutMs < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
    }

    private static int getTimeoutMs(UdpConfig config) {
        Duration timeout = config.getTimeout();
        if (timeout == null || timeout.isZero()) {
            return 0;
        }
        try {
            long millis = timeout.toMillis();
            return millis > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(1L, millis);
        } catch (ArithmeticException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    static void applySocketConfig(DatagramSocket socket, UdpConfig config) throws IOException {
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

        if (socket instanceof MulticastSocket) {
            MulticastSocket multicastSocket = (MulticastSocket) socket;
            multicastSocket.setTimeToLive(config.getMulticastTtl());
            // MulticastSocket uses the inverse "disable loopback" flag.
            multicastSocket.setLoopbackMode(!config.isLoopbackMode());
        }
    }

    private static UdpPacket receiveInternal(DatagramSocket socket, int timeoutMs, byte[] buffer)
            throws IOException {
        // Always assign the timeout: zero must clear a timeout from an earlier receive.
        socket.setSoTimeout(timeoutMs);
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        socket.receive(packet);

        byte[] data = Arrays.copyOfRange(
                packet.getData(), packet.getOffset(), packet.getOffset() + packet.getLength());
        return UdpPacket.newBuilder()
                .address(packet.getAddress(), packet.getPort())
                .data(data)
                .build();
    }

    public static class Builder {
        private UdpConfig.Builder configBuilder = UdpConfig.newBuilder();
        private String defaultHost;
        private int defaultPort;

        public Builder config(UdpConfig config) {
            if (config == null) {
                this.configBuilder = UdpConfig.newBuilder();
            } else {
                this.configBuilder = UdpConfig.newBuilder()
                        .timeout(config.getTimeout())
                        .sendBufferSize(config.getSendBufferSize())
                        .receiveBufferSize(config.getReceiveBufferSize())
                        .broadcast(config.isBroadcast())
                        .timeToLive(config.getTimeToLive())
                        .loopbackMode(config.isLoopbackMode())
                        .trafficClass(config.getTrafficClass())
                        .multicastTtl(config.getMulticastTtl());
            }
            return this;
        }

        public Builder timeout(Duration timeout) {
            configBuilder.timeout(timeout);
            return this;
        }

        public Builder broadcast(boolean broadcast) {
            configBuilder.broadcast(broadcast);
            return this;
        }

        public Builder sendBufferSize(int size) {
            configBuilder.sendBufferSize(size);
            return this;
        }

        public Builder receiveBufferSize(int size) {
            configBuilder.receiveBufferSize(size);
            return this;
        }

        public Builder timeToLive(int ttl) {
            configBuilder.timeToLive(ttl);
            return this;
        }

        public Builder multicastTtl(int ttl) {
            configBuilder.multicastTtl(ttl);
            return this;
        }

        public Builder loopbackMode(boolean enabled) {
            configBuilder.loopbackMode(enabled);
            return this;
        }

        public Builder trafficClass(int trafficClass) {
            configBuilder.trafficClass(trafficClass);
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
            if (defaultHost != null && defaultHost.trim().isEmpty()) {
                throw new IllegalStateException("Default host cannot be empty");
            }
            boolean hasHost = defaultHost != null && !defaultHost.trim().isEmpty();
            boolean hasPort = defaultPort != 0;
            if (hasHost != hasPort) {
                throw new IllegalStateException("Default host and port must be set together");
            }
            if (hasPort && (defaultPort < 1 || defaultPort > 65_535)) {
                throw new IllegalStateException("Default port must be between 1 and 65535");
            }
            return new UdpClient(this);
        }
    }
}
