package com.jnet.udp;

import java.io.IOException;

/**
 * UDP Server - Simple one-thread server
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class Server {
    private final int port;
    private final PacketHandler handler;

    private Server(Builder builder) {
        this.port = builder.port;
        this.handler = builder.handler;
    }

    // ========== Factory Methods ==========

    public static Builder newBuilder() {
        return new Builder();
    }

    public static Builder builder() {
        return newBuilder();
    }

    public static Server create() {
        return newBuilder().build();
    }

    // ========== Builder ==========

    public static class Builder {
        private int port;
        private PacketHandler handler;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder handler(PacketHandler handler) {
            this.handler = handler;
            return this;
        }

        public Server build() {
            return new Server(this);
        }
    }

    public interface PacketHandler {
        void onPacket(String data, String host, int port);
    }

    // ========== Public API (placeholder) ==========

    public void start(int port) throws IOException {
        if (handler != null) {
            // Placeholder for server start
            System.out.println("UDP Server placeholder - implement actual server logic");
        }
    }

    public void start() throws IOException {
        if (port <= 0) {
            throw new IOException("Port must be set before starting server");
        }
        start(port);
    }

    public void stop() {
        // Placeholder for server stop
    }

    public int getPort() {
        return port;
    }
}
