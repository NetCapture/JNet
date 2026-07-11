package com.jnet.tcp;

import com.jnet.core.JNet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class TcpRegressionTest {

    @Test
    @SuppressWarnings("deprecation")
    void trafficClassConstantsPreserveLegacyValuesAndExposeStandardTosBits() throws Exception {
        assertEquals(0, TcpConfig.Builder.TC_IPTOS_THROUGHPUT);
        assertEquals(1, TcpConfig.Builder.TC_IPTOS_LOWCOST);
        assertEquals(2, TcpConfig.Builder.TC_IPTOS_RELIABILITY);
        assertEquals(3, TcpConfig.Builder.TC_IPTOS_THROUGHPUT_RELIABILITY);
        assertEquals(4, TcpConfig.Builder.TC_IPTOS_BULK);
        assertTrue(TcpConfig.Builder.class.getField("TC_IPTOS_THROUGHPUT")
                .isAnnotationPresent(Deprecated.class));
        assertTrue(TcpConfig.Builder.class.getField("TC_IPTOS_LOWCOST")
                .isAnnotationPresent(Deprecated.class));
        assertTrue(TcpConfig.Builder.class.getField("TC_IPTOS_RELIABILITY")
                .isAnnotationPresent(Deprecated.class));
        assertTrue(TcpConfig.Builder.class.getField("TC_IPTOS_THROUGHPUT_RELIABILITY")
                .isAnnotationPresent(Deprecated.class));
        assertTrue(TcpConfig.Builder.class.getField("TC_IPTOS_BULK")
                .isAnnotationPresent(Deprecated.class));

        assertEquals(0x02, TcpConfig.Builder.class.getField("IP_TOS_LOW_COST").getInt(null));
        assertEquals(0x04, TcpConfig.Builder.class.getField("IP_TOS_RELIABILITY").getInt(null));
        assertEquals(0x08, TcpConfig.Builder.class.getField("IP_TOS_THROUGHPUT").getInt(null));
        assertEquals(0x10, TcpConfig.Builder.class.getField("IP_TOS_LOW_DELAY").getInt(null));
    }

    @Test
    void trafficClassAcceptsRawUnsignedByteValues() {
        assertEquals(255, TcpConfig.newBuilder().trafficClass(255).build().getTrafficClass());
        assertThrows(IllegalStateException.class,
                () -> TcpConfig.newBuilder().trafficClass(-1).build());
        assertThrows(IllegalStateException.class,
                () -> TcpConfig.newBuilder().trafficClass(256).build());
    }

    @Test
    void executeUsesTheInvokingClientsReadTimeout() throws Exception {
        try (DelayedReplyServer server = new DelayedReplyServer(250)) {
            TcpClient client = TcpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(1))
                    .readTimeout(Duration.ofMillis(50))
                    .writeTimeout(Duration.ofSeconds(1))
                    .build();
            TcpRequest request = client.newRequest("127.0.0.1", server.getPort())
                    .data("request")
                    .build();

            assertThrows(SocketTimeoutException.class, () -> client.execute(request));
        }
    }

    @Test
    void reconnectIsBoundedAndCountsEachRetry() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        TcpClient client = TcpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(100))
                .autoReconnect(true)
                .maxReconnectAttempts(2)
                .reconnectDelay(0)
                .build();
        TcpSession session = client.newSession("127.0.0.1", closedPort);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () ->
                assertThrows(IOException.class, () -> session.send("request")));
        assertEquals(2, session.getReconnectCount());
    }

    @Test
    void nullPayloadMeansConnectWithoutWriting() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write("greeting".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            assertEquals("greeting", TcpClient.send("127.0.0.1", server.getPort()));
        }
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write("greeting".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            assertEquals("greeting", TcpClient.send("127.0.0.1", server.getPort(), (String) null));
        }
    }

    @Test
    void receivePerformsOneBlockingReadInsteadOfDrainingAvailableBytes() throws Exception {
        byte[] payload = new byte[9_000];
        Arrays.fill(payload, (byte) 7);
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
        })) {
            TcpSession session = TcpClient.create().newSession("127.0.0.1", server.getPort());
            session.connect();
            assertTrue(server.awaitHandled());

            assertEquals(8_192, session.receive().length);
            session.close();
        }
    }

    @Test
    void receiveAllStillReadsUntilPeerCloses() throws Exception {
        byte[] payload = new byte[9_000];
        Arrays.fill(payload, (byte) 3);
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write(payload);
            socket.getOutputStream().flush();
        })) {
            TcpSession session = TcpClient.create().newSession("127.0.0.1", server.getPort());
            session.connect();

            assertArrayEquals(payload, session.receiveAll());
            assertFalse(session.isConnected());
            session.close();
        }
    }

    @Test
    void sessionAppliesConfiguredSocketOptions() throws Exception {
        TcpConfig config = TcpConfig.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .readTimeout(Duration.ofMillis(1_200))
                .writeTimeout(Duration.ofMillis(100))
                .keepAlive(true)
                .tcpNoDelay(true)
                .soReuseAddress(true)
                .build();
        try (OneConnectionServer server = new OneConnectionServer(socket ->
                socket.getInputStream().read())) {
            TcpSession session = TcpClient.newBuilder()
                    .config(config)
                    .build()
                    .newSession("127.0.0.1", server.getPort());
            session.connect();

            assertTrue(session.getSocket().getKeepAlive());
            assertTrue(session.getSocket().getTcpNoDelay());
            assertTrue(session.getSocket().getReuseAddress());
            assertEquals(1_200, session.getSocket().getSoTimeout());
            session.close();
        }
    }

    @Test
    void tcpRequestDefensivelyCopiesBinaryData() {
        byte[] source = {1, 2, 3};
        TcpRequest request = TcpRequest.newBuilder()
                .host("127.0.0.1")
                .port(1)
                .data(source)
                .build();

        source[0] = 9;
        byte[] returned = request.getData();
        returned[1] = 9;

        assertArrayEquals(new byte[] {1, 2, 3}, request.getData());
    }

    @Test
    void tcpResponseDefensivelyCopiesBinaryData() {
        byte[] source = {4, 5, 6};
        TcpResponse response = TcpResponse.success().data(source).build();

        source[0] = 9;
        byte[] returned = response.getData();
        returned[1] = 9;

        assertArrayEquals(new byte[] {4, 5, 6}, response.getData());
    }

    @Test
    void clientTracksCompletedAndActiveRequests() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getInputStream().readAllBytes();
            socket.getOutputStream().write("ok".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            TcpClient client = TcpClient.create();
            TcpRequest request = client.newRequest("127.0.0.1", server.getPort()).data("request").build();

            assertEquals("ok", client.execute(request).getDataAsString());
            assertEquals(1, client.getTotalRequestsCount());
            assertEquals(0, client.getActiveSessionCount());
        }
    }

    @Test
    void requestRejectsNegativeAndOverflowingTimeouts() {
        TcpRequest.Builder request = TcpRequest.newBuilder().host("127.0.0.1").port(1);

        assertThrows(IllegalArgumentException.class, () -> request.timeout(-1));
        assertThrows(IllegalArgumentException.class,
                () -> request.timeout(Duration.ofMillis((long) Integer.MAX_VALUE + 1)));
    }

    @Test
    void sessionRejectsNegativePerReadTimeoutsBeforeConnecting() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write('x');
            socket.getOutputStream().flush();
        })) {
            TcpSession session = TcpClient.create().newSession("127.0.0.1", server.getPort());
            try {
                assertThrows(IllegalArgumentException.class, () -> session.receive(-1));
            } finally {
                session.close();
            }
        }
    }

    @Test
    void facadeAcceptsANullStringAsAnEmptyTcpPayload() throws Exception {
        try (OneConnectionServer server = new OneConnectionServer(socket -> {
            socket.getOutputStream().write("greeting".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            socket.getOutputStream().flush();
        })) {
            assertEquals("greeting", JNet.tcp("127.0.0.1", server.getPort(), (String) null));
        }
    }

    @Test
    void facadeRejectsNegativeSessionTimeouts() {
        assertThrows(IllegalArgumentException.class,
                () -> JNet.tcpSession("127.0.0.1", 1, -1));
    }

    private static final class DelayedReplyServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch started = new CountDownLatch(1);

        private DelayedReplyServer(long delayMillis) throws IOException, InterruptedException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> {
                started.countDown();
                try (Socket socket = serverSocket.accept()) {
                    socket.getInputStream().read();
                    Thread.sleep(delayMillis);
                    socket.getOutputStream().write("response".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } catch (IOException ignored) {
                    // The client is expected to time out and close the connection.
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "tcp-delayed-reply-test");
            thread.setDaemon(true);
            thread.start();
            if (!started.await(1, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Server did not start");
            }
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
    }

    @FunctionalInterface
    private interface SocketHandler {
        void handle(Socket socket) throws Exception;
    }

    private static final class OneConnectionServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;
        private final CountDownLatch handled = new CountDownLatch(1);

        private OneConnectionServer(SocketHandler handler) throws IOException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> {
                try (Socket socket = serverSocket.accept()) {
                    handler.handle(socket);
                } catch (Exception ignored) {
                    // Assertions are made from the client side.
                } finally {
                    handled.countDown();
                }
            }, "tcp-one-connection-test");
            thread.setDaemon(true);
            thread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private boolean awaitHandled() throws InterruptedException {
            return handled.await(1, TimeUnit.SECONDS);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
    }
}
