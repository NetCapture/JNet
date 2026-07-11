package com.jnet.rtsp;

import com.jnet.protocol.ProtocolRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class RtspClientRegressionTest {

    @Test
    void protocolAdapterAsyncFailuresCompleteExceptionally() {
        RtspClient client = RtspClient.newBuilder()
                .url("rtsp://127.0.0.1/live")
                .build();
        ProtocolRequest request = ProtocolRequest.newBuilder()
                .host("127.0.0.1", 554)
                .transport(ProtocolRequest.TransportType.UDP)
                .build();

        ExecutionException error = assertThrows(ExecutionException.class,
                () -> client.executeAsync(request).get(1, TimeUnit.SECONDS));

        assertTrue(error.getCause() instanceof IOException);
    }

    @Test
    void keepsOneSessionConnectionAndFramesEveryControlRequest() throws Exception {
        try (ScriptedRtspServer server = new ScriptedRtspServer()) {
            RtspClient client = RtspClient.newBuilder()
                    .url("rtsp://127.0.0.1:" + server.port() + "/live")
                    .credentials("camera", "secret")
                    .build();

            client.connect(1);
            assertTrue(client.isConnected());
            assertEquals(2, client.getDescription().getMediaDescriptions().size());

            client.setup(1, "RTP/AVP;unicast;client_port=5002-5003");
            assertEquals("session-123", client.getSessionId());
            client.play("00:00:01.00");
            assertTrue(client.isStreaming());
            client.teardown();

            assertFalse(client.isConnected());
            assertFalse(client.isStreaming());
            assertTrue(server.await());
            server.assertHealthy();
            assertEquals(1, server.acceptedConnections());

            List<String> requests = server.requests();
            assertEquals(5, requests.size());
            assertTrue(requests.get(0).startsWith("DESCRIBE rtsp://"));
            assertTrue(requests.get(0).contains("CSeq: 1\r\n"));
            assertTrue(requests.get(1).contains("CSeq: 2\r\n"));
            assertTrue(requests.get(1).contains("Authorization: Basic "
                    + Base64.getEncoder().encodeToString("camera:secret".getBytes(StandardCharsets.UTF_8))));
            assertTrue(requests.get(2).startsWith("SETUP rtsp://127.0.0.1:" + server.port()
                    + "/live/trackID=1 RTSP/1.0"));
            assertTrue(requests.get(2).contains("Transport: RTP/AVP;unicast;client_port=5002-5003"));
            assertTrue(requests.get(3).startsWith("PLAY rtsp://127.0.0.1:" + server.port()
                    + "/live/ RTSP/1.0"));
            assertTrue(requests.get(3).contains("Session: session-123"));
            assertTrue(requests.get(3).contains("Range: npt=00:00:01.00-"));
            assertTrue(requests.get(4).startsWith("TEARDOWN rtsp://127.0.0.1:" + server.port()
                    + "/live/ RTSP/1.0"));
        }
    }

    @Test
    void usesResolvedSessionControlForAggregateOperations() throws Exception {
        try (AggregateControlRtspServer server = new AggregateControlRtspServer()) {
            RtspClient client = RtspClient.newBuilder()
                    .url("rtsp://127.0.0.1:" + server.port() + "/live")
                    .build();

            client.connect();
            client.setup(0, "RTP/AVP/TCP;unicast;interleaved=0-1");
            client.play();
            client.pause();
            client.teardown();

            assertTrue(server.await());
            server.assertHealthy();
            String aggregate = "rtsp://127.0.0.1:" + server.port() + "/catalog/aggregate";
            String track = "rtsp://127.0.0.1:" + server.port() + "/catalog/trackID=0";
            List<String> requests = server.requests();
            assertEquals(5, requests.size());
            assertTrue(requests.get(1).startsWith("SETUP " + track + " RTSP/1.0"));
            assertTrue(requests.get(2).startsWith("PLAY " + aggregate + " RTSP/1.0"));
            assertTrue(requests.get(3).startsWith("PAUSE " + aggregate + " RTSP/1.0"));
            assertTrue(requests.get(4).startsWith("TEARDOWN " + aggregate + " RTSP/1.0"));
        }
    }

    @Test
    void closeCannotBeUndoneByAnInFlightConnect() throws Exception {
        try (SlowDescriptionRtspServer server = new SlowDescriptionRtspServer()) {
            RtspClient client = RtspClient.newBuilder()
                    .url("rtsp://127.0.0.1:" + server.port() + "/live")
                    .build();
            AtomicReference<Throwable> connectFailure = new AtomicReference<>();
            Thread connector = new Thread(() -> {
                try {
                    client.connect();
                } catch (Throwable error) {
                    connectFailure.set(error);
                }
            }, "rtsp-connect-race");
            Thread closer = new Thread(client::close, "rtsp-close-race");

            connector.start();
            assertTrue(server.awaitRequest());
            closer.start();
            closer.join(1_000);
            assertFalse(closer.isAlive(), "close must abort an in-flight RTSP exchange promptly");

            connector.join(5_000);
            assertFalse(connector.isAlive());
            assertTrue(connectFailure.get() instanceof IOException,
                    "the aborted connect must report an IOException");
            assertFalse(client.isConnected());
            assertFalse(client.isStreaming());
            assertNull(client.getDescription());
            assertTrue(server.await());
            server.assertHealthy();
        }
    }

    @Test
    void rejectsHeaderInjectionAndInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> RtspRequest.newBuilder()
                .url("rtsp://example.test/live")
                .header("Transport", "safe\r\nInjected: yes"));
        assertThrows(IllegalArgumentException.class, () -> RtspRequest.newBuilder()
                .url("rtsp://example.test/live")
                .header("Bad:Header", "value"));
        assertThrows(IllegalArgumentException.class, () -> RtspRequest.newBuilder()
                .url("rtsp://example.test/live")
                .header("Content-Length", "1"));
        assertThrows(IllegalArgumentException.class, () -> RtspRequest.newBuilder()
                .url("rtsp://example.test/live\r\nInjected: yes")
                .build());
        assertThrows(IllegalArgumentException.class, () -> RtspClient.newBuilder()
                .url("http://example.test/live")
                .build());
        assertThrows(IllegalArgumentException.class, () -> RtspClient.newBuilder()
                .url("rtsp://example.test/live")
                .timeout(java.time.Duration.ZERO)
                .build());
        assertThrows(IllegalArgumentException.class, () -> RtspClient.newBuilder()
                .url("rtsp://user@example.test/live")
                .build());
        assertThrows(IllegalArgumentException.class, () -> RtspClient.newBuilder()
                .url("rtsp://example.test/live#fragment")
                .build());
    }

    @Test
    void rejectsNegativeContentLength() {
        RtspResponse response = RtspResponse.parse(
                "RTSP/1.0 200 OK\r\nContent-Length: -1\r\n\r\n");

        assertFalse(response.isSuccessful());
        assertEquals("Invalid Content-Length", response.getErrorMessage());
    }

    @Test
    void preservesTheDeclaredResponseBodyAndCaseInsensitiveHeaders() {
        String body = " leading space\r\ntrailing space \r\n";
        RtspResponse response = RtspResponse.parse("RTSP/1.0 200 OK\r\n"
                + "content-type: application/sdp\r\n"
                + "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length
                + "\r\n\r\n" + body);

        assertTrue(response.isOk());
        assertEquals(body, response.getBody());
        assertEquals("application/sdp", response.getHeader("Content-Type"));
    }

    @Test
    void clearsSessionWhenTheControlConnectionFails() throws Exception {
        try (DroppingRtspServer server = new DroppingRtspServer()) {
            RtspClient client = RtspClient.newBuilder()
                    .url("rtsp://127.0.0.1:" + server.port() + "/live")
                    .build();

            client.connect();
            client.setup(0, "RTP/AVP/TCP;unicast;interleaved=0-1");
            assertEquals("stale-session", client.getSessionId());

            assertThrows(IOException.class, client::play);
            assertNull(client.getSessionId());
            assertFalse(client.isConnected());
            assertFalse(client.isStreaming());
            assertTrue(server.await());
            server.assertHealthy();
        }
    }

    @Test
    void retriesWithDigestAuthenticationOnTheSameConnection() throws Exception {
        try (DigestRtspServer server = new DigestRtspServer()) {
            String url = "rtsp://127.0.0.1:" + server.port() + "/live";
            RtspClient client = RtspClient.newBuilder()
                    .url(url)
                    .credentials("camera", "secret")
                    .build();

            assertTrue(client.options().isOk());
            client.close();

            assertTrue(server.await());
            server.assertHealthy();
            assertEquals(1, server.acceptedConnections());
            assertEquals(2, server.requests().size());
            assertTrue(server.requests().get(0).contains("CSeq: 1\r\n"));
            assertTrue(server.requests().get(1).contains("CSeq: 2\r\n"));

            String ha1 = md5("camera:camera:secret");
            String ha2 = md5("OPTIONS:" + url);
            String digest = md5(ha1 + ":abcdef:" + ha2);
            assertTrue(server.requests().get(1).contains("Authorization: Digest username=\"camera\""
                    + ", realm=\"camera\", nonce=\"abcdef\", uri=\"" + url
                    + "\", response=\"" + digest + "\", algorithm=MD5\r\n"));
        }
    }

    @Test
    void digestSessionAlgorithmSendsTheCnonceUsedByItsHash() throws Exception {
        RtspAuthenticator authenticator = new RtspAuthenticator("camera", "secret");
        assertTrue(authenticator.accept(
                "Digest realm=\"camera\", nonce=\"abcdef\", algorithm=MD5-sess"));

        String authorization = authenticator.authorization(
                null, RtspMethod.OPTIONS, "rtsp://example.test/live");
        String cnonce = directive(authorization, "cnonce");
        assertTrue(cnonce != null && !cnonce.isEmpty());

        String ha1 = md5(md5("camera:camera:secret") + ":abcdef:" + cnonce);
        String ha2 = md5("OPTIONS:rtsp://example.test/live");
        assertEquals(md5(ha1 + ":abcdef:" + ha2), directive(authorization, "response"));
    }

    private static String directive(String authorization, String name) {
        String marker = name + "=\"";
        int start = authorization.indexOf(marker);
        if (start < 0) {
            return null;
        }
        start += marker.length();
        int end = authorization.indexOf('"', start);
        return end < 0 ? null : authorization.substring(start, end);
    }

    private static String md5(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("MD5").digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte current : digest) {
            result.append(Character.forDigit((current >>> 4) & 0x0f, 16));
            result.append(Character.forDigit(current & 0x0f, 16));
        }
        return result.toString();
    }

    private static boolean awaitState(Thread thread, Thread.State expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (System.nanoTime() < deadline) {
            if (thread.getState() == expected) {
                return true;
            }
            Thread.sleep(1);
        }
        return thread.getState() == expected;
    }

    private static final class ScriptedRtspServer extends TestServer {
        private static final String SDP = "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Camera\r\n"
                + "t=0 0\r\n"
                + "a=control:*\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:trackID=0\r\n"
                + "m=audio 0 RTP/AVP 97\r\n"
                + "a=rtpmap:97 MPEG4-GENERIC/48000/2\r\n"
                + "a=control:trackID=1\r\n";

        private final List<String> requests = new ArrayList<>();

        private ScriptedRtspServer() throws IOException {
            start("rtsp-regression-server");
        }

        private List<String> requests() {
            return requests;
        }

        @Override
        void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            requests.add(readRequest(input));
            writeResponse(output, "401 Unauthorized",
                    "WWW-Authenticate: Basic realm=\"camera\"\r\n", "");

            requests.add(readRequest(input));
            String contentBase = "Content-Base: rtsp://127.0.0.1:" + port() + "/live/\r\n"
                    + "Content-Type: application/sdp\r\n";
            byte[] response = response("200 OK", contentBase, SDP);
            int split = response.length - 17;
            output.write(response, 0, split);
            output.flush();
            Thread.sleep(20);
            output.write(response, split, response.length - split);
            output.flush();

            requests.add(readRequest(input));
            writeResponse(output, "200 OK", "Session: session-123;timeout=60\r\n", "");

            requests.add(readRequest(input));
            output.write(new byte[] {'$', 0, 0, 3, 1, 2, 3});
            writeResponse(output, "200 OK", "Session: session-123\r\n", "");

            requests.add(readRequest(input));
            writeResponse(output, "200 OK", "Session: session-123\r\n", "");
        }
    }

    private static final class DroppingRtspServer extends TestServer {
        private static final String SDP = "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Camera\r\n"
                + "t=0 0\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=control:trackID=0\r\n";

        private DroppingRtspServer() throws IOException {
            start("rtsp-dropping-server");
        }

        @Override
        void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            readRequest(input);
            writeResponse(output, "200 OK", "Content-Type: application/sdp\r\n", SDP);
            readRequest(input);
            writeResponse(output, "200 OK", "Session: stale-session\r\n", "");
            readRequest(input);
        }
    }

    private static final class AggregateControlRtspServer extends TestServer {
        private static final String SDP = "v=0\r\n"
                + "o=- 1 1 IN IP4 127.0.0.1\r\n"
                + "s=Aggregate control\r\n"
                + "t=0 0\r\n"
                + "a=control:aggregate\r\n"
                + "m=video 0 RTP/AVP 96\r\n"
                + "a=rtpmap:96 H264/90000\r\n"
                + "a=control:trackID=0\r\n";

        private final List<String> requests = new ArrayList<>();

        private AggregateControlRtspServer() throws IOException {
            start("rtsp-aggregate-control-server");
        }

        private List<String> requests() {
            return requests;
        }

        @Override
        void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            requests.add(readRequest(input));
            writeResponse(output, "200 OK",
                    "Content-Location: catalog/\r\nContent-Type: application/sdp\r\n", SDP);

            requests.add(readRequest(input));
            writeResponse(output, "200 OK", "Session: aggregate-session\r\n", "");

            for (int index = 0; index < 3; index++) {
                requests.add(readRequest(input));
                writeResponse(output, "200 OK", "Session: aggregate-session\r\n", "");
            }
        }
    }

    private static final class SlowDescriptionRtspServer extends TestServer {
        private final CountDownLatch requestReceived = new CountDownLatch(1);

        private SlowDescriptionRtspServer() throws IOException {
            start("rtsp-slow-description-server");
        }

        private boolean awaitRequest() throws InterruptedException {
            return requestReceived.await(2, TimeUnit.SECONDS);
        }

        @Override
        void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            readRequest(input);
            requestReceived.countDown();
            if (input.read() != -1) {
                throw new IOException("Expected the RTSP client to close the control connection");
            }
        }
    }

    private static final class DigestRtspServer extends TestServer {
        private final List<String> requests = new ArrayList<>();

        private DigestRtspServer() throws IOException {
            start("rtsp-digest-server");
        }

        private List<String> requests() {
            return requests;
        }

        @Override
        void handle(Socket socket) throws Exception {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();
            requests.add(readRequest(input));
            writeResponse(output, "401 Unauthorized",
                    "WWW-Authenticate: Digest realm=\"camera\", nonce=\"abcdef\", algorithm=MD5\r\n", "");
            requests.add(readRequest(input));
            writeResponse(output, "200 OK", "", "");
        }
    }

    private abstract static class TestServer implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0);
        private final CountDownLatch finished = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final AtomicInteger connections = new AtomicInteger();
        private Thread thread;

        private TestServer() throws IOException {
        }

        final void start(String name) {
            thread = new Thread(this::serve, name);
            thread.setDaemon(true);
            thread.start();
        }

        final int port() {
            return server.getLocalPort();
        }

        final int acceptedConnections() {
            return connections.get();
        }

        final boolean await() throws InterruptedException {
            return finished.await(2, TimeUnit.SECONDS);
        }

        final void assertHealthy() {
            Throwable error = failure.get();
            if (error != null) {
                throw new AssertionError("RTSP test server failed", error);
            }
        }

        abstract void handle(Socket socket) throws Exception;

        private void serve() {
            try (Socket socket = server.accept()) {
                connections.incrementAndGet();
                handle(socket);
            } catch (Throwable error) {
                if (!server.isClosed()) {
                    failure.set(error);
                }
            } finally {
                finished.countDown();
            }
        }

        @Override
        public final void close() throws Exception {
            server.close();
            if (thread != null) {
                thread.join(1_000);
            }
        }
    }

    private static String readRequest(InputStream input) throws IOException {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        int state = 0;
        while (state < 4) {
            int value = input.read();
            if (value < 0) {
                throw new IOException("Unexpected EOF while reading RTSP request");
            }
            header.write(value);
            state = state == 0 && value == '\r' ? 1
                    : state == 1 && value == '\n' ? 2
                    : state == 2 && value == '\r' ? 3
                    : state == 3 && value == '\n' ? 4 : 0;
        }
        String headers = header.toString(StandardCharsets.ISO_8859_1);
        int length = 0;
        for (String line : headers.split("\\r\\n")) {
            if (line.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:")) {
                length = Integer.parseInt(line.substring(line.indexOf(':') + 1).trim());
            }
        }
        byte[] body = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(body, offset, length - offset);
            if (read < 0) {
                throw new IOException("Unexpected EOF while reading RTSP request body");
            }
            offset += read;
        }
        return headers + new String(body, StandardCharsets.UTF_8);
    }

    private static void writeResponse(OutputStream output, String status, String headers, String body)
            throws IOException {
        output.write(response(status, headers, body));
        output.flush();
    }

    private static byte[] response(String status, String headers, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        return ("RTSP/1.0 " + status + "\r\n"
                + headers
                + "Content-Length: " + bodyBytes.length + "\r\n\r\n"
                + body).getBytes(StandardCharsets.UTF_8);
    }
}
