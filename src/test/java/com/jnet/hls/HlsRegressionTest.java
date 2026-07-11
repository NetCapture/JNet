package com.jnet.hls;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HlsRegressionTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesMediaStateIntoImmutableModels() {
        String content = "\uFEFF#EXTM3U\n"
                + "#EXT-X-VERSION:7\n"
                + "#EXT-X-TARGETDURATION:8\n"
                + "#EXT-X-MEDIA-SEQUENCE:42\n"
                + "#EXT-X-MAP:URI=\"init.mp4\",BYTERANGE=\"4@1\"\n"
                + "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\",IV=0x2A\n"
                + "#EXTINF:4.25,first segment\n"
                + "#EXT-X-BYTERANGE:16@4\n"
                + "media.mp4\n"
                + "#EXT-X-KEY:METHOD=NONE\n"
                + "#EXTINF:3.75,clear segment\n"
                + "clear.ts\n"
                + "#EXT-X-ENDLIST\n";

        M3U8Parser.HlsMediaPlaylist playlist = M3U8Parser.parse(content);

        assertEquals("8", playlist.getTargetDuration());
        assertEquals(42, playlist.getMediaSequence());
        assertEquals(2, playlist.getSegmentCount());
        assertEquals(8.0, playlist.getDuration());
        assertFalse(playlist.isLive());
        assertThrows(UnsupportedOperationException.class,
                () -> playlist.getSegments().add(playlist.getSegment(0)));
        assertThrows(UnsupportedOperationException.class,
                () -> playlist.getMetadata().put("mutable", "no"));

        M3U8Parser.HlsSegment encrypted = playlist.getSegment(0);
        assertArrayEquals(new byte[0], encrypted.getData());
        assertEquals(42L, encrypted.getSequenceNumber());
        assertEquals(16L, encrypted.getByteRange().getLength());
        assertEquals(4L, encrypted.getByteRange().getOffset());
        assertEquals("init.mp4", encrypted.getInitializationSegment().getUri());
        assertEquals(4L, encrypted.getInitializationSegment().getByteRange().getLength());
        assertEquals(1L, encrypted.getInitializationSegment().getByteRange().getOffset());
        assertEquals("AES-128", encrypted.getEncryptionKey().getMethod());
        assertEquals("key.bin", encrypted.getEncryptionKey().getUri());
        byte[] iv = encrypted.getEncryptionKey().getIv();
        assertEquals(42, iv[15] & 0xff);
        iv[15] = 0;
        assertEquals(42, encrypted.getEncryptionKey().getIv()[15] & 0xff);

        M3U8Parser.HlsSegment clear = playlist.getSegment(1);
        assertEquals(43L, clear.getSequenceNumber());
        assertNull(clear.getByteRange());
        assertNull(clear.getEncryptionKey());
        assertEquals(encrypted.getInitializationSegment(), clear.getInitializationSegment());
    }

    @Test
    void readTimeoutCoversTheCompletePlaylistBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow.m3u8", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            try {
                exchange.getResponseBody().write(("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n").getBytes(StandardCharsets.UTF_8));
                exchange.getResponseBody().flush();
                Thread.sleep(2_000);
                exchange.getResponseBody().write(("#EXTINF:4,\n"
                        + "segment.ts\n").getBytes(StandardCharsets.UTF_8));
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // The expected timeout closes the response stream.
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            HlsClient client = HlsClient.newBuilder()
                    .url("http://127.0.0.1:" + server.getAddress().getPort() + "/slow.m3u8")
                    .readTimeout(Duration.ofMillis(150))
                    .build();

            long started = System.nanoTime();
            assertThrows(IOException.class, client::getPlaylist);
            long elapsedMillis = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                    System.nanoTime() - started);
            assertTrue(elapsedMillis < 1_000,
                    "the response-body timeout must abort the stalled body, elapsed=" + elapsedMillis);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void cancellingExecuteAsyncClosesThePlaylistTransfer() throws Exception {
        try (StalledHttpServer server = new StalledHttpServer()) {
            HlsClient client = HlsClient.newBuilder()
                    .url("http://127.0.0.1:" + server.port() + "/playlist.m3u8")
                    .readTimeout(Duration.ofSeconds(10))
                    .build();

            CompletableFuture<?> request = client.executeAsync(null);
            assertTrue(server.awaitResponse());
            assertTrue(request.cancel(true));
            assertTrue(request.isCancelled());
            assertTrue(server.awaitClientClose(),
                    "cancelling executeAsync must close its in-flight HTTP response body");
            server.assertHealthy();
        }
    }

    @Test
    void resolvesImplicitByteRangeOffsetOnlyForTheSameResource() {
        M3U8Parser.HlsMediaPlaylist playlist = M3U8Parser.parse("#EXTM3U\n"
                + "#EXT-X-TARGETDURATION:4\n"
                + "#EXTINF:4,\n"
                + "#EXT-X-BYTERANGE:5@2\n"
                + "packed.ts\n"
                + "#EXTINF:4,\n"
                + "#EXT-X-BYTERANGE:7\n"
                + "packed.ts\n");

        assertEquals(2L, playlist.getSegment(0).getByteRange().getOffset());
        assertEquals(7L, playlist.getSegment(1).getByteRange().getOffset());

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> M3U8Parser.parse("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:5@2\n"
                        + "first.ts\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:5\n"
                        + "second.ts\n"));
        assertTrue(error.getMessage().contains("implicit byte range"));

        assertThrows(IllegalArgumentException.class,
                () -> M3U8Parser.parse("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:5@2\n"
                        + "packed.ts\n"
                        + "#EXTINF:4,\n"
                        + "packed.ts\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:5\n"
                        + "packed.ts\n"));
    }

    @Test
    void rejectsMasterAndUnsupportedEncryptionExplicitly() {
        IllegalArgumentException master = assertThrows(IllegalArgumentException.class,
                () -> M3U8Parser.parse("#EXTM3U\n"
                        + "#EXT-X-STREAM-INF:BANDWIDTH=1000000\n"
                        + "video.m3u8\n"));
        assertTrue(master.getMessage().contains("Master playlists are not supported"));

        IllegalArgumentException encryption = assertThrows(IllegalArgumentException.class,
                () -> M3U8Parser.parse("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXT-X-KEY:METHOD=SAMPLE-AES,URI=\"key.bin\"\n"
                        + "#EXTINF:4,\nsegment.ts\n"));
        assertTrue(encryption.getMessage().contains("Unsupported encryption method"));

        assertThrows(IllegalArgumentException.class,
                () -> M3U8Parser.parse("#EXT-X-TARGETDURATION:4\n"));
    }

    @Test
    void downloadsMapsRangesAndAes128SegmentsWithoutChangingBinaryData() throws Exception {
        byte[] key = "0123456789abcdef".getBytes(StandardCharsets.US_ASCII);
        byte[] plaintext = new byte[] {0, 1, 2, 3, 4, 5, 6, (byte) 0xff, 0, 10, 13};
        byte[] encrypted = encrypt(plaintext, key, sequenceIv(7));
        byte[] packed = "xxxxINITxxxxDATAxxxx".getBytes(StandardCharsets.US_ASCII);
        List<String> ranges = new ArrayList<>();
        AtomicInteger keyRequests = new AtomicInteger();

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                String path = exchange.getRequestURI().getPath();
                if ("/hls/playlist.m3u8".equals(path)) {
                    String playlist = "#EXTM3U\n"
                            + "#EXT-X-TARGETDURATION:4\n"
                            + "#EXT-X-MEDIA-SEQUENCE:7\n"
                            + "#EXT-X-MAP:URI=\"../media/container.mp4\",BYTERANGE=\"4@4\"\n"
                            + "#EXT-X-KEY:METHOD=AES-128,URI=\"../keys/key.bin\"\n"
                            + "#EXTINF:4,\n"
                            + "../media/encrypted.bin\n"
                            + "#EXT-X-KEY:METHOD=NONE\n"
                            + "#EXTINF:4,\n"
                            + "#EXT-X-BYTERANGE:4@12\n"
                            + "../media/container.mp4\n"
                            + "#EXT-X-ENDLIST\n";
                    send(exchange, 200, playlist.getBytes(StandardCharsets.UTF_8));
                } else if ("/keys/key.bin".equals(path)) {
                    keyRequests.incrementAndGet();
                    send(exchange, 200, key);
                } else if ("/media/encrypted.bin".equals(path)) {
                    send(exchange, 200, encrypted);
                } else if ("/media/container.mp4".equals(path)) {
                    String range = exchange.getRequestHeaders().getFirst("Range");
                    ranges.add(range);
                    long[] bounds = parseRange(range);
                    byte[] selected = Arrays.copyOfRange(packed, (int) bounds[0], (int) bounds[1] + 1);
                    exchange.getResponseHeaders().add("Content-Range",
                            "bytes " + bounds[0] + "-" + bounds[1] + "/" + packed.length);
                    send(exchange, 206, selected);
                } else {
                    send(exchange, 404, new byte[0]);
                }
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            int port = server.getAddress().getPort();
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:" + port + "/hls/playlist.m3u8");
            AtomicInteger updates = new AtomicInteger();
            AtomicInteger completions = new AtomicInteger();
            client.downloadSegments(temporaryDirectory.toString(), new HlsClient.ProgressListener() {
                @Override
                public void onUpdate(int segment, int total, long bytes) {
                    assertEquals(segment, updates.incrementAndGet());
                    assertEquals(2, total);
                    assertTrue(bytes > 0);
                }

                @Override
                public void onComplete() {
                    completions.incrementAndGet();
                }
            });

            assertArrayEquals("INIT".getBytes(StandardCharsets.US_ASCII),
                    Files.readAllBytes(temporaryDirectory.resolve("init_00000_container.mp4")));
            assertArrayEquals(plaintext,
                    Files.readAllBytes(temporaryDirectory.resolve("encrypted.bin")));
            assertArrayEquals("DATA".getBytes(StandardCharsets.US_ASCII),
                    Files.readAllBytes(temporaryDirectory.resolve("container.mp4")));
            assertEquals(List.of("bytes=4-7", "bytes=12-15"), ranges);
            assertEquals(1, keyRequests.get());
            assertEquals(2, updates.get());
            assertEquals(1, completions.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPartialResponsesForTheWrongRange() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist.m3u8", exchange -> {
            try {
                send(exchange, 200, ("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:4@4\n"
                        + "packed.ts\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        });
        server.createContext("/packed.ts", exchange -> {
            try {
                exchange.getResponseHeaders().add("Content-Range", "bytes 0-3/8");
                send(exchange, 206, "WRNG".getBytes(StandardCharsets.US_ASCII));
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class,
                    () -> client.downloadSegments(temporaryDirectory.toString(), null));
            assertTrue(error.getMessage().contains("Content-Range"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resolvesPlaylistReferencesAgainstTheFinalRedirectUri() throws Exception {
        AtomicInteger staleBaseRequests = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/origin/playlist.m3u8", exchange -> {
            try {
                exchange.getResponseHeaders().add("Location", "/cdn/live/playlist.m3u8");
                exchange.sendResponseHeaders(302, -1);
            } finally {
                exchange.close();
            }
        });
        server.createContext("/cdn/live/playlist.m3u8", exchange -> {
            try {
                send(exchange, 200, ("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXTINF:4,\n"
                        + "segment.ts\n"
                        + "#EXT-X-ENDLIST\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        });
        server.createContext("/cdn/live/segment.ts", exchange -> {
            try {
                send(exchange, 200, "redirect-base".getBytes(StandardCharsets.US_ASCII));
            } finally {
                exchange.close();
            }
        });
        server.createContext("/origin/segment.ts", exchange -> {
            try {
                staleBaseRequests.incrementAndGet();
                send(exchange, 404, new byte[0]);
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/origin/playlist.m3u8");
            client.downloadSegments(temporaryDirectory.toString(), null);

            assertArrayEquals("redirect-base".getBytes(StandardCharsets.US_ASCII),
                    Files.readAllBytes(temporaryDirectory.resolve("segment.ts")));
            assertEquals(0, staleBaseRequests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsAFullResponseThatCannotContainANonZeroRequestedRange() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/playlist.m3u8", exchange -> {
            try {
                send(exchange, 200, ("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n"
                        + "#EXTINF:4,\n"
                        + "#EXT-X-BYTERANGE:4@4\n"
                        + "packed.ts\n").getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        });
        server.createContext("/packed.ts", exchange -> {
            try {
                send(exchange, 200, "WRNG".getBytes(StandardCharsets.US_ASCII));
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class,
                    () -> client.downloadSegments(temporaryDirectory.toString(), null));
            assertTrue(error.getMessage().contains("insufficient data"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsPlaylistResponsesAboveTheSafeInMemoryLimit() throws Exception {
        long declaredLength = 2L * 1024 * 1024 + 1;
        HttpServer server = oversizedServer("/playlist.m3u8", declaredLength);
        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class, client::getPlaylist);
            assertTrue(error.getMessage().contains("maximum"));
            assertTrue(error.getMessage().contains(String.valueOf(2L * 1024 * 1024)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsSegmentResponsesAboveTheSafeInMemoryLimit() throws Exception {
        long declaredLength = 64L * 1024 * 1024 + 1;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addPlaylist(server, "#EXTINF:4,\nlarge.ts\n");
        addOversizedResponse(server, "/large.ts", declaredLength);
        server.start();
        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class,
                    () -> client.downloadSegments(temporaryDirectory.toString(), null));
            assertTrue(error.getMessage().contains("maximum"));
            assertTrue(error.getMessage().contains(String.valueOf(64L * 1024 * 1024)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsInitializationResponsesAboveTheSafeInMemoryLimit() throws Exception {
        long declaredLength = 16L * 1024 * 1024 + 1;
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addPlaylist(server, "#EXT-X-MAP:URI=\"init.mp4\"\n#EXTINF:4,\nsegment.ts\n");
        addOversizedResponse(server, "/init.mp4", declaredLength);
        server.createContext("/segment.ts", exchange -> {
            try {
                send(exchange, 200, "segment".getBytes(StandardCharsets.US_ASCII));
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class,
                    () -> client.downloadSegments(temporaryDirectory.toString(), null));
            assertTrue(error.getMessage().contains("maximum"));
            assertTrue(error.getMessage().contains(String.valueOf(16L * 1024 * 1024)));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsEncryptionKeysLargerThanAes128Requires() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addPlaylist(server, "#EXT-X-KEY:METHOD=AES-128,URI=\"key.bin\","
                + "IV=0x00000000000000000000000000000001\n"
                + "#EXTINF:4,\nencrypted.ts\n");
        addOversizedResponse(server, "/key.bin", 17);
        server.createContext("/encrypted.ts", exchange -> {
            try {
                send(exchange, 200, new byte[16]);
            } finally {
                exchange.close();
            }
        });
        server.start();
        try {
            HlsClient client = HlsClient.fromUrl("http://127.0.0.1:"
                    + server.getAddress().getPort() + "/playlist.m3u8");
            IOException error = assertThrows(IOException.class,
                    () -> client.downloadSegments(temporaryDirectory.toString(), null));
            assertTrue(error.getMessage().contains("maximum"));
            assertTrue(error.getMessage().contains("16"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rejectsUnsafePlaylistUris() {
        assertThrows(IllegalArgumentException.class,
                () -> HlsClient.fromUrl("http://user@example.test/playlist.m3u8"));
        assertThrows(IllegalArgumentException.class,
                () -> HlsClient.fromUrl("http://example.test/playlist.m3u8#fragment"));
    }

    private static byte[] encrypt(byte[] plaintext, byte[] key, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new IvParameterSpec(iv));
        return cipher.doFinal(plaintext);
    }

    private static byte[] sequenceIv(long sequence) {
        byte[] iv = new byte[16];
        for (int i = iv.length - 1; i >= 0 && sequence != 0; i--) {
            iv[i] = (byte) sequence;
            sequence >>>= 8;
        }
        return iv;
    }

    private static long[] parseRange(String header) {
        assertTrue(header != null && header.startsWith("bytes="));
        String[] values = header.substring("bytes=".length()).split("-", 2);
        return new long[] {Long.parseLong(values[0]), Long.parseLong(values[1])};
    }

    private static HttpServer oversizedServer(String path, long declaredLength) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        addOversizedResponse(server, path, declaredLength);
        server.start();
        return server;
    }

    private static void addPlaylist(HttpServer server, String body) {
        server.createContext("/playlist.m3u8", exchange -> {
            try {
                send(exchange, 200, ("#EXTM3U\n#EXT-X-TARGETDURATION:4\n" + body)
                        .getBytes(StandardCharsets.UTF_8));
            } finally {
                exchange.close();
            }
        });
    }

    private static void addOversizedResponse(HttpServer server, String path, long declaredLength) {
        server.createContext(path, exchange -> {
            try {
                exchange.sendResponseHeaders(200, declaredLength);
            } finally {
                exchange.close();
            }
        });
    }

    private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static final class StalledHttpServer implements AutoCloseable {
        private final ServerSocket server = new ServerSocket(0);
        private final CountDownLatch responseStarted = new CountDownLatch(1);
        private final CountDownLatch clientClosed = new CountDownLatch(1);
        private final AtomicReference<Throwable> failure = new AtomicReference<>();
        private final Thread thread;
        private volatile Socket connection;

        private StalledHttpServer() throws IOException {
            thread = new Thread(this::serve, "hls-stalled-http-server");
            thread.setDaemon(true);
            thread.start();
        }

        private int port() {
            return server.getLocalPort();
        }

        private boolean awaitResponse() throws InterruptedException {
            return responseStarted.await(2, TimeUnit.SECONDS);
        }

        private boolean awaitClientClose() throws InterruptedException {
            return clientClosed.await(1, TimeUnit.SECONDS);
        }

        private void assertHealthy() {
            Throwable error = failure.get();
            if (error != null) {
                throw new AssertionError("Stalled HTTP server failed", error);
            }
        }

        private void serve() {
            try (Socket socket = server.accept()) {
                connection = socket;
                InputStream input = socket.getInputStream();
                readHttpHeaders(input);
                byte[] prefix = ("#EXTM3U\n"
                        + "#EXT-X-TARGETDURATION:4\n").getBytes(StandardCharsets.UTF_8);
                OutputStream output = socket.getOutputStream();
                output.write(("HTTP/1.1 200 OK\r\n"
                        + "Content-Type: application/vnd.apple.mpegurl\r\n"
                        + "Transfer-Encoding: chunked\r\n"
                        + "Connection: close\r\n\r\n"
                        + Integer.toHexString(prefix.length) + "\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                output.write(prefix);
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
                output.flush();
                responseStarted.countDown();
                if (input.read() != -1) {
                    throw new IOException("Expected the HLS client to close the HTTP connection");
                }
                clientClosed.countDown();
            } catch (Throwable error) {
                if (!server.isClosed()) {
                    failure.set(error);
                }
            } finally {
                responseStarted.countDown();
            }
        }

        private static void readHttpHeaders(InputStream input) throws IOException {
            int state = 0;
            while (state < 4) {
                int value = input.read();
                if (value < 0) {
                    throw new IOException("Unexpected EOF in HTTP request headers");
                }
                state = state == 0 && value == '\r' ? 1
                        : state == 1 && value == '\n' ? 2
                        : state == 2 && value == '\r' ? 3
                        : state == 3 && value == '\n' ? 4 : 0;
            }
        }

        @Override
        public void close() throws Exception {
            server.close();
            Socket current = connection;
            if (current != null) {
                current.close();
            }
            thread.join(1_000);
        }
    }
}
