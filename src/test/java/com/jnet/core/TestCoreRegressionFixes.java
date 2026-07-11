package com.jnet.core;

import com.sun.net.httpserver.HttpServer;
import com.jnet.core.org.json.JSONObject;
import com.jnet.protocol.ProtocolRequest;
import com.jnet.protocol.ProtocolResponse;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCoreRegressionFixes {

    @Test
    void nullStringBodyClearsPreviouslyConfiguredPublisher() {
        Request request = Request.newBuilder()
                .url("https://example.com")
                .method("POST")
                .body("stale")
                .body((String) null)
                .build();

        assertNull(request.getBody());
        assertNull(request.getBodyPublisher());
    }

    @Test
    void replacingStringBodyWithPublisherPreservesPublisherAcrossToBuilder() {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(new byte[] {1, 2, 3, 4});
        Request original = Request.newBuilder()
                .url("https://example.com")
                .method("POST")
                .body("stale")
                .body(publisher)
                .build();

        Request rebuilt = original.toBuilder().header("X-Test", "value").build();

        assertNull(original.getBody());
        assertSame(publisher, rebuilt.getBodyPublisher());
    }

    @Test
    void headerInterceptorPreservesStreamingBodyPublisher() throws Exception {
        HttpRequest.BodyPublisher publisher = HttpRequest.BodyPublishers.ofByteArray(new byte[] {1, 2, 3});
        Request request = Request.newBuilder()
                .url("https://example.com")
                .method("POST")
                .body(publisher)
                .build();
        Request[] proceeded = new Request[1];
        Interceptor.Chain chain = new Interceptor.Chain() {
            @Override
            public Request request() {
                return request;
            }

            @Override
            public Response proceed(Request nextRequest) {
                proceeded[0] = nextRequest;
                return Response.success(nextRequest).code(200).build();
            }
        };

        new Interceptor.HeaderInterceptor("X-Test", "value").intercept(chain);

        assertSame(publisher, proceeded[0].getBodyPublisher());
    }

    @Test
    void jNetAcceptsCustomHttpMethodTokens() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resource", exchange -> {
            byte[] response = exchange.getRequestMethod().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/resource";
            String response = assertDoesNotThrow(() -> JNet.request("PROPFIND", url));
            assertEquals("PROPFIND", response);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void asyncCallWithInterceptorsTracksItsPendingFuture() throws Exception {
        CountDownLatch interceptorStarted = new CountDownLatch(1);
        CountDownLatch releaseInterceptor = new CountDownLatch(1);
        CountDownLatch callbackFinished = new CountDownLatch(1);
        Interceptor blockingInterceptor = chain -> {
            interceptorStarted.countDown();
            try {
                if (!releaseInterceptor.await(2, TimeUnit.SECONDS)) {
                    throw new AssertionError("interceptor was not released");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("interrupted", e);
            }
            return Response.success(chain.request()).code(200).body("ok").build();
        };
        JNetClient client = JNetClient.newBuilder().addInterceptor(blockingInterceptor).build();
        Call.RealCall call = (Call.RealCall) client.newGet("https://example.com").build().newCall();

        try {
            call.enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    callbackFinished.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    callbackFinished.countDown();
                }
            });
            assertTrue(interceptorStarted.await(2, TimeUnit.SECONDS));

            Field pendingFuture = Call.RealCall.class.getDeclaredField("pendingFuture");
            pendingFuture.setAccessible(true);
            assertNotNull((CompletableFuture<?>) pendingFuture.get(call));
        } finally {
            releaseInterceptor.countDown();
            assertTrue(callbackFinished.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void asyncNetworkResponseReportsMeasuredDuration() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow", exchange -> {
            try {
                Thread.sleep(75);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            CountDownLatch callbackFinished = new CountDownLatch(1);
            AtomicReference<Response> responseRef = new AtomicReference<>();
            AtomicReference<Exception> failureRef = new AtomicReference<>();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/slow";

            JNetClient.create().newGet(url).build().newCall().enqueue(new Call.Callback() {
                @Override
                public void onSuccess(Response response) {
                    responseRef.set(response);
                    callbackFinished.countDown();
                }

                @Override
                public void onFailure(Exception e) {
                    failureRef.set(e);
                    callbackFinished.countDown();
                }
            });

            assertTrue(callbackFinished.await(3, TimeUnit.SECONDS));
            assertNull(failureRef.get());
            assertNotNull(responseRef.get());
            assertTrue(responseRef.get().getDuration() > 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void asyncExecutorUsesDaemonThreads() throws Exception {
        assertTrue(AsyncExecutor.getExecutor()
                .submit(() -> Thread.currentThread().isDaemon())
                .get(2, TimeUnit.SECONDS));
    }

    @Test
    void nullCookieHandlerDisablesCookieManagement() {
        JNetClient client = assertDoesNotThrow(() -> JNetClient.newBuilder()
                .cookieHandler(null)
                .build());

        assertTrue(client.getHttpClient().cookieHandler().isEmpty());
    }

    @Test
    void clientTimeoutsRejectInvalidOrOverflowingValues() {
        assertThrows(IllegalArgumentException.class,
                () -> JNetClient.newBuilder().connectTimeout(0, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> JNetClient.newBuilder().readTimeout(-1, TimeUnit.MILLISECONDS));
        assertThrows(IllegalArgumentException.class,
                () -> JNetClient.newBuilder().connectTimeout(Integer.MAX_VALUE, TimeUnit.DAYS));
    }

    @Test
    void responseCacheSeparatesCookieAndVaryHeaderValues() {
        ResponseCache cache = new ResponseCache(60_000);
        Request tenantA = Request.newBuilder()
                .url("https://example.com/data")
                .header("Cookie", "session=A")
                .header("X-Tenant", "A")
                .build();
        Request tenantB = Request.newBuilder()
                .url("https://example.com/data")
                .header("Cookie", "session=B")
                .header("X-Tenant", "B")
                .build();
        Response responseA = Response.success(tenantA)
                .code(200)
                .header("Vary", "Cookie, X-Tenant")
                .body("private-A")
                .build();

        cache.put(tenantA, responseA);

        assertNotNull(cache.get(tenantA));
        assertNull(cache.get(tenantB));
    }

    @Test
    void responseCacheDoesNotStoreForbiddenResponsesOrNonPositiveTtl() {
        Request request = Request.newBuilder().url("https://example.com/data").build();
        for (String headerValue : new String[] {"no-store", "private"}) {
            ResponseCache cache = new ResponseCache(60_000);
            cache.put(request, Response.success(request)
                    .code(200)
                    .header("Cache-Control", headerValue)
                    .body("secret")
                    .build());
            assertEquals(0, cache.size());
        }

        ResponseCache varyStarCache = new ResponseCache(60_000);
        varyStarCache.put(request, Response.success(request)
                .code(200)
                .header("Vary", "*")
                .body("secret")
                .build());
        assertEquals(0, varyStarCache.size());

        ResponseCache zeroTtlCache = new ResponseCache(0);
        zeroTtlCache.put(request, Response.success(request).code(200).body("data").build());
        assertEquals(0, zeroTtlCache.size());
    }

    @Test
    void responseCacheSaturatesCacheControlTtlOverflow() {
        ResponseCache cache = new ResponseCache(Long.MAX_VALUE, Collections.emptyList());
        Request request = Request.newBuilder().url("https://example.com/data").build();
        Response response = Response.success(request)
                .code(200)
                .header("Cache-Control", "max-age=" + Long.MAX_VALUE)
                .body("data")
                .build();

        cache.put(request, response);

        assertNotNull(cache.get(request));
    }

    @Test
    void responseCacheIncludesCookiesAddedByTheClientsCookieHandler() {
        CookieManager cookies = new CookieManager();
        URI uri = URI.create("https://example.com/private");
        HttpCookie sessionA = new HttpCookie("session", "A");
        sessionA.setVersion(0);
        sessionA.setPath("/");
        cookies.getCookieStore().add(uri, sessionA);
        JNetClient client = JNetClient.newBuilder().cookieHandler(cookies).build();
        Request request = client.newGet(uri.toString()).build();
        ResponseCache cache = new ResponseCache(60_000);
        cache.put(request, Response.success(request).code(200).body("private-A").build());

        cookies.getCookieStore().removeAll();
        HttpCookie sessionB = new HttpCookie("session", "B");
        sessionB.setVersion(0);
        sessionB.setPath("/");
        cookies.getCookieStore().add(uri, sessionB);

        assertNull(cache.get(request));
    }

    @Test
    void responseCacheHasABoundedEntryCount() {
        ResponseCache cache = new ResponseCache(60_000, Collections.emptyList(), 2);
        for (int i = 0; i < 3; i++) {
            Request request = Request.newBuilder().url("https://example.com/" + i).build();
            cache.put(request, Response.success(request).code(200).body("data").build());
        }
        assertTrue(cache.size() <= 2);
    }

    @Test
    void gitApiPayloadsEscapeCallerControlledJsonFields() {
        String data = GitApiPayloads.content(
                "base64-content",
                "commit \"message\"\nnext line",
                "sha-value",
                "user\\name",
                "mail\"@example.com");

        JSONObject payload = new JSONObject(data);
        assertEquals("base64-content", payload.getString("content"));
        assertEquals("commit \"message\"\nnext line", payload.getString("message"));
        assertEquals("sha-value", payload.getString("sha"));
        JSONObject committer = payload.getJSONObject("committer");
        assertEquals("user\\name", committer.getString("name"));
        assertEquals("mail\"@example.com", committer.getString("email"));
    }

    @Test
    void compatibilityPostOverloadUsesJsonWhenProvided() throws Exception {
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/post", exchange -> {
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/post";
            JNet.post(url, Collections.singletonMap("X-Test", "value"), "ignored",
                    Collections.singletonMap("name", "JNet"));

            assertEquals("application/json", contentType.get());
            assertEquals("{\"name\":\"JNet\"}", requestBody.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void publishedConnectionPoolCompatibilityShimRemainsAvailable() throws Exception {
        Class<?> type = Class.forName("com.jnet.core.ConnectionPool");
        Object pool = type.getConstructor().newInstance();
        HttpURLConnection connection = (HttpURLConnection) type.getMethod("get", String.class)
                .invoke(pool, "http://127.0.0.1/resource");

        assertEquals(10_000, connection.getConnectTimeout());
        assertEquals(10_000, connection.getReadTimeout());
        assertTrue(!connection.getInstanceFollowRedirects());
        assertDoesNotThrow(() -> type.getMethod("release", HttpURLConnection.class)
                .invoke(pool, connection));
        assertDoesNotThrow(() -> type.getMethod("release", HttpURLConnection.class)
                .invoke(pool, new Object[] { null }));
        assertDoesNotThrow(() -> type.getMethod("shutdown").invoke(pool));
    }

    @Test
    void protocolRequestStoresValidatedHostAndMethodValues() {
        ProtocolRequest request = ProtocolRequest.newBuilder()
                .host(" 127.0.0.1 ", 8080)
                .method(" ping ")
                .build();

        assertEquals("127.0.0.1", request.getHost());
        assertEquals("ping", request.getMethod());
    }

    @Test
    void protocolResponseDefaultsBytesReadToThePayloadLength() {
        assertEquals(3, ProtocolResponse.success().data(new byte[] {1, 2, 3}).build().getBytesRead());
        assertEquals(1, ProtocolResponse.success()
                .data(new byte[] {1, 2, 3})
                .bytesRead(1)
                .build()
                .getBytesRead());
    }
}
