package com.jnet.rtsp;

import com.jnet.core.JNet;
import com.jnet.protocol.ProtocolAdapter;
import com.jnet.protocol.ProtocolRequest;
import com.jnet.protocol.ProtocolResponse;
import com.jnet.tcp.TcpClient;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Small stateful RTSP/1.0 control client.
 *
 * <p>One TCP or TLS connection is retained for a session. Control requests are
 * serialized, response bodies use {@code Content-Length}, and interleaved RTP
 * frames are skipped because media delivery is outside this client.</p>
 */
public final class RtspClient implements ProtocolAdapter, AutoCloseable {
    private final URI uri;
    private final String userAgent;
    private final Object ioLock = new Object();
    private final RtspTransport transport;
    private final RtspAuthenticator authenticator;

    private volatile String sessionId;
    private volatile int cseq;
    private volatile boolean connected;
    private volatile boolean closed;
    private volatile boolean streaming;
    private volatile SdpParser.SdpInfo description;
    private URI descriptionBase;
    private URI aggregateControlUri;

    private RtspClient(Builder builder) {
        uri = validateUri(builder.url);
        Duration timeout = requirePositive(builder.timeout, "timeout");
        userAgent = builder.userAgent == null || builder.userAgent.isEmpty()
                ? "JNet/3.5.2 (RTSP)"
                : requireSingleLine(builder.userAgent, "userAgent");
        transport = new RtspTransport(uri, timeoutMillis(timeout));
        authenticator = new RtspAuthenticator(builder.username, builder.password);
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /** Raw TCP compatibility entry point; it does not join the RTSP session. */
    @Override
    public ProtocolResponse execute(ProtocolRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }
        if (request.getTransport() != ProtocolRequest.TransportType.TCP) {
            throw new IOException("RTSP adapter only supports TCP transport");
        }

        long started = System.nanoTime();
        String response = JNet.tcp(request.getHost(), request.getPort(), request.getData());
        byte[] bytes = response == null ? new byte[0] : response.getBytes(StandardCharsets.UTF_8);
        return ProtocolResponse.success()
                .host(request.getHost(), request.getPort())
                .data(bytes)
                .bytesRead(bytes.length)
                .duration(elapsedMillis(started))
                .request(request)
                .build();
    }

    @Override
    public CompletableFuture<ProtocolResponse> executeAsync(ProtocolRequest request) {
        CompletableFuture<ProtocolResponse> result = new CompletableFuture<>();
        if (request == null) {
            result.completeExceptionally(new IllegalArgumentException("Request cannot be null"));
            return result;
        }
        if (request.getTransport() != ProtocolRequest.TransportType.TCP) {
            result.completeExceptionally(new IOException("RTSP adapter only supports TCP transport"));
            return result;
        }

        long started = System.nanoTime();
        CompletableFuture<String> network = TcpClient.sendAsync(
                request.getHost(), request.getPort(), request.getData());
        result.whenComplete((ignored, error) -> {
            if (result.isCancelled()) {
                network.cancel(true);
            }
        });
        network.whenComplete((response, error) -> {
            if (error != null) {
                result.completeExceptionally(error);
                return;
            }
            byte[] bytes = response == null
                    ? new byte[0]
                    : response.getBytes(StandardCharsets.UTF_8);
            result.complete(ProtocolResponse.success()
                    .host(request.getHost(), request.getPort())
                    .data(bytes)
                    .bytesRead(bytes.length)
                    .duration(elapsedMillis(started))
                    .request(request)
                    .build());
        });
        return result;
    }

    public void connect() throws IOException {
        connect(0);
    }

    /** Connects and validates a zero-based media stream when SDP contains media. */
    public void connect(int streamIndex) throws IOException {
        synchronized (ioLock) {
            if (streamIndex < 0) {
                throw new IllegalArgumentException("streamIndex must be >= 0");
            }
            checkOpen();
            if (connected && transport.isOpen()) {
                validateStreamIndex(streamIndex);
                return;
            }

            RtspResponse response = describe();
            if (!response.isOk()) {
                clearPresentation();
                closeTransport();
                throw responseFailure("DESCRIBE", response);
            }

            SdpParser.SdpInfo parsedDescription = parseDescription(response.getBody());
            URI parsedDescriptionBase = resolveDescriptionBase(response);
            description = parsedDescription;
            descriptionBase = parsedDescriptionBase;
            aggregateControlUri = resolveAggregateControlUri(parsedDescription, parsedDescriptionBase);
            validateStreamIndex(streamIndex);
            connected = true;
        }
    }

    public void setup(int streamIndex, String transportValue) throws IOException {
        synchronized (ioLock) {
            checkConnected();
            if (transportValue == null || transportValue.trim().isEmpty()) {
                throw new IllegalArgumentException("transport cannot be null or empty");
            }
            requireSingleLine(transportValue, "transport");

            RtspResponse response = executeRtsp(newRequest(RtspMethod.SETUP, resolveControlUrl(streamIndex))
                    .header("Transport", transportValue.trim())
                    .build());
            if (!response.isOk()) {
                throw responseFailure("SETUP", response);
            }
        }
    }

    public void play() throws IOException {
        play("npt=0.000-");
    }

    public void play(String range) throws IOException {
        synchronized (ioLock) {
            checkConnected();
            RtspResponse response = executeRtsp(newRequest(RtspMethod.PLAY, aggregateControlUrl())
                    .range(normalizeRange(range))
                    .build());
            if (!response.isOk()) {
                throw responseFailure("PLAY", response);
            }
            streaming = true;
        }
    }

    public void pause() throws IOException {
        synchronized (ioLock) {
            checkConnected();
            RtspResponse response = executeRtsp(newRequest(RtspMethod.PAUSE, aggregateControlUrl()).build());
            if (!response.isOk()) {
                throw responseFailure("PAUSE", response);
            }
            streaming = false;
        }
    }

    public void resume() throws IOException {
        play("npt=now-");
    }

    /** Ends the RTSP session while leaving this client reusable. */
    public void teardown() throws IOException {
        synchronized (ioLock) {
            checkConnected();
            IOException failure = null;
            try {
                RtspResponse response = executeRtsp(newRequest(RtspMethod.TEARDOWN, aggregateControlUrl()).build());
                if (!response.isOk()) {
                    failure = responseFailure("TEARDOWN", response);
                }
            } finally {
                resetSession();
                closeTransport();
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    public RtspResponse options() throws IOException {
        synchronized (ioLock) {
            checkOpen();
            return executeRtsp(newRequest(RtspMethod.OPTIONS, uri.toString()).build());
        }
    }

    public RtspResponse describe() throws IOException {
        synchronized (ioLock) {
            checkOpen();
            return executeRtsp(newRequest(RtspMethod.DESCRIBE, uri.toString())
                    .header("Accept", "application/sdp")
                    .build());
        }
    }

    public RtspResponse getParameter(String parameter) throws IOException {
        synchronized (ioLock) {
            checkConnected();
            return executeRtsp(newRequest(RtspMethod.GET_PARAMETER, uri.toString())
                    .header("Content-Type", "text/parameters")
                    .data(parameter)
                    .build());
        }
    }

    public RtspResponse setParameter(String parameter, String value) throws IOException {
        synchronized (ioLock) {
            checkConnected();
            if (parameter == null || parameter.isEmpty()) {
                throw new IllegalArgumentException("parameter cannot be null or empty");
            }
            return executeRtsp(newRequest(RtspMethod.SET_PARAMETER, uri.toString())
                    .header("Content-Type", "text/parameters")
                    .data(parameter + (value == null ? "" : ": " + value))
                    .build());
        }
    }

    public RtspResponse record() throws IOException {
        synchronized (ioLock) {
            checkConnected();
            return executeRtsp(newRequest(RtspMethod.RECORD, uri.toString()).build());
        }
    }

    public boolean isConnected() {
        return connected && transport.isOpen();
    }

    public boolean isStreaming() {
        return streaming;
    }

    public String getSessionId() {
        return sessionId;
    }

    public int getCseq() {
        return cseq;
    }

    public SdpParser.SdpInfo getDescription() {
        return description;
    }

    @Override
    public void close() {
        transport.shutdown();
        synchronized (ioLock) {
            if (closed) {
                return;
            }
            closed = true;
            resetSession();
        }
    }

    private RtspResponse executeRtsp(RtspRequest template) throws IOException {
        synchronized (ioLock) {
            checkOpen();
            try {
                RtspResponse response = transport.exchange(prepareRequest(template));
                if (response.getStatusCode() == 401
                        && authenticator.hasCredentials()
                        && authenticator.accept(response.getHeader("WWW-Authenticate"))) {
                    response = transport.exchange(prepareRequest(template));
                }
                updateSession(response);
                return response;
            } catch (IOException e) {
                clearPresentation();
                transport.close();
                throw e;
            }
        }
    }

    private RtspRequest prepareRequest(RtspRequest template) throws IOException {
        String authorization = authenticator.authorization(
                template.getAuthorization(), template.getMethod(), template.getUrl());
        return template.toBuilder()
                .authorization(authorization)
                .cseq(nextCseq())
                .build();
    }

    private RtspRequest.Builder newRequest(RtspMethod method, String requestUrl) {
        return RtspRequest.newBuilder()
                .url(requestUrl)
                .method(method)
                .sessionId(sessionId)
                .userAgent(userAgent);
    }

    private SdpParser.SdpInfo parseDescription(String body) throws IOException {
        if (body == null || body.trim().isEmpty()) {
            return null;
        }
        try {
            return SdpParser.parse(body);
        } catch (IllegalArgumentException e) {
            clearPresentation();
            closeTransport();
            throw new IOException("Invalid SDP returned by RTSP server", e);
        }
    }

    private void updateSession(RtspResponse response) {
        String value = response.getHeader("Session");
        if (value == null) {
            return;
        }
        int separator = value.indexOf(';');
        String id = (separator < 0 ? value : value.substring(0, separator)).trim();
        if (!id.isEmpty()) {
            sessionId = id;
        }
    }

    private URI resolveDescriptionBase(RtspResponse response) {
        String value = response.getHeader("Content-Base");
        if (value == null || value.trim().isEmpty()) {
            value = response.getHeader("Content-Location");
        }
        if (value == null || value.trim().isEmpty()) {
            return uri;
        }
        try {
            return uri.resolve(value.trim());
        } catch (IllegalArgumentException ignored) {
            return uri;
        }
    }

    private String resolveControlUrl(int streamIndex) {
        validateStreamIndex(streamIndex);
        if (description == null || !description.hasMedia()) {
            return uri.toString();
        }
        String control = description.getMediaDescriptions().get(streamIndex).getControl();
        if (control == null || control.isEmpty() || "*".equals(control)) {
            return uri.toString();
        }

        URI controlUri = URI.create(control);
        if (controlUri.isAbsolute()) {
            return controlUri.toString();
        }
        URI base = descriptionBase == null ? uri : descriptionBase;
        if (control.startsWith("/")) {
            return base.resolve(control).toString();
        }
        String baseValue = withoutQueryOrFragment(base.toString());
        return URI.create(baseValue.endsWith("/") ? baseValue : baseValue + "/")
                .resolve(control)
                .toString();
    }

    private URI resolveAggregateControlUri(SdpParser.SdpInfo parsedDescription, URI base) {
        if (parsedDescription == null) {
            return uri;
        }
        String control = parsedDescription.getControl();
        if (control == null || control.isEmpty()) {
            return uri;
        }
        URI effectiveBase = base == null ? uri : base;
        if ("*".equals(control)) {
            return effectiveBase;
        }
        URI controlUri = URI.create(control);
        return controlUri.isAbsolute() ? controlUri : effectiveBase.resolve(controlUri);
    }

    private String aggregateControlUrl() {
        return aggregateControlUri == null ? uri.toString() : aggregateControlUri.toString();
    }

    private void validateStreamIndex(int streamIndex) {
        if (streamIndex < 0) {
            throw new IllegalArgumentException("streamIndex must be >= 0");
        }
        if (description != null && description.hasMedia()
                && streamIndex >= description.getMediaDescriptions().size()) {
            throw new IllegalArgumentException("streamIndex is outside the SDP media list");
        }
    }

    private void checkConnected() throws IOException {
        checkOpen();
        if (!connected) {
            throw new IOException("Not connected to RTSP server");
        }
    }

    private void checkOpen() throws IOException {
        if (closed) {
            throw new IOException("RtspClient is closed");
        }
    }

    private int nextCseq() {
        cseq = cseq == Integer.MAX_VALUE ? 1 : cseq + 1;
        return cseq;
    }

    private void clearPresentation() {
        sessionId = null;
        connected = false;
        streaming = false;
        description = null;
        descriptionBase = null;
        aggregateControlUri = null;
    }

    private void resetSession() {
        clearPresentation();
        authenticator.reset();
    }

    private void closeTransport() {
        synchronized (ioLock) {
            transport.close();
        }
    }

    private static IOException responseFailure(String operation, RtspResponse response) {
        String detail = response.getErrorMessage();
        if (detail == null || detail.isEmpty()) {
            detail = response.getStatusCode() + " " + String.valueOf(response.getStatusText());
        }
        return new IOException(operation + " failed: " + detail.trim());
    }

    private static String normalizeRange(String range) {
        if (range == null || range.trim().isEmpty()) {
            return "npt=0.000-";
        }
        String value = requireSingleLine(range.trim(), "range");
        if (value.indexOf('=') >= 0) {
            return value;
        }
        return "npt=" + (value.endsWith("-") ? value : value + "-");
    }

    private static String withoutQueryOrFragment(String value) {
        int fragment = value.indexOf('#');
        if (fragment >= 0) {
            value = value.substring(0, fragment);
        }
        int query = value.indexOf('?');
        return query < 0 ? value : value.substring(0, query);
    }

    private static URI validateUri(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("URL must be set");
        }
        final URI parsed;
        try {
            parsed = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid RTSP URL", e);
        }
        String scheme = parsed.getScheme();
        if (!"rtsp".equalsIgnoreCase(scheme) && !"rtsps".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("URL scheme must be rtsp or rtsps");
        }
        if (parsed.getHost() == null || parsed.getHost().isEmpty()) {
            throw new IllegalArgumentException("RTSP URL must include a host");
        }
        if (parsed.getRawUserInfo() != null || parsed.getRawFragment() != null
                || parsed.getPort() > 65_535) {
            throw new IllegalArgumentException("Invalid RTSP URL");
        }
        return parsed;
    }

    private static Duration requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static int timeoutMillis(Duration value) {
        final long millis;
        try {
            millis = value.toMillis();
        } catch (ArithmeticException e) {
            return Integer.MAX_VALUE;
        }
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, millis));
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static String requireSingleLine(String value, String name) {
        if (value != null && (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0)) {
            throw new IllegalArgumentException(name + " must not contain CR/LF");
        }
        return value;
    }

    public static class Builder {
        private String url;
        private Duration timeout = Duration.ofSeconds(5);
        private String username;
        private String password;
        private String userAgent;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public Builder credentials(String username, String password) {
            if ((username == null) != (password == null)) {
                throw new IllegalArgumentException("username and password must be provided together");
            }
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public RtspClient build() {
            return new RtspClient(this);
        }
    }
}
