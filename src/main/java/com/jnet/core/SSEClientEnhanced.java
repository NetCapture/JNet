package com.jnet.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;

/**
 * SSE client with bounded reconnect, heartbeat monitoring and Last-Event-ID.
 * Lines are limited to 64 KiB and events to 1 MiB by default.
 */
public class SSEClientEnhanced implements AutoCloseable {
    private static final AtomicInteger THREAD_IDS = new AtomicInteger();
    private static final long MAX_RETRY_DELAY_MILLIS = 60_000L;

    private final HttpClient httpClient;
    private final Duration readTimeout;
    private final int maxRetries;
    private final long initialRetryDelay;
    private final double retryBackoffMultiplier;
    private final long heartbeatInterval;
    private final int maxLineBytes;
    private final int maxEventBytes;
    private final AtomicLong generation = new AtomicLong();
    private final AtomicInteger reconnectCount = new AtomicInteger();
    private final Object lifecycleLock = new Object();

    private volatile Predicate<SSEEvent> eventFilter;
    private volatile String lastEventId;
    private volatile long serverRetryDelay = -1L;
    private volatile boolean running;
    private volatile CompletableFuture<?> streamFuture;
    private volatile Flow.Subscription streamSubscription;
    private volatile ScheduledExecutorService scheduler;
    private volatile ScheduledFuture<?> reconnectTask;
    private volatile Attempt activeAttempt;

    private SSEClientEnhanced(Builder builder) {
        this.httpClient = builder.httpClient == null
                ? JNetClient.getInstance().getHttpClient()
                : builder.httpClient;
        this.readTimeout = requirePositive(builder.readTimeout, "readTimeout");
        this.maxRetries = requireNonNegative(builder.maxRetries, "maxRetries");
        this.initialRetryDelay = requireNonNegative(builder.initialRetryDelay, "initialRetryDelay");
        if (!Double.isFinite(builder.retryBackoffMultiplier) || builder.retryBackoffMultiplier <= 0) {
            throw new IllegalArgumentException("retryBackoffMultiplier must be finite and positive");
        }
        this.retryBackoffMultiplier = builder.retryBackoffMultiplier;
        this.heartbeatInterval = requireNonNegative(builder.heartbeatInterval, "heartbeatInterval");
        this.maxLineBytes = SseBodySubscriber.requirePositive(builder.maxLineBytes, "maxLineBytes");
        this.maxEventBytes = SseBodySubscriber.requirePositive(builder.maxEventBytes, "maxEventBytes");
        this.eventFilter = builder.eventFilter;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class SSEEvent {
        private final String id;
        private final String event;
        private final String data;
        private final long timestamp;

        public SSEEvent(String id, String event, String data) {
            this.id = id;
            this.event = event;
            this.data = data;
            this.timestamp = System.currentTimeMillis();
        }

        public String getId() { return id; }
        public String getEvent() { return event; }
        public String getData() { return data; }
        public long getTimestamp() { return timestamp; }
    }

    public interface EnhancedSSEListener {
        void onEvent(SSEEvent event);
        void onError(Exception e);
        void onReconnect(int attempt);
        default void onHeartbeatTimeout() {}
        default void onComplete() {}
    }

    public void connect(String url, Map<String, String> headers, EnhancedSSEListener listener) {
        Objects.requireNonNull(listener, "listener");
        URI endpoint = URI.create(url);
        Map<String, String> copiedHeaders = headers == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
        long connectionGeneration;
        synchronized (lifecycleLock) {
            connectionGeneration = generation.incrementAndGet();
            stopLocked();
            running = true;
            reconnectCount.set(0);
            serverRetryDelay = -1L;
            lastEventId = null;
            ensureSchedulerLocked();
        }
        startAttempt(endpoint, copiedHeaders, listener, connectionGeneration, 0);
    }

    private void startAttempt(URI url, Map<String, String> headers, EnhancedSSEListener listener,
                              long connectionGeneration, int retryAttempt) {
        if (!isCurrent(connectionGeneration)) {
            return;
        }
        Attempt attempt = new Attempt(url, headers, listener, connectionGeneration, retryAttempt);
        CompletableFuture<HttpResponse<Void>> future;
        try {
            HttpRequest.Builder request = HttpRequest.newBuilder()
                    .uri(url)
                    .setHeader("Accept", "text/event-stream")
                    .setHeader("Cache-Control", "no-cache")
                    .GET();
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
            if (lastEventId != null && !lastEventId.isEmpty()) {
                request.setHeader("Last-Event-ID", lastEventId);
            }
            future = httpClient.sendAsync(request.build(), responseInfo ->
                    responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300
                            && responseInfo.statusCode() != 204
                            ? new SseBodySubscriber(attempt.subscriber.parser, maxLineBytes,
                                    maxEventBytes, attempt.subscriber)
                            : HttpResponse.BodySubscribers.replacing(null));
        } catch (RuntimeException error) {
            failPermanently(attempt, error);
            return;
        }
        attempt.future = future;
        synchronized (lifecycleLock) {
            if (!isCurrent(connectionGeneration) || attempt.isFinished()) {
                future.cancel(true);
                return;
            }
            streamFuture = future;
            activeAttempt = attempt;
            scheduleAttemptMonitors(attempt);
        }
        future.whenComplete((response, error) -> {
            if (error != null) {
                attempt.reconnect(toException(error));
            } else if (response.statusCode() == 204) {
                attempt.completePermanently();
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                IOException statusError = new IOException("HTTP " + response.statusCode());
                if (isRetryableStatus(response.statusCode())) {
                    attempt.reconnect(statusError);
                } else {
                    attempt.failPermanently(statusError);
                }
            } else {
                attempt.reconnect(new IOException("SSE stream ended before disconnect"));
            }
        });
    }

    private void scheduleAttemptMonitors(Attempt attempt) {
        ScheduledExecutorService executor = scheduler;
        if (executor == null) {
            return;
        }
        long initialTimeout = durationMillis(readTimeout);
        attempt.initialReadTask = executor.schedule(() -> attempt.reconnect(
                new IOException("Timed out waiting for the first SSE event")),
                initialTimeout, TimeUnit.MILLISECONDS);
        if (heartbeatInterval > 0) {
            attempt.heartbeatTask = executor.scheduleAtFixedRate(() -> {
                if (!attempt.isActive()) {
                    return;
                }
                long idle = System.currentTimeMillis() - attempt.lastEventTime.get();
                if (idle > saturatingMultiply(heartbeatInterval, 2)) {
                    listenerSafely(attempt.listener::onHeartbeatTimeout, attempt.listener);
                    attempt.reconnect(new IOException("SSE heartbeat timeout"));
                }
            }, heartbeatInterval, heartbeatInterval, TimeUnit.MILLISECONDS);
        }
    }

    private void scheduleReconnect(Attempt attempt, Exception cause) {
        if (!isCurrent(attempt.generation)) {
            return;
        }
        if (attempt.retryAttempt >= maxRetries) {
            IOException exhausted = new IOException(
                    "SSE reconnect limit reached after " + maxRetries + " retries", cause);
            terminateWithError(attempt.generation, attempt.listener, exhausted);
            return;
        }

        int nextAttempt = attempt.retryAttempt + 1;
        reconnectCount.set(nextAttempt);
        listenerSafely(() -> attempt.listener.onReconnect(nextAttempt), attempt.listener);
        long delay = retryDelay(nextAttempt);
        synchronized (lifecycleLock) {
            ScheduledExecutorService executor = scheduler;
            if (!isCurrent(attempt.generation) || executor == null || executor.isShutdown()) {
                return;
            }
            reconnectTask = executor.schedule(
                    () -> {
                        reconnectTask = null;
                        startAttempt(attempt.url, attempt.headers, attempt.listener,
                                attempt.generation, nextAttempt);
                    },
                    delay, TimeUnit.MILLISECONDS);
        }
    }

    private void failPermanently(Attempt attempt, Exception error) {
        terminateWithError(attempt.generation, attempt.listener, error);
    }

    private void terminateWithError(long connectionGeneration, EnhancedSSEListener listener,
                                    Exception error) {
        if (stopGeneration(connectionGeneration)) {
            reportErrorSafely(listener, error);
        }
    }

    private void terminateNormally(long connectionGeneration, EnhancedSSEListener listener) {
        if (stopGeneration(connectionGeneration)) {
            listenerSafely(listener::onComplete, listener);
        }
    }

    private boolean stopGeneration(long connectionGeneration) {
        synchronized (lifecycleLock) {
            if (running && generation.get() == connectionGeneration) {
                generation.incrementAndGet();
                stopLocked();
                return true;
            }
            return false;
        }
    }

    public void setEventFilter(Predicate<SSEEvent> filter) {
        eventFilter = filter;
    }

    public void disconnect() {
        synchronized (lifecycleLock) {
            generation.incrementAndGet();
            stopLocked();
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public int getReconnectCount() {
        return reconnectCount.get();
    }

    public String getLastEventId() {
        return lastEventId;
    }

    private void stopLocked() {
        running = false;
        cancel(reconnectTask);
        reconnectTask = null;
        Attempt attempt = activeAttempt;
        activeAttempt = null;
        if (attempt != null) {
            attempt.cancelMonitors();
        }
        Flow.Subscription subscription = streamSubscription;
        streamSubscription = null;
        if (subscription != null) {
            subscription.cancel();
        }
        CompletableFuture<?> future = streamFuture;
        streamFuture = null;
        if (future != null) {
            future.cancel(true);
        }
        ScheduledExecutorService currentScheduler = scheduler;
        scheduler = null;
        if (currentScheduler != null) {
            currentScheduler.shutdownNow();
        }
    }

    private void ensureSchedulerLocked() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "jnet-sse-" + THREAD_IDS.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            });
        }
    }

    private boolean isCurrent(long connectionGeneration) {
        return running && generation.get() == connectionGeneration;
    }

    private long retryDelay(int attempt) {
        long serverDelay = serverRetryDelay;
        if (serverDelay >= 0) {
            return Math.min(serverDelay, MAX_RETRY_DELAY_MILLIS);
        }
        double calculated = initialRetryDelay * Math.pow(retryBackoffMultiplier, Math.max(0, attempt - 1));
        if (!Double.isFinite(calculated) || calculated >= MAX_RETRY_DELAY_MILLIS) {
            return MAX_RETRY_DELAY_MILLIS;
        }
        return Math.max(0L, (long) calculated);
    }

    private static void cancel(ScheduledFuture<?> future) {
        if (future != null) {
            future.cancel(false);
        }
    }

    private static long durationMillis(Duration duration) {
        try {
            return Math.max(1L, duration.toMillis());
        } catch (ArithmeticException e) {
            return Long.MAX_VALUE;
        }
    }

    private static long saturatingMultiply(long value, long multiplier) {
        return value > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : value * multiplier;
    }

    private static boolean isRetryableStatus(int status) {
        return status == 408 || status == 425 || status == 429 || status >= 500;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static long requireNonNegative(long value, String name) {
        if (value < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    private static Exception toException(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof ExecutionException)
                && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof Exception ? (Exception) cause : new Exception(cause);
    }

    private static void listenerSafely(Runnable callback, EnhancedSSEListener listener) {
        try {
            callback.run();
        } catch (RuntimeException callbackFailure) {
            reportErrorSafely(listener, callbackFailure);
        }
    }

    private static void reportErrorSafely(EnhancedSSEListener listener, Exception error) {
        try {
            listener.onError(error);
        } catch (RuntimeException ignored) {
            // Listener failures must not terminate transport or scheduler threads.
        }
    }

    private final class Attempt {
        private final URI url;
        private final Map<String, String> headers;
        private final EnhancedSSEListener listener;
        private final long generation;
        private final int retryAttempt;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicLong lastEventTime = new AtomicLong(System.currentTimeMillis());
        private final EnhancedSubscriber subscriber;
        private volatile CompletableFuture<?> future;
        private volatile ScheduledFuture<?> heartbeatTask;
        private volatile ScheduledFuture<?> initialReadTask;

        private Attempt(URI url, Map<String, String> headers, EnhancedSSEListener listener,
                        long generation, int retryAttempt) {
            this.url = url;
            this.headers = headers;
            this.listener = listener;
            this.generation = generation;
            this.retryAttempt = retryAttempt;
            this.subscriber = new EnhancedSubscriber(this);
        }

        private boolean isActive() {
            return !finished.get() && isCurrent(generation);
        }

        private boolean isFinished() {
            return finished.get();
        }

        private void reconnect(Exception cause) {
            if (!finished.compareAndSet(false, true) || !isCurrent(generation)) {
                return;
            }
            releaseResources();
            scheduleReconnect(this, cause);
        }

        private void completePermanently() {
            if (!finished.compareAndSet(false, true) || !isCurrent(generation)) {
                return;
            }
            terminateNormally(generation, listener);
        }

        private void failPermanently(Exception error) {
            if (!finished.compareAndSet(false, true) || !isCurrent(generation)) {
                return;
            }
            terminateWithError(generation, listener, error);
        }

        private void releaseResources() {
            Flow.Subscription subscription = subscriber.detachSubscription();
            CompletableFuture<?> currentFuture = future;
            synchronized (lifecycleLock) {
                cancelMonitors();
                if (activeAttempt == this) {
                    activeAttempt = null;
                }
                if (streamSubscription == subscription) {
                    streamSubscription = null;
                }
                if (streamFuture == currentFuture) {
                    streamFuture = null;
                }
            }
            if (subscription != null) {
                subscription.cancel();
            }
            if (currentFuture != null && !currentFuture.isDone()) {
                currentFuture.cancel(true);
            }
        }

        private void cancelInitialRead() {
            cancel(initialReadTask);
            initialReadTask = null;
        }

        private void cancelMonitors() {
            cancel(heartbeatTask);
            heartbeatTask = null;
            cancelInitialRead();
        }
    }

    private final class EnhancedSubscriber implements SseBodySubscriber.Control {
        private final Attempt attempt;
        private final SseEventParser parser;
        private Flow.Subscription subscription;

        private EnhancedSubscriber(Attempt attempt) {
            this.attempt = attempt;
            this.parser = new SseEventParser(new SseEventParser.Sink() {
                @Override
                public void onEvent(String id, String event, String data) {
                    synchronized (lifecycleLock) {
                        if (!attempt.isActive()) {
                            return;
                        }
                        lastEventId = id;
                    }
                    SSEEvent parsed = new SSEEvent(id, event, data);
                    Predicate<SSEEvent> filter = eventFilter;
                    if (filter == null || filter.test(parsed)) {
                        attempt.listener.onEvent(parsed);
                    }
                }

                @Override
                public void onRetry(long retryMillis) {
                    synchronized (lifecycleLock) {
                        if (attempt.isActive()) {
                            serverRetryDelay = retryMillis;
                        }
                    }
                }
            }, lastEventId);
        }

        @Override
        public boolean onSubscribe(Flow.Subscription subscription) {
            synchronized (lifecycleLock) {
                if (!attempt.isActive()) {
                    subscription.cancel();
                    return false;
                }
                this.subscription = subscription;
                streamSubscription = subscription;
            }
            return true;
        }

        @Override
        public boolean isActive() {
            return attempt.isActive();
        }

        @Override
        public void onActivity() {
            attempt.lastEventTime.set(System.currentTimeMillis());
            attempt.cancelInitialRead();
        }

        @Override
        public void onFailure(Exception error) {
            attempt.failPermanently(error);
        }

        private Flow.Subscription detachSubscription() {
            Flow.Subscription current = subscription;
            subscription = null;
            return current;
        }
    }

    public static class Builder {
        private HttpClient httpClient;
        private Duration readTimeout = Duration.ofSeconds(30);
        private int maxRetries = 5;
        private long initialRetryDelay = 1_000L;
        private double retryBackoffMultiplier = 2.0;
        private long heartbeatInterval = 30_000L;
        private int maxLineBytes = SseBodySubscriber.DEFAULT_MAX_LINE_BYTES;
        private int maxEventBytes = SseBodySubscriber.DEFAULT_MAX_EVENT_BYTES;
        private Predicate<SSEEvent> eventFilter;

        public Builder httpClient(HttpClient client) {
            this.httpClient = client;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        public Builder maxRetries(int retries) {
            this.maxRetries = retries;
            return this;
        }

        public Builder initialRetryDelay(long millis) {
            this.initialRetryDelay = millis;
            return this;
        }

        public Builder retryBackoffMultiplier(double multiplier) {
            this.retryBackoffMultiplier = multiplier;
            return this;
        }

        public Builder heartbeatInterval(long millis) {
            this.heartbeatInterval = millis;
            return this;
        }

        public Builder maxLineBytes(int bytes) {
            this.maxLineBytes = bytes;
            return this;
        }

        public Builder maxEventBytes(int bytes) {
            this.maxEventBytes = bytes;
            return this;
        }

        public Builder eventFilter(Predicate<SSEEvent> filter) {
            this.eventFilter = filter;
            return this;
        }

        public SSEClientEnhanced build() {
            return new SSEClientEnhanced(this);
        }
    }
}
