package com.jnet.udp;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Small single-threaded UDP server.
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class Server implements AutoCloseable {
    private static final int MAX_DATAGRAM_SIZE = 65_535;

    private final int port;
    private final PacketHandler handler;
    private final BinaryPacketHandler binaryHandler;
    private final UdpConfig config;
    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile int boundPort;
    private volatile DatagramSocket socket;
    private volatile Thread worker;

    private Server(Builder builder) {
        this.port = builder.port;
        this.handler = builder.handler;
        this.binaryHandler = builder.binaryHandler;
        this.config = builder.config != null ? builder.config : UdpConfig.defaultConfig();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder builder() {
        return newBuilder();
    }

    public static Server create() {
        return newBuilder().build();
    }

    public void start(int port) throws IOException {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("Port must be between 0 and 65535");
        }
        if (handler == null && binaryHandler == null) {
            throw new IllegalStateException("Packet handler must be set before starting the server");
        }

        synchronized (lifecycleLock) {
            if (running) {
                throw new IllegalStateException("UDP server is already running");
            }

            DatagramSocket created = new DatagramSocket(null);
            try {
                UdpClient.applySocketConfig(created, config);
                created.bind(new InetSocketAddress(port));
            } catch (IOException | RuntimeException e) {
                created.close();
                throw e;
            }

            socket = created;
            boundPort = created.getLocalPort();
            running = true;

            Thread thread = new Thread(() -> run(created), "jnet-udp-server-" + boundPort);
            thread.setDaemon(true);
            worker = thread;
            try {
                thread.start();
            } catch (RuntimeException | Error e) {
                running = false;
                socket = null;
                worker = null;
                created.close();
                throw e;
            }
        }
    }

    public void start() throws IOException {
        if (port <= 0) {
            throw new IOException("Port must be set before starting server");
        }
        start(port);
    }

    public void stop() {
        Thread thread;
        synchronized (lifecycleLock) {
            running = false;
            DatagramSocket activeSocket = socket;
            socket = null;
            thread = worker;
            worker = null;
            if (activeSocket != null) {
                activeSocket.close();
            }
        }

        if (thread != null && thread != Thread.currentThread()) {
            try {
                thread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void close() {
        stop();
    }

    public boolean isRunning() {
        DatagramSocket activeSocket = socket;
        return running && activeSocket != null && !activeSocket.isClosed();
    }

    /** Returns the bound port while or after running, otherwise the configured port. */
    public int getPort() {
        return boundPort > 0 ? boundPort : port;
    }

    private void run(DatagramSocket activeSocket) {
        byte[] buffer = new byte[MAX_DATAGRAM_SIZE];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
        try {
            while (running && socket == activeSocket && !activeSocket.isClosed()) {
                packet.setLength(buffer.length);
                activeSocket.receive(packet);
                try {
                    if (binaryHandler != null) {
                        binaryHandler.onPacket(UdpPacket.newBuilder()
                                .address(packet.getAddress(), packet.getPort())
                                .data(copyPacketData(packet))
                                .build());
                    } else {
                        handler.onPacket(
                                new String(
                                        packet.getData(),
                                        packet.getOffset(),
                                        packet.getLength(),
                                        StandardCharsets.UTF_8),
                                packet.getAddress().getHostAddress(),
                                packet.getPort());
                    }
                } catch (RuntimeException ignored) {
                    // One bad packet/handler invocation must not terminate the receive loop.
                }
            }
        } catch (IOException e) {
            // Closing the socket is the normal stop path. Other receive failures stop this instance.
        } finally {
            activeSocket.close();
            synchronized (lifecycleLock) {
                if (socket == activeSocket) {
                    socket = null;
                    worker = null;
                    running = false;
                }
            }
        }
    }

    public interface PacketHandler {
        void onPacket(String data, String host, int port);
    }

    /** Receives the exact datagram bytes without text decoding. */
    public interface BinaryPacketHandler {
        void onPacket(UdpPacket packet);
    }

    private static byte[] copyPacketData(DatagramPacket packet) {
        byte[] data = new byte[packet.getLength()];
        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, packet.getLength());
        return data;
    }

    public static class Builder {
        private int port;
        private PacketHandler handler;
        private BinaryPacketHandler binaryHandler;
        private UdpConfig config;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder handler(PacketHandler handler) {
            this.handler = handler;
            this.binaryHandler = null;
            return this;
        }

        public Builder packetHandler(BinaryPacketHandler handler) {
            this.binaryHandler = handler;
            this.handler = null;
            return this;
        }

        public Builder config(UdpConfig config) {
            this.config = config;
            return this;
        }

        public Server build() {
            return new Server(this);
        }
    }
}
