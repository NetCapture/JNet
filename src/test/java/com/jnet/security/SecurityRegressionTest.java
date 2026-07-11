package com.jnet.security;

import com.sun.net.httpserver.HttpServer;
import com.jnet.auth.BasicAuth;
import com.jnet.auth.BearerAuth;
import com.jnet.auth.DigestAuth;
import com.jnet.cloudflare.CloudflareInterceptor;
import com.jnet.cloudflare.UserAgentRotator;
import com.jnet.core.Interceptor;
import com.jnet.core.Request;
import com.jnet.core.Response;
import com.jnet.core.SSLConfigEnhanced;
import com.jnet.webdav.JWebDAV;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLParameters;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SecurityRegressionTest {

    @Test
    void credentialsRejectNullAndHeaderControlCharacters() {
        assertThrows(IllegalArgumentException.class, () -> new BasicAuth(null, "password"));
        assertThrows(IllegalArgumentException.class, () -> new BasicAuth("user\r\nInjected", "password"));
        assertThrows(IllegalArgumentException.class, () -> new BearerAuth(null));
        assertThrows(IllegalArgumentException.class, () -> new BearerAuth("token\r\nInjected: value"));
    }

    @Test
    void digestSupportsQuotedQopListsAndSha256Sess() throws Exception {
        DigestAuth auth = new DigestAuth("Mufasa", "Circle Of Life");
        auth.parseChallenge("dIgEsT REALM=\"test,realm\", NONCE=\"nonce-value\", "
                + "QOP=\"auth-int, auth\", ALGORITHM=\"SHA-256-sess\", OPAQUE=\"opaque\"");

        Request request = Request.newBuilder()
                .url("https://example.com/dir/index.html?x=1")
                .method("GET")
                .build();
        Map<String, String> fields = parseDigestHeader(auth.apply(request).getHeader("Authorization"));

        assertEquals("auth", fields.get("qop"));
        assertEquals("SHA-256-sess", fields.get("algorithm"));
        assertEquals("test,realm", fields.get("realm"));
        String cnonce = fields.get("cnonce");
        String nc = fields.get("nc");
        String ha1 = hash("SHA-256", "Mufasa:test,realm:Circle Of Life");
        ha1 = hash("SHA-256", ha1 + ":nonce-value:" + cnonce);
        String ha2 = hash("SHA-256", "GET:/dir/index.html?x=1");
        String expected = hash("SHA-256", ha1 + ":nonce-value:" + nc + ":" + cnonce
                + ":auth:" + ha2);
        assertEquals(expected, fields.get("response"));
    }

    @Test
    void digestAuthIntHashesTheEntityBodyAndRejectsOpaquePublishers() throws Exception {
        DigestAuth auth = new DigestAuth("user", "password");
        auth.parseChallenge("Digest realm=\"realm\", nonce=\"nonce\", qop=\"auth-int\", algorithm=MD5-sess");

        Request request = Request.newBuilder()
                .url("https://example.com/upload")
                .method("POST")
                .body("payload")
                .build();
        Map<String, String> fields = parseDigestHeader(auth.apply(request).getHeader("Authorization"));
        String cnonce = fields.get("cnonce");
        String nc = fields.get("nc");
        String ha1 = hash("MD5", "user:realm:password");
        ha1 = hash("MD5", ha1 + ":nonce:" + cnonce);
        String entityHash = hash("MD5", "payload");
        String ha2 = hash("MD5", "POST:/upload:" + entityHash);
        String expected = hash("MD5", ha1 + ":nonce:" + nc + ":" + cnonce
                + ":auth-int:" + ha2);
        assertEquals(expected, fields.get("response"));

        Request streaming = Request.newBuilder()
                .url("https://example.com/upload")
                .method("POST")
                .body(java.net.http.HttpRequest.BodyPublishers.ofInputStream(
                        () -> new java.io.ByteArrayInputStream(new byte[] { 1, 2, 3 })))
                .build();
        assertThrows(IllegalStateException.class, () -> auth.apply(streaming));
    }

    @Test
    void digestRejectsUnsupportedAlgorithmsAndMalformedChallenges() {
        DigestAuth auth = new DigestAuth("user", "password");
        assertThrows(IllegalArgumentException.class,
                () -> auth.parseChallenge("Digest realm=\"realm\", nonce=\"nonce\", algorithm=SHA-1"));
        assertThrows(IllegalArgumentException.class,
                () -> auth.parseChallenge("Digest realm=\"realm\", nonce=\"nonce\", qop=\"unknown\""));
        assertThrows(IllegalArgumentException.class,
                () -> auth.parseChallenge("Digest realm=\"realm\", nonce=\"nonce\"\r\nInjected: true"));
        assertThrows(IllegalArgumentException.class, () -> new DigestAuth("user:name", "password"));
    }

    @Test
    void digestSupportsUserhashUtf8UsernameEncodingAndNfcCredentials() throws Exception {
        String username = "Jo\u0308hn";
        String password = "pa\u0308ss";
        DigestAuth userhash = new DigestAuth(username, password);
        userhash.parseChallenge(
                "Digest realm=\"realm\", nonce=\"nonce\", algorithm=SHA-256, userhash=true");
        Request request = Request.newBuilder().url("https://example.com/resource").build();
        Map<String, String> hashed = parseDigestHeader(
                userhash.apply(request).getHeader("Authorization"));

        String normalizedUsername = Normalizer.normalize(username, Normalizer.Form.NFC);
        String normalizedPassword = Normalizer.normalize(password, Normalizer.Form.NFC);
        assertEquals(hash("SHA-256", normalizedUsername + ":realm"), hashed.get("username"));
        assertEquals("true", hashed.get("userhash"));
        String ha1 = hash("SHA-256", normalizedUsername + ":realm:" + normalizedPassword);
        String ha2 = hash("SHA-256", "GET:/resource");
        assertEquals(hash("SHA-256", ha1 + ":nonce:" + ha2), hashed.get("response"));

        DigestAuth extended = new DigestAuth("Jöhn", "password");
        extended.parseChallenge("Digest realm=\"realm\", nonce=\"nonce\"");
        Map<String, String> fields = parseDigestHeader(
                extended.apply(request).getHeader("Authorization"));
        assertEquals("UTF-8''J%C3%B6hn", fields.get("username*"));
        assertFalse(fields.containsKey("username"));
    }

    @Test
    void digestSupportsSha512_256AndUsesTheRawRequestTarget() throws Exception {
        DigestAuth auth = new DigestAuth("user", "password");
        auth.parseChallenge(
                "Digest realm=\"realm\", nonce=\"nonce\", algorithm=SHA-512-256");
        Request request = Request.newBuilder()
                .url("https://example.com/a%2Fb?value=%2F")
                .build();
        Map<String, String> fields = parseDigestHeader(auth.apply(request).getHeader("Authorization"));

        String ha1 = hash("SHA-512/256", "user:realm:password");
        String ha2 = hash("SHA-512/256", "GET:/a%2Fb?value=%2F");
        assertEquals(hash("SHA-512/256", ha1 + ":nonce:" + ha2), fields.get("response"));
        assertEquals("/a%2Fb?value=%2F", fields.get("uri"));
    }

    @Test
    void requestRejectsInjectedFieldsAndInvalidHttpUris() {
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com").header("X-Test\r\nInjected", "value"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com").header("X-Test", "value\r\nInjected: true"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com").header("X-Test", "non-http-\u0100"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com").method("GET\r\nInjected"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .uri(URI.create("file:///tmp/test")));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://user:password@example.com/path"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com/path#fragment"));
        assertThrows(IllegalArgumentException.class, () -> Request.newBuilder()
                .url("https://example.com:70000/path"));
    }

    @Test
    void requestAndResponseReplaceHeaderNamesCaseInsensitively() {
        Request request = Request.newBuilder()
                .url("https://example.com")
                .header("Authorization", "first")
                .header("authorization", "second")
                .build();
        assertEquals(1, request.getHeaders().size());
        assertEquals("second", request.getHeader("AUTHORIZATION"));

        Response response = Response.success(request)
                .header("Set-Cookie", "a=1")
                .headerValues("set-cookie", Arrays.asList("b=2", "c=3"))
                .build();
        assertEquals(1, response.getHeaders().size());
        assertEquals(Arrays.asList("b=2", "c=3"), response.getHeaderValues("SET-COOKIE"));
        assertThrows(UnsupportedOperationException.class,
                () -> response.getHeaderValues("set-cookie").add("d=4"));
    }

    @Test
    void responseDefensivelyCopiesHeaderListsAndValidatesValues() {
        Request request = Request.newBuilder().url("https://example.com").build();
        List<String> values = new ArrayList<>(Arrays.asList("one", "two"));
        Response response = Response.success(request).headerValues("X-Test", values).build();
        values.set(0, "changed");
        assertEquals(Arrays.asList("one", "two"), response.getHeaderValues("x-test"));
        assertThrows(IllegalArgumentException.class,
                () -> Response.success(request).header("X-Test", "safe\r\nInjected: yes"));
    }

    @Test
    void sslParametersEnableHostnameVerificationAndDefensivelyCopyArrays() throws Exception {
        String[] protocols = { "TLSv1.2" };
        String[] ciphers = { "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" };
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .protocols(protocols)
                .cipherSuites(ciphers)
                .build();
        protocols[0] = "TLSv1";
        ciphers[0] = "TLS_RSA_WITH_AES_128_CBC_SHA";

        SSLParameters parameters = config.getSSLParameters();
        assertArrayEquals(new String[] { "TLSv1.2" }, parameters.getProtocols());
        assertArrayEquals(new String[] { "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256" },
                parameters.getCipherSuites());
        assertEquals("HTTPS", parameters.getEndpointIdentificationAlgorithm());
    }

    @Test
    void sslPinningRejectsMalformedAndConflictingConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> SSLConfigEnhanced.newBuilder().pinCertificate("example.com", "abc123"));
        assertThrows(IllegalArgumentException.class, () -> SSLConfigEnhanced.newBuilder()
                .pinCertificate("example.com", repeat('a', 64))
                .trustAllCertificates()
                .build());
    }

    @Test
    void cloudflareRetriesOnlySafeRequestsAndFindsHeadersCaseInsensitively() throws Exception {
        CloudflareInterceptor interceptor = new CloudflareInterceptor(1, 0);
        Request get = Request.newBuilder().url("https://example.com").build();
        AtomicInteger getCalls = new AtomicInteger();
        Interceptor.Chain getChain = challengeChain(get, getCalls);
        interceptor.intercept(getChain);
        assertEquals(2, getCalls.get());

        Request post = Request.newBuilder().url("https://example.com").method("POST").body("data").build();
        AtomicInteger postCalls = new AtomicInteger();
        interceptor.intercept(challengeChain(post, postCalls));
        assertEquals(1, postCalls.get());

        assertThrows(IllegalArgumentException.class, () -> new CloudflareInterceptor(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CloudflareInterceptor(1, -1));
    }

    @Test
    void userAgentRotationToleratesConcurrentReadsAndWrites() throws Exception {
        UserAgentRotator rotator = new UserAgentRotator();
        assertThrows(IllegalArgumentException.class,
                () -> rotator.addUserAgent("Safe\r\nInjected: yes"));

        ExecutorService executor = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < 3; i++) {
            executor.execute(() -> {
                try {
                    start.await();
                    for (int j = 0; j < 2_000; j++) {
                        rotator.getRandomUserAgent();
                    }
                } catch (Throwable t) {
                    failures.add(t);
                }
            });
        }
        executor.execute(() -> {
            try {
                start.await();
                for (int j = 0; j < 500; j++) {
                    rotator.clear();
                    rotator.addUserAgent("Agent/" + j);
                }
            } catch (Throwable t) {
                failures.add(t);
            }
        });
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertTrue(failures.isEmpty(), failures.toString());
    }

    @Test
    void webDavResponseApisPreserveStatusAndLockTokenHeaders() throws Exception {
        Method lockResponse = JWebDAV.class.getMethod(
                "lockResponse", String.class, String.class, long.class);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/resource", exchange -> {
            exchange.getResponseHeaders().set("Lock-Token", "<opaquelocktoken:test>");
            exchange.sendResponseHeaders(423, -1);
            exchange.close();
        });
        server.start();
        try {
            Response response = (Response) lockResponse.invoke(null,
                    "http://127.0.0.1:" + server.getAddress().getPort() + "/resource",
                    "owner", 60L);

            assertEquals(423, response.getCode());
            assertEquals("<opaquelocktoken:test>", response.getHeader("Lock-Token"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void webDavRejectsEmptyBracketedTokensAndIllegalXmlCharactersBeforeNetworkIo() {
        assertThrows(IllegalArgumentException.class,
                () -> JWebDAV.unlock("http://127.0.0.1:1/resource", "<>"));
        assertThrows(IllegalArgumentException.class,
                () -> JWebDAV.unlock("http://127.0.0.1:1/resource", "token<fragment"));
        assertThrows(IllegalArgumentException.class,
                () -> JWebDAV.lock("http://127.0.0.1:1/resource", "owner\u0001", 60));
    }

    private static Interceptor.Chain challengeChain(Request request, AtomicInteger calls) {
        return new Interceptor.Chain() {
            @Override
            public Request request() {
                return request;
            }

            @Override
            public Response proceed(Request current) {
                int call = calls.incrementAndGet();
                if (call == 1) {
                    return Response.failure(current)
                            .code(503)
                            .header("server", "CloudFlare")
                            .build();
                }
                return Response.success(current).code(200).build();
            }
        };
    }

    private static Map<String, String> parseDigestHeader(String header) {
        assertTrue(header.startsWith("Digest "));
        Map<String, String> fields = new HashMap<>();
        String value = header.substring("Digest ".length());
        int index = 0;
        while (index < value.length()) {
            while (index < value.length() && (value.charAt(index) == ',' || Character.isWhitespace(value.charAt(index)))) {
                index++;
            }
            int equals = value.indexOf('=', index);
            String name = value.substring(index, equals).trim().toLowerCase(Locale.ROOT);
            index = equals + 1;
            String fieldValue;
            if (value.charAt(index) == '"') {
                StringBuilder decoded = new StringBuilder();
                index++;
                while (index < value.length() && value.charAt(index) != '"') {
                    char current = value.charAt(index++);
                    if (current == '\\') {
                        current = value.charAt(index++);
                    }
                    decoded.append(current);
                }
                index++;
                fieldValue = decoded.toString();
            } else {
                int comma = value.indexOf(',', index);
                if (comma < 0) {
                    comma = value.length();
                }
                fieldValue = value.substring(index, comma).trim();
                index = comma;
            }
            fields.put(name, fieldValue);
        }
        return fields;
    }

    private static String hash(String algorithm, String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance(algorithm);
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte current : bytes) {
            hex.append(String.format("%02x", current & 0xff));
        }
        return hex.toString();
    }

    private static String repeat(char value, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, value);
        return new String(chars);
    }
}
