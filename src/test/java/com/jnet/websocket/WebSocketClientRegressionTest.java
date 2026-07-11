package com.jnet.websocket;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import org.junit.jupiter.api.Test;

class WebSocketClientRegressionTest {

    @Test
    void serializesOutboundMessagesUntilThePreviousSendCompletes() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        CompletableFuture<WebSocket> first = client.sendText("first");
        CompletableFuture<WebSocket> second = client.sendText("second");

        assertEquals(List.of("first"), connection.socket.textMessages);
        assertFalse(second.isDone());
        connection.socket.completeNextSend();
        assertEquals(connection.socket, first.get(1, TimeUnit.SECONDS));
        assertEquals(List.of("first", "second"), connection.socket.textMessages);
        connection.socket.completeNextSend();
        assertEquals(connection.socket, second.get(1, TimeUnit.SECONDS));
        client.abort();
    }

    @Test
    void pendingSendLimitFailsFastAndOnlyRecoversAfterTransportCompletion() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .maxPendingSends(2)
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        CompletableFuture<WebSocket> first = client.sendText("first");
        CompletableFuture<WebSocket> second = client.sendText("second");

        assertRejected(client.sendBinary(new byte[16]));
        assertTrue(first.cancel(false));
        assertRejected(client.sendText("cancelled-result-must-not-free-capacity"));

        connection.socket.completeNextSend();
        assertEquals(List.of("first", "second"), connection.socket.textMessages);

        CompletableFuture<WebSocket> third = client.sendText("third");
        assertFalse(third.isCompletedExceptionally());
        connection.socket.completeNextSend();
        assertEquals(List.of("first", "second", "third"), connection.socket.textMessages);
        connection.socket.completeNextSend();

        assertEquals(connection.socket, second.get(1, TimeUnit.SECONDS));
        assertEquals(connection.socket, third.get(1, TimeUnit.SECONDS));
        client.abort();
    }

    @Test
    void pendingSendByteBudgetFailsFastAndRecoversAfterTransportCompletion() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .maxPendingSends(10)
                .maxPendingSendBytes(4)
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        CompletableFuture<WebSocket> first = client.sendBinary(new byte[4]);
        assertRejected(client.sendText("x"));

        connection.socket.completeNextSend();
        assertEquals(connection.socket, first.get(1, TimeUnit.SECONDS));
        assertFalse(client.sendText("x").isCompletedExceptionally());
        connection.socket.completeNextSend();
        client.abort();
    }

    @Test
    void pingPayloadsOverControlFrameLimitFailBeforeTransportUse() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .build();
        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.open();

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.sendPing(ByteBuffer.allocate(126)).get(1, TimeUnit.SECONDS));

        assertTrue(error.getCause() instanceof IllegalArgumentException);
        assertEquals(0, connection.socket.pingCalls.get());
        client.abort();
    }

    @Test
    void closeBypassesAFullPendingSendLimitAndStillWaitsForQueuedData() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .maxPendingSends(1)
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        client.sendText("first");
        assertRejected(client.sendText("rejected"));
        CompletableFuture<WebSocket> closing = client.close();

        assertEquals(0, connection.socket.closeCalls.get());
        connection.socket.completeNextSend();
        assertEquals(connection.socket, closing.get(1, TimeUnit.SECONDS));
        assertEquals(1, connection.socket.closeCalls.get());
    }

    @Test
    void closeTimeoutAbortsWhileWaitingBehindAFullSendQueue() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .maxPendingSends(1)
                .closeTimeout(Duration.ofMillis(25))
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        client.sendText("blocked");
        CompletableFuture<WebSocket> closing = client.close();

        assertTrue(connection.socket.abortedSignal.await(1, TimeUnit.SECONDS));
        assertTrue(closing.isCompletedExceptionally());
        assertEquals(0, connection.socket.closeCalls.get());
    }

    @Test
    void closeWaitsForAlreadyQueuedMessagesWithoutDroppingThem() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();

        client.sendText("first");
        CompletableFuture<WebSocket> second = client.sendText("second");
        CompletableFuture<WebSocket> closing = client.close();

        assertEquals(List.of("first"), connection.socket.textMessages);
        assertEquals(0, connection.socket.closeCalls.get());
        connection.socket.completeNextSend();
        assertEquals(List.of("first", "second"), connection.socket.textMessages);
        connection.socket.completeNextSend();
        assertEquals(connection.socket, second.get(1, TimeUnit.SECONDS));
        assertEquals(connection.socket, closing.get(1, TimeUnit.SECONDS));
        assertEquals(1, connection.socket.closeCalls.get());
    }

    @Test
    void invalidCloseArgumentsDoNotDetachTheLiveSocket() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.open();

        assertThrows(IllegalArgumentException.class, () -> client.close(1005, "reserved"));
        assertThrows(IllegalArgumentException.class, () -> client.close(1002, "client cannot send this code"));
        assertTrue(client.isConnected());
        assertFalse(connection.socket.aborted);
        client.abort();
    }

    @Test
    void abortsWhenTheCloseFrameCannotCompleteBeforeTheConfiguredTimeout() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .closeTimeout(Duration.ofMillis(25))
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.completeClose = false;
        connection.open();

        CompletableFuture<WebSocket> closing = client.close();

        assertTrue(connection.socket.abortedSignal.await(1, TimeUnit.SECONDS));
        assertTrue(closing.isCompletedExceptionally());
    }

    @Test
    void abortInterruptsAnInFlightGracefulCloseImmediately() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.completeClose = false;
        connection.open();
        client.close();

        client.abort();

        assertTrue(connection.socket.aborted);
    }

    @Test
    void listenerClosingInOnOpenDoesNotRestartTheScheduler() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        AtomicReference<WebSocketClient> reference = new AtomicReference<>();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .pingInterval(10)
                .disableReconnect()
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onOpen(WebSocket ignored) {
                        reference.get().abort();
                    }
                })
                .build();
        reference.set(client);

        client.connect("ws://example.test/socket");
        httpClient.latest().open();

        Field field = WebSocketClient.class.getDeclaredField("pingExecutor");
        field.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) field.get(client);
        assertTrue(executor == null || executor.isShutdown());
    }

    @Test
    void rejectsMessagesThatExceedTheConfiguredFragmentLimit() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        List<String> messages = new ArrayList<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .maxMessageSize(4)
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onMessage(String message) {
                        messages.add(message);
                    }

                    @Override
                    public void onError(Throwable error) {
                        failure.set(error);
                    }
                })
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.open();
        connection.listener.onText(connection.socket, "hel", false);
        connection.listener.onText(connection.socket, "lo", true);

        assertTrue(connection.socket.aborted);
        assertTrue(failure.get() instanceof IllegalStateException);
        assertTrue(messages.isEmpty());
        client.abort();
    }

    @Test
    void listenerRuntimeFailuresAreReportedWithoutBreakingInboundBackpressure() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        RuntimeException textFailure = new IllegalStateException("text listener failed");
        RuntimeException binaryFailure = new IllegalArgumentException("binary listener failed");
        RuntimeException pingFailure = new IllegalStateException("ping listener failed");
        RuntimeException pongFailure = new IllegalArgumentException("pong listener failed");
        List<Throwable> reported = new ArrayList<>();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onMessage(String message) {
                        throw textFailure;
                    }

                    @Override
                    public void onBinaryMessage(byte[] data) {
                        throw binaryFailure;
                    }

                    @Override
                    public void onPing(ByteBuffer data) {
                        throw pingFailure;
                    }

                    @Override
                    public void onPong(ByteBuffer data) {
                        throw pongFailure;
                    }

                    @Override
                    public void onError(Throwable error) {
                        reported.add(error);
                    }
                })
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.open();
        assertEquals(1, connection.socket.requestCalls.get());

        assertDoesNotThrow(() -> connection.listener.onText(connection.socket, "text", true));
        assertEquals(2, connection.socket.requestCalls.get());
        assertDoesNotThrow(() -> connection.listener.onBinary(
                connection.socket, ByteBuffer.wrap(new byte[] {1, 2}), true));
        assertEquals(3, connection.socket.requestCalls.get());
        assertDoesNotThrow(() -> connection.listener.onPing(
                connection.socket, ByteBuffer.wrap(new byte[] {3})));
        assertEquals(4, connection.socket.requestCalls.get());
        assertDoesNotThrow(() -> connection.listener.onPong(
                connection.socket, ByteBuffer.wrap(new byte[] {4})));

        assertEquals(5, connection.socket.requestCalls.get());
        assertEquals(List.of(textFailure, binaryFailure, pingFailure, pongFailure), reported);
        assertTrue(client.isConnected());
        client.abort();
    }

    @Test
    void pingSendFailureAbortsTheConnectionInsteadOfSilentlyStoppingHeartbeat() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .pingInterval(10)
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.failPing = true;
        connection.open();

        assertTrue(connection.socket.abortedSignal.await(1, TimeUnit.SECONDS));
        client.abort();
    }

    @Test
    void heartbeatDoesNotQueueRepeatedPingsBehindASlowSend() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .pingInterval(100)
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();
        client.sendText("blocked");

        Thread.sleep(230);
        connection.socket.completeNextSend();
        assertTrue(waitUntil(() -> connection.socket.pingCalls.get() == 1, 500));
        connection.socket.completeNextSend();
        Thread.sleep(20);

        assertEquals(1, connection.socket.pingCalls.get());
        client.abort();
    }

    @Test
    void lateHeartbeatFailureDoesNotAbortAnExplicitGracefulClose() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .pingInterval(10)
                .disableReconnect()
                .build();

        client.connect("ws://example.test/socket");
        PendingConnection connection = httpClient.latest();
        connection.socket.autoCompleteSends = false;
        connection.open();
        assertTrue(waitUntil(() -> connection.socket.pingCalls.get() == 1, 500));

        CompletableFuture<WebSocket> closing = client.close();
        connection.socket.failNextSend(new IOException("late ping failure"));

        assertEquals(connection.socket, closing.get(1, TimeUnit.SECONDS));
        assertFalse(connection.socket.aborted);
    }

    @Test
    void ignoresTerminalCallbacksFromASupersededConnection() {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        AtomicInteger closes = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .disableReconnect()
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onClose(int statusCode, String reason) {
                        closes.incrementAndGet();
                    }

                    @Override
                    public void onError(Throwable error) {
                        errors.incrementAndGet();
                    }
                })
                .build();

        client.connect("ws://first.example/socket");
        PendingConnection first = httpClient.latest();
        first.open();
        first.socket.abortCallback = () -> first.listener.onError(
                first.socket, new IOException("aborted old connection"));
        client.connect("ws://second.example/socket");
        PendingConnection second = httpClient.latest();
        second.open();

        first.listener.onClose(first.socket, WebSocket.NORMAL_CLOSURE, "old");
        first.listener.onError(first.socket, new IOException("old"));

        assertEquals(0, closes.get());
        assertEquals(0, errors.get());
        assertTrue(client.isConnected());
        client.abort();
    }

    @Test
    void normalizesTheUrlUsedByReconnectAttempts() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .maxReconnectAttempts(1)
                .reconnectDelay(1)
                .build();

        client.connect("  ws://example.test/socket  ");
        PendingConnection first = httpClient.latest();
        assertEquals(URI.create("ws://example.test/socket"), first.uri);
        first.fail(new IOException("first"));

        assertTrue(waitUntil(() -> httpClient.size() == 2, 1_000));
        assertEquals(URI.create("ws://example.test/socket"), httpClient.latest().uri);
        client.abort();
    }

    @Test
    void releasesTheSchedulerWhenTheReconnectLimitIsExhausted() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .maxReconnectAttempts(1)
                .reconnectDelay(1)
                .build();
        Field field = WebSocketClient.class.getDeclaredField("pingExecutor");
        field.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) field.get(client);

        client.connect("ws://example.test/socket");
        httpClient.latest().fail(new IOException("first"));
        assertTrue(waitUntil(() -> httpClient.size() == 2, 1_000));
        httpClient.latest().fail(new IOException("second"));

        assertTrue(waitUntil(executor::isShutdown, 1_000));
        assertNull(field.get(client));
        client.abort();
    }

    @Test
    void reconnectListenerFailureDoesNotPreventTheRetry() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .maxReconnectAttempts(1)
                .reconnectDelay(1)
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onReconnecting(int attempt) {
                        throw new IllegalStateException("listener failed");
                    }
                })
                .build();

        client.connect("ws://example.test/socket");
        httpClient.latest().fail(new IOException("first"));

        assertTrue(waitUntil(() -> httpClient.size() == 2, 1_000));
        client.abort();
    }

    @Test
    void reconnectListenerCancellingTheClientDoesNotResurrectTheScheduler() throws Exception {
        RecordingHttpClient httpClient = new RecordingHttpClient();
        AtomicReference<WebSocketClient> reference = new AtomicReference<>();
        WebSocketClient client = WebSocketClient.newBuilder()
                .httpClient(httpClient)
                .disablePing()
                .maxReconnectAttempts(1)
                .reconnectDelay(1)
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onReconnecting(int attempt) {
                        reference.get().abort();
                    }
                })
                .build();
        reference.set(client);
        Field field = WebSocketClient.class.getDeclaredField("pingExecutor");
        field.setAccessible(true);

        client.connect("ws://example.test/socket");
        httpClient.latest().fail(new IOException("first"));
        Thread.sleep(50);

        ScheduledExecutorService executor = (ScheduledExecutorService) field.get(client);
        assertTrue(executor == null || executor.isShutdown());
        assertEquals(1, httpClient.size());
    }

    @Test
    void aggregatesTextAndBinaryFragmentsBeforeNotifyingListener() throws Exception {
        try (FragmentingWebSocketServer server = new FragmentingWebSocketServer()) {
            List<String> textMessages = new ArrayList<>();
            List<byte[]> binaryMessages = new ArrayList<>();
            CountDownLatch messages = new CountDownLatch(2);
            WebSocketClient client = WebSocketClient.newBuilder()
                    .disablePing()
                    .disableReconnect()
                    .listener(new WebSocketClient.WebSocketListener() {
                        @Override
                        public void onMessage(String message) {
                            textMessages.add(message);
                            messages.countDown();
                        }

                        @Override
                        public void onBinaryMessage(byte[] data) {
                            binaryMessages.add(data);
                            messages.countDown();
                        }
                    })
                    .build();

            try {
                client.connect("ws://127.0.0.1:" + server.getPort()).get(2, TimeUnit.SECONDS);
                assertTrue(messages.await(2, TimeUnit.SECONDS));
                assertEquals(List.of("hello"), textMessages);
                assertEquals(1, binaryMessages.size());
                assertArrayEquals(new byte[] {1, 2, 3, 4}, binaryMessages.get(0));
            } finally {
                client.abort();
            }
        }
    }

    @Test
    void retriesEveryConsecutiveConnectionFailureUpToTheConfiguredLimit() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        CountDownLatch retries = new CountDownLatch(3);
        WebSocketClient client = WebSocketClient.newBuilder()
                .disablePing()
                .maxReconnectAttempts(3)
                .reconnectDelay(10)
                .listener(new WebSocketClient.WebSocketListener() {
                    @Override
                    public void onReconnecting(int attempt) {
                        retries.countDown();
                    }
                })
                .build();

        try {
            client.connect("ws://127.0.0.1:" + closedPort);
            assertTrue(retries.await(2, TimeUnit.SECONDS));
            assertEquals(3, client.getReconnectAttempts());
        } finally {
            client.abort();
        }
    }

    @Test
    void schedulerUsesDaemonThreadAndIsReleasedByAbort() throws Exception {
        WebSocketClient client = WebSocketClient.newBuilder().pingInterval(25).build();
        Method startPingPong = WebSocketClient.class.getDeclaredMethod("startPingPong");
        startPingPong.setAccessible(true);
        startPingPong.invoke(client);
        Field field = WebSocketClient.class.getDeclaredField("pingExecutor");
        field.setAccessible(true);
        ScheduledExecutorService executor = (ScheduledExecutorService) field.get(client);

        try {
            assertTrue(executor.submit(() -> Thread.currentThread().isDaemon()).get(1, TimeUnit.SECONDS));
        } finally {
            client.abort();
        }
        assertTrue(executor.isShutdown());
    }

    @Test
    void rejectsInvalidUrlsAndNonPositiveConnectTimeoutsBeforeAllocatingAConnection() {
        WebSocketClient client = WebSocketClient.newBuilder().disableReconnect().disablePing().build();

        assertThrows(IllegalArgumentException.class, () -> client.connect("https://example.com/socket"));
        assertThrows(IllegalArgumentException.class, () -> client.connect("ws://user@example.com/socket"));
        assertThrows(IllegalArgumentException.class, () -> client.connect("ws://example.com/socket#fragment"));
        assertThrows(IllegalStateException.class, () -> WebSocketClient.newBuilder()
                .connectTimeout(java.time.Duration.ZERO)
                .build());
        assertThrows(IllegalStateException.class, () -> WebSocketClient.newBuilder()
                .maxPendingSends(0)
                .build());
        assertThrows(IllegalStateException.class, () -> WebSocketClient.newBuilder()
                .maxPendingSendBytes(0)
                .build());
    }

    private static void assertRejected(CompletableFuture<WebSocket> future) {
        ExecutionException failure = assertThrows(ExecutionException.class,
                () -> future.get(1, TimeUnit.SECONDS));
        assertTrue(failure.getCause() instanceof RejectedExecutionException);
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
        boolean get() throws Exception;
    }

    private static final class RecordingHttpClient extends HttpClient {
        private final List<PendingConnection> connections = Collections.synchronizedList(new ArrayList<>());

        @Override
        public WebSocket.Builder newWebSocketBuilder() {
            return new WebSocket.Builder() {
                @Override
                public WebSocket.Builder header(String name, String value) {
                    return this;
                }

                @Override
                public WebSocket.Builder connectTimeout(Duration timeout) {
                    return this;
                }

                @Override
                public WebSocket.Builder subprotocols(String mostPreferred, String... lesserPreferred) {
                    return this;
                }

                @Override
                public CompletableFuture<WebSocket> buildAsync(URI uri, WebSocket.Listener listener) {
                    PendingConnection connection = new PendingConnection(uri, listener);
                    connections.add(connection);
                    return connection.future;
                }
            };
        }

        private PendingConnection latest() {
            synchronized (connections) {
                return connections.get(connections.size() - 1);
            }
        }

        private int size() {
            return connections.size();
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public SSLContext sslContext() {
            return null;
        }

        @Override
        public SSLParameters sslParameters() {
            return new SSLParameters();
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class PendingConnection {
        private final URI uri;
        private final WebSocket.Listener listener;
        private final ControlledWebSocket socket = new ControlledWebSocket();
        private final CompletableFuture<WebSocket> future = new CompletableFuture<>();

        private PendingConnection(URI uri, WebSocket.Listener listener) {
            this.uri = uri;
            this.listener = listener;
        }

        private void open() {
            listener.onOpen(socket);
            future.complete(socket);
        }

        private void fail(Throwable error) {
            future.completeExceptionally(error);
        }
    }

    private static final class ControlledWebSocket implements WebSocket {
        private final List<String> textMessages = Collections.synchronizedList(new ArrayList<>());
        private final Deque<CompletableFuture<WebSocket>> pendingSends = new LinkedList<>();
        private final CountDownLatch abortedSignal = new CountDownLatch(1);
        private final AtomicInteger closeCalls = new AtomicInteger();
        private final AtomicInteger pingCalls = new AtomicInteger();
        private final AtomicInteger requestCalls = new AtomicInteger();
        private volatile boolean autoCompleteSends = true;
        private volatile boolean completeClose = true;
        private volatile boolean failPing;
        private volatile boolean aborted;
        private volatile boolean outputClosed;
        private volatile Runnable abortCallback;

        @Override
        public synchronized CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            textMessages.add(data.toString());
            return newSendFuture();
        }

        @Override
        public synchronized CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            return newSendFuture();
        }

        @Override
        public synchronized CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            pingCalls.incrementAndGet();
            if (failPing) {
                outputClosed = true;
                throw new IllegalStateException("pending send");
            }
            return newSendFuture();
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            if (statusCode == 1005) {
                throw new IllegalArgumentException("reserved close code");
            }
            closeCalls.incrementAndGet();
            outputClosed = true;
            return completeClose ? CompletableFuture.completedFuture(this) : new CompletableFuture<>();
        }

        @Override
        public void request(long n) {
            requestCalls.incrementAndGet();
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return outputClosed || aborted;
        }

        @Override
        public boolean isInputClosed() {
            return aborted;
        }

        @Override
        public void abort() {
            aborted = true;
            outputClosed = true;
            abortedSignal.countDown();
            Runnable callback = abortCallback;
            if (callback != null) {
                abortCallback = null;
                callback.run();
            }
        }

        private CompletableFuture<WebSocket> newSendFuture() {
            CompletableFuture<WebSocket> future = new CompletableFuture<>();
            if (autoCompleteSends) {
                future.complete(this);
            } else {
                pendingSends.addLast(future);
            }
            return future;
        }

        private synchronized void completeNextSend() {
            CompletableFuture<WebSocket> future = pendingSends.removeFirst();
            future.complete(this);
        }

        private synchronized void failNextSend(Throwable error) {
            CompletableFuture<WebSocket> future = pendingSends.removeFirst();
            future.completeExceptionally(error);
        }
    }

    private static final class FragmentingWebSocketServer implements AutoCloseable {
        private final ServerSocket serverSocket = new ServerSocket(0);
        private final Thread thread;

        private FragmentingWebSocketServer() throws IOException {
            thread = new Thread(this::serve, "websocket-fragment-test");
            thread.setDaemon(true);
            thread.start();
        }

        private int getPort() {
            return serverSocket.getLocalPort();
        }

        private void serve() {
            try (Socket socket = serverSocket.accept()) {
                String headers = readHttpHeaders(socket.getInputStream());
                String key = findHeader(headers, "sec-websocket-key");
                String accept = Base64.getEncoder().encodeToString(
                        MessageDigest.getInstance("SHA-1")
                                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                        .getBytes(StandardCharsets.US_ASCII)));
                OutputStream out = socket.getOutputStream();
                out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                writeFrame(out, false, 1, "hel".getBytes(StandardCharsets.UTF_8));
                writeFrame(out, true, 0, "lo".getBytes(StandardCharsets.UTF_8));
                writeFrame(out, false, 2, new byte[] {1, 2});
                writeFrame(out, true, 0, new byte[] {3, 4});
                out.flush();
                Thread.sleep(500);
            } catch (Exception ignored) {
                // Client-side assertions report failures.
            }
        }

        private static String readHttpHeaders(InputStream in) throws IOException {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int state = 0;
            while (state < 4) {
                int value = in.read();
                if (value == -1) {
                    throw new IOException("Unexpected EOF during WebSocket handshake");
                }
                out.write(value);
                state = (state == 0 && value == '\r') ? 1
                        : (state == 1 && value == '\n') ? 2
                        : (state == 2 && value == '\r') ? 3
                        : (state == 3 && value == '\n') ? 4
                        : 0;
            }
            return out.toString(StandardCharsets.US_ASCII);
        }

        private static String findHeader(String headers, String name) throws IOException {
            for (String line : headers.split("\r\n")) {
                int separator = line.indexOf(':');
                if (separator > 0
                        && line.substring(0, separator).trim().toLowerCase(Locale.ROOT).equals(name)) {
                    return line.substring(separator + 1).trim();
                }
            }
            throw new IOException("Missing " + name);
        }

        private static void writeFrame(OutputStream out, boolean fin, int opcode, byte[] payload)
                throws IOException {
            out.write((fin ? 0x80 : 0) | opcode);
            out.write(payload.length);
            out.write(payload);
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(1_000);
        }
    }
}
