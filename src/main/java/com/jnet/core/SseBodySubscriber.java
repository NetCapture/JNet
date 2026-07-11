package com.jnet.core;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;

/** Incrementally frames a bounded UTF-8 event stream without buffering the response body. */
final class SseBodySubscriber implements HttpResponse.BodySubscriber<Void> {
    static final int DEFAULT_MAX_LINE_BYTES = 64 * 1024;
    static final int DEFAULT_MAX_EVENT_BYTES = 1024 * 1024;

    interface Control {
        boolean onSubscribe(Flow.Subscription subscription);

        boolean isActive();

        default void onActivity() {}

        void onFailure(Exception error);
    }

    private final SseEventParser parser;
    private final int maxLineBytes;
    private final int maxEventBytes;
    private final Control control;
    private final CompletableFuture<Void> body = new CompletableFuture<>();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);

    private byte[] lineBytes;
    private int lineLength;
    private int eventBytes;
    private boolean skipLineFeed;
    private volatile Flow.Subscription subscription;

    SseBodySubscriber(SseEventParser parser, int maxLineBytes, int maxEventBytes, Control control) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.maxLineBytes = requirePositive(maxLineBytes, "maxLineBytes");
        this.maxEventBytes = requirePositive(maxEventBytes, "maxEventBytes");
        this.control = Objects.requireNonNull(control, "control");
        this.lineBytes = new byte[Math.min(256, maxLineBytes)];
    }

    static int requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    @Override
    public CompletionStage<Void> getBody() {
        return body;
    }

    @Override
    public void onSubscribe(Flow.Subscription newSubscription) {
        Objects.requireNonNull(newSubscription, "subscription");
        if (subscription != null) {
            newSubscription.cancel();
            return;
        }
        subscription = newSubscription;
        try {
            if (!control.onSubscribe(newSubscription) || !control.isActive()) {
                cancelInactive();
                return;
            }
            newSubscription.request(1);
        } catch (RuntimeException error) {
            failProcessing(error);
        }
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (terminated.get()) {
            return;
        }
        if (!control.isActive()) {
            cancelInactive();
            return;
        }
        try {
            for (ByteBuffer buffer : Objects.requireNonNull(buffers, "buffers")) {
                consume(Objects.requireNonNull(buffer, "buffer"));
                if (!control.isActive()) {
                    cancelInactive();
                    return;
                }
            }
            if (!terminated.get() && control.isActive()) {
                subscription.request(1);
            } else if (!terminated.get()) {
                cancelInactive();
            }
        } catch (Exception error) {
            failProcessing(error);
        }
    }

    @Override
    public void onError(Throwable error) {
        Objects.requireNonNull(error, "error");
        if (terminated.compareAndSet(false, true)) {
            body.completeExceptionally(error);
        }
    }

    @Override
    public void onComplete() {
        if (terminated.get()) {
            return;
        }
        if (!control.isActive()) {
            cancelInactive();
            return;
        }
        try {
            if (lineLength > 0) {
                emitLine();
            }
            parser.finish();
            if (!control.isActive()) {
                cancelInactive();
                return;
            }
            if (terminated.compareAndSet(false, true)) {
                body.complete(null);
            }
        } catch (Exception error) {
            failProcessing(error);
        }
    }

    private void consume(ByteBuffer buffer) throws IOException {
        while (buffer.hasRemaining()) {
            byte value = buffer.get();
            if (skipLineFeed) {
                skipLineFeed = false;
                if (value == '\n') {
                    continue;
                }
            }
            if (value == '\r') {
                emitLine();
                skipLineFeed = true;
            } else if (value == '\n') {
                emitLine();
            } else {
                append(value);
            }
        }
    }

    private void append(byte value) throws IOException {
        if (lineLength >= maxLineBytes) {
            throw new IOException("SSE line exceeds maximum of " + maxLineBytes + " bytes");
        }
        if (eventBytes >= maxEventBytes) {
            throw new IOException("SSE event exceeds maximum of " + maxEventBytes + " bytes");
        }
        ensureLineCapacity(lineLength + 1);
        lineBytes[lineLength++] = value;
        eventBytes++;
    }

    private void ensureLineCapacity(int required) {
        if (required <= lineBytes.length) {
            return;
        }
        int doubled = lineBytes.length > maxLineBytes / 2 ? maxLineBytes : lineBytes.length * 2;
        int capacity = Math.max(required, doubled);
        lineBytes = Arrays.copyOf(lineBytes, capacity);
    }

    private void emitLine() throws IOException {
        control.onActivity();
        if (lineLength == 0) {
            eventBytes = 0;
            parser.accept("");
            return;
        }

        String line;
        try {
            line = decoder.decode(ByteBuffer.wrap(lineBytes, 0, lineLength)).toString();
        } catch (CharacterCodingException error) {
            throw new IOException("Invalid UTF-8 in SSE stream", error);
        }
        lineLength = 0;
        parser.accept(line);
    }

    private void cancelInactive() {
        if (terminated.compareAndSet(false, true)) {
            Flow.Subscription current = subscription;
            if (current != null) {
                current.cancel();
            }
            body.cancel(false);
        }
    }

    private void failProcessing(Exception error) {
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        Flow.Subscription current = subscription;
        if (current != null) {
            current.cancel();
        }
        try {
            control.onFailure(error);
        } catch (RuntimeException ignored) {
            // The body still has to terminate even when a failure callback is defective.
        }
        body.completeExceptionally(error);
    }
}
