package com.jnet.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A small, single-stream SSE client built on the JDK 11 HTTP client.
 * Lines are limited to 64 KiB and events to 1 MiB by default.
 */
public class SSEClient implements AutoCloseable {
    private final HttpClient httpClient;
    private final int maxLineBytes;
    private final int maxEventBytes;
    private final AtomicLong generation = new AtomicLong();
    private volatile CompletableFuture<?> activeStream;
    private volatile Flow.Subscription activeSubscription;

    public SSEClient() {
        this(JNetClient.getInstance(), SseBodySubscriber.DEFAULT_MAX_LINE_BYTES,
                SseBodySubscriber.DEFAULT_MAX_EVENT_BYTES);
    }

    public SSEClient(JNetClient client) {
        this(client, SseBodySubscriber.DEFAULT_MAX_LINE_BYTES,
                SseBodySubscriber.DEFAULT_MAX_EVENT_BYTES);
    }

    public SSEClient(int maxLineBytes, int maxEventBytes) {
        this(JNetClient.getInstance(), maxLineBytes, maxEventBytes);
    }

    public SSEClient(JNetClient client, int maxLineBytes, int maxEventBytes) {
        this.httpClient = Objects.requireNonNull(client, "client").getHttpClient();
        this.maxLineBytes = SseBodySubscriber.requirePositive(maxLineBytes, "maxLineBytes");
        this.maxEventBytes = SseBodySubscriber.requirePositive(maxEventBytes, "maxEventBytes");
    }

    public interface SSEListener {
        void onData(String data);
        void onEvent(String event, String data);
        void onComplete();
        void onError(Exception e);
    }

    public void stream(String url, Map<String, String> headers, SSEListener listener) {
        HttpRequest.Builder request = baseRequest(url, headers).GET();
        execute(request.build(), listener);
    }

    public void streamPost(String url, String data, Map<String, String> headers, SSEListener listener) {
        HttpRequest.Builder request = baseRequest(url, headers)
                .setHeader("Content-Type", "application/json")
                .POST(data == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(data));
        execute(request.build(), listener);
    }

    private static HttpRequest.Builder baseRequest(String url, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .setHeader("Accept", "text/event-stream")
                .setHeader("Cache-Control", "no-cache");
        if (headers != null) {
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), entry.getValue());
            }
        }
        return request;
    }

    private synchronized void execute(HttpRequest request, SSEListener listener) {
        Objects.requireNonNull(listener, "listener");
        long streamGeneration = generation.incrementAndGet();
        cancelActive();
        SSESubscriber subscriber = new SSESubscriber(listener, streamGeneration);
        CompletableFuture<HttpResponse<Void>> future = httpClient.sendAsync(request, responseInfo ->
                responseInfo.statusCode() >= 200 && responseInfo.statusCode() < 300
                        ? new SseBodySubscriber(subscriber.parser, maxLineBytes, maxEventBytes, subscriber)
                        : HttpResponse.BodySubscribers.replacing(null));
        activeStream = future;
        future.whenComplete((response, error) -> {
            if (!isCurrent(streamGeneration)) {
                return;
            }
            activeStream = null;
            if (error != null) {
                subscriber.fail(toException(error));
            } else if (response.statusCode() < 200 || response.statusCode() >= 300) {
                subscriber.fail(new IOException("HTTP " + response.statusCode()));
            } else {
                subscriber.complete();
            }
        });
    }

    @Override
    public synchronized void close() {
        generation.incrementAndGet();
        cancelActive();
    }

    private void cancelActive() {
        Flow.Subscription subscription = activeSubscription;
        activeSubscription = null;
        if (subscription != null) {
            subscription.cancel();
        }
        CompletableFuture<?> stream = activeStream;
        activeStream = null;
        if (stream != null) {
            stream.cancel(true);
        }
    }

    private boolean isCurrent(long streamGeneration) {
        return generation.get() == streamGeneration;
    }

    private static Exception toException(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause instanceof Exception ? (Exception) cause : new Exception(cause);
    }

    /** @deprecated Runtime examples are not executed from the library artifact. */
    @Deprecated
    public static void main(String[] args) {
        // Retained for binary compatibility with JNet 3.0.
    }

    private final class SSESubscriber implements SseBodySubscriber.Control {
        private final SSEListener listener;
        private final long streamGeneration;
        private final AtomicBoolean terminated = new AtomicBoolean();
        private final SseEventParser parser;
        private Flow.Subscription subscription;

        private SSESubscriber(SSEListener listener, long streamGeneration) {
            this.listener = listener;
            this.streamGeneration = streamGeneration;
            this.parser = new SseEventParser((id, event, data) -> {
                if (!isActive()) {
                    return;
                }
                listener.onData(data);
                if (isActive() && event != null) {
                    listener.onEvent(event, data);
                }
            });
        }

        @Override
        public boolean onSubscribe(Flow.Subscription subscription) {
            synchronized (SSEClient.this) {
                if (!isActive()) {
                    subscription.cancel();
                    return false;
                }
                this.subscription = subscription;
                activeSubscription = subscription;
            }
            return true;
        }

        @Override
        public boolean isActive() {
            return isCurrent(streamGeneration) && !terminated.get();
        }

        @Override
        public void onFailure(Exception error) {
            fail(error);
        }

        private void complete() {
            if (isCurrent(streamGeneration) && terminated.compareAndSet(false, true)) {
                if (activeSubscription == subscription) {
                    activeSubscription = null;
                }
                try {
                    listener.onComplete();
                } catch (RuntimeException ignored) {
                    // Listener failures must not escape the Flow callback.
                }
            }
        }

        private void fail(Exception error) {
            if (isCurrent(streamGeneration) && terminated.compareAndSet(false, true)) {
                Flow.Subscription current = subscription;
                if (current != null) {
                    current.cancel();
                }
                if (activeSubscription == current) {
                    activeSubscription = null;
                }
                try {
                    listener.onError(error);
                } catch (RuntimeException ignored) {
                    // Listener failures must not escape the Flow callback.
                }
            }
        }
    }
}
