package com.jnet.tcp;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Small HTTP/1.1 server backed only by {@link ServerSocket}. */
public final class Server implements AutoCloseable {
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;
    private static final int WORKER_THREADS = Math.max(
            2, Math.min(32, Runtime.getRuntime().availableProcessors()));
    private static final int MAX_PENDING_CONNECTIONS = 256;

    private final int port;
    private final TcpConfig config;
    private final RequestHandler requestHandler;
    private final String route;
    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile int boundPort;
    private volatile ServerSocket serverSocket;
    private volatile Thread worker;
    private volatile ThreadPoolExecutor requestExecutor;
    private volatile Set<Socket> activeSockets = Collections.emptySet();

    private Server(Builder builder) {
        this.port = builder.port;
        this.config = builder.config == null ? TcpConfig.defaultConfig() : builder.config;
        this.requestHandler = builder.requestHandler;
        this.route = builder.route;
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
        if (requestHandler == null) {
            throw new IllegalStateException("Request handler must be set before starting the server");
        }
        synchronized (lifecycleLock) {
            if (running) {
                throw new IllegalStateException("TCP server is already running");
            }
            ServerSocket created = new ServerSocket();
            try {
                created.setReuseAddress(config.isSoReuseAddress());
                if (config.getReceiveBufferSize() > 0) {
                    created.setReceiveBufferSize(config.getReceiveBufferSize());
                }
                created.bind(new InetSocketAddress(port));
            } catch (IOException | RuntimeException e) {
                created.close();
                throw e;
            }
            serverSocket = created;
            boundPort = created.getLocalPort();
            Set<Socket> connections = ConcurrentHashMap.newKeySet();
            ThreadPoolExecutor executor = createRequestExecutor(boundPort);
            activeSockets = connections;
            requestExecutor = executor;
            running = true;
            Thread thread = new Thread(
                    () -> run(created, executor, connections),
                    "jnet-tcp-server-" + boundPort);
            thread.setDaemon(true);
            worker = thread;
            try {
                thread.start();
            } catch (RuntimeException | Error e) {
                running = false;
                serverSocket = null;
                worker = null;
                requestExecutor = null;
                activeSockets = Collections.emptySet();
                executor.shutdownNow();
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
        ThreadPoolExecutor executor;
        Set<Socket> connections;
        synchronized (lifecycleLock) {
            running = false;
            ServerSocket active = serverSocket;
            serverSocket = null;
            thread = worker;
            worker = null;
            executor = requestExecutor;
            requestExecutor = null;
            connections = activeSockets;
            activeSockets = Collections.emptySet();
            if (active != null) {
                try {
                    active.close();
                } catch (IOException ignored) {
                    // stop() is best effort.
                }
            }
        }
        closeConnections(connections);
        if (executor != null) {
            executor.shutdownNow();
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
        ServerSocket active = serverSocket;
        return running && active != null && !active.isClosed();
    }

    public int getPort() {
        return boundPort > 0 ? boundPort : port;
    }

    private void run(
            ServerSocket activeServer,
            ThreadPoolExecutor executor,
            Set<Socket> connections) {
        try {
            while (running && serverSocket == activeServer && !activeServer.isClosed()) {
                Socket accepted = null;
                try {
                    accepted = activeServer.accept();
                    configure(accepted);
                    if (!running || serverSocket != activeServer) {
                        closeSocketQuietly(accepted);
                        continue;
                    }
                    Socket client = accepted;
                    connections.add(client);
                    try {
                        executor.execute(() -> serve(client, connections));
                    } catch (RejectedExecutionException e) {
                        connections.remove(client);
                        closeSocketQuietly(client);
                    }
                } catch (IOException e) {
                    closeSocketQuietly(accepted);
                    if (running && !activeServer.isClosed()) {
                        // A single failed accept/configuration must not terminate the server.
                    }
                }
            }
        } finally {
            try {
                activeServer.close();
            } catch (IOException ignored) {
                // Already closed in the usual path.
            }
            closeConnections(connections);
            executor.shutdownNow();
            synchronized (lifecycleLock) {
                if (serverSocket == activeServer) {
                    serverSocket = null;
                    worker = null;
                    requestExecutor = null;
                    activeSockets = Collections.emptySet();
                    running = false;
                }
            }
        }
    }

    private void serve(Socket socket, Set<Socket> connections) {
        try (Socket client = socket) {
            handle(client);
        } catch (IOException ignored) {
            // A malformed or disconnected client must not affect other connections.
        } finally {
            connections.remove(socket);
        }
    }

    private void handle(Socket socket) throws IOException {
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        BufferedOutputStream output = new BufferedOutputStream(socket.getOutputStream());
        try {
            byte[] headerBytes = readHeaders(input);
            String headers = new String(headerBytes, StandardCharsets.ISO_8859_1);
            String[] lines = headers.substring(0, headers.length() - 4).split("\\r\\n");
            String[] requestLine = lines[0].split(" ", 3);
            if (requestLine.length != 3
                    || !("HTTP/1.0".equals(requestLine[2]) || "HTTP/1.1".equals(requestLine[2]))) {
                writeResponse(output, 400, "Bad Request", "Malformed request line");
                return;
            }
            if (!matchesRoute(requestLine[1])) {
                writeResponse(output, 404, "Not Found", "Not Found");
                return;
            }
            int contentLength = contentLength(lines);
            byte[] body = readExactly(input, contentLength);
            String response = requestHandler.onRequest(requestLine[0], requestLine[1], body);
            writeResponse(output, 200, "OK", response == null ? "" : response);
        } catch (BadRequestException e) {
            writeResponse(output, e.statusCode, e.statusText, e.getMessage());
        } catch (RuntimeException e) {
            writeResponse(output, 500, "Internal Server Error", "Internal Server Error");
        }
    }

    private static byte[] readHeaders(BufferedInputStream input) throws IOException {
        ByteArrayOutputStream result = new ByteArrayOutputStream(512);
        int state = 0;
        while (state < 4) {
            int value = input.read();
            if (value < 0) {
                throw new EOFException("Connection closed during request headers");
            }
            result.write(value);
            if (result.size() > MAX_HEADER_BYTES) {
                throw new BadRequestException(431, "Request Header Fields Too Large", "Request headers too large");
            }
            state = state == 0 && value == '\r' ? 1
                    : state == 1 && value == '\n' ? 2
                    : state == 2 && value == '\r' ? 3
                    : state == 3 && value == '\n' ? 4
                    : value == '\r' ? 1 : 0;
        }
        return result.toByteArray();
    }

    private static int contentLength(String[] lines) throws BadRequestException {
        int length = 0;
        boolean hasContentLength = false;
        boolean hasTransferEncoding = false;
        for (int i = 1; i < lines.length; i++) {
            int separator = lines[i].indexOf(':');
            if (separator <= 0) {
                throw new BadRequestException(400, "Bad Request", "Malformed header");
            }
            String name = lines[i].substring(0, separator).trim().toLowerCase(Locale.ROOT);
            String value = lines[i].substring(separator + 1).trim();
            if ("transfer-encoding".equals(name)) {
                if (hasTransferEncoding || hasContentLength || !"identity".equalsIgnoreCase(value)) {
                    throw new BadRequestException(501, "Not Implemented", "Transfer-Encoding is not supported");
                }
                hasTransferEncoding = true;
            }
            if ("content-length".equals(name)) {
                if (hasContentLength || hasTransferEncoding) {
                    throw new BadRequestException(400, "Bad Request", "Ambiguous request body framing");
                }
                hasContentLength = true;
                try {
                    long parsed = Long.parseLong(value);
                    if (parsed < 0 || parsed > MAX_BODY_BYTES) {
                        throw new BadRequestException(413, "Payload Too Large", "Request body too large");
                    }
                    length = (int) parsed;
                } catch (NumberFormatException e) {
                    throw new BadRequestException(400, "Bad Request", "Invalid Content-Length");
                }
            }
        }
        return length;
    }

    private boolean matchesRoute(String requestTarget) {
        if (route == null) {
            return true;
        }
        int query = requestTarget.indexOf('?');
        String path = query >= 0 ? requestTarget.substring(0, query) : requestTarget;
        return route.equals(path);
    }

    private static byte[] readExactly(BufferedInputStream input, int length) throws IOException {
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(body, offset, length - offset);
            if (read < 0) {
                throw new BadRequestException(400, "Bad Request", "Incomplete request body");
            }
            offset += read;
        }
        return body;
    }

    private static void writeResponse(BufferedOutputStream output, int status, String statusText, String body)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        output.write(("HTTP/1.1 " + status + " " + statusText + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + bytes.length + "\r\n"
                + "Connection: close\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        output.write(bytes);
        output.flush();
    }

    private void configure(Socket socket) throws IOException {
        socket.setKeepAlive(config.isKeepAlive());
        socket.setTcpNoDelay(config.isTcpNoDelay());
        socket.setReuseAddress(config.isSoReuseAddress());
        if (config.getReceiveBufferSize() > 0) {
            socket.setReceiveBufferSize(config.getReceiveBufferSize());
        }
        if (config.getSendBufferSize() > 0) {
            socket.setSendBufferSize(config.getSendBufferSize());
        }
        socket.setSoTimeout(readTimeoutMillis());
        if (config.getTrafficClass() > 0) {
            socket.setTrafficClass(config.getTrafficClass());
        }
    }

    private static ThreadPoolExecutor createRequestExecutor(int port) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(
                WORKER_THREADS,
                WORKER_THREADS,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(MAX_PENDING_CONNECTIONS),
                task -> {
                    Thread thread = new Thread(
                            task,
                            "jnet-tcp-request-" + port + "-" + sequence.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    private static void closeConnections(Set<Socket> connections) {
        if (connections == null || connections.isEmpty()) {
            return;
        }
        for (Socket socket : connections) {
            closeSocketQuietly(socket);
        }
        connections.clear();
    }

    private static void closeSocketQuietly(Socket socket) {
        if (socket == null || socket.isClosed()) {
            return;
        }
        try {
            socket.close();
        } catch (IOException ignored) {
            // Best-effort lifecycle cleanup.
        }
    }

    private int readTimeoutMillis() {
        int socketTimeout = config.getSoTimeout();
        if (socketTimeout > 0) {
            return socketTimeout;
        }
        java.time.Duration timeout = config.getReadTimeout();
        if (timeout == null) {
            return 0;
        }
        if (timeout.isZero()) {
            return 0;
        }
        long millis;
        try {
            millis = timeout.toMillis();
        } catch (ArithmeticException e) {
            millis = Integer.MAX_VALUE;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.max(1L, millis));
    }

    public interface RequestHandler {
        String onRequest(String method, String path, byte[] body);
    }

    public static class Builder {
        private int port;
        private TcpConfig config;
        private RequestHandler requestHandler;
        private String route;

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

        public Builder route(String route) {
            if (route == null || route.isEmpty() || route.charAt(0) != '/') {
                throw new IllegalArgumentException("Route must start with '/'");
            }
            if (route.indexOf('?') >= 0 || route.indexOf('#') >= 0) {
                throw new IllegalArgumentException("Route must not contain a query or fragment");
            }
            this.route = route;
            return this;
        }

        public Server build() {
            return new Server(this);
        }
    }

    private static final class BadRequestException extends IOException {
        private static final long serialVersionUID = 1L;

        private final int statusCode;
        private final String statusText;

        private BadRequestException(int statusCode, String statusText, String message) {
            super(message);
            this.statusCode = statusCode;
            this.statusText = statusText;
        }
    }

}
