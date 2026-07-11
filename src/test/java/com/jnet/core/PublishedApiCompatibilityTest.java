package com.jnet.core;

import com.sun.net.httpserver.HttpServer;
import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONException;
import com.jnet.core.org.json.JSONObject;
import ff.jnezha.jnt.Jnt;
import ff.jnezha.jnt.JntResponse;
import org.junit.jupiter.api.Test;

import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishedApiCompatibilityTest {

    @Test
    void gitFacadeResultTypesArePubliclyAccessible() {
        assertTrue(Modifier.isPublic(GithubHelper.ShaInfo.class.getModifiers()));
        assertTrue(Modifier.isStatic(GithubHelper.ShaInfo.class.getModifiers()));
        assertTrue(Modifier.isPublic(GiteeHelper.ShaInfo.class.getModifiers()));
        assertTrue(Modifier.isStatic(GiteeHelper.ShaInfo.class.getModifiers()));
    }

    @Test
    void jnt30PublicDescriptorsRemainAvailable() throws Exception {
        Class<?> connectionPool = Class.forName("com.jnet.core.ConnectionPool");
        assertNotNull(connectionPool.getConstructor());
        assertNotNull(connectionPool.getMethod("get", String.class));
        assertNotNull(connectionPool.getMethod("release", HttpURLConnection.class));
        assertNotNull(connectionPool.getMethod("shutdown"));

        assertNotNull(Call.RealCall.class.getConstructor(
                Request.class, JNetClient.class, List.class, connectionPool));
        assertEquals(URL.class, Request.class.getMethod("getUrl").getReturnType());
        assertNotNull(SSEClient.class.getMethod("main", String[].class));

        assertNotNull(JSONObject.class.getMethod("opt", String.class));
        assertNotNull(JSONObject.class.getMethod("optLong", String.class));
        assertNotNull(JSONObject.class.getMethod("isNull", String.class));
        assertNotNull(JSONObject.class.getMethod("toString", int.class));
        assertNotNull(JSONObject.class.getMethod("length"));
        assertNotNull(JSONObject.class.getMethod("put", String.class, boolean.class));
        assertNotNull(JSONObject.class.getMethod("put", String.class, double.class));
        assertNotNull(JSONObject.class.getMethod("put", String.class, int.class));
        assertNotNull(JSONObject.class.getMethod("put", String.class, long.class));
        assertNotNull(JSONObject.class.getMethod("escape", String.class));

        assertNotNull(JSONArray.class.getMethod("getJSONArray", int.class));
        assertNotNull(JSONArray.class.getMethod("put", boolean.class));
        assertNotNull(JSONArray.class.getMethod("put", double.class));
        assertNotNull(JSONArray.class.getMethod("put", int.class));
        assertNotNull(JSONArray.class.getMethod("toString", int.class));
        assertNotNull(JSONException.class.getDeclaredMethod("getCause"));

        assertEquals(Map.class,
                GiteeHelper.class.getMethod("getHttpHeader", String.class).getReturnType());
        assertEquals(int.class, JNetClient.class.getMethod("getWriteTimeout").getReturnType());
        assertEquals(Proxy.class, JNetClient.class.getMethod("getProxy").getReturnType());
        assertEquals(boolean.class, JNetClient.class.getMethod("isFollowRedirects").getReturnType());
        assertNotNull(SSLConfig.class.getField("NOT_VERYFY"));
    }

    @Test
    void jnt30ErrorTypeNamesRemainAvailable() {
        String[] published = {
                "NETWORK_UNAVAILABLE",
                "CONNECTION_TIMEOUT",
                "READ_TIMEOUT",
                "SSL_HANDSHAKE_FAILED",
                "HTTP_PROTOCOL_ERROR",
                "HTTP_CLIENT_ERROR",
                "HTTP_SERVER_ERROR",
                "RESPONSE_PARSING_ERROR",
                "REQUEST_BUILD_ERROR",
                "UNKNOWN"
        };
        for (String name : published) {
            assertEquals(name, JNetException.ErrorType.valueOf(name).name());
        }
    }

    @Test
    void latest35ErrorTypeOrdinalsRemainStable() {
        String[] published = {
                "NETWORK_UNAVAILABLE",
                "CONNECTION_REFUSED",
                "CONNECTION_TIMEOUT",
                "READ_TIMEOUT",
                "SSL_HANDSHAKE_FAILED",
                "HTTP_PROTOCOL_ERROR",
                "HTTP_CLIENT_ERROR",
                "HTTP_SERVER_ERROR",
                "RESPONSE_PARSING_ERROR",
                "REQUEST_BUILD_ERROR",
                "IO_ERROR",
                "INTERRUPTED",
                "UNKNOWN"
        };
        for (int ordinal = 0; ordinal < published.length; ordinal++) {
            assertEquals(published[ordinal], JNetException.ErrorType.values()[ordinal].name());
        }
    }

    @Test
    void compatibilityResponseFacadePreservesHttpStatusAndHeaders() throws Exception {
        byte[] body = "missing".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/missing", exchange -> {
            exchange.getResponseHeaders().set("X-Reason", "not-found");
            exchange.sendResponseHeaders(404, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            JntResponse response = Jnt.getResp(
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/missing");

            assertEquals(404, response.getCode());
            assertFalse(response.isSuccess());
            assertEquals("missing", response.getBody());
            assertEquals("not-found", response.getHeaders().get("x-reason"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void compatibilityResponseFacadeDoesNotInventAnHttpErrorStatusForLocalFailures() {
        JntResponse response = Jnt.getResp("not-a-url");

        assertEquals(0, response.getCode());
        assertFalse(response.isSuccess());
    }

    @Test
    void compatibilityResponseToStringDoesNotExposeTheResponseBody() {
        JntResponse response = JntResponse.success("secret-token");

        assertFalse(response.toString().contains("secret-token"));
    }
}
