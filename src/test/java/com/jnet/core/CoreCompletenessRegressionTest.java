package com.jnet.core;

import com.jnet.jsonrpc.JJsonRPC;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreCompletenessRegressionTest {

    @Test
    void closerClosesResourcesThatImplementOnlyAutoCloseable() {
        AtomicBoolean closed = new AtomicBoolean();
        AutoCloseable resource = () -> closed.set(true);

        Closer.close(resource);

        assertTrue(closed.get());
    }

    @Test
    void customHttpMethodsKeepTheirValidatedCaseThroughTheFacade() throws Exception {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/method", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/method";
            JNet.requestResponse("  CuStOm  ", url, null);
            assertEquals("CuStOm", receivedMethod.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void facadeAuthenticationHelpersUseTheHardenedValidationRules() {
        assertThrows(IllegalArgumentException.class, () -> JNet.basicAuth(null, "password"));
        assertThrows(IllegalArgumentException.class, () -> JNet.basicAuth("user:name", "password"));
        assertThrows(IllegalArgumentException.class, () -> JNet.bearerToken(null));
        assertThrows(IllegalArgumentException.class, () -> JNet.bearerToken("has whitespace"));
    }

    @Test
    void jsonRpcOffersAnObservableNotificationFuture() throws Exception {
        Method notifyAsync = JJsonRPC.class.getMethod(
                "notifyAsync", String.class, String.class, Object.class);

        @SuppressWarnings("unchecked")
        CompletableFuture<Void> result = (CompletableFuture<Void>) notifyAsync.invoke(
                null, "not a valid url", "event", null);

        assertThrows(ExecutionException.class, result::get);
    }
}
