package com.jnet.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.Test;

class SSERegressionTest {

    @Test
    void byteSubscriberPreservesSplitUtf8AndRequestsOneBatchAtATime() throws Exception {
        List<String> events = new ArrayList<>();
        AtomicReference<Exception> failure = new AtomicReference<>();
        SseEventParser parser = new SseEventParser((id, event, data) ->
                events.add(event + "|" + data));
        SseBodySubscriber subscriber = new SseBodySubscriber(parser, 64, 128,
                alwaysActiveControl(failure));
        RecordingSubscription subscription = new RecordingSubscription();
        byte[] stream = "event: update\r\ndata: 汉🙂字".getBytes(StandardCharsets.UTF_8);
        int splitInsideEmoji = "event: update\r\ndata: 汉".getBytes(StandardCharsets.UTF_8).length + 2;

        subscriber.onSubscribe(subscription);
        assertEquals(1L, subscription.requested.get());
        subscriber.onNext(List.of(ByteBuffer.wrap(stream, 0, splitInsideEmoji)));
        assertEquals(2L, subscription.requested.get());
        subscriber.onNext(List.of(ByteBuffer.wrap(
                stream, splitInsideEmoji, stream.length - splitInsideEmoji)));
        assertEquals(3L, subscription.requested.get());
        subscriber.onComplete();
        subscriber.getBody().toCompletableFuture().get(1, TimeUnit.SECONDS);

        assertNull(failure.get());
        assertFalse(subscription.cancelled.get());
        assertEquals(List.of("update|汉🙂字"), events);
    }

    @Test
    void byteSubscriberCancelsUpstreamAsSoonAsALimitIsExceeded() {
        AtomicReference<Exception> failure = new AtomicReference<>();
        SseBodySubscriber subscriber = new SseBodySubscriber(
                new SseEventParser((id, event, data) -> { }), 8, 32,
                alwaysActiveControl(failure));
        RecordingSubscription subscription = new RecordingSubscription();

        subscriber.onSubscribe(subscription);
        subscriber.onNext(List.of(ByteBuffer.wrap(
                "data:xxxx".getBytes(StandardCharsets.UTF_8))));

        assertTrue(subscription.cancelled.get());
        assertEquals(1L, subscription.requested.get(), "a failed batch must not request more data");
        assertNotNull(failure.get());
        assertTrue(failure.get().getMessage().contains("SSE line exceeds maximum of 8 bytes"));
        assertTrue(subscriber.getBody().toCompletableFuture().isCompletedExceptionally());
    }

    @Test
    void simpleClientRejectsAnUnterminatedLineAboveTheDefaultLimit() throws Exception {
        byte[] body = ("data:" + "x".repeat(64 * 1024)).getBytes(StandardCharsets.UTF_8);
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicInteger completions = new AtomicInteger();
        AtomicReference<Exception> failure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/long-line", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, body.length);
            try {
                exchange.getResponseBody().write(body);
            } catch (java.io.IOException ignored) {
                // Expected when the bounded subscriber cancels the response body.
            } finally {
                exchange.close();
            }
        });
        server.start();

        SSEClient client = new SSEClient();
        try {
            client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/long-line",
                    Collections.emptyMap(), new SSEClient.SSEListener() {
                        @Override
                        public void onData(String data) {
                        }

                        @Override
                        public void onEvent(String event, String data) {
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                            terminal.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            failure.set(error);
                            terminal.countDown();
                        }
                    });

            assertTrue(terminal.await(2, TimeUnit.SECONDS));
            assertEquals(0, completions.get());
            assertNotNull(failure.get());
            assertTrue(messageChain(failure.get()).contains(
                    "SSE line exceeds maximum of 65536 bytes"), messageChain(failure.get()));
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    void enhancedClientRejectsAnEventAboveTheDefaultLimitWithoutReconnect() throws Exception {
        byte[] body = ("data:" + "x".repeat(65_000) + "\n")
                .repeat(17)
                .getBytes(StandardCharsets.UTF_8);
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        CountDownLatch terminal = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/large-event", exchange -> {
            requests.incrementAndGet();
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(body);
            } catch (java.io.IOException ignored) {
                // Expected when the bounded subscriber cancels the response body.
            } finally {
                exchange.close();
            }
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .heartbeatInterval(0)
                .initialRetryDelay(10)
                .maxRetries(3)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/large-event",
                    Collections.emptyMap(), new SSEClientEnhanced.EnhancedSSEListener() {
                        @Override
                        public void onEvent(SSEClientEnhanced.SSEEvent event) {
                        }

                        @Override
                        public void onError(Exception error) {
                            failure.set(error);
                            terminal.countDown();
                        }

                        @Override
                        public void onReconnect(int attempt) {
                            reconnects.incrementAndGet();
                        }
                    });

            assertTrue(terminal.await(2, TimeUnit.SECONDS));
            assertEquals(1, requests.get());
            assertEquals(0, reconnects.get());
            assertNotNull(failure.get());
            assertTrue(messageChain(failure.get()).contains(
                    "SSE event exceeds maximum of 1048576 bytes"), messageChain(failure.get()));
        } finally {
            client.disconnect();
            server.stop(0);
        }
    }

    @Test
    void decodesUtf8SplitAcrossChunksAndDispatchesTailEventAtEof() throws Exception {
        String payload = "汉🙂字";
        byte[] body = ("event: update\r\ndata: " + payload).getBytes(StandardCharsets.UTF_8);
        List<String> data = Collections.synchronizedList(new ArrayList<>());
        List<String> events = Collections.synchronizedList(new ArrayList<>());
        CountDownLatch complete = new CountDownLatch(1);
        AtomicReference<Exception> failure = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/utf8-tail", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            try {
                for (byte value : body) {
                    exchange.getResponseBody().write(value);
                    exchange.getResponseBody().flush();
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        SSEClient client = new SSEClient();
        try {
            client.stream("http://127.0.0.1:" + server.getAddress().getPort() + "/utf8-tail",
                    Collections.emptyMap(), new SSEClient.SSEListener() {
                        @Override
                        public void onData(String value) {
                            data.add(value);
                        }

                        @Override
                        public void onEvent(String event, String value) {
                            events.add(event + "|" + value);
                        }

                        @Override
                        public void onComplete() {
                            complete.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            failure.set(error);
                            complete.countDown();
                        }
                    });

            assertTrue(complete.await(2, TimeUnit.SECONDS));
            assertNull(failure.get());
            assertEquals(List.of(payload), data);
            assertEquals(List.of("update|" + payload), events);
        } finally {
            client.close();
            server.stop(0);
        }
    }

    @Test
    void parserPreservesDataWhitespaceAndLastEventId() {
        List<String> events = new ArrayList<>();
        AtomicLong retry = new AtomicLong(-1);
        SseEventParser parser = new SseEventParser(new SseEventParser.Sink() {
            @Override
            public void onEvent(String id, String event, String data) {
                events.add(id + "|" + event + "|" + data);
            }

            @Override
            public void onRetry(long retryMillis) {
                retry.set(retryMillis);
            }
        });

        parser.accept("\ufeffid: 42");
        parser.accept("event: update");
        parser.accept("data:  leading and trailing  ");
        parser.accept("data:second");
        parser.accept("retry: 1500");
        parser.accept("");
        parser.accept("data:");
        parser.accept("");

        assertEquals(List.of(
                "42|update| leading and trailing  \nsecond",
                "42|null|"), events);
        assertEquals(1_500L, retry.get());
    }

    @Test
    void reconnectsWithLastEventIdAndReleasesItsDaemonScheduler() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger completions = new AtomicInteger();
        CountDownLatch events = new CountDownLatch(2);
        CountDownLatch reconnect = new CountDownLatch(1);
        CountDownLatch allowSecondEof = new CountDownLatch(1);
        CountDownLatch terminal = new CountDownLatch(1);
        List<String> received = Collections.synchronizedList(new ArrayList<>());
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/events", exchange -> {
            int request = requests.incrementAndGet();
            if (request == 2) {
                assertEquals("event-1", exchange.getRequestHeaders().getFirst("Last-Event-ID"));
            }
            byte[] body = (request == 1
                    ? "id: event-1\ndata: first\nretry: 10\n\n"
                    : "data: second\n\n").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
            exchange.sendResponseHeaders(200, 0);
            exchange.getResponseBody().write(body);
            exchange.getResponseBody().flush();
            if (request == 2) {
                try {
                    allowSecondEof.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                }
            }
            exchange.close();
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .readTimeout(Duration.ofSeconds(2))
                .heartbeatInterval(0)
                .initialRetryDelay(100)
                .maxRetries(1)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/events",
                    Collections.emptyMap(), new SSEClientEnhanced.EnhancedSSEListener() {
                        @Override
                        public void onEvent(SSEClientEnhanced.SSEEvent event) {
                            received.add(event.getData());
                            events.countDown();
                        }

                        @Override
                        public void onError(Exception error) {
                            errors.incrementAndGet();
                            terminal.countDown();
                        }

                        @Override
                        public void onReconnect(int attempt) {
                            reconnect.countDown();
                        }

                        @Override
                        public void onComplete() {
                            completions.incrementAndGet();
                        }
                    });

            assertTrue(reconnect.await(2, TimeUnit.SECONDS));
            assertTrue(events.await(2, TimeUnit.SECONDS));
            assertEquals(List.of("first", "second"), received);
            assertEquals(2, requests.get());
            assertEquals("event-1", client.getLastEventId());

            java.lang.reflect.Field field = SSEClientEnhanced.class.getDeclaredField("scheduler");
            field.setAccessible(true);
            ScheduledExecutorService scheduler = (ScheduledExecutorService) field.get(client);
            assertNotNull(scheduler);
            assertTrue(scheduler.submit(() -> Thread.currentThread().isDaemon()).get(1, TimeUnit.SECONDS));
            allowSecondEof.countDown();
            assertTrue(terminal.await(2, TimeUnit.SECONDS));
            assertEquals(0, completions.get(), "recoverable EOF must not look like logical completion");
            assertEquals(1, errors.get(), "only retry exhaustion is terminal");
            assertTrue(await(scheduler::isShutdown, 1_000));
            assertNull(field.get(client));
        } finally {
            allowSecondEof.countDown();
            client.disconnect();
            server.stop(0);
        }
    }

    @Test
    void http204CompletesOnceWithoutReconnect() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        CountDownLatch complete = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/stop", exchange -> {
            requests.incrementAndGet();
            requestStarted.countDown();
            try {
                allowResponse.await(2, TimeUnit.SECONDS);
                exchange.sendResponseHeaders(204, -1);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .heartbeatInterval(0)
                .initialRetryDelay(10)
                .maxRetries(3)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/stop",
                    Collections.emptyMap(), listener(errors, reconnects, complete));
            assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
            ScheduledExecutorService scheduler = schedulerOf(client);
            assertNotNull(scheduler);
            allowResponse.countDown();
            assertTrue(complete.await(1, TimeUnit.SECONDS));
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(1, requests.get());
            assertEquals(0, reconnects.get());
            assertEquals(0, errors.get());
            assertTrue(await(scheduler::isShutdown, 1_000));
            assertNull(schedulerOf(client));
        } finally {
            allowResponse.countDown();
            client.disconnect();
            server.stop(0);
        }
    }

    @Test
    void transientHttpFailuresReportOneTerminalErrorAfterRetryExhaustion() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        CountDownLatch terminal = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/unavailable", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .heartbeatInterval(0)
                .initialRetryDelay(10)
                .maxRetries(1)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/unavailable",
                    Collections.emptyMap(), listener(errors, reconnects, terminal));
            assertTrue(terminal.await(2, TimeUnit.SECONDS));
            assertEquals(2, requests.get());
            assertEquals(1, reconnects.get());
            assertEquals(1, errors.get());
            assertNull(schedulerOf(client));
        } finally {
            client.disconnect();
            server.stop(0);
        }
    }

    @Test
    void nonRetryableHttpFailureStopsImmediately() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();
        AtomicInteger reconnects = new AtomicInteger();
        CountDownLatch terminal = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/unauthorized", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .heartbeatInterval(0)
                .initialRetryDelay(10)
                .maxRetries(3)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/unauthorized",
                    Collections.emptyMap(), listener(errors, reconnects, terminal));
            assertTrue(terminal.await(1, TimeUnit.SECONDS));
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(1, requests.get());
            assertEquals(0, reconnects.get());
            assertEquals(1, errors.get());
            assertNull(schedulerOf(client));
        } finally {
            client.disconnect();
            server.stop(0);
        }
    }

    @Test
    void disconnectCancelsPendingResponseAndSuppressesLateCallbacks() throws Exception {
        AtomicInteger callbacks = new AtomicInteger();
        CountDownLatch requestStarted = new CountDownLatch(1);
        CountDownLatch allowResponse = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            requestStarted.countDown();
            try {
                allowResponse.await(2, TimeUnit.SECONDS);
                byte[] body = "data: late\n\n".getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException ignored) {
                // Cancellation can close the exchange before the fixture writes.
            } finally {
                exchange.close();
            }
        });
        server.start();

        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .heartbeatInterval(100)
                .maxRetries(2)
                .build();
        try {
            client.connect("http://127.0.0.1:" + server.getAddress().getPort() + "/slow",
                    Collections.emptyMap(), new SSEClientEnhanced.EnhancedSSEListener() {
                        @Override
                        public void onEvent(SSEClientEnhanced.SSEEvent event) {
                            callbacks.incrementAndGet();
                        }

                        @Override
                        public void onError(Exception error) {
                            callbacks.incrementAndGet();
                        }

                        @Override
                        public void onReconnect(int attempt) {
                            callbacks.incrementAndGet();
                        }

                        @Override
                        public void onComplete() {
                            callbacks.incrementAndGet();
                        }
                    });
            assertTrue(requestStarted.await(1, TimeUnit.SECONDS));
            ScheduledExecutorService scheduler = schedulerOf(client);
            client.disconnect();
            allowResponse.countDown();
            assertTrue(await(scheduler::isShutdown, 1_000));
            TimeUnit.MILLISECONDS.sleep(100);
            assertEquals(0, callbacks.get());
            assertNull(schedulerOf(client));
        } finally {
            allowResponse.countDown();
            client.disconnect();
            server.stop(0);
        }
    }

    private static SSEClientEnhanced.EnhancedSSEListener listener(AtomicInteger errors,
            AtomicInteger reconnects, CountDownLatch terminal) {
        return new SSEClientEnhanced.EnhancedSSEListener() {
            @Override
            public void onEvent(SSEClientEnhanced.SSEEvent event) {
            }

            @Override
            public void onError(Exception error) {
                errors.incrementAndGet();
                terminal.countDown();
            }

            @Override
            public void onReconnect(int attempt) {
                reconnects.incrementAndGet();
            }

            @Override
            public void onComplete() {
                terminal.countDown();
            }
        };
    }

    private static ScheduledExecutorService schedulerOf(SSEClientEnhanced client) throws Exception {
        java.lang.reflect.Field field = SSEClientEnhanced.class.getDeclaredField("scheduler");
        field.setAccessible(true);
        return (ScheduledExecutorService) field.get(client);
    }

    private static boolean await(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        return condition.getAsBoolean();
    }

    private static String messageChain(Throwable error) {
        StringBuilder messages = new StringBuilder();
        Throwable current = error;
        while (current != null) {
            if (current.getMessage() != null) {
                if (messages.length() > 0) {
                    messages.append(" -> ");
                }
                messages.append(current.getMessage());
            }
            current = current.getCause();
        }
        return messages.toString();
    }

    private static SseBodySubscriber.Control alwaysActiveControl(
            AtomicReference<Exception> failure) {
        return new SseBodySubscriber.Control() {
            @Override
            public boolean onSubscribe(Flow.Subscription subscription) {
                return true;
            }

            @Override
            public boolean isActive() {
                return true;
            }

            @Override
            public void onFailure(Exception error) {
                failure.set(error);
            }
        };
    }

    private static final class RecordingSubscription implements Flow.Subscription {
        private final AtomicLong requested = new AtomicLong();
        private final AtomicBoolean cancelled = new AtomicBoolean();

        @Override
        public void request(long amount) {
            requested.addAndGet(amount);
        }

        @Override
        public void cancel() {
            cancelled.set(true);
        }
    }
}
