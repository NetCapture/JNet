package com.jnet.tcp;

import java.io.IOException;

/**
 * TCP Server - Simple one-thread server
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class Server {
    private final int port;
    private final TcpConfig config;
    private final RequestHandler requestHandler;

    private Server(Builder builder) {
        this.port = builder.port;
        this.config = builder.config;
        this.requestHandler = builder.requestHandler;
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
        private TcpConfig config;
        private RequestHandler requestHandler;

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder config(TcpConfig config) {
            this.config = config;
            return this;
        }

        public Builder requestHandler(RequestHandler handler) {
            this.requestHandler = handler;
            return this;
        }

        public Builder handler(RequestHandler handler) {
            return requestHandler(handler);
        }

        public Server build() {
            return new Server(this);
        }
    }

    public interface RequestHandler {
        String onRequest(String method, String path, byte[] body);
    }

    // ========== Public API (placeholder) ==========

    public void start(int port) throws IOException {
        if (requestHandler != null) {
            // Placeholder for server start
            System.out.println("TCP Server placeholder - implement actual server logic");
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
