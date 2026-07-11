package com.jnet.socketio;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class SocketIOClientRegressionTest {

    @Test
    void disconnectDoesNotWaitForAnInFlightPollingHandshake() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> {
                    entered.countDown();
                    release.await(2, TimeUnit.SECONDS);
                    return "0{\"sid\":\"late\",\"upgrades\":[\"websocket\"]}";
                },
                new FakeTransportFactory());
        Thread connector = new Thread(client::connect);
        Thread disconnector = new Thread(client::disconnect);
        try {
            connector.start();
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            disconnector.start();
            disconnector.join(250);

            assertFalse(disconnector.isAlive(), "disconnect must not block behind network I/O");
            assertFalse(client.isConnected());
            assertNull(client.getSessionId());
        } finally {
            release.countDown();
            connector.join(2_000);
            disconnector.join(2_000);
        }
    }

    @Test
    void oversizedPollingHandshakeIsRejectedBeforeOpeningAWebSocket() {
        FakeTransportFactory transports = new FakeTransportFactory();
        AtomicInteger errors = new AtomicInteger();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0" + "x".repeat(64 * 1024 + 1),
                transports);
        client.on("connect_error", ignored -> errors.incrementAndGet());

        client.connect();

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertNull(transports.latest());
        assertEquals(1, errors.get());
    }

    @Test
    void engineClosePacketClearsConnectionStateAndSession() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = connectedClient(transports);
        FakeTransport transport = transports.latest();
        AtomicInteger disconnects = new AtomicInteger();
        client.on("disconnect", ignored -> disconnects.incrementAndGet());

        transport.receive("1");

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(1, disconnects.get());
    }

    @Test
    void engineCloseDuringUpgradeReportsAConnectError() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}",
                transports);
        AtomicInteger connectErrors = new AtomicInteger();
        client.on("connect_error", ignored -> connectErrors.incrementAndGet());

        client.connect();
        FakeTransport transport = transports.latest();
        transport.receive("1");

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(1, connectErrors.get());
    }

    @Test
    void terminalTransportErrorClearsAConnectedClient() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = connectedClient(transports);
        FakeTransport transport = transports.latest();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        client.on("error", ignored -> errors.incrementAndGet());
        client.on("disconnect", ignored -> disconnects.incrementAndGet());

        transport.fail(new IllegalStateException("transport failed"));

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(1, errors.get());
        assertEquals(1, disconnects.get());
    }

    @Test
    void disconnectDuringProbeCannotBeUndoneByTheStaleProbeCallback() throws Exception {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}",
                transports);
        client.connect();
        FakeTransport transport = transports.latest();
        CountDownLatch sendEntered = new CountDownLatch(1);
        CountDownLatch releaseSend = new CountDownLatch(1);
        transport.blockSend("5", sendEntered, releaseSend);
        AtomicReference<Throwable> callbackFailure = new AtomicReference<>();
        Thread receiver = new Thread(() -> {
            try {
                transport.receive("3probe");
            } catch (Throwable failure) {
                callbackFailure.set(failure);
            }
        });

        receiver.start();
        assertTrue(sendEntered.await(1, TimeUnit.SECONDS));
        client.disconnect();
        releaseSend.countDown();
        receiver.join(2_000);

        assertFalse(receiver.isAlive());
        assertNull(callbackFailure.get(), "a stale transport callback must be ignored, not mutate state");
        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertEquals(List.of("2probe", "5"), transport.sent);
    }

    @Test
    void namespaceConnectErrorDetachesTransportAndLateAckCannotReconnect() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}",
                transports);
        AtomicInteger connectErrors = new AtomicInteger();
        client.on("connect_error", ignored -> connectErrors.incrementAndGet());

        client.connect();
        FakeTransport transport = transports.latest();
        transport.receive("3probe");
        transport.receive("44{\"message\":\"denied\"}");
        transport.receive("40{\"sid\":\"late\"}");

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(1, connectErrors.get());
    }

    @Test
    void explicitDisconnectIgnoresALateNamespaceAcknowledgement() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}",
                transports);
        AtomicInteger connects = new AtomicInteger();
        client.on("connect", ignored -> connects.incrementAndGet());

        client.connect();
        FakeTransport transport = transports.latest();
        transport.receive("3probe");
        client.disconnect();
        transport.receive("40{\"sid\":\"late\"}");

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(0, connects.get());
    }

    @Test
    void serverNamespaceDisconnectDetachesAndClosesTheTransport() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = connectedClient(transports);
        FakeTransport transport = transports.latest();
        AtomicInteger disconnects = new AtomicInteger();
        client.on("disconnect", ignored -> disconnects.incrementAndGet());

        transport.receive("41");

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(transport.closed);
        assertEquals(1, disconnects.get());
    }

    @Test
    void engineProbeHasABoundedDeadline() throws Exception {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"],\"pingTimeout\":25}",
                transports);
        AtomicReference<Object[]> connectError = new AtomicReference<>();
        client.on("connect_error", connectError::set);

        client.connect();
        FakeTransport transport = transports.latest();

        assertTrue(waitUntil(() -> transport.closed && connectError.get() != null, 2_000));
        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(connectError.get()[0] instanceof TimeoutException);
    }

    @Test
    void namespaceAcknowledgementHasABoundedDeadline() throws Exception {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"],\"pingTimeout\":25}",
                transports);
        AtomicReference<Object[]> connectError = new AtomicReference<>();
        client.on("connect_error", connectError::set);

        client.connect();
        FakeTransport transport = transports.latest();
        transport.receive("3probe");

        assertTrue(waitUntil(() -> transport.closed && connectError.get() != null, 2_000));
        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(connectError.get()[0] instanceof TimeoutException);
    }

    @Test
    void lateSendFailureFromAnOldTransportDoesNotPolluteTheNewConnection() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = connectedClient(transports);
        FakeTransport oldTransport = transports.latest();
        AtomicInteger errors = new AtomicInteger();
        client.on("error", ignored -> errors.incrementAndGet());
        CompletableFuture<Void> oldSend = oldTransport.pendingNextSend();

        client.emit("old", "payload");
        client.connect();
        FakeTransport newTransport = transports.latest();
        newTransport.receive("3probe");
        newTransport.receive("40");
        oldSend.completeExceptionally(new IllegalStateException("late failure"));

        assertTrue(client.isConnected());
        assertEquals(0, errors.get());
    }

    @Test
    void disconnectForcesTransportCloseWhenGoodbyeSendNeverCompletes() throws Exception {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = connectedClient(transports);
        FakeTransport transport = transports.latest();
        transport.pendingNextSend();

        client.disconnect();

        assertFalse(client.isConnected());
        assertNull(client.getSessionId());
        assertTrue(waitUntil(() -> transport.closed, 2_000));
    }

    @Test
    void completesEngineUpgradeBeforeConnectingTheSocketNamespace() {
        AtomicReference<String> handshakeUrl = new AtomicReference<>();
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                url -> {
                    handshakeUrl.set(url);
                    return "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}";
                },
                transports);
        AtomicInteger connects = new AtomicInteger();
        AtomicInteger disconnects = new AtomicInteger();
        AtomicReference<Object[]> event = new AtomicReference<>();
        client.on("connect", ignored -> connects.incrementAndGet());
        client.on("disconnect", ignored -> disconnects.incrementAndGet());
        client.on("news", event::set);

        client.connect();
        FakeTransport transport = transports.latest();
        assertEquals("http://example.test:3000/socket.io/?EIO=4&transport=polling", handshakeUrl.get());
        assertEquals("ws://example.test:3000/socket.io/?EIO=4&transport=websocket&sid=engine-session",
                transport.url);
        assertEquals(List.of("2probe"), transport.sent);
        assertFalse(client.isConnected());

        transport.receive("3probe");
        assertEquals(List.of("2probe", "5", "40"), transport.sent);
        assertFalse(client.isConnected());

        transport.receive("40{\"sid\":\"namespace-session\"}");
        assertTrue(client.isConnected());
        assertEquals(1, connects.get());

        client.emit("hello", "world", 7);
        assertEquals("42[\"hello\",\"world\",7]", transport.sent.get(3));
        transport.receive("2heartbeat");
        assertEquals("3heartbeat", transport.sent.get(4));
        transport.receive("42[\"news\",\"payload\",3]");
        assertArrayEquals(new Object[] {"payload", 3}, event.get());

        client.disconnect();
        assertEquals(1, disconnects.get());
        assertTrue(transport.closed);
    }

    @Test
    void formatsNonRootNamespacePacketsWithTheRequiredComma() {
        FakeTransportFactory transports = new FakeTransportFactory();
        SocketIOClient root = new SocketIOClient(
                "https://example.test/socket.io",
                ignored -> "0{\"sid\":\"s id\",\"upgrades\":[\"websocket\"]}",
                transports);
        SocketIOClient chat = root.namespace("chat");

        chat.connect();
        FakeTransport transport = transports.latest();
        assertTrue(transport.url.startsWith("wss://example.test/socket.io/?EIO=4&transport=websocket&sid=s%20id"));
        transport.receive("3probe");
        assertEquals("40/chat,", transport.sent.get(2));
        transport.receive("40/chat,{\"sid\":\"chat\"}");
        assertTrue(chat.isConnected());

        chat.emit("message", "hi");
        assertEquals("42/chat,[\"message\",\"hi\"]", transport.sent.get(3));
    }

    @Test
    void rejectsUnsafeEndpointsBeforeHandshake() {
        assertThrows(IllegalArgumentException.class,
                () -> new SocketIOClient("http://user@example.test/socket.io"));
        assertThrows(IllegalArgumentException.class,
                () -> new SocketIOClient("http://example.test/socket.io#fragment"));
        assertThrows(IllegalArgumentException.class,
                () -> new SocketIOClient("ftp://example.test/socket.io"));
    }

    private static SocketIOClient connectedClient(FakeTransportFactory transports) {
        SocketIOClient client = new SocketIOClient(
                "http://example.test:3000",
                ignored -> "0{\"sid\":\"engine-session\",\"upgrades\":[\"websocket\"]}",
                transports);
        client.connect();
        FakeTransport transport = transports.latest();
        transport.receive("3probe");
        transport.receive("40{\"sid\":\"namespace-session\"}");
        assertTrue(client.isConnected());
        return client;
    }

    private static boolean waitUntil(Check check, long timeoutMillis) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (System.nanoTime() < deadline) {
            if (check.get()) {
                return true;
            }
            Thread.sleep(5);
        }
        return check.get();
    }

    @FunctionalInterface
    private interface Check {
        boolean get();
    }

    private static final class FakeTransportFactory implements SocketIOClient.TransportFactory {
        private FakeTransport latest;

        @Override
        public SocketIOClient.Transport create() {
            latest = new FakeTransport();
            return latest;
        }

        private FakeTransport latest() {
            return latest;
        }
    }

    private static final class FakeTransport implements SocketIOClient.Transport {
        private final List<String> sent = new ArrayList<>();
        private SocketIOClient.TransportListener listener;
        private String url;
        private volatile boolean closed;
        private CompletableFuture<?> nextSend;
        private String blockedPacket;
        private CountDownLatch sendEntered;
        private CountDownLatch releaseSend;

        @Override
        public void connect(String url, SocketIOClient.TransportListener listener) {
            this.url = url;
            this.listener = listener;
            listener.onOpen();
        }

        @Override
        public CompletableFuture<?> sendText(String text) {
            sent.add(text);
            if (text.equals(blockedPacket)) {
                sendEntered.countDown();
                try {
                    releaseSend.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return failedFuture(interrupted);
                }
            }
            if (nextSend != null) {
                CompletableFuture<?> future = nextSend;
                nextSend = null;
                return future;
            }
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void close() {
            closed = true;
        }

        private void receive(String message) {
            listener.onMessage(message);
        }

        private void fail(Throwable error) {
            listener.onError(error);
        }

        private CompletableFuture<Void> pendingNextSend() {
            CompletableFuture<Void> future = new CompletableFuture<>();
            nextSend = future;
            return future;
        }

        private void blockSend(String packet, CountDownLatch entered, CountDownLatch release) {
            blockedPacket = packet;
            sendEntered = entered;
            releaseSend = release;
        }

        private static <T> CompletableFuture<T> failedFuture(Throwable error) {
            CompletableFuture<T> future = new CompletableFuture<>();
            future.completeExceptionally(error);
            return future;
        }
    }
}
