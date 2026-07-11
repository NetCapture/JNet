package com.jnet.tcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class TcpServerRegressionTest {

    @Test
    void servesAFramedHttpRequestAndStopsCleanly() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        Server server = Server.newBuilder()
                .handler((method, path, body) -> {
                    request.set(method + " " + path + " " + new String(body, StandardCharsets.UTF_8));
                    return "accepted";
                })
                .build();
        server.start(0);
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            assertTrue(server.isRunning());
            socket.getOutputStream().write(("POST /submit?q=1 HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: 7\r\n\r\n"
                    + "payload").getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            assertDoesNotThrow(() -> socket.getInputStream().transferTo(response));
            String text = response.toString(StandardCharsets.UTF_8);
            assertTrue(text.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(text.endsWith("accepted"));
            assertEquals("POST /submit?q=1 payload", request.get());
        } finally {
            server.close();
        }
        assertFalse(server.isRunning());
    }

    @Test
    void routeFilteringRejectsOtherTargetsBeforeCallingHandler() throws Exception {
        AtomicReference<String> handledPath = new AtomicReference<>();
        Server server = Server.newBuilder()
                .route("/api")
                .handler((method, path, body) -> {
                    handledPath.set(path);
                    return "matched";
                })
                .build();
        server.start(0);
        try {
            String missing = request(server.getPort(), "GET /other HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertTrue(missing.startsWith("HTTP/1.1 404 Not Found\r\n"));
            assertEquals(null, handledPath.get());

            String matched = request(server.getPort(), "GET /api?value=1 HTTP/1.1\r\nHost: localhost\r\n\r\n");
            assertTrue(matched.startsWith("HTTP/1.1 200 OK\r\n"));
            assertTrue(matched.endsWith("matched"));
            assertEquals("/api?value=1", handledPath.get());
        } finally {
            server.close();
        }
    }

    @Test
    void rejectsAmbiguousContentLengthFraming() throws Exception {
        Server server = Server.newBuilder()
                .handler((method, path, body) -> "unexpected")
                .build();
        server.start(0);
        try {
            String response = request(server.getPort(), "POST / HTTP/1.1\r\n"
                    + "Host: localhost\r\n"
                    + "Content-Length: 1\r\n"
                    + "Content-Length: 2\r\n\r\nxx");
            assertTrue(response.startsWith("HTTP/1.1 400 Bad Request\r\n"));
        } finally {
            server.close();
        }
    }

    @Test
    void zeroReadTimeoutKeepsTheServerSocketBlocking() throws Exception {
        Server server = Server.newBuilder()
                .config(TcpConfig.newBuilder().readTimeout(Duration.ZERO).build())
                .handler((method, path, body) -> "ok")
                .build();
        server.start(0);
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(1_000);
            Thread.sleep(50);
            socket.getOutputStream().write(
                    "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                            .getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            ByteArrayOutputStream response = new ByteArrayOutputStream();
            assertDoesNotThrow(() -> socket.getInputStream().transferTo(response));
            assertTrue(response.toString(StandardCharsets.UTF_8)
                    .startsWith("HTTP/1.1 200 OK\r\n"));
        } finally {
            server.close();
        }
    }

    @Test
    void explicitSoTimeoutOverridesTheDefaultReadTimeout() throws Exception {
        Server server = Server.newBuilder()
                .config(TcpConfig.newBuilder()
                        .readTimeout(Duration.ofSeconds(30))
                        .soTimeout(100)
                        .build())
                .handler((method, path, body) -> "unexpected")
                .build();
        server.start(0);
        try (Socket socket = new Socket("127.0.0.1", server.getPort())) {
            socket.setSoTimeout(1_000);
            socket.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();

            assertEquals(-1, socket.getInputStream().read(),
                    "SO_TIMEOUT must release a worker waiting on incomplete headers");
        } finally {
            server.close();
        }
    }

    @Test
    void oneSlowClientDoesNotBlockOtherClients() throws Exception {
        Server server = Server.newBuilder()
                .config(TcpConfig.newBuilder().readTimeout(Duration.ZERO).build())
                .handler((method, path, body) -> "ok")
                .build();
        server.start(0);

        try (Socket slow = new Socket("127.0.0.1", server.getPort())) {
            slow.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            slow.getOutputStream().flush();

            try (Socket fast = new Socket("127.0.0.1", server.getPort())) {
                fast.setSoTimeout(750);
                fast.getOutputStream().write(
                        "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
                                .getBytes(StandardCharsets.US_ASCII));
                fast.getOutputStream().flush();

                ByteArrayOutputStream response = new ByteArrayOutputStream();
                fast.getInputStream().transferTo(response);
                assertTrue(response.toString(StandardCharsets.UTF_8)
                        .startsWith("HTTP/1.1 200 OK\r\n"));
            }
        } finally {
            server.close();
        }
    }

    @Test
    void stopClosesClientsThatAreBlockedInARequest() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        Server server = Server.newBuilder()
                .config(TcpConfig.newBuilder().readTimeout(Duration.ZERO).build())
                .handler((method, path, body) -> "ok")
                .build();
        server.start(0);

        try (Socket client = new Socket("127.0.0.1", server.getPort())) {
            client.setSoTimeout(750);
            client.getOutputStream().write("GET / HTTP/1.1\r\n".getBytes(StandardCharsets.US_ASCII));
            client.getOutputStream().flush();

            Thread observer = new Thread(() -> {
                while (server.isRunning()) {
                    if (client.isConnected()) {
                        accepted.countDown();
                        return;
                    }
                    Thread.yield();
                }
            }, "tcp-server-accept-observer");
            observer.setDaemon(true);
            observer.start();
            assertTrue(accepted.await(250, TimeUnit.MILLISECONDS));

            server.stop();
            assertEquals(-1, client.getInputStream().read());
        } finally {
            server.close();
        }
    }

    private static String request(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            socket.getInputStream().transferTo(response);
            return response.toString(StandardCharsets.UTF_8);
        }
    }
}
