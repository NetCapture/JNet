package com.jnet.hls;

import com.jnet.core.AsyncExecutor;
import com.jnet.core.JNetClient;
import com.jnet.protocol.ProtocolAdapter;
import com.jnet.protocol.ProtocolRequest;
import com.jnet.protocol.ProtocolResponse;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal HLS media-playlist client. It downloads binary segments, byte ranges,
 * initialization sections and identity AES-128 encrypted media.
 */
public final class HlsClient implements ProtocolAdapter {

    private static final long MAX_PLAYLIST_BYTES = 2L * 1024 * 1024;
    private static final long MAX_SEGMENT_BYTES = 64L * 1024 * 1024;
    private static final long MAX_INITIALIZATION_BYTES = 16L * 1024 * 1024;
    private static final long MAX_KEY_BYTES = 16;
    private static final int MAX_REDIRECTS = 10;

    private final URI playlistUri;
    private final Duration readTimeout;

    private HlsClient(Builder builder) {
        this.playlistUri = validateHttpUri(builder.url, "HLS URL");
        if (builder.readTimeout == null || builder.readTimeout.isZero()
                || builder.readTimeout.isNegative()) {
            throw new IllegalArgumentException("readTimeout must be positive");
        }
        this.readTimeout = builder.readTimeout;
    }

    public static HlsClient fromUrl(String url) {
        return newBuilder().url(url).build();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /** Segment downloads are bounded operations; live playlist streaming is not implemented. */
    @Override
    public boolean supportsStreaming() {
        return false;
    }

    @Override
    public ProtocolResponse execute(ProtocolRequest request) throws IOException {
        long started = System.nanoTime();
        LoadedPlaylist loaded = loadPlaylist();
        byte[] playlistBytes = loaded.content;

        return ProtocolResponse.success()
                .host(loaded.uri.getHost(), effectivePort(loaded.uri))
                .data(playlistBytes)
                .bytesRead(playlistBytes.length)
                .duration((System.nanoTime() - started) / 1_000_000L)
                .request(request)
                .header("Content-Type", "application/vnd.apple.mpegurl")
                .build();
    }

    @Override
    public CompletableFuture<ProtocolResponse> executeAsync(ProtocolRequest request) {
        return AsyncExecutor.submit(() -> execute(request));
    }

    public M3U8Parser.HlsMediaPlaylist getPlaylist() throws IOException {
        return loadPlaylist().playlist;
    }

    public void downloadSegments(String outputDir, ProgressListener listener) throws IOException {
        downloadSegments(outputDir, null, listener);
    }

    /**
     * Downloads one media-playlist snapshot. The optional {@code mediaType}
     * supplies only the fallback file extension when a segment URI has none.
     */
    public void downloadSegments(String outputDir, String mediaType, ProgressListener listener)
            throws IOException {
        LoadedPlaylist loaded = loadPlaylist();
        M3U8Parser.HlsMediaPlaylist playlist = loaded.playlist;
        URI resourceBase = loaded.uri;
        List<M3U8Parser.HlsSegment> segments = playlist.getSegments();
        Path directory = prepareDirectory(outputDir);
        String fallbackExtension = fallbackExtension(mediaType);
        Set<String> usedNames = new HashSet<>();
        Set<M3U8Parser.HlsInitializationSegment> downloadedMaps =
                Collections.newSetFromMap(new IdentityHashMap<>());
        Map<URI, byte[]> keyCache = new HashMap<>();
        int mapIndex = 0;

        for (int index = 0; index < segments.size(); index++) {
            M3U8Parser.HlsSegment segment = segments.get(index);
            M3U8Parser.HlsInitializationSegment initialization = segment.getInitializationSegment();
            if (initialization != null && downloadedMaps.add(initialization)) {
                URI initializationUri = resolveHttpUri(resourceBase, initialization.getUri());
                byte[] initializationData = fetchBytes(initializationUri, initialization.getByteRange(),
                        MAX_INITIALIZATION_BYTES, "initialization section").content;
                initializationData = decrypt(initializationData, initialization.getEncryptionKey(),
                        -1, keyCache, true, resourceBase);
                int currentMapIndex = mapIndex++;
                String mapName = String.format(Locale.ROOT, "init_%05d_%s", currentMapIndex,
                        safeBasename(initializationUri, currentMapIndex, "bin"));
                mapName = uniqueName(mapName, usedNames, index);
                writeAtomically(directory, mapName, initializationData);
            }

            URI segmentUri = resolveHttpUri(resourceBase, segment.getUri());
            byte[] data = fetchBytes(segmentUri, segment.getByteRange(),
                    MAX_SEGMENT_BYTES, "segment").content;
            data = decrypt(data, segment.getEncryptionKey(), segment.getSequenceNumber(),
                    keyCache, false, resourceBase);
            String filename = uniqueName(
                    safeBasename(segmentUri, index, fallbackExtension), usedNames, index);
            writeAtomically(directory, filename, data);

            if (listener != null) {
                listener.onUpdate(index + 1, segments.size(), data.length);
            }
        }

        if (listener != null) {
            listener.onComplete();
        }
    }

    private LoadedPlaylist loadPlaylist() throws IOException {
        FetchedBytes fetched = fetchBytes(playlistUri, null, MAX_PLAYLIST_BYTES, "playlist");
        return new LoadedPlaylist(fetched.uri, fetched.content, parsePlaylist(fetched.content));
    }

    private M3U8Parser.HlsMediaPlaylist parsePlaylist(byte[] content) throws IOException {
        if (content.length == 0) {
            throw new IOException("Empty playlist content");
        }
        try {
            return M3U8Parser.parse(new String(content, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid HLS playlist: " + error.getMessage(), error);
        }
    }

    private byte[] decrypt(byte[] data, M3U8Parser.HlsEncryptionKey key, long sequenceNumber,
            Map<URI, byte[]> keyCache, boolean initializationSection, URI resourceBase) throws IOException {
        if (key == null) {
            return data;
        }
        URI keyUri = resolveHttpUri(resourceBase, key.getUri());
        byte[] keyBytes = keyCache.get(keyUri);
        if (keyBytes == null) {
            keyBytes = fetchBytes(keyUri, null, MAX_KEY_BYTES, "encryption key").content;
            if (keyBytes.length != 16) {
                throw new IOException("AES-128 key must contain exactly 16 bytes: " + keyUri);
            }
            keyCache.put(keyUri, keyBytes);
        }
        byte[] iv = key.getIv();
        if (iv == null) {
            if (initializationSection) {
                throw new IOException("Encrypted HLS initialization section requires an explicit IV");
            }
            iv = sequenceIv(sequenceNumber);
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"),
                    new IvParameterSpec(iv));
            return cipher.doFinal(data);
        } catch (java.security.GeneralSecurityException error) {
            throw new IOException("Unable to decrypt AES-128 HLS data", error);
        }
    }

    private static byte[] sequenceIv(long sequenceNumber) throws IOException {
        if (sequenceNumber < 0) {
            throw new IOException("HLS media sequence must be non-negative");
        }
        byte[] iv = new byte[16];
        for (int index = iv.length - 1; index >= 0 && sequenceNumber != 0; index--) {
            iv[index] = (byte) sequenceNumber;
            sequenceNumber >>>= 8;
        }
        return iv;
    }

    private FetchedBytes fetchBytes(URI target, M3U8Parser.HlsByteRange range,
            long maxBytes, String resourceKind) throws IOException {
        if (range != null && range.getLength() > maxBytes) {
            throw responseTooLarge(resourceKind, maxBytes, target);
        }
        long timeoutBudget = timeoutNanos(readTimeout);
        long started = System.nanoTime();
        URI current = target;
        Set<URI> visited = new HashSet<>();
        visited.add(current.normalize());

        for (int redirects = 0; ; redirects++) {
            long remaining = remainingTimeoutNanos(timeoutBudget, started, target);
            HttpRequest.Builder request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofNanos(remaining))
                    .header("Accept-Encoding", "identity")
                    .GET();
            if (range != null) {
                long end = Math.addExact(range.getOffset(), range.getLength() - 1);
                request.header("Range", "bytes=" + range.getOffset() + '-' + end);
            }

            BodyCancellation bodyCancellation = new BodyCancellation();
            CompletableFuture<HttpResponse<byte[]>> transfer =
                    JNetClient.getInstance().getHttpClient()
                            .sendAsync(request.build(), boundedBodyHandler(
                                    range, maxBytes, resourceKind, current, bodyCancellation));
            HttpResponse<byte[]> response;
            try {
                response = transfer.get(remaining, TimeUnit.NANOSECONDS);
            } catch (InterruptedException error) {
                bodyCancellation.cancel();
                transfer.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException("HLS request interrupted", error);
            } catch (TimeoutException error) {
                bodyCancellation.cancel();
                transfer.cancel(true);
                throw new HttpTimeoutException("HLS request timed out: " + target);
            } catch (ExecutionException error) {
                throw requestFailure(current, error.getCause());
            }

            int status = response.statusCode();
            String location = response.headers().firstValue("Location").orElse(null);
            if (isRedirect(status) && location != null) {
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("HLS redirect limit exceeded: " + target);
                }
                URI next = resolveRedirect(current, location);
                if (isHttpsDowngrade(current, next)) {
                    throw new IOException("Refusing HLS redirect from HTTPS to HTTP: " + next);
                }
                if (!visited.add(next.normalize())) {
                    throw new IOException("HLS redirect loop detected: " + next);
                }
                current = next;
                continue;
            }

            URI responseUri = validateResponseUri(response.uri());
            byte[] content = response.body();
            if (range == null) {
                if (status < 200 || status >= 300) {
                    throw new IOException("HLS request failed with HTTP " + status + ": " + current);
                }
                return new FetchedBytes(responseUri, content);
            }
            if (status == 206) {
                validateContentRange(response, range, current);
                if (content.length != range.getLength()) {
                    throw new IOException("HLS byte-range response length mismatch: " + current);
                }
                return new FetchedBytes(responseUri, content);
            }
            if (status == 200) {
                rejectKnownInsufficientFullResponse(response, range, current);
                return new FetchedBytes(responseUri, sliceFullResponse(content, range, current));
            }
            throw new IOException("HLS byte-range request failed with HTTP " + status + ": " + current);
        }
    }

    private static long remainingTimeoutNanos(long budget, long started, URI target)
            throws HttpTimeoutException {
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long elapsed = Math.max(0L, System.nanoTime() - started);
        if (elapsed >= budget) {
            throw new HttpTimeoutException("HLS request timed out: " + target);
        }
        return budget - elapsed;
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static URI resolveRedirect(URI current, String location) throws IOException {
        try {
            URI resolved = current.resolve(URI.create(location.trim()));
            if (resolved.getRawFragment() != null) {
                String value = resolved.toASCIIString();
                resolved = URI.create(value.substring(0, value.indexOf('#')));
            }
            return validateHttpUri(resolved.toString(), "HLS redirect URI");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid HLS redirect location: " + location, error);
        }
    }

    private static boolean isHttpsDowngrade(URI current, URI next) {
        return "https".equalsIgnoreCase(current.getScheme())
                && !"https".equalsIgnoreCase(next.getScheme());
    }

    private static HttpResponse.BodyHandler<byte[]> boundedBodyHandler(
            M3U8Parser.HlsByteRange range, long maxBytes, String resourceKind, URI target,
            BodyCancellation bodyCancellation) {
        return responseInfo -> {
            long responseLimit = range != null && responseInfo.statusCode() == 206
                    ? range.getLength() : maxBytes;
            IOException tooLarge = responseTooLarge(resourceKind, responseLimit, target);
            long declaredLength = responseInfo.headers()
                    .firstValueAsLong("Content-Length")
                    .orElse(-1);
            if (declaredLength > responseLimit) {
                return new FailedBodySubscriber(tooLarge);
            }
            return new BoundedByteArraySubscriber(responseLimit, tooLarge, bodyCancellation);
        };
    }

    private static IOException responseTooLarge(String resourceKind, long maxBytes, URI target) {
        return new IOException("HLS " + resourceKind + " response exceeds maximum of "
                + maxBytes + " bytes: " + target);
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static IOException requestFailure(URI target, Throwable failure) {
        Throwable cause = failure;
        while (cause instanceof CompletionException && cause.getCause() != null) {
            cause = cause.getCause();
        }
        if (cause instanceof IOException) {
            return (IOException) cause;
        }
        return new IOException("HLS request failed: " + target, cause);
    }

    private static final class BoundedByteArraySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final HttpResponse.BodySubscriber<byte[]> delegate =
                HttpResponse.BodySubscribers.ofByteArray();
        private final long maxBytes;
        private final IOException tooLarge;
        private final BodyCancellation bodyCancellation;
        private Flow.Subscription subscription;
        private long receivedBytes;
        private boolean done;

        private BoundedByteArraySubscriber(long maxBytes, IOException tooLarge,
                BodyCancellation bodyCancellation) {
            this.maxBytes = maxBytes;
            this.tooLarge = tooLarge;
            this.bodyCancellation = bodyCancellation;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            Objects.requireNonNull(subscription, "subscription");
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            bodyCancellation.attach(subscription);
            delegate.onSubscribe(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            if (done) {
                return;
            }
            long batchBytes = 0;
            for (ByteBuffer buffer : buffers) {
                batchBytes += buffer.remaining();
            }
            if (batchBytes > maxBytes - receivedBytes) {
                done = true;
                if (subscription != null) {
                    subscription.cancel();
                }
                bodyCancellation.clear(subscription);
                delegate.onError(tooLarge);
                return;
            }
            receivedBytes += batchBytes;
            delegate.onNext(buffers);
        }

        @Override
        public void onError(Throwable throwable) {
            if (!done) {
                done = true;
                bodyCancellation.clear(subscription);
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                bodyCancellation.clear(subscription);
                delegate.onComplete();
            }
        }
    }

    private static final class FailedBodySubscriber
            implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> body = new CompletableFuture<>();
        private final IOException failure;

        private FailedBodySubscriber(IOException error) {
            this.failure = error;
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.cancel();
            body.completeExceptionally(failure);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            // The response was rejected from its Content-Length header.
        }

        @Override
        public void onError(Throwable throwable) {
            body.completeExceptionally(throwable);
        }

        @Override
        public void onComplete() {
            if (!body.isDone()) {
                body.completeExceptionally(failure);
            }
        }
    }

    private static final class BodyCancellation {
        private Flow.Subscription subscription;
        private boolean canceled;

        private void attach(Flow.Subscription subscription) {
            boolean cancel;
            synchronized (this) {
                cancel = canceled;
                if (!cancel) {
                    this.subscription = subscription;
                }
            }
            if (cancel) {
                subscription.cancel();
            }
        }

        private void clear(Flow.Subscription expected) {
            synchronized (this) {
                if (subscription == expected) {
                    subscription = null;
                }
            }
        }

        private void cancel() {
            Flow.Subscription current;
            synchronized (this) {
                canceled = true;
                current = subscription;
                subscription = null;
            }
            if (current != null) {
                current.cancel();
            }
        }
    }

    private static void rejectKnownInsufficientFullResponse(HttpResponse<?> response,
            M3U8Parser.HlsByteRange range, URI target) throws IOException {
        long declaredLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
        long requiredLength;
        try {
            requiredLength = Math.addExact(range.getOffset(), range.getLength());
        } catch (ArithmeticException error) {
            throw new IOException("Invalid HLS byte range: " + target, error);
        }
        if (declaredLength >= 0 && declaredLength < requiredLength) {
            throw new IOException("Server ignored Range and returned insufficient data: " + target);
        }
    }

    private static URI validateResponseUri(URI uri) throws IOException {
        try {
            return validateHttpUri(uri == null ? null : uri.toString(), "HLS response URI");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid HLS response URI", error);
        }
    }

    private static void validateContentRange(HttpResponse<?> response,
            M3U8Parser.HlsByteRange range, URI target) throws IOException {
        String value = response.headers().firstValue("Content-Range")
                .orElseThrow(() -> new IOException("Missing Content-Range for HLS byte range: " + target))
                .trim();
        if (!value.regionMatches(true, 0, "bytes ", 0, 6)) {
            throw new IOException("Invalid Content-Range for HLS byte range: " + target);
        }
        int dash = value.indexOf('-', 6);
        int slash = value.indexOf('/', dash + 1);
        if (dash < 0 || slash < 0 || slash == value.length() - 1) {
            throw new IOException("Invalid Content-Range for HLS byte range: " + target);
        }
        try {
            long start = Long.parseLong(value.substring(6, dash));
            long end = Long.parseLong(value.substring(dash + 1, slash));
            long expectedEnd = Math.addExact(range.getOffset(), range.getLength() - 1);
            if (start != range.getOffset() || end != expectedEnd) {
                throw new IOException("Content-Range does not match the requested HLS byte range: " + target);
            }
        } catch (NumberFormatException | ArithmeticException error) {
            throw new IOException("Invalid Content-Range for HLS byte range: " + target, error);
        }
    }

    private static byte[] sliceFullResponse(byte[] body, M3U8Parser.HlsByteRange range, URI target)
            throws IOException {
        if (range.getOffset() == 0 && body.length == range.getLength()) {
            return body;
        }
        long endExclusive = Math.addExact(range.getOffset(), range.getLength());
        if (endExclusive > body.length || range.getOffset() > Integer.MAX_VALUE) {
            throw new IOException("Server ignored Range and returned insufficient data: " + target);
        }
        return Arrays.copyOfRange(body, (int) range.getOffset(), (int) endExclusive);
    }

    private static URI resolveHttpUri(URI baseUri, String reference) throws IOException {
        if (reference == null || reference.isEmpty() || reference.indexOf('\r') >= 0
                || reference.indexOf('\n') >= 0) {
            throw new IOException("Invalid HLS resource URI");
        }
        try {
            return validateHttpUri(baseUri.resolve(reference).toString(), "HLS resource URI");
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid HLS resource URI: " + reference, error);
        }
    }

    private static Path prepareDirectory(String outputDir) throws IOException {
        if (outputDir == null || outputDir.trim().isEmpty()) {
            throw new IllegalArgumentException("outputDir must not be empty");
        }
        Path directory = Paths.get(outputDir).toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (!Files.isDirectory(directory)) {
            throw new IOException("HLS output path is not a directory: " + directory);
        }
        return directory;
    }

    private static String safeBasename(URI uri, int index, String fallbackExtension) {
        String path = uri.getPath();
        String candidate = path == null ? "" : path.substring(path.lastIndexOf('/') + 1);
        StringBuilder sanitized = new StringBuilder(Math.min(candidate.length(), 180));
        for (int cursor = 0; cursor < candidate.length() && sanitized.length() < 180; cursor++) {
            char value = candidate.charAt(cursor);
            sanitized.append(isSafeFilenameCharacter(value) ? value : '_');
        }
        String filename = sanitized.toString();
        if (filename.isEmpty() || ".".equals(filename) || "..".equals(filename)
                || isWindowsReservedName(filename) || !hasFileExtension(filename)) {
            filename = String.format(Locale.ROOT, "segment_%05d.%s", index, fallbackExtension);
        }
        return filename;
    }

    private static boolean isSafeFilenameCharacter(char value) {
        return value >= 'a' && value <= 'z'
                || value >= 'A' && value <= 'Z'
                || value >= '0' && value <= '9'
                || value == '.' || value == '_' || value == '-';
    }

    private static boolean hasFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 && dot < filename.length() - 1;
    }

    private static boolean isWindowsReservedName(String filename) {
        int dot = filename.indexOf('.');
        String base = (dot < 0 ? filename : filename.substring(0, dot)).toUpperCase(Locale.ROOT);
        if ("CON".equals(base) || "PRN".equals(base) || "AUX".equals(base) || "NUL".equals(base)) {
            return true;
        }
        return base.length() == 4
                && (base.startsWith("COM") || base.startsWith("LPT"))
                && base.charAt(3) >= '1' && base.charAt(3) <= '9';
    }

    private static String uniqueName(String filename, Set<String> usedNames, int index) {
        if (usedNames.add(filename)) {
            return filename;
        }
        int dot = filename.lastIndexOf('.');
        String stem = dot > 0 ? filename.substring(0, dot) : filename;
        String extension = dot > 0 ? filename.substring(dot) : "";
        int attempt = 0;
        String unique;
        do {
            unique = String.format(Locale.ROOT, "%s_%05d_%d%s", stem, index, attempt++, extension);
        } while (!usedNames.add(unique));
        return unique;
    }

    private static String fallbackExtension(String mediaType) {
        if (mediaType == null || mediaType.trim().isEmpty()) {
            return "ts";
        }
        String value = mediaType.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0) {
            value = value.substring(slash + 1);
        }
        if (value.startsWith(".")) {
            value = value.substring(1);
        }
        if (value.length() > 12 || value.isEmpty()) {
            return "ts";
        }
        for (int index = 0; index < value.length(); index++) {
            if (!Character.isLetterOrDigit(value.charAt(index))) {
                return "ts";
            }
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private static void writeAtomically(Path directory, String filename, byte[] data) throws IOException {
        Path target = directory.resolve(filename).normalize();
        if (!directory.equals(target.getParent())) {
            throw new IOException("Unsafe HLS output filename: " + filename);
        }
        Path temporary = Files.createTempFile(directory, ".jnet-hls-", ".part");
        try {
            Files.write(temporary, data);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static URI validateHttpUri(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        URI uri = URI.create(value);
        if (uri.getHost() == null || !("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()))) {
            throw new IllegalArgumentException(name + " must use HTTP or HTTPS and include a host");
        }
        if (uri.getRawUserInfo() != null || uri.getRawFragment() != null || uri.getPort() > 65_535) {
            throw new IllegalArgumentException(name + " is not a safe HTTP URI");
        }
        return uri;
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private static final class FetchedBytes {
        private final URI uri;
        private final byte[] content;

        private FetchedBytes(URI uri, byte[] content) {
            this.uri = uri;
            this.content = content;
        }
    }

    private static final class LoadedPlaylist {
        private final URI uri;
        private final byte[] content;
        private final M3U8Parser.HlsMediaPlaylist playlist;

        private LoadedPlaylist(URI uri, byte[] content, M3U8Parser.HlsMediaPlaylist playlist) {
            this.uri = uri;
            this.content = content;
            this.playlist = playlist;
        }
    }

    public static class Builder {
        private String url;
        private Duration readTimeout = Duration.ofSeconds(30);

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder readTimeout(Duration timeout) {
            this.readTimeout = timeout;
            return this;
        }

        /**
         * Retained for source compatibility. This snapshot client does not poll
         * live playlists; callers must schedule refreshes themselves.
         *
         * @deprecated No live-refresh operation is exposed by this client.
         */
        @Deprecated
        public Builder refreshInterval(int seconds) {
            if (seconds <= 0) {
                throw new IllegalArgumentException("refreshInterval must be positive");
            }
            return this;
        }

        public HlsClient build() {
            return new HlsClient(this);
        }
    }

    public interface ProgressListener {
        void onUpdate(int segment, int total, long bytes);

        void onComplete();
    }
}
