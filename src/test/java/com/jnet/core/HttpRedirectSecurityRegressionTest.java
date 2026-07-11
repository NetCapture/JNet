package com.jnet.core;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRedirectSecurityRegressionTest {

    @Test
    void crossOriginRedirectStripsSensitiveHeadersSynchronously() throws Exception {
        assertCrossOriginHeadersAreStripped(false);
    }

    @Test
    void crossOriginRedirectStripsSensitiveHeadersAsynchronously() throws Exception {
        assertCrossOriginHeadersAreStripped(true);
    }

    @Test
    void sameOriginRedirectRetainsCallerHeaders() throws Exception {
        AtomicReference<com.sun.net.httpserver.Headers> received = new AtomicReference<>();
        HttpServer server = newServer();
        server.createContext("/start", exchange -> redirect(exchange, 302, "/target"));
        server.createContext("/target", exchange -> {
            received.set(exchange.getRequestHeaders());
            send(exchange, 200, "ok");
        });
        server.start();
        try {
            String base = baseUrl(server);
            Response response = JNetClient.create().newGet(base + "/start")
                    .header("Authorization", "Bearer same-origin")
                    .header("Proxy-Authorization", "Basic same-origin")
                    .header("Cookie", "session=same-origin")
                    .build()
                    .newCall()
                    .execute();

            assertEquals(200, response.getCode());
            assertEquals("Bearer same-origin", received.get().getFirst("Authorization"));
            assertEquals("session=same-origin", received.get().getFirst("Cookie"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redirectMethodAndBodyRulesMatchForSyncAndAsyncCalls() throws Exception {
        HttpServer server = newServer();
        server.createContext("/redirect", exchange -> {
            String status = exchange.getRequestURI().getPath().substring("/redirect/".length());
            exchange.getRequestBody().readAllBytes();
            redirect(exchange, Integer.parseInt(status), "/target");
        });
        server.createContext("/target", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            send(exchange, 200, exchange.getRequestMethod() + ":" + body + ":"
                    + String.valueOf(exchange.getRequestHeaders().getFirst("Content-Type")));
        });
        server.start();
        try {
            for (boolean async : new boolean[] { false, true }) {
                assertRedirectResult(server, 301, "POST", "payload", async, "GET::null");
                assertRedirectResult(server, 302, "POST", "payload", async, "GET::null");
                assertRedirectResult(server, 303, "PUT", "payload", async, "GET::null");
                assertRedirectResult(server, 303, "post", "payload", async, "GET::null");
                assertRedirectResult(server, 307, "POST", "payload", async,
                        "POST:payload:text/plain");
                assertRedirectResult(server, 308, "POST", "payload", async,
                        "POST:payload:text/plain");
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void methodChangingRedirectMayTargetTheSameUriOnce() throws Exception {
        AtomicInteger hits = new AtomicInteger();
        HttpServer server = newServer();
        server.createContext("/same", exchange -> {
            hits.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if ("POST".equals(exchange.getRequestMethod())) {
                redirect(exchange, 303, "/same");
            } else {
                send(exchange, 200, exchange.getRequestMethod());
            }
        });
        server.start();
        try {
            Response response = JNetClient.create().newPost(baseUrl(server) + "/same")
                    .body("payload")
                    .build()
                    .newCall()
                    .execute();

            assertEquals("GET", response.getBody());
            assertEquals(2, hits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    @Timeout(2)
    void readTimeoutCoversTheWholeRedirectChainInsteadOfResettingPerHop() throws Exception {
        HttpServer server = newServer();
        server.createContext("/slow-start", exchange -> {
            sleep(300);
            redirect(exchange, 302, "/slow-target");
        });
        server.createContext("/slow-target", exchange -> {
            sleep(300);
            send(exchange, 200, "late");
        });
        server.start();
        try {
            JNetClient client = JNetClient.newBuilder()
                    .readTimeout(450, TimeUnit.MILLISECONDS)
                    .build();

            assertThrows(IOException.class,
                    () -> client.newGet(baseUrl(server) + "/slow-start").build().newCall().execute());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void redirectLoopAndExcessiveUniqueRedirectsFailBoundedly() throws Exception {
        AtomicInteger loopHits = new AtomicInteger();
        AtomicInteger chainHits = new AtomicInteger();
        HttpServer server = newServer();
        server.createContext("/loop-a", exchange -> {
            loopHits.incrementAndGet();
            redirect(exchange, 302, "/loop-b");
        });
        server.createContext("/loop-b", exchange -> {
            loopHits.incrementAndGet();
            redirect(exchange, 302, "/loop-a");
        });
        server.createContext("/chain", exchange -> {
            int current = Integer.parseInt(exchange.getRequestURI().getQuery());
            chainHits.incrementAndGet();
            redirect(exchange, 302, "/chain?" + (current + 1));
        });
        server.start();
        try {
            JNetClient client = JNetClient.create();
            assertThrows(IOException.class,
                    () -> client.newGet(baseUrl(server) + "/loop-a").build().newCall().execute());
            assertTrue(loopHits.get() <= 2, "loop must be detected before another network hop");

            assertThrows(IOException.class,
                    () -> client.newGet(baseUrl(server) + "/chain?0").build().newCall().execute());
            assertTrue(chainHits.get() <= 11, "redirect count must have a small fixed bound");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preservingRedirectRejectsOpaqueRequestBodiesInsteadOfGuessingReplayability() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        HttpServer server = newServer();
        server.createContext("/start", exchange -> {
            exchange.getRequestBody().readAllBytes();
            redirect(exchange, 307, "/target");
        });
        server.createContext("/target", exchange -> {
            targetHits.incrementAndGet();
            send(exchange, 200, "unexpected");
        });
        server.start();
        try {
            Request request = JNetClient.create().newPost(baseUrl(server) + "/start")
                    .body(HttpRequest.BodyPublishers.ofByteArray(new byte[] { 1, 2, 3 }))
                    .build();

            IOException failure = assertThrows(IOException.class, () -> request.newCall().execute());
            assertTrue(failure.getMessage().toLowerCase(Locale.ROOT).contains("replay"));
            assertEquals(0, targetHits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void disablingRedirectsReturnsTheOriginalRedirectResponse() throws Exception {
        AtomicInteger targetHits = new AtomicInteger();
        HttpServer server = newServer();
        server.createContext("/start", exchange -> redirect(exchange, 302, "/target"));
        server.createContext("/target", exchange -> {
            targetHits.incrementAndGet();
            send(exchange, 200, "target");
        });
        server.start();
        try {
            JNetClient client = JNetClient.newBuilder().followRedirects(false).build();
            Response response = client.newGet(baseUrl(server) + "/start").build().newCall().execute();

            assertEquals(302, response.getCode());
            assertEquals(0, targetHits.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void defaultClientsAreStatelessAndCookieStorageIsExplicitOptIn() throws Exception {
        HttpServer server = newServer();
        server.createContext("/set", exchange -> {
            exchange.getResponseHeaders().add("Set-Cookie", "session=secret; Path=/");
            send(exchange, 200, "set");
        });
        server.createContext("/echo", exchange ->
                send(exchange, 200, String.valueOf(exchange.getRequestHeaders().getFirst("Cookie"))));
        server.start();
        try {
            JNetClient stateless = JNetClient.create();
            stateless.newGet(baseUrl(server) + "/set").build().newCall().execute();
            Response withoutCookies = stateless.newGet(baseUrl(server) + "/echo")
                    .build().newCall().execute();

            assertTrue(stateless.getHttpClient().cookieHandler().isEmpty());
            assertTrue(JNetClient.getInstance().getHttpClient().cookieHandler().isEmpty());
            assertTrue(JNet.getDefaultHttpClient().cookieHandler().isEmpty());
            assertEquals("null", withoutCookies.getBody());

            CookieManager cookieManager = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
            JNetClient session = JNetClient.newBuilder().cookieHandler(cookieManager).build();
            session.newGet(baseUrl(server) + "/set").build().newCall().execute();
            Response withCookies = session.newGet(baseUrl(server) + "/echo")
                    .build().newCall().execute();
            assertTrue(withCookies.getBody().contains("session=secret"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void clientsUseOneBoundedLibraryOwnedDaemonExecutor() throws Exception {
        JNetClient first = JNetClient.create();
        JNetClient second = JNetClient.create();

        Executor firstExecutor = first.getHttpClient().executor().orElseThrow(AssertionError::new);
        Executor secondExecutor = second.getHttpClient().executor().orElseThrow(AssertionError::new);
        assertSame(firstExecutor, secondExecutor);
        assertTrue(firstExecutor instanceof ThreadPoolExecutor);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) firstExecutor;
        assertTrue(pool.getQueue().remainingCapacity() < Integer.MAX_VALUE);

        CompletableFuture<Boolean> daemon = new CompletableFuture<>();
        firstExecutor.execute(() -> daemon.complete(Thread.currentThread().isDaemon()));
        assertTrue(daemon.get(2, TimeUnit.SECONDS));
    }

    @Test
    void jdkTransportNeverPerformsRedirectsBehindJNet() {
        JNetClient client = JNetClient.create();
        assertTrue(client.isFollowRedirects());
        assertEquals(HttpClient.Redirect.NEVER, client.getHttpClient().followRedirects());
    }

    private static void assertCrossOriginHeadersAreStripped(boolean async) throws Exception {
        AtomicReference<com.sun.net.httpserver.Headers> received = new AtomicReference<>();
        HttpServer target = newServer();
        target.createContext("/target", exchange -> {
            received.set(exchange.getRequestHeaders());
            send(exchange, 200, "target");
        });
        target.start();

        HttpServer origin = newServer();
        origin.createContext("/start", exchange ->
                redirect(exchange, 302, baseUrl(target) + "/target"));
        origin.start();
        try {
            Request request = JNetClient.create().newGet(baseUrl(origin) + "/start")
                    .header("Authorization", "Bearer secret")
                    .header("Proxy-Authorization", "Basic proxy-secret")
                    .header("Cookie", "session=secret")
                    .header("Host", "forged.invalid")
                    .build();

            Response response = execute(request.newCall(), async);
            assertEquals(200, response.getCode());
            assertEquals("target", response.getBody());
            assertNull(received.get().getFirst("Authorization"));
            assertNull(received.get().getFirst("Proxy-Authorization"));
            assertNull(received.get().getFirst("Cookie"));
            assertFalse("forged.invalid".equalsIgnoreCase(received.get().getFirst("Host")));
            assertEquals("127.0.0.1:" + target.getAddress().getPort(), received.get().getFirst("Host"));
        } finally {
            origin.stop(0);
            target.stop(0);
        }
    }

    private static void assertRedirectResult(HttpServer server, int status, String method,
            String body, boolean async, String expected) throws Exception {
        Request request = JNetClient.create().newGet(baseUrl(server) + "/redirect/" + status)
                .method(method)
                .header("Content-Type", "text/plain")
                .body(body)
                .build();
        assertEquals(expected, execute(request.newCall(), async).getBody());
    }

    private static Response execute(Call call, boolean async) throws Exception {
        if (!async) {
            return call.execute();
        }
        CompletableFuture<Response> result = new CompletableFuture<>();
        call.enqueue(new Call.Callback() {
            @Override
            public void onSuccess(Response response) {
                result.complete(response);
            }

            @Override
            public void onFailure(Exception error) {
                result.completeExceptionally(error);
            }
        });
        return result.get(3, TimeUnit.SECONDS);
    }

    private static HttpServer newServer() throws IOException {
        return HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static void redirect(HttpExchange exchange, int status, String location) throws IOException {
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(status, -1);
        exchange.close();
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        exchange.getRequestBody().readAllBytes();
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void sleep(long millis) throws IOException {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("test server interrupted", error);
        }
    }
}
