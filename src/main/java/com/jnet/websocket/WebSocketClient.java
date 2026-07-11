package com.jnet.websocket;

import com.jnet.core.JNetClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * WebSocket client based on the JDK 11 HTTP client.
 */
public class WebSocketClient {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final int DEFAULT_MAX_MESSAGE_SIZE = 16 * 1024 * 1024;
    private static final int DEFAULT_MAX_PENDING_SENDS = 1_024;
    private static final long DEFAULT_MAX_PENDING_SEND_BYTES = 64L * 1024L * 1024L;
    private static final Duration DEFAULT_CLOSE_TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient httpClient;
    private final Duration connectTimeout;
    private final WebSocketListener listener;
    private final long pingInterval;
    private final int maxReconnectAttempts;
    private final long reconnectDelay;
    private final int maxMessageSize;
    private final int maxPendingSends;
    private final long maxPendingSendBytes;
    private final long closeTimeoutMillis;

    private final AtomicLong lastPongTime = new AtomicLong(System.currentTimeMillis());
    private final AtomicBoolean shouldReconnect = new AtomicBoolean(true);
    private final AtomicInteger reconnectAttempts = new AtomicInteger();
    private final AtomicBoolean reconnectScheduled = new AtomicBoolean();
    private final AtomicBoolean pingOutstanding = new AtomicBoolean();
    private final AtomicLong connectionGeneration = new AtomicLong();
    private final AtomicLong connectionAttempt = new AtomicLong();
    private final Object schedulerLock = new Object();
    private final Object outboundLock = new Object();

    private volatile WebSocket webSocket;
    private volatile String currentUrl;
    private volatile ScheduledExecutorService pingExecutor;
    private volatile ScheduledFuture<?> pingTask;
    private volatile ScheduledFuture<?> reconnectTask;
    private OutboundQueue outboundQueue;

    private WebSocketClient(Builder builder) {
        this.httpClient = builder.httpClient != null
                ? builder.httpClient
                : JNetClient.getInstance().getHttpClient();
        this.connectTimeout = builder.connectTimeout;
        this.listener = builder.listener;
        this.pingInterval = builder.pingInterval;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelay = builder.reconnectDelay;
        this.maxMessageSize = builder.maxMessageSize;
        this.maxPendingSends = builder.maxPendingSends;
        this.maxPendingSendBytes = builder.maxPendingSendBytes;
        this.closeTimeoutMillis = durationToMillis(builder.closeTimeout);
        if (needsScheduler()) {
            this.pingExecutor = newScheduler();
        }
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public interface WebSocketListener {
        default void onOpen(WebSocket webSocket) {}
        default void onMessage(String message) {}
        default void onBinaryMessage(byte[] data) {}
        default void onPing(ByteBuffer data) {}
        default void onPong(ByteBuffer data) {}
        default void onClose(int statusCode, String reason) {}
        default void onError(Throwable error) {}
        default void onReconnecting(int attempt) {}
    }

    /** Connect, cancelling any reconnect left over from an earlier explicit connection. */
    public CompletableFuture<WebSocket> connect(String url) {
        URI uri = requireWebSocketUri(url);
        long generation = connectionGeneration.incrementAndGet();
        WebSocket previous = detachSocket();
        if (previous != null) {
            previous.abort();
        }
        stopPingTask();
        pingOutstanding.set(false);
        currentUrl = uri.toString();
        shouldReconnect.set(true);
        reconnectAttempts.set(0);
        cancelReconnectTask();
        ensureScheduler();
        return connectInternal(uri, generation);
    }

    private CompletableFuture<WebSocket> connectInternal(URI uri, long generation) {
        long attempt = connectionAttempt.incrementAndGet();
        CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                .connectTimeout(connectTimeout)
                .buildAsync(uri, new WebSocket.Listener() {
                    private StringBuilder textFragments = new StringBuilder();
                    private ByteArrayOutputStream binaryFragments = new ByteArrayOutputStream();

                    @Override
                    public void onOpen(WebSocket openedSocket) {
                        if (!isCurrent(generation, attempt) || !shouldReconnect.get()) {
                            openedSocket.abort();
                            return;
                        }
                        webSocket = openedSocket;
                        installOutbound(openedSocket, generation);
                        reconnectAttempts.set(0);
                        reconnectScheduled.set(false);
                        reconnectTask = null;
                        pingOutstanding.set(false);
                        lastPongTime.set(System.currentTimeMillis());
                        if (listener != null) {
                            try {
                                listener.onOpen(openedSocket);
                            } catch (RuntimeException listenerFailure) {
                                notifyError(listenerFailure);
                            }
                        }
                        if (!isCurrent(generation, attempt) || !shouldReconnect.get()
                                || webSocket != openedSocket || openedSocket.isOutputClosed()) {
                            discardOpenedSocket(openedSocket);
                            return;
                        }
                        startPingPong(openedSocket, generation, attempt);
                        openedSocket.request(1);
                    }

                    @Override
                    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                        if (isCurrent(generation, attempt) && webSocket == socket) {
                            if (data.length() > maxMessageSize - textFragments.length()) {
                                textFragments = new StringBuilder();
                                failAttempt(socket, generation, attempt,
                                        new IllegalStateException("WebSocket text message exceeds "
                                                + maxMessageSize + " characters"));
                                return null;
                            }
                            textFragments.append(data);
                            if (last) {
                                String message = textFragments.toString();
                                textFragments = new StringBuilder();
                                if (listener != null) {
                                    try {
                                        listener.onMessage(message);
                                    } catch (RuntimeException listenerFailure) {
                                        notifyError(listenerFailure);
                                    }
                                }
                            }
                            if (isCurrent(generation, attempt) && webSocket == socket) {
                                socket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onBinary(WebSocket socket, ByteBuffer data, boolean last) {
                        if (isCurrent(generation, attempt) && webSocket == socket) {
                            if (data.remaining() > maxMessageSize - binaryFragments.size()) {
                                binaryFragments = new ByteArrayOutputStream();
                                failAttempt(socket, generation, attempt,
                                        new IllegalStateException("WebSocket binary message exceeds "
                                                + maxMessageSize + " bytes"));
                                return null;
                            }
                            byte[] fragment = new byte[data.remaining()];
                            data.get(fragment);
                            binaryFragments.write(fragment, 0, fragment.length);
                            if (last) {
                                byte[] message = binaryFragments.toByteArray();
                                binaryFragments = new ByteArrayOutputStream();
                                if (listener != null) {
                                    try {
                                        listener.onBinaryMessage(message);
                                    } catch (RuntimeException listenerFailure) {
                                        notifyError(listenerFailure);
                                    }
                                }
                            }
                            if (isCurrent(generation, attempt) && webSocket == socket) {
                                socket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onPing(WebSocket socket, ByteBuffer message) {
                        if (isCurrent(generation, attempt) && webSocket == socket) {
                            if (listener != null) {
                                try {
                                    listener.onPing(message.asReadOnlyBuffer());
                                } catch (RuntimeException listenerFailure) {
                                    notifyError(listenerFailure);
                                }
                            }
                            if (isCurrent(generation, attempt) && webSocket == socket) {
                                socket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onPong(WebSocket socket, ByteBuffer message) {
                        if (isCurrent(generation, attempt) && webSocket == socket) {
                            lastPongTime.set(System.currentTimeMillis());
                            if (listener != null) {
                                try {
                                    listener.onPong(message.asReadOnlyBuffer());
                                } catch (RuntimeException listenerFailure) {
                                    notifyError(listenerFailure);
                                }
                            }
                            if (isCurrent(generation, attempt) && webSocket == socket) {
                                socket.request(1);
                            }
                        }
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket socket, int statusCode, String reason) {
                        finishClosedAttempt(socket, generation, attempt, statusCode, reason);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket socket, Throwable error) {
                        failAttempt(socket, generation, attempt, error);
                    }
                });

        future.whenComplete((socket, error) -> {
            if (error != null) {
                failAttempt(socket, generation, attempt, unwrap(error));
            }
        });
        return future;
    }

    private boolean isCurrent(long generation) {
        return connectionGeneration.get() == generation;
    }

    private boolean isCurrent(long generation, long attempt) {
        return isCurrent(generation) && connectionAttempt.get() == attempt;
    }

    private boolean isActive(WebSocket socket, long generation, long attempt) {
        return shouldReconnect.get() && isCurrent(generation, attempt) && webSocket == socket;
    }

    private void startPingPong() {
        WebSocket current = webSocket;
        startPingPong(current, connectionGeneration.get(), connectionAttempt.get());
    }

    private void startPingPong(WebSocket socket, long generation, long attempt) {
        if (pingInterval <= 0) {
            return;
        }
        stopPingTask();
        ScheduledExecutorService executor = ensureScheduler();
        if (executor == null) {
            return;
        }
        pingTask = executor.scheduleAtFixedRate(() -> {
            try {
                if (socket == null || !isActive(socket, generation, attempt)
                        || socket.isOutputClosed()) {
                    return;
                }
                if (System.currentTimeMillis() - lastPongTime.get()
                        > saturatingMultiply(pingInterval, 3)) {
                    if (isActive(socket, generation, attempt)) {
                        failAttempt(socket, generation, attempt,
                                new IllegalStateException("Ping timeout - no pong received"));
                    }
                    return;
                }
                if (!pingOutstanding.compareAndSet(false, true)) {
                    return;
                }
                sendPing(ByteBuffer.allocate(0)).whenComplete((ignored, error) -> {
                    if (isActive(socket, generation, attempt)) {
                        pingOutstanding.set(false);
                        if (error != null) {
                            failAttempt(socket, generation, attempt, unwrap(error));
                        }
                    }
                });
            } catch (RuntimeException error) {
                if (isActive(socket, generation, attempt)) {
                    pingOutstanding.set(false);
                    failAttempt(socket, generation, attempt, error);
                }
            }
        }, pingInterval, pingInterval, TimeUnit.MILLISECONDS);
    }

    private void stopPingTask() {
        ScheduledFuture<?> task = pingTask;
        pingTask = null;
        pingOutstanding.set(false);
        if (task != null) {
            task.cancel(false);
        }
    }

    private void scheduleReconnect(long generation) {
        if (!shouldReconnect.get() || !isCurrent(generation)) {
            return;
        }
        if (reconnectAttempts.get() >= maxReconnectAttempts) {
            releaseIdleScheduler();
            return;
        }
        if (!reconnectScheduled.compareAndSet(false, true)) {
            return;
        }

        int attempt = reconnectAttempts.incrementAndGet();
        if (listener != null) {
            try {
                listener.onReconnecting(attempt);
            } catch (RuntimeException listenerFailure) {
                notifyError(listenerFailure);
            }
        }
        if (!shouldReconnect.get() || !isCurrent(generation)) {
            reconnectScheduled.set(false);
            releaseIdleScheduler();
            return;
        }

        ScheduledExecutorService executor = ensureScheduler();
        if (executor == null) {
            reconnectScheduled.set(false);
            return;
        }
        long delay = reconnectDelay > Long.MAX_VALUE / Math.max(1, attempt)
                ? Long.MAX_VALUE
                : reconnectDelay * attempt;
        try {
            reconnectTask = executor.schedule(() -> {
                reconnectTask = null;
                reconnectScheduled.set(false);
                String url = currentUrl;
                if (shouldReconnect.get() && isCurrent(generation) && url != null) {
                    connectInternal(URI.create(url), generation);
                }
            }, delay, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            reconnectScheduled.set(false);
            if (shouldReconnect.get()) {
                notifyError(e);
            }
        }
    }

    private void cancelReconnectTask() {
        ScheduledFuture<?> task = reconnectTask;
        reconnectTask = null;
        reconnectScheduled.set(false);
        if (task != null) {
            task.cancel(false);
        }
    }

    public CompletableFuture<WebSocket> sendText(String text) {
        Objects.requireNonNull(text, "text");
        return enqueueSend(utf8Length(text), () -> socket -> socket.sendText(text, true));
    }

    public CompletableFuture<WebSocket> sendBinary(byte[] data) {
        if (data == null) {
            return failedFuture(new IllegalArgumentException("data cannot be null"));
        }
        return enqueueSend(data.length, () -> {
            byte[] copy = data.clone();
            return socket -> socket.sendBinary(ByteBuffer.wrap(copy), true);
        });
    }

    public CompletableFuture<WebSocket> sendPing(ByteBuffer data) {
        if (data == null) {
            return failedFuture(new IllegalArgumentException("data cannot be null"));
        }
        int payloadBytes = data.remaining();
        if (payloadBytes > 125) {
            return failedFuture(new IllegalArgumentException(
                    "WebSocket ping payload cannot exceed 125 bytes"));
        }
        return enqueueSend(payloadBytes, () -> {
            ByteBuffer source = data.asReadOnlyBuffer();
            ByteBuffer copy = ByteBuffer.allocate(source.remaining());
            copy.put(source).flip();
            return socket -> socket.sendPing(copy);
        });
    }

    public CompletableFuture<WebSocket> close() {
        return close(WebSocket.NORMAL_CLOSURE, "");
    }

    public CompletableFuture<WebSocket> close(int statusCode, String reason) {
        validateClose(statusCode, reason);
        shouldReconnect.set(false);
        cancelReconnectTask();
        stopPingTask();
        WebSocket current = webSocket;
        webSocket = null;
        if (current == null) {
            shutdownScheduler();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<WebSocket> closing = enqueueClose(current, statusCode, reason);
        closing.orTimeout(closeTimeoutMillis, TimeUnit.MILLISECONDS)
                .whenComplete((ignored, error) -> {
                    if (error != null) {
                        current.abort();
                    }
                    clearOutbound(current);
                    shutdownScheduler();
                });
        return closing;
    }

    public void abort() {
        shouldReconnect.set(false);
        connectionGeneration.incrementAndGet();
        cancelReconnectTask();
        stopPingTask();
        WebSocket current = detachSocket();
        if (current != null) {
            current.abort();
        }
        shutdownScheduler();
    }

    public boolean isConnected() {
        WebSocket current = webSocket;
        return current != null && !current.isInputClosed() && !current.isOutputClosed();
    }

    public int getReconnectAttempts() {
        return reconnectAttempts.get();
    }

    private boolean needsScheduler() {
        return pingInterval > 0 || maxReconnectAttempts > 0;
    }

    private ScheduledExecutorService ensureScheduler() {
        if (!needsScheduler()) {
            return null;
        }
        synchronized (schedulerLock) {
            if (pingExecutor == null || pingExecutor.isShutdown()) {
                pingExecutor = newScheduler();
            }
            return pingExecutor;
        }
    }

    private static ScheduledExecutorService newScheduler() {
        return Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "jnet-websocket-" + THREAD_IDS.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
    }

    private void shutdownScheduler() {
        synchronized (schedulerLock) {
            ScheduledExecutorService executor = pingExecutor;
            pingExecutor = null;
            if (executor != null) {
                executor.shutdownNow();
            }
        }
    }

    private void releaseIdleScheduler() {
        if (webSocket == null && !reconnectScheduled.get()) {
            shutdownScheduler();
        }
    }

    private void installOutbound(WebSocket socket, long generation) {
        synchronized (outboundLock) {
            outboundQueue = new OutboundQueue(socket, generation);
        }
    }

    private void clearOutbound(WebSocket socket) {
        if (socket == null) {
            return;
        }
        synchronized (outboundLock) {
            if (outboundQueue != null && outboundQueue.socket == socket) {
                outboundQueue = null;
            }
        }
    }

    private WebSocket detachSocket() {
        synchronized (outboundLock) {
            WebSocket current = webSocket;
            webSocket = null;
            if (current == null && outboundQueue != null) {
                current = outboundQueue.socket;
            }
            outboundQueue = null;
            return current;
        }
    }

    private void discardOpenedSocket(WebSocket socket) {
        boolean gracefulClose;
        synchronized (outboundLock) {
            if (webSocket == socket) {
                webSocket = null;
            }
            gracefulClose = outboundQueue != null
                    && outboundQueue.socket == socket && outboundQueue.closing;
            if (!gracefulClose && outboundQueue != null && outboundQueue.socket == socket) {
                outboundQueue = null;
            }
        }
        if (!gracefulClose && !socket.isOutputClosed()) {
            socket.abort();
        }
    }

    private CompletableFuture<WebSocket> enqueueSend(
            long payloadBytes, SendOperationFactory operationFactory) {
        WebSocket current = webSocket;
        if (current == null || current.isOutputClosed()) {
            return failedFuture(new IllegalStateException("WebSocket not connected"));
        }
        final OutboundQueue selected;
        final CompletableFuture<WebSocket> previous;
        final CompletableFuture<WebSocket> sequence = new CompletableFuture<>();
        final CompletableFuture<WebSocket> result = new CompletableFuture<>();
        synchronized (outboundLock) {
            if (webSocket != current || current.isOutputClosed()) {
                return failedFuture(new IllegalStateException("WebSocket not connected"));
            }
            OutboundQueue queue = outboundQueue;
            if (queue == null || queue.socket != current) {
                queue = new OutboundQueue(current, connectionGeneration.get());
                outboundQueue = queue;
            }
            if (queue.closing) {
                return failedFuture(new IllegalStateException("WebSocket is closing"));
            }
            if (queue.pendingSends >= maxPendingSends) {
                return failedFuture(new RejectedExecutionException(
                        "WebSocket pending send limit reached: " + maxPendingSends));
            }
            if (payloadBytes > maxPendingSendBytes - queue.pendingSendBytes) {
                return failedFuture(new RejectedExecutionException(
                        "WebSocket pending send byte limit reached: " + maxPendingSendBytes));
            }
            selected = queue;
            previous = selected.tail;
            selected.tail = sequence;
            selected.pendingSends++;
            selected.pendingSendBytes += payloadBytes;
        }
        final SendOperation operation;
        try {
            operation = Objects.requireNonNull(operationFactory.create(),
                    "WebSocket send operation cannot be null");
        } catch (RuntimeException error) {
            failPreparedSend(previous, selected, sequence, result, payloadBytes, error);
            return result;
        } catch (Error error) {
            failPreparedSend(previous, selected, sequence, result, payloadBytes, error);
            throw error;
        }
        previous.handle((ignored, error) -> selected.socket)
                .thenCompose(socket -> executeSend(selected, socket, operation))
                .whenComplete((sent, error) -> finishPendingSend(
                        selected, sequence, result, payloadBytes, sent, error));
        return result;
    }

    private void failPreparedSend(CompletableFuture<WebSocket> previous,
            OutboundQueue selected, CompletableFuture<WebSocket> sequence,
            CompletableFuture<WebSocket> result, long payloadBytes, Throwable error) {
        previous.whenComplete((ignored, previousError) ->
                finishPendingSend(selected, sequence, result, payloadBytes, null, error));
    }

    private void finishPendingSend(OutboundQueue selected,
            CompletableFuture<WebSocket> sequence, CompletableFuture<WebSocket> result,
            long payloadBytes, WebSocket sent, Throwable error) {
        releasePendingSend(selected, payloadBytes);
        complete(sequence, sent, error);
        complete(result, sent, error);
    }

    private void releasePendingSend(OutboundQueue selected, long payloadBytes) {
        synchronized (outboundLock) {
            if (selected.pendingSends > 0) {
                selected.pendingSends--;
            }
            selected.pendingSendBytes = Math.max(0L, selected.pendingSendBytes - payloadBytes);
        }
    }

    private CompletableFuture<WebSocket> enqueueClose(WebSocket socket, int statusCode, String reason) {
        final OutboundQueue selected;
        final CompletableFuture<WebSocket> previous;
        final CompletableFuture<WebSocket> result = new CompletableFuture<>();
        synchronized (outboundLock) {
            OutboundQueue queue = outboundQueue;
            if (queue == null || queue.socket != socket) {
                queue = new OutboundQueue(socket, connectionGeneration.get());
                outboundQueue = queue;
            }
            queue.closing = true;
            selected = queue;
            previous = selected.tail;
            selected.tail = result;
        }
        previous.handle((ignored, error) -> selected.socket)
                .thenCompose(current -> executeClose(current, statusCode, reason))
                .whenComplete((closed, error) -> complete(result, closed, error));
        return result;
    }

    private CompletableFuture<WebSocket> executeSend(
            OutboundQueue selected, WebSocket socket, SendOperation operation) {
        synchronized (outboundLock) {
            if (outboundQueue != selected || (!selected.closing && webSocket != socket)
                    || socket.isOutputClosed()
                    || connectionGeneration.get() != selected.generation) {
                return failedFuture(new IllegalStateException("WebSocket not connected"));
            }
        }
        try {
            CompletableFuture<WebSocket> sent = operation.send(socket);
            return sent == null
                    ? failedFuture(new IllegalStateException("WebSocket send returned null"))
                    : sent;
        } catch (RuntimeException error) {
            return failedFuture(error);
        }
    }

    private static CompletableFuture<WebSocket> executeClose(
            WebSocket socket, int statusCode, String reason) {
        try {
            CompletableFuture<WebSocket> sent = socket.sendClose(statusCode, reason);
            return sent == null
                    ? failedFuture(new IllegalStateException("WebSocket close returned null"))
                    : sent;
        } catch (RuntimeException error) {
            return failedFuture(error);
        }
    }

    private static <T> void complete(CompletableFuture<T> target, T value, Throwable error) {
        if (error == null) {
            target.complete(value);
        } else {
            target.completeExceptionally(error);
        }
    }

    private void finishClosedAttempt(WebSocket socket, long generation, long attempt,
            int statusCode, String reason) {
        if (!isCurrent(generation) || !connectionAttempt.compareAndSet(attempt, attempt + 1)) {
            return;
        }
        if (webSocket == socket) {
            webSocket = null;
        }
        clearOutbound(socket);
        stopPingTask();
        if (listener != null) {
            try {
                listener.onClose(statusCode, reason);
            } catch (RuntimeException listenerFailure) {
                notifyError(listenerFailure);
            }
        }
        scheduleReconnect(generation);
    }

    private void failAttempt(WebSocket socket, long generation, long attempt, Throwable error) {
        if (!isCurrent(generation) || !connectionAttempt.compareAndSet(attempt, attempt + 1)) {
            return;
        }
        WebSocket current = socket != null ? socket : webSocket;
        if (webSocket == current) {
            webSocket = null;
        }
        clearOutbound(current);
        stopPingTask();
        if (current != null) {
            current.abort();
        }
        notifyError(error);
        scheduleReconnect(generation);
    }

    private void notifyError(Throwable error) {
        if (listener != null) {
            try {
                listener.onError(error);
            } catch (RuntimeException ignored) {
                // Listener failures must not break transport lifecycle cleanup.
            }
        }
    }

    private static void validateClose(int statusCode, String reason) {
        Objects.requireNonNull(reason, "reason");
        if (statusCode < 1_000 || statusCode >= 5_000
                || (statusCode >= 1_016 && statusCode <= 2_999)
                || statusCode == 1_002 || statusCode == 1_003
                || statusCode == 1_004 || statusCode == 1_005
                || statusCode == 1_006 || statusCode == 1_007
                || statusCode == 1_009 || statusCode == 1_010
                || statusCode == 1_012 || statusCode == 1_013
                || statusCode == 1_014 || statusCode == 1_015) {
            throw new IllegalArgumentException("Invalid WebSocket close status code: " + statusCode);
        }
        if (reason.getBytes(StandardCharsets.UTF_8).length > 123) {
            throw new IllegalArgumentException("WebSocket close reason exceeds 123 UTF-8 bytes");
        }
    }

    private static Throwable unwrap(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }

    private static long durationToMillis(Duration duration) {
        try {
            return Math.max(1L, duration.toMillis());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }

    private static URI requireWebSocketUri(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("WebSocket URL cannot be null or empty");
        }
        URI uri = URI.create(url.trim());
        String scheme = uri.getScheme();
        if (!"ws".equalsIgnoreCase(scheme) && !"wss".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("WebSocket URL must use ws or wss");
        }
        if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawFragment() != null
                || uri.getPort() > 65_535) {
            throw new IllegalArgumentException("Invalid WebSocket URL");
        }
        return uri;
    }

    private static long saturatingMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static long utf8Length(String value) {
        long length = 0;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            long bytes;
            if (current <= 0x7f) {
                bytes = 1;
            } else if (current <= 0x7ff) {
                bytes = 2;
            } else if (Character.isHighSurrogate(current)
                    && i + 1 < value.length()
                    && Character.isLowSurrogate(value.charAt(i + 1))) {
                bytes = 4;
                i++;
            } else {
                bytes = 3;
            }
            length = length > Long.MAX_VALUE - bytes ? Long.MAX_VALUE : length + bytes;
        }
        return length;
    }

    @FunctionalInterface
    private interface SendOperation {
        CompletableFuture<WebSocket> send(WebSocket socket);
    }

    @FunctionalInterface
    private interface SendOperationFactory {
        SendOperation create();
    }

    private static final class OutboundQueue {
        private final WebSocket socket;
        private final long generation;
        private CompletableFuture<WebSocket> tail;
        private int pendingSends;
        private long pendingSendBytes;
        private boolean closing;

        private OutboundQueue(WebSocket socket, long generation) {
            this.socket = socket;
            this.generation = generation;
            this.tail = CompletableFuture.completedFuture(socket);
        }
    }

    public static class Builder {
        private HttpClient httpClient;
        private Duration connectTimeout = Duration.ofSeconds(10);
        private WebSocketListener listener;
        private long pingInterval = 30_000;
        private int maxReconnectAttempts = 5;
        private long reconnectDelay = 1_000;
        private int maxMessageSize = DEFAULT_MAX_MESSAGE_SIZE;
        private int maxPendingSends = DEFAULT_MAX_PENDING_SENDS;
        private long maxPendingSendBytes = DEFAULT_MAX_PENDING_SEND_BYTES;
        private Duration closeTimeout = DEFAULT_CLOSE_TIMEOUT;

        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        public Builder connectTimeout(Duration timeout) {
            this.connectTimeout = timeout;
            return this;
        }

        public Builder listener(WebSocketListener listener) {
            this.listener = listener;
            return this;
        }

        public Builder pingInterval(long millis) {
            this.pingInterval = millis;
            return this;
        }

        public Builder maxReconnectAttempts(int attempts) {
            this.maxReconnectAttempts = attempts;
            return this;
        }

        public Builder reconnectDelay(long millis) {
            this.reconnectDelay = millis;
            return this;
        }

        /** Maximum aggregate text characters or binary bytes accepted for one message. */
        public Builder maxMessageSize(int size) {
            this.maxMessageSize = size;
            return this;
        }

        /** Maximum number of accepted sends waiting for transport completion. */
        public Builder maxPendingSends(int maxPendingSends) {
            this.maxPendingSends = maxPendingSends;
            return this;
        }

        /** Maximum aggregate encoded payload bytes retained by accepted pending sends. */
        public Builder maxPendingSendBytes(long maxPendingSendBytes) {
            this.maxPendingSendBytes = maxPendingSendBytes;
            return this;
        }

        /** Maximum time allowed for queued sends plus the closing frame. */
        public Builder closeTimeout(Duration timeout) {
            this.closeTimeout = timeout;
            return this;
        }

        public Builder disablePing() {
            return pingInterval(0);
        }

        public Builder disableReconnect() {
            return maxReconnectAttempts(0);
        }

        public WebSocketClient build() {
            if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
                throw new IllegalStateException("Connect timeout must be positive");
            }
            if (pingInterval < 0 || reconnectDelay < 0 || maxReconnectAttempts < 0) {
                throw new IllegalStateException("Ping and reconnect settings must be non-negative");
            }
            if (maxMessageSize <= 0) {
                throw new IllegalStateException("Maximum WebSocket message size must be positive");
            }
            if (maxPendingSends <= 0) {
                throw new IllegalStateException("Maximum pending WebSocket sends must be positive");
            }
            if (maxPendingSendBytes <= 0) {
                throw new IllegalStateException(
                        "Maximum pending WebSocket send bytes must be positive");
            }
            if (closeTimeout == null || closeTimeout.isZero() || closeTimeout.isNegative()) {
                throw new IllegalStateException("Close timeout must be positive");
            }
            return new WebSocketClient(this);
        }
    }
}
