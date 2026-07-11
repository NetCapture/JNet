package com.jnet.download;

import com.jnet.core.JNetClient;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

/**
 * 文件下载工具类
 * 支持流式下载和进度回调
 */
public class Download {
    private static final long DEFAULT_MAX_IN_MEMORY_BYTES = 64L * 1024 * 1024;
    private static final int MAX_REDIRECTS = 10;

    /** @deprecated Use the static download methods directly. */
    @Deprecated
    public Download() {
    }

    /**
     * 下载到文件
     */
    public static void toFile(String url, File destination, ProgressListener listener) throws IOException, InterruptedException {
        DownloadPaths paths = prepareDownload(destination);
        try {
            HttpResponse<Path> response = sendFollowingRedirects(
                    request(url), fileBodyHandler(paths.temporary, listener, null));
            publishDownload(response, paths, listener);
        } finally {
            Files.deleteIfExists(paths.temporary);
        }
    }

    /**
     * 下载到字节数组
     */
    public static byte[] toBytes(String url, ProgressListener listener) throws IOException, InterruptedException {
        return toBytes(url, listener, DEFAULT_MAX_IN_MEMORY_BYTES);
    }

    /**
     * 下载到字节数组，并限制最大响应体大小。大文件应使用 {@link #toFile}。
     */
    public static byte[] toBytes(String url, ProgressListener listener, long maxBytes)
            throws IOException, InterruptedException {
        if (maxBytes < 0) {
            throw new IllegalArgumentException("Maximum byte count cannot be negative");
        }
        HttpResponse<byte[]> response = sendFollowingRedirects(
                request(url), byteArrayBodyHandler(listener, maxBytes));
        checkResponse(response);
        byte[] body = response.body();
        if (listener != null) {
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            listener.update(body.length, contentLength, true);
        }
        return body;
    }

    /**
     * 异步下载到文件
     */
    public static CompletableFuture<Void> toFileAsync(String url, File destination, ProgressListener listener) {
        final DownloadPaths paths;
        try {
            paths = prepareDownload(destination);
        } catch (Exception e) {
            return failedFuture(e);
        }

        BodyCancellation bodyCancellation = new BodyCancellation();
        final CompletableFuture<HttpResponse<Path>> transport;
        try {
            transport = sendAsyncFollowingRedirects(
                    request(url), fileBodyHandler(paths.temporary, listener, bodyCancellation));
        } catch (Exception e) {
            deleteQuietly(paths.temporary);
            return failedFuture(e);
        }

        DownloadFuture result = new DownloadFuture(transport, bodyCancellation);
        transport.whenComplete((response, error) -> {
            if (error != null) {
                completeDownloadFailure(result, paths.temporary, unwrap(error));
                return;
            }
            if (!result.beginPublishing()) {
                deleteQuietly(paths.temporary);
                return;
            }
            try {
                publishDownload(response, paths, listener);
                result.complete(null);
            } catch (Throwable failure) {
                completeDownloadFailure(result, paths.temporary, failure);
            }
        });
        return result;
    }

    private static HttpClient client() {
        return JNetClient.getInstance().getHttpClient();
    }

    private static <T> HttpResponse<T> sendFollowingRedirects(
            HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler)
            throws IOException, InterruptedException {
        CompletableFuture<HttpResponse<T>> transfer = sendAsyncFollowingRedirects(request, bodyHandler);
        try {
            return transfer.get();
        } catch (ExecutionException error) {
            Throwable cause = unwrap(error);
            if (cause instanceof IOException) {
                throw (IOException) cause;
            }
            throw new IOException("Download request failed", cause);
        } catch (CancellationException error) {
            throw new IOException("Download request canceled", error);
        } catch (InterruptedException error) {
            transfer.cancel(true);
            throw error;
        }
    }

    private static <T> CompletableFuture<HttpResponse<T>> sendAsyncFollowingRedirects(
            HttpRequest request, HttpResponse.BodyHandler<T> bodyHandler) {
        RedirectFuture<HttpResponse<T>> result = new RedirectFuture<>();
        Set<URI> visited = new HashSet<>();
        visited.add(request.uri().normalize());
        long timeoutNanos = request.timeout().map(Download::timeoutNanos).orElse(Long.MAX_VALUE);
        sendRedirectHop(request, request.uri(), bodyHandler, 0, visited,
                timeoutNanos, System.nanoTime(), result);
        return result;
    }

    private static <T> void sendRedirectHop(HttpRequest template, URI target,
            HttpResponse.BodyHandler<T> bodyHandler, int redirects, Set<URI> visited,
            long timeoutBudget, long started, RedirectFuture<HttpResponse<T>> result) {
        if (result.isDone()) {
            return;
        }

        final HttpRequest hop;
        final CompletableFuture<HttpResponse<T>> transfer;
        try {
            long remaining = remainingTimeoutNanos(timeoutBudget, started, target);
            hop = copyGetRequest(template, target, remaining);
            transfer = client().sendAsync(hop, bodyHandler);
        } catch (Exception error) {
            result.completeExceptionally(error);
            return;
        }

        result.attach(transfer);
        transfer.whenComplete((response, error) -> {
            result.clear(transfer);
            if (result.isDone()) {
                return;
            }
            if (error != null) {
                result.completeExceptionally(unwrap(error));
                return;
            }
            try {
                int status = response.statusCode();
                String location = response.headers().firstValue("Location").orElse(null);
                if (!isRedirect(status) || location == null) {
                    result.complete(response);
                    return;
                }
                if (redirects >= MAX_REDIRECTS) {
                    throw new IOException("Download redirect limit exceeded");
                }
                URI next = resolveRedirect(target, location);
                if (isHttpsDowngrade(target, next)) {
                    throw new IOException("Refusing download redirect from HTTPS to HTTP");
                }
                if (!visited.add(next.normalize())) {
                    throw new IOException("Download redirect loop detected");
                }
                sendRedirectHop(template, next, bodyHandler,
                        redirects + 1, visited, timeoutBudget, started, result);
            } catch (Exception redirectFailure) {
                result.completeExceptionally(redirectFailure);
            }
        });
    }

    private static HttpRequest copyGetRequest(HttpRequest source, URI target, long timeoutNanos) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(target).GET();
        if (timeoutNanos > 0) {
            builder.timeout(Duration.ofNanos(timeoutNanos));
        }
        for (Map.Entry<String, List<String>> header : source.headers().map().entrySet()) {
            for (String value : header.getValue()) {
                builder.header(header.getKey(), value);
            }
        }
        return builder.build();
    }

    private static long remainingTimeoutNanos(long budget, long started, URI target)
            throws HttpTimeoutException {
        if (budget == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        long elapsed = Math.max(0L, System.nanoTime() - started);
        if (elapsed >= budget) {
            throw new HttpTimeoutException("Download request timed out: " + target);
        }
        return budget - elapsed;
    }

    private static long timeoutNanos(Duration timeout) {
        try {
            return Math.max(1L, timeout.toNanos());
        } catch (ArithmeticException ignored) {
            return Long.MAX_VALUE;
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static URI resolveRedirect(URI current, String location) throws IOException {
        try {
            URI target = current.resolve(URI.create(location.trim()));
            if (target.getRawFragment() != null) {
                String value = target.toASCIIString();
                target = URI.create(value.substring(0, value.indexOf('#')));
            }
            String scheme = target.getScheme();
            if (target.getHost() == null || !("http".equalsIgnoreCase(scheme)
                    || "https".equalsIgnoreCase(scheme)) || target.getRawUserInfo() != null) {
                throw new IllegalArgumentException("redirect target must be a safe HTTP URI");
            }
            return target;
        } catch (IllegalArgumentException error) {
            throw new IOException("Invalid download redirect location: " + location, error);
        }
    }

    private static boolean isHttpsDowngrade(URI current, URI target) {
        return "https".equalsIgnoreCase(current.getScheme())
                && !"https".equalsIgnoreCase(target.getScheme());
    }

    private static HttpRequest request(String url) {
        JNetClient jnetClient = JNetClient.getInstance();
        HttpRequest.Builder request = HttpRequest.newBuilder().uri(URI.create(url)).GET();
        if (jnetClient.getReadTimeout() > 0) {
            request.timeout(Duration.ofMillis(jnetClient.getReadTimeout()));
        }
        return request.build();
    }

    private static HttpResponse.BodyHandler<Path> fileBodyHandler(
            Path temporary, ProgressListener listener, BodyCancellation bodyCancellation) {
        return responseInfo -> {
            if (!isSuccessful(responseInfo.statusCode())) {
                return HttpResponse.BodySubscribers.replacing(temporary);
            }
            long contentLength = responseInfo.headers()
                    .firstValueAsLong("Content-Length").orElse(-1);
            HttpResponse.BodySubscriber<Path> delegate = HttpResponse.BodySubscribers.ofFile(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            return new TrackingBodySubscriber<>(
                    delegate, listener, contentLength, Long.MAX_VALUE, bodyCancellation);
        };
    }

    private static HttpResponse.BodyHandler<byte[]> byteArrayBodyHandler(
            ProgressListener listener, long maxBytes) {
        return responseInfo -> {
            if (!isSuccessful(responseInfo.statusCode())) {
                return HttpResponse.BodySubscribers.replacing(new byte[0]);
            }
            long contentLength = responseInfo.headers()
                    .firstValueAsLong("Content-Length").orElse(-1);
            if (contentLength > maxBytes) {
                return new FailingBodySubscriber<>(new IOException(
                        "Download exceeds maximum in-memory size of " + maxBytes + " bytes"));
            }
            return new TrackingBodySubscriber<>(
                    HttpResponse.BodySubscribers.ofByteArray(),
                    listener,
                    contentLength,
                    maxBytes);
        };
    }

    private static Path prepareTarget(File destination) throws IOException {
        if (destination == null) {
            throw new IllegalArgumentException("Destination cannot be null");
        }
        Path target = destination.toPath().toAbsolutePath();
        if (target.getFileName() == null || Files.isDirectory(target)) {
            throw new IllegalArgumentException("Destination must be a file path");
        }
        Path parent = target.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        return target;
    }

    private static DownloadPaths prepareDownload(File destination) throws IOException {
        Path target = prepareTarget(destination);
        Path parent = target.getParent();
        String filename = target.getFileName().toString();
        String prefix = filename.length() >= 3 ? filename : (filename + "___").substring(0, 3);
        return new DownloadPaths(target, Files.createTempFile(parent, prefix, ".part"));
    }

    private static void moveIntoPlace(Path temporary, Path target) throws IOException {
        try {
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void checkResponse(HttpResponse<?> response) throws IOException {
        int code = response.statusCode();
        if (!isSuccessful(code)) {
            throw new IOException("Download failed with HTTP " + code);
        }
    }

    private static boolean isSuccessful(int code) {
        return code >= 200 && code < 300;
    }

    private static void publishDownload(
            HttpResponse<Path> response, DownloadPaths paths, ProgressListener listener) throws IOException {
        checkResponse(response);
        moveIntoPlace(paths.temporary, paths.target);
        if (listener != null) {
            long contentLength = response.headers().firstValueAsLong("Content-Length").orElse(-1);
            listener.update(Files.size(paths.target), contentLength, true);
        }
    }

    private static CompletableFuture<Void> failedFuture(Throwable failure) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        result.completeExceptionally(failure);
        return result;
    }

    private static void completeDownloadFailure(
            CompletableFuture<Void> result, Path temporary, Throwable failure) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        result.completeExceptionally(failure);
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while ((current instanceof CompletionException || current instanceof ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Cancellation cleanup is best effort; transport completion closes the file first.
        }
    }

    private static final class DownloadPaths {
        private final Path target;
        private final Path temporary;

        private DownloadPaths(Path target, Path temporary) {
            this.target = target;
            this.temporary = temporary;
        }
    }

    private static final class RedirectFuture<T> extends CompletableFuture<T> {
        private CompletableFuture<?> current;
        private boolean interrupt;

        private synchronized void attach(CompletableFuture<?> future) {
            if (isCancelled()) {
                future.cancel(interrupt);
            } else if (!isDone()) {
                current = future;
            }
        }

        private synchronized void clear(CompletableFuture<?> future) {
            if (current == future) {
                current = null;
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            CompletableFuture<?> active;
            synchronized (this) {
                if (!super.cancel(mayInterruptIfRunning)) {
                    return false;
                }
                interrupt = mayInterruptIfRunning;
                active = current;
                current = null;
            }
            if (active != null) {
                active.cancel(mayInterruptIfRunning);
            }
            return true;
        }
    }

    private static final class DownloadFuture extends CompletableFuture<Void> {
        private final CompletableFuture<?> transport;
        private final BodyCancellation bodyCancellation;
        private boolean publishing;

        private DownloadFuture(
                CompletableFuture<?> transport, BodyCancellation bodyCancellation) {
            this.transport = transport;
            this.bodyCancellation = bodyCancellation;
        }

        private synchronized boolean beginPublishing() {
            if (isDone()) {
                return false;
            }
            publishing = true;
            return true;
        }

        @Override
        public synchronized boolean cancel(boolean mayInterruptIfRunning) {
            if (publishing) {
                return false;
            }
            boolean canceled = super.cancel(mayInterruptIfRunning);
            if (canceled) {
                bodyCancellation.cancel();
                transport.cancel(mayInterruptIfRunning);
            }
            return canceled;
        }
    }

    private static final class TrackingBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
        private final HttpResponse.BodySubscriber<T> delegate;
        private final ProgressListener listener;
        private final long contentLength;
        private final long maxBytes;
        private final BodyCancellation bodyCancellation;
        private Flow.Subscription subscription;
        private long receivedBytes;
        private boolean done;

        private TrackingBodySubscriber(
                HttpResponse.BodySubscriber<T> delegate,
                ProgressListener listener,
                long contentLength,
                long maxBytes) {
            this(delegate, listener, contentLength, maxBytes, null);
        }

        private TrackingBodySubscriber(
                HttpResponse.BodySubscriber<T> delegate,
                ProgressListener listener,
                long contentLength,
                long maxBytes,
                BodyCancellation bodyCancellation) {
            this.delegate = delegate;
            this.listener = listener;
            this.contentLength = contentLength;
            this.maxBytes = maxBytes;
            this.bodyCancellation = bodyCancellation;
        }

        @Override
        public CompletionStage<T> getBody() {
            return delegate.getBody();
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            if (this.subscription != null) {
                subscription.cancel();
                return;
            }
            this.subscription = subscription;
            if (bodyCancellation != null) {
                bodyCancellation.attach(subscription);
            }
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
                fail(new IOException(
                        "Download exceeds maximum in-memory size of " + maxBytes + " bytes"));
                return;
            }
            delegate.onNext(buffers);
            receivedBytes += batchBytes;
            if (listener != null) {
                try {
                    listener.update(receivedBytes, contentLength, false);
                } catch (RuntimeException listenerFailure) {
                    fail(listenerFailure);
                }
            }
        }

        @Override
        public void onError(Throwable throwable) {
            if (!done) {
                done = true;
                clearBodyCancellation();
                delegate.onError(throwable);
            }
        }

        @Override
        public void onComplete() {
            if (!done) {
                done = true;
                clearBodyCancellation();
                delegate.onComplete();
            }
        }

        private void fail(Throwable failure) {
            if (done) {
                return;
            }
            done = true;
            if (subscription != null) {
                subscription.cancel();
            }
            clearBodyCancellation();
            delegate.onError(failure);
        }

        private void clearBodyCancellation() {
            if (bodyCancellation != null) {
                bodyCancellation.clear(subscription);
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

    private static final class FailingBodySubscriber<T> implements HttpResponse.BodySubscriber<T> {
        private final CompletableFuture<T> body = new CompletableFuture<>();
        private final Throwable failure;

        private FailingBodySubscriber(Throwable failure) {
            this.failure = failure;
        }

        @Override
        public CompletionStage<T> getBody() {
            return body;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscription.cancel();
            body.completeExceptionally(failure);
        }

        @Override
        public void onNext(List<ByteBuffer> buffers) {
            // Subscription is canceled immediately.
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
}
