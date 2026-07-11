package com.jnet.core;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

/** Internal response-body handlers that enforce deterministic in-memory limits. */
final class BoundedBodyHandlers {
    private BoundedBodyHandlers() {
    }

    static HttpResponse.BodyHandler<String> ofString(int maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("Maximum response size must be positive");
        }
        HttpResponse.BodyHandler<String> delegate = HttpResponse.BodyHandlers.ofString();
        return responseInfo -> {
            long contentLength = responseInfo.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1L);
            if (contentLength > maxBytes) {
                return new RejectedBodySubscriber<>(new IOException(
                        "HTTP response declares " + contentLength
                                + " bytes, exceeding maximum size of " + maxBytes + " bytes"));
            }
            return new LimitingBodySubscriber<>(delegate.apply(responseInfo), maxBytes);
        };
    }

    private static final class RejectedBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
        private final CompletableFuture<T> body = new CompletableFuture<>();

        private RejectedBodySubscriber(Throwable error) {
            body.completeExceptionally(error);
        }

        @Override
        public CompletionStage<T> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription").cancel();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            // Rejected before the response body is consumed.
        }

        @Override
        public void onError(Throwable throwable) {
            // The original size error is more actionable.
        }

        @Override
        public void onComplete() {
            // The body future is already completed exceptionally.
        }
    }

    private static final class LimitingBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
        private final HttpResponse.BodySubscriber<T> delegate;
        private final long maxBytes;
        private Flow.Subscription subscription;
        private long receivedBytes;
        private boolean done;

        private LimitingBodySubscriber(HttpResponse.BodySubscriber<T> delegate, long maxBytes) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.maxBytes = maxBytes;
        }

        @Override
        public CompletionStage<T> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (done) {
                return;
            }
            long batchBytes = 0;
            for (ByteBuffer buffer : buffers) {
                batchBytes += buffer.remaining();
            }
            if (batchBytes > maxBytes - receivedBytes) {
                done = true;
                if (subscription != null) {
                    subscription.cancel();
                }
                delegate.onError(new IOException(
                        "HTTP response exceeds maximum size of " + maxBytes + " bytes"));
                return;
            }
            receivedBytes += batchBytes;
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!done) {
                done = true;
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                delegate.onComplete();
            }
        }
    }
}
