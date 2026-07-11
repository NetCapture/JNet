package com.jnet.tcp;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class TcpTransportSafetyTest {

    @Test
    @Timeout(3)
    void cancelingAsyncSendClosesTheBlockingSession() throws Exception {
        CountDownLatch accepted = new CountDownLatch(1);
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            accepted.countDown();
            Thread.sleep(2_000);
        })) {
            TcpClient client = TcpClient.getInstance();
            CompletableFuture<String> future = TcpClient.sendAsync(
                    "127.0.0.1", server.getPort(), "request");
            assertTrue(accepted.await(1, TimeUnit.SECONDS));

            assertTrue(future.cancel(true));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (client.getActiveSessionCount() != 0 && System.nanoTime() < deadline) {
                Thread.sleep(5);
            }

            assertEquals(0, client.getActiveSessionCount(),
                    "cancel must close the socket instead of waiting for the read timeout");
        }
    }

    @Test
    @Timeout(3)
    void executeReadsTheCompleteResponseUntilThePeerCloses() throws Exception {
        byte[] response = new byte[20_000];
        Arrays.fill(response, (byte) 0x5a);

        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getInputStream().readAllBytes();
            socket.getOutputStream().write(response, 0, 8_192);
            socket.getOutputStream().flush();
            Thread.sleep(25);
            socket.getOutputStream().write(response, 8_192, response.length - 8_192);
            socket.getOutputStream().flush();
        })) {
            TcpClient client = TcpClient.newBuilder()
                    .readTimeout(Duration.ofSeconds(1))
                    .build();
            TcpRequest request = client.newRequest("127.0.0.1", server.getPort())
                    .data("request")
                    .build();

            assertArrayEquals(response, client.execute(request).getData());
        }
    }

    @Test
    @Timeout(3)
    void executeHalfClosesTheRequestBeforeWaitingForAResponse() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            ByteArrayOutputStream request = new ByteArrayOutputStream();
            byte[] buffer = new byte[256];
            int read;
            while ((read = socket.getInputStream().read(buffer)) >= 0) {
                request.write(buffer, 0, read);
            }
            assertEquals("request", request.toString(StandardCharsets.UTF_8));
            socket.getOutputStream().write("response".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            TcpClient client = TcpClient.newBuilder()
                    .readTimeout(Duration.ofSeconds(1))
                    .build();
            TcpRequest request = client.newRequest("127.0.0.1", server.getPort())
                    .data("request")
                    .build();

            assertEquals("response", client.execute(request).getDataAsString());
        }
    }

    @Test
    @Timeout(3)
    void receiveAllRejectsAResponseBeyondTheCallerLimit() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write(new byte[1_025]);
            socket.getOutputStream().flush();
        })) {
            TcpSession session = TcpClient.create().newSession("127.0.0.1", server.getPort());
            try {
                session.connect();
                assertThrows(IOException.class, () -> session.receiveAll(1_024));
            } finally {
                session.close();
            }
        }
    }

    @Test
    @Timeout(3)
    void receiveAllTimeoutInvalidatesThePartiallyConsumedConnection() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write("partial".getBytes(StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
            Thread.sleep(1_000);
        })) {
            TcpSession session = TcpSession.newBuilder()
                    .host("127.0.0.1", server.getPort())
                    .readTimeout(Duration.ofMillis(100))
                    .build();
            try {
                session.connect();
                assertThrows(java.net.SocketTimeoutException.class, session::receiveAll);
                assertFalse(session.isConnected(),
                        "a timed-out receiveAll has lost bytes and must not leave the socket reusable");
            } finally {
                session.close();
            }
        }
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class OneConnectionServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;

        private OneConnectionServer(SocketHandler handler) throws IOException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    handler.handle(socket);
                } catch (Exception ignored) {
                    // Client-side assertions expose failures.
                }
            }, "tcp-transport-safety-server");
            thread.setDaemon(true);
            thread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws IOException {
            serverSocket.close();
            try {
                thread.join(1_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while stopping test server", e);
            }
        }
    }
}
