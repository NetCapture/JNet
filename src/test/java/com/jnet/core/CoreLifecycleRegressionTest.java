package com.jnet.core;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreLifecycleRegressionTest {

    @Test
    void legacyDefaultTimeoutReconfiguresTheFacadeClient() {
        try {
            JNet.setDefaultTimeout(java.time.Duration.ofMillis(123));

            assertEquals(123, JNet.getClient().getConnectTimeout());
            assertEquals(123, JNet.getClient().getReadTimeout());
            assertSame(JNet.getClient().getHttpClient(), JNet.getDefaultHttpClient());
        } finally {
            JNet.setDefaultTimeout(java.time.Duration.ofSeconds(10));
        }
    }

    @Test
    void callbackExceptionsDoNotCauseSecondCallbacksAndPendingFuturesAreReleased() throws Exception {
        JNetClient successClient = JNetClient.newBuilder()
                .addInterceptor(chain -> Response.success(chain.request()).code(200).build())
                .build();
        Call.RealCall successCall = (Call.RealCall) successClient.newGet("https://example.com").build().newCall();
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failureCount = new AtomicInteger();
        CountDownLatch successCallback = new CountDownLatch(1);
        successCall.enqueue(new Call.Callback() {
            @Override
            public void onSuccess(Response response) {
                successCount.incrementAndGet();
                successCallback.countDown();
                throw new IllegalStateException("consumer failure");
            }

            @Override
            public void onFailure(Exception e) {
                failureCount.incrementAndGet();
            }
        });

        assertTrue(successCallback.await(2, TimeUnit.SECONDS));
        awaitPendingFutureRelease(successCall);
        assertEquals(1, successCount.get());
        assertEquals(0, failureCount.get());

        JNetClient failureClient = JNetClient.newBuilder()
                .addInterceptor(chain -> {
                    throw new IOException("network failure");
                })
                .build();
        Call.RealCall failureCall = (Call.RealCall) failureClient.newGet("https://example.com").build().newCall();
        CountDownLatch failureCallback = new CountDownLatch(1);
        AtomicInteger secondFailureCount = new AtomicInteger();
        failureCall.enqueue(new Call.Callback() {
            @Override
            public void onSuccess(Response response) {
                throw new AssertionError("unexpected success");
            }

            @Override
            public void onFailure(Exception e) {
                secondFailureCount.incrementAndGet();
                failureCallback.countDown();
                throw new IllegalStateException("consumer failure");
            }
        });

        assertTrue(failureCallback.await(2, TimeUnit.SECONDS));
        awaitPendingFutureRelease(failureCall);
        assertEquals(1, secondFailureCount.get());
    }

    @Test
    void cancelingAQueuedInterceptorCallDeliversExactlyOneFailure() throws Exception {
        int workerCount = Math.max(2,
                Math.min(32, Runtime.getRuntime().availableProcessors() * 2));
        CountDownLatch workersStarted = new CountDownLatch(workerCount);
        CountDownLatch releaseWorkers = new CountDownLatch(1);
        CountDownLatch workersFinished = new CountDownLatch(workerCount);
        JNetClient blockingClient = JNetClient.newBuilder().addInterceptor(chain -> {
            workersStarted.countDown();
            try {
                releaseWorkers.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", e);
            }
            return Response.success(chain.request()).code(200).build();
        }).build();

        for (int i = 0; i < workerCount; i++) {
            blockingClient.newGet("https://example.com/" + i).build().newCall().enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    workersFinished.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    workersFinished.countDown();
                }
            });
        }

        assertTrue(workersStarted.await(3, TimeUnit.SECONDS));
        JNetClient queuedClient = JNetClient.newBuilder()
                .addInterceptor(chain -> Response.success(chain.request()).code(200).build())
                .build();
        Call.RealCall queuedCall = (Call.RealCall) queuedClient.newGet("https://example.com/queued")
                .build().newCall();
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        CountDownLatch callback = new CountDownLatch(1);
        try {
            queuedCall.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    successes.incrementAndGet();
                    callback.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    failures.incrementAndGet();
                    callback.countDown();
                }
            });
            queuedCall.cancel();

            assertTrue(callback.await(1, TimeUnit.SECONDS), "cancel must complete the callback");
            awaitPendingFutureRelease(queuedCall);
        } finally {
            releaseWorkers.countDown();
            assertTrue(workersFinished.await(3, TimeUnit.SECONDS));
        }
        Thread.sleep(50);
        assertEquals(0, successes.get());
        assertEquals(1, failures.get());
    }

    @Test
    void interceptorListsIgnoreNullsAndAreDefensivelyCopied() throws Exception {
        Interceptor first = chain -> chain.proceed(chain.request());
        Interceptor second = chain -> chain.proceed(chain.request());
        List<Interceptor> supplied = new ArrayList<>(Arrays.asList(first, null));

        JNetClient client = JNetClient.newBuilder().interceptors(supplied).build();
        supplied.clear();
        supplied.add(second);

        assertEquals(Collections.singletonList(first), client.getInterceptors());
        assertThrows(UnsupportedOperationException.class,
                () -> client.getInterceptors().add(second));
        assertTrue(JNetClient.newBuilder().interceptors(null).build().getInterceptors().isEmpty());

        Request request = client.newGet("https://example.com").build();
        Call.RealCall direct = new Call.RealCall(request, client, Arrays.asList(null,
                chain -> Response.success(chain.request()).code(200).build()));
        assertEquals(200, direct.execute().getCode());
    }

    @Test
    void cacheDoesNotAliasOpaqueStreamingRequestBodies() {
        JNetClient client = JNetClient.newBuilder().cookieHandler(null).build();
        Request first = client.newGet("https://example.com/search")
                .body(HttpRequest.BodyPublishers.ofByteArray(new byte[] { 1 }))
                .build();
        Request second = client.newGet("https://example.com/search")
                .body(HttpRequest.BodyPublishers.ofByteArray(new byte[] { 2 }))
                .build();
        ResponseCache cache = new ResponseCache(60_000);

        cache.put(first, Response.success(first).code(200).body("first").build());

        assertEquals(0, cache.size());
        assertNull(cache.get(second));
    }

    @Test
    void cacheHonorsFieldQualifiedNoCacheAndAge() {
        JNetClient client = JNetClient.newBuilder().cookieHandler(null).build();
        Request request = client.newGet("https://example.com/data").build();

        ResponseCache noCache = new ResponseCache(60_000);
        noCache.put(request, Response.success(request).code(200)
                .header("Cache-Control", "no-cache=\"Set-Cookie\"")
                .body("private").build());
        assertEquals(0, noCache.size());

        ResponseCache privateCache = new ResponseCache(60_000);
        privateCache.put(request, Response.success(request).code(200)
                .header("Cache-Control", "private = \"Authorization\"")
                .body("private").build());
        assertEquals(0, privateCache.size());

        ResponseCache aged = new ResponseCache(60_000);
        aged.put(request, Response.success(request).code(200)
                .header("Cache-Control", "max-age=1")
                .header("Age", "2")
                .body("stale").build());
        assertEquals(0, aged.size());
    }

    @Test
    void concurrentCapacityEnforcementRetainsExactlyTheConfiguredNumberOfEntries() throws Exception {
        int writers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(writers);
        JNetClient client = JNetClient.newBuilder().cookieHandler(null).build();
        try {
            for (int round = 0; round < 20; round++) {
                ResponseCache cache = new ResponseCache(60_000, Collections.emptyList(), 2);
                CountDownLatch start = new CountDownLatch(1);
                List<Future<?>> writes = new ArrayList<>();
                for (int i = 0; i < writers; i++) {
                    final int index = i;
                    writes.add(executor.submit(() -> {
                        start.await();
                        Request request = client.newGet("https://example.com/" + index).build();
                        cache.put(request, Response.success(request).code(200).body("data").build());
                        return null;
                    }));
                }
                start.countDown();
                for (Future<?> write : writes) {
                    write.get(3, TimeUnit.SECONDS);
                }
                assertEquals(2, cache.size());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void cancelingAnInterceptorCallDoesNotRetryTheCanceledNetworkRequest() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        CountDownLatch firstRequest = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/retry", exchange -> {
            int attempt = requests.incrementAndGet();
            if (attempt == 1) {
                firstRequest.countDown();
                try {
                    releaseFirst.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            byte[] body = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JNetClient client = JNetClient.newBuilder()
                    .addInterceptor(new Interceptor.RetryInterceptor(3, 10))
                    .build();
            Call call = client.newGet(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/retry")
                    .build().newCall();
            CountDownLatch callback = new CountDownLatch(1);
            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    callback.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    callback.countDown();
                }
            });

            assertTrue(firstRequest.await(1, TimeUnit.SECONDS));
            call.cancel();
            releaseFirst.countDown();
            assertTrue(callback.await(1, TimeUnit.SECONDS));

            Thread.sleep(250);
            assertEquals(1, requests.get());
        } finally {
            releaseFirst.countDown();
            server.stop(0);
        }
    }

    private static void awaitPendingFutureRelease(Call.RealCall call) throws Exception {
        Field field = Call.RealCall.class.getDeclaredField("pendingFuture");
        field.setAccessible(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (field.get(call) != null && System.nanoTime() < deadline) {
            Thread.sleep(5);
        }
        assertNull((CompletableFuture<?>) field.get(call));
    }
}
