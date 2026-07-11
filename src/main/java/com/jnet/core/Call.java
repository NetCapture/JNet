package com.jnet.core;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * 请求执行接口 - 负责实际的网络请求
 * 线程安全，支持同步和异步执行
 * 支持拦截器
 * 
 * <p>
 * 基于JDK 11 HttpClient实现
 * </p>
 *
 * @author sanbo
 * @version 3.0.0
 */
public interface Call {
    /**
     * 获取关联的请求
     */
    Request request();

    /**
     * 同步执行请求
     *
     * @return Response
     * @throws IOException 网络异常
     */
    Response execute() throws IOException;

    /**
     * 异步执行请求
     *
     * @param callback 回调接口
     */
    void enqueue(Callback callback);

    /**
     * 取消请求
     */
    void cancel();

    /**
     * 判断是否已执行
     */
    boolean isExecuted();

    /**
     * 判断是否已取消
     */
    boolean isCanceled();

    /**
     * Call实现类
     */
    class RealCall implements Call {
        private final Request request;
        private final JNetClient client;
        private final List<Interceptor> interceptors;
        private volatile boolean executed;
        private volatile boolean canceled;
        // Active async operation; cleared before callback delivery.
        private volatile CompletableFuture<?> pendingFuture;

        public RealCall(Request request, JNetClient client) {
            this(request, client, null);
        }

        public RealCall(Request request, JNetClient client, List<Interceptor> interceptors) {
            this.request = Objects.requireNonNull(request, "request");
            this.client = Objects.requireNonNull(client, "client");
            if (interceptors == null || interceptors.isEmpty()) {
                this.interceptors = Collections.emptyList();
            } else {
                List<Interceptor> copy = new ArrayList<>(interceptors.size());
                for (Interceptor interceptor : interceptors) {
                    if (interceptor != null) {
                        copy.add(interceptor);
                    }
                }
                this.interceptors = Collections.unmodifiableList(copy);
            }
        }

        /**
         * @deprecated Connection reuse is managed by the JDK HTTP client.
         */
        @Deprecated
        public RealCall(Request request, JNetClient client, List<Interceptor> interceptors,
                ConnectionPool connectionPool) {
            this(request, client, interceptors);
        }

        @Override
        public Request request() {
            return request;
        }

        @Override
        public Response execute() throws IOException {
            synchronized (this) {
                if (executed) {
                    throw new IllegalStateException("Call already executed");
                }
                executed = true;
            }

            if (canceled) {
                throw new IOException("Request canceled");
            }

            try {
                return executeWithInterceptors();
            } catch (IOException e) {
                throw enhanceException(e);
            } catch (Exception e) {
                throw enhanceException(new IOException(e));
            }
        }

        @Override
        public void enqueue(Callback callback) {
            Objects.requireNonNull(callback, "callback");
            synchronized (this) {
                if (executed) {
                    throw new IllegalStateException("Call already executed");
                }
                executed = true;
            }

            if (canceled) {
                callback.onFailure(new IOException("Request canceled"));
                return;
            }

            try {
                if (!interceptors.isEmpty()) {
                    CompletableFuture<Response> future = AsyncExecutor.submit(
                            this::executeWithInterceptors);
                    registerPendingFuture(future, callback);
                } else {
                    CompletableFuture<Response> future = executeInternalAsync(request);
                    registerPendingFuture(future, callback);
                }
            } catch (Exception e) {
                callback.onFailure(enhanceException(e));
            }
        }

        @Override
        public void cancel() {
            CompletableFuture<?> future;
            synchronized (this) {
                canceled = true;
                future = pendingFuture;
            }
            if (future != null) {
                future.cancel(true);
            }
        }

        @Override
        public boolean isExecuted() {
            return executed;
        }

        @Override
        public boolean isCanceled() {
            return canceled;
        }

        private Response executeWithInterceptors() throws IOException {
            if (interceptors.isEmpty()) {
                return executeInternal();
            }

            Interceptor.Chain chain = new Interceptor.RealChain(interceptors, 0, request, this);
            return chain.proceed(request);
        }

        /**
         * 执行实际的网络请求（供拦截器链调用）
         */
        Response executeNetworkRequest(Request req) throws IOException {
            return executeInternalWithRequest(req);
        }

        private Response executeInternalWithRequest(Request req) throws IOException {
            if (canceled) {
                throw new IOException("Request canceled");
            }
            CompletableFuture<Response> networkFuture = executeInternalAsync(req);
            setPendingFuture(networkFuture);
            try {
                return networkFuture.get();
            } catch (InterruptedException e) {
                networkFuture.cancel(true);
                Thread.currentThread().interrupt();
                throw new IOException("Request interrupted", e);
            } catch (CancellationException e) {
                throw new IOException("Request canceled", e);
            } catch (java.util.concurrent.ExecutionException e) {
                Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                throw new IOException(cause);
            } finally {
                clearPendingFuture(networkFuture);
            }
        }

        private Response executeInternal() throws IOException {
            return executeInternalWithRequest(request);
        }

        private CompletableFuture<Response> executeInternalAsync(Request req) {
            RedirectFuture result = new RedirectFuture();
            Set<String> visited = new HashSet<>();
            visited.add(redirectKey(req));
            sendAsyncHop(req, req, 0, visited, System.nanoTime(), result);
            return result;
        }

        private void sendAsyncHop(Request original, Request current, int redirects,
                Set<String> visited, long startNanos, RedirectFuture result) {
            if (result.isDone()) {
                return;
            }
            if (canceled) {
                result.completeExceptionally(new IOException("Request canceled"));
                return;
            }

            final HttpRequest jdkRequest;
            final CompletableFuture<HttpResponse<String>> networkFuture;
            try {
                jdkRequest = buildJdkRequest(current, startNanos);
                networkFuture = client.getHttpClient().sendAsync(
                        jdkRequest, BoundedBodyHandlers.ofString(client.getMaxResponseBytes()));
            } catch (Throwable error) {
                result.completeExceptionally(enhanceException(error));
                return;
            }

            result.attach(networkFuture);
            networkFuture.whenComplete((httpResponse, throwable) -> {
                result.clear(networkFuture);
                if (result.isDone()) {
                    return;
                }
                if (throwable != null) {
                    result.completeExceptionally(enhanceException(throwable));
                    return;
                }
                if (canceled) {
                    result.completeExceptionally(new IOException("Request canceled"));
                    return;
                }

                try {
                    Request redirected = createRedirectRequest(current, httpResponse);
                    if (redirected == null) {
                        long duration = (System.nanoTime() - startNanos) / 1_000_000L;
                        result.complete(toJNetResponse(httpResponse, original, duration));
                        return;
                    }
                    if (redirects >= client.getMaxRedirects()) {
                        throw new IOException("Maximum redirect count exceeded");
                    }
                    if (!visited.add(redirectKey(redirected))) {
                        throw new IOException("Redirect loop detected");
                    }
                    sendAsyncHop(original, redirected, redirects + 1,
                            visited, startNanos, result);
                } catch (Throwable error) {
                    result.completeExceptionally(enhanceException(error));
                }
            });
        }

        private Request createRedirectRequest(Request current, HttpResponse<?> response) throws IOException {
            int status = response.statusCode();
            if (!client.isFollowRedirects() || !isRedirectStatus(status)) {
                return null;
            }
            String location = response.headers().firstValue("Location").orElse(null);
            if (location == null) {
                return null;
            }

            URI target;
            try {
                target = withoutFragment(current.getUri().resolve(URI.create(location.trim())));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid redirect location", e);
            }
            if (isHttpsDowngrade(current.getUri(), target)) {
                throw new IOException("Refusing redirect from HTTPS to HTTP");
            }

            boolean sameOrigin = sameOrigin(current.getUri(), target);
            String redirectedMethod = redirectedMethod(current.getMethod(), status);
            boolean preserveBody = !rewritesRequestBody(current.getMethod(), status);

            Request.Builder builder = Request.newBuilder()
                    .client(client)
                    .uri(target)
                    .method(redirectedMethod)
                    .tag(current.getTag());
            for (Map.Entry<String, String> header : current.getHeaders().entrySet()) {
                if (shouldDropRedirectHeader(header.getKey(), sameOrigin, preserveBody)) {
                    continue;
                }
                builder.header(header.getKey(), header.getValue());
            }

            HttpRequest.BodyPublisher publisher = current.getBodyPublisher();
            if (preserveBody && publisher != null) {
                if (current.getBody() != null) {
                    builder.body(current.getBody());
                } else if (publisher.contentLength() != 0L) {
                    throw new IOException("Redirect requires replaying an opaque request body");
                }
            }

            try {
                return builder.build();
            } catch (IllegalArgumentException | IllegalStateException e) {
                throw new IOException("Invalid redirect target", e);
            }
        }

        private HttpRequest buildJdkRequest(Request request, long startNanos) throws IOException {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(request.getUri());

            if (client.getReadTimeout() > 0) {
                long budgetNanos = Duration.ofMillis(client.getReadTimeout()).toNanos();
                long elapsedNanos = Math.max(0L, System.nanoTime() - startNanos);
                if (elapsedNanos >= budgetNanos) {
                    throw new HttpTimeoutException("Request timed out while following redirects");
                }
                builder.timeout(Duration.ofNanos(budgetNanos - elapsedNanos));
            }

            // Headers
            for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
                // Host is controlled by the transport. Forwarding a caller-supplied
                // value would be unsafe after an origin change and is rejected by JDK 11.
                if (headerEquals(entry.getKey(), "Host")) {
                    continue;
                }
                builder.header(entry.getKey(), entry.getValue());
            }

            // Method & Body
            HttpRequest.BodyPublisher bodyPublisher = request.getBodyPublisher() != null
                    ? request.getBodyPublisher()
                    : HttpRequest.BodyPublishers.noBody();

            builder.method(request.getMethod(), bodyPublisher);

            return builder.build();
        }

        private static boolean isRedirectStatus(int status) {
            return status == 301 || status == 302 || status == 303
                    || status == 307 || status == 308;
        }

        private static String redirectedMethod(String method, int status) {
            if (status == 303) {
                return "HEAD".equals(method) ? "HEAD" : "GET";
            }
            if ((status == 301 || status == 302) && "POST".equals(method)) {
                return "GET";
            }
            return method;
        }

        private static boolean rewritesRequestBody(String method, int status) {
            return ((status == 301 || status == 302) && "POST".equals(method))
                    || status == 303;
        }

        private static boolean shouldDropRedirectHeader(String name, boolean sameOrigin,
                boolean preserveBody) {
            if (!sameOrigin && (headerEquals(name, "Authorization")
                    || headerEquals(name, "Proxy-Authorization")
                    || headerEquals(name, "Cookie")
                    || headerEquals(name, "Cookie2")
                    || headerEquals(name, "Host"))) {
                return true;
            }
            return !preserveBody && (headerEquals(name, "Content-Length")
                    || headerEquals(name, "Content-Type")
                    || headerEquals(name, "Transfer-Encoding"));
        }

        private static boolean headerEquals(String actual, String expected) {
            return actual != null && actual.equalsIgnoreCase(expected);
        }

        private static boolean sameOrigin(URI first, URI second) {
            return equalsIgnoreCase(first.getScheme(), second.getScheme())
                    && equalsIgnoreCase(first.getHost(), second.getHost())
                    && effectivePort(first) == effectivePort(second);
        }

        private static boolean equalsIgnoreCase(String first, String second) {
            return first == null ? second == null : second != null && first.equalsIgnoreCase(second);
        }

        private static int effectivePort(URI uri) {
            if (uri.getPort() >= 0) {
                return uri.getPort();
            }
            if ("http".equalsIgnoreCase(uri.getScheme())) {
                return 80;
            }
            if ("https".equalsIgnoreCase(uri.getScheme())) {
                return 443;
            }
            return -1;
        }

        private static boolean isHttpsDowngrade(URI from, URI to) {
            return "https".equalsIgnoreCase(from.getScheme())
                    && !"https".equalsIgnoreCase(to.getScheme());
        }

        private static URI withoutFragment(URI uri) {
            if (uri.getRawFragment() == null) {
                return uri;
            }
            String value = uri.toASCIIString();
            return URI.create(value.substring(0, value.indexOf('#')));
        }

        private static String redirectKey(Request request) {
            return request.getMethod() + ' ' + withoutFragment(request.getUri()).normalize().toASCIIString();
        }

        private Response toJNetResponse(HttpResponse<String> httpResponse, Request request, long duration) {
            boolean isSuccess = httpResponse.statusCode() >= 200 && httpResponse.statusCode() < 300;
            Response.Builder builder = isSuccess ? Response.success(request) : Response.failure(request);

            builder.code(httpResponse.statusCode())
                    .body(httpResponse.body())
                    .duration(duration);

            for (Map.Entry<String, List<String>> entry : httpResponse.headers().map().entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    builder.headerValues(entry.getKey(), entry.getValue());
                }
            }

            return builder.build();
        }

        private void registerPendingFuture(CompletableFuture<Response> future, Callback callback) {
            synchronized (this) {
                if (pendingFuture == null) {
                    pendingFuture = future;
                }
            }
            future.whenComplete((response, throwable) -> {
                IOException failure = throwable == null ? null : enhanceException(throwable);

                clearPendingFuture(future);
                if (canceled || throwable instanceof CancellationException) {
                    failure = new IOException("Request canceled");
                }
                if (failure != null) {
                    callback.onFailure(failure);
                } else {
                    callback.onSuccess(response);
                }
            });
            if (canceled) {
                future.cancel(true);
            }
        }

        private void clearPendingFuture(CompletableFuture<?> completed) {
            synchronized (this) {
                if (pendingFuture == completed) {
                    pendingFuture = null;
                }
            }
        }

        private void setPendingFuture(CompletableFuture<?> future) {
            boolean cancelNow;
            synchronized (this) {
                pendingFuture = future;
                cancelNow = canceled;
            }
            if (cancelNow) {
                future.cancel(true);
            }
        }

        private IOException enhanceException(Throwable throwable) {
            Throwable cause = throwable;
            while ((cause instanceof java.util.concurrent.CompletionException
                    || cause instanceof java.util.concurrent.ExecutionException) && cause.getCause() != null) {
                cause = cause.getCause();
            }
            return cause instanceof IOException ? (IOException) cause : new IOException(cause);
        }

        private static final class RedirectFuture extends CompletableFuture<Response> {
            private CompletableFuture<?> current;
            private boolean interrupt;

            synchronized void attach(CompletableFuture<?> future) {
                if (isCancelled()) {
                    future.cancel(interrupt);
                } else if (!isDone()) {
                    current = future;
                }
            }

            synchronized void clear(CompletableFuture<?> future) {
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
    }

    /**
     * 回调接口
     */
    public interface Callback {
        /**
         * 请求成功
         */
        void onSuccess(Response response);

        /**
         * 请求失败
         */
        void onFailure(Exception e);
    }
}
