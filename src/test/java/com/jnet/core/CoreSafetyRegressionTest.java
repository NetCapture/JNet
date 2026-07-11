package com.jnet.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpServer;
import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;
import ff.jnezha.jnt.NJnt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreSafetyRegressionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void configuredHttpResponseLimitStopsUnboundedAggregation() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/large", exchange -> {
            byte[] body = new byte[2_048];
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JNetClient client = JNetClient.newBuilder()
                    .maxResponseBytes(1_024)
                    .build();
            Request request = client.newGet(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/large")
                    .build();

            assertThrows(IOException.class, () -> request.newCall().execute());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void sharedAsyncExecutorHasABoundedBacklog() {
        assertTrue(AsyncExecutor.getExecutor() instanceof ThreadPoolExecutor);
        ThreadPoolExecutor executor = (ThreadPoolExecutor) AsyncExecutor.getExecutor();
        assertTrue(executor.getQueue().remainingCapacity() < Integer.MAX_VALUE);
    }

    @Test
    void interruptibleAsyncSubmissionPropagatesCancellationToTheWorker() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        CompletableFuture<Void> future = AsyncExecutor.submit(() -> {
            started.countDown();
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException error) {
                interrupted.countDown();
                throw error;
            }
            return null;
        });

        assertTrue(started.await(1, TimeUnit.SECONDS));
        assertTrue(future.cancel(true));
        assertTrue(interrupted.await(1, TimeUnit.SECONDS));
    }

    @Test
    void legacyInputStreamConversionCanBeBounded() {
        assertNull(DataConver.parserInputStreamToString(
                new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)), 4));
    }

    @Test
    void streamReadAllCanBeBounded() throws Exception {
        try (StreamResponse response = new StreamResponse(
                null, new ByteArrayInputStream("hello".getBytes(StandardCharsets.UTF_8)))) {
            assertThrows(IOException.class, () -> response.readAll(4));
        }
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyBuilderRequiresAnExplicitUrlBeforeNetworkIo() {
        assertThrows(IllegalStateException.class, () -> NJnt.get().exec());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyResponseToStringDoesNotExposeBodies() {
        JntResponse response = new JntResponse();
        response.setInputStream("input-secret");
        response.setErrorStream("error-secret");

        String rendered = response.toString();
        assertFalse(rendered.contains("input-secret"));
        assertFalse(rendered.contains("error-secret"));
    }

    @Test
    void requestToStringRecognizesCustomBodyPublishers() {
        Request request = Request.newBuilder()
                .url("https://example.com")
                .body(HttpRequest.BodyPublishers.ofByteArray(new byte[] {1}))
                .build();

        assertTrue(request.toString().contains("hasBody=true"));
    }

    @Test
    void socksProxyIsRejectedInsteadOfSilentlySendingTrafficDirectly() {
        Proxy socks = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 1080));
        assertThrows(IllegalArgumentException.class,
                () -> JNetClient.newBuilder().proxy(socks).build());
    }

    @Test
    void explicitDirectProxyDoesNotInheritTheJvmProxySelector() {
        ProxySelector original = ProxySelector.getDefault();
        ProxySelector.setDefault(new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return Collections.singletonList(
                        new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 9)));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
            }
        });
        try {
            JNetClient client = JNetClient.newBuilder().proxy(Proxy.NO_PROXY).build();
            Proxy selected = client.getHttpClient().proxy().orElseThrow()
                    .select(URI.create("http://example.com")).get(0);
            assertTrue(selected.type() == Proxy.Type.DIRECT);
        } finally {
            ProxySelector.setDefault(original);
        }
    }

    @Test
    void embeddedJsonRejectsCircularContainersWithoutStackOverflow() {
        JSONObject object = new JSONObject();
        object.put("self", object);
        assertThrows(JSONException.class, object::toString);

        JSONArray array = new JSONArray();
        array.put(array);
        assertThrows(JSONException.class, array::toString);

        Map<String, Object> map = new IdentityHashMap<>();
        map.put("self", map);
        assertThrows(JSONException.class, () -> new JSONObject(map));
    }

    @Test
    void base64FileDecodeHonorsItsLimitAndPublishesAtomically() throws Exception {
        Path target = temporaryDirectory.resolve("decoded.bin");
        Files.writeString(target, "original", StandardCharsets.UTF_8);
        String encoded = Base64.getEncoder().encodeToString("replacement".getBytes(StandardCharsets.UTF_8));

        assertFalse(FileUtils.saveBase64ToFile(encoded, target.toString(), 4));
        assertEquals("original", Files.readString(target, StandardCharsets.UTF_8));

        assertTrue(FileUtils.saveBase64ToFile(encoded, target.toString(), 64));
        assertEquals("replacement", Files.readString(target, StandardCharsets.UTF_8));
    }
}
