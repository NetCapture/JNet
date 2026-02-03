package com.jnet.rtsp;

import com.jnet.core.JNet;
import com.jnet.protocol.ProtocolAdapter;
import com.jnet.protocol.ProtocolRequest;
import com.jnet.protocol.ProtocolResponse;
import com.jnet.rtsp.SdpParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * RTSP (Real-Time Streaming Protocol) Client
 * Based on RFC 2326 (1998) and RFC 4566 (2006)
 * Supports DESCRIBE, SETUP, PLAY, PAUSE, TEARDOWN, RECORD, OPTIONS
 *
 * <p>Usage Examples:</p>
 * <pre>{@code
 * // ========== Connect and Describe ==========
 * RtspClient rtsp = RtspClient.newBuilder()
 *     .url("rtsp://example.com:554/stream")
 *     .credentials("admin", "password")
 *     .build();
 *
 * rtsp.connect();
 * RtspResponse describe = rtsp.describe();
 * System.out.println("SDP:\n" + describe.getBody());
 *
 * // ========== Setup Stream ==========
 * rtsp.setup(0, "RTP/AVP;unicast;client_port=5000");
 * rtsp.setup(1, "RTP/AVP;unicast;client_port=5001");
 * rtsp.play();
 *
 * // ========== Stream Control ==========
 * rtsp.pause();
 * rtsp.play();
 * rtsp.teardown();
 *
 * ========== Range Request ==========
 * RtspResponse response = rtsp.play("00:00:01.00"); // Start at 1 second
 * System.out.println("Status: " + response.getStatusCode());
 * }
 * }</pre>
 *
 * @author sanbo
 * @version 3.5.0
 */
public final class RtspClient implements ProtocolAdapter, AutoCloseable {

    private final String url;
    private final URI uri;
    private final Duration timeout;
    private final String username;
    private final String password;
    private final String userAgent;

    // State
    private String sessionId;
    private int cseq = 0;
    private boolean connected = false;
    private volatile boolean closed = false;
    private boolean streaming = false;

    private RtspClient(Builder builder) {
        this.url = builder.url;
        this.uri = URI.create(builder.url);
        this.timeout = builder.timeout;
        this.username = builder.username;
        this.password = builder.password;
        this.userAgent = builder.userAgent;
    }

    // ========== Factory Methods ==========

    /**
     * Create a new builder
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    // ========== Public API ==========

    @Override
    public ProtocolResponse execute(ProtocolRequest request) throws IOException {
        if (request == null) {
            throw new IllegalArgumentException("Request cannot be null");
        }

        String response = JNet.tcp(
                request.getHost(),
                request.getPort(),
                request.getData()
        );

        return ProtocolResponse.success()
                .host(request.getHost(), request.getPort())
                .data(response)
                .bytesRead(response != null ? response.length() : 0)
                .request(request)
                .build();
    }

    @Override
    public CompletableFuture<ProtocolResponse> executeAsync(ProtocolRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return execute(request);
            } catch (IOException e) {
                return ProtocolResponse.failure()
                        .host(request != null ? request.getHost() : null,
                                request != null ? request.getPort() : 0)
                        .errorMessage(e.getMessage())
                        .request(request)
                        .build();
            }
        });
    }

    /**
     * Connect to RTSP server
     */
    public void connect() throws IOException {
        if (closed) {
            throw new IOException("RtspClient is closed");
        }

        connect(1); // Default starts from first stream
    }

    /**
     * Connect starting from a specific stream index
     */
    public void connect(int streamIndex) throws IOException {
        if (closed) {
            throw new IOException("RtspClient is closed");
        }

        // Send DESCRIBE to get session description
        RtspResponse describe = describe();
        if (!describe.isSuccessful()) {
            throw new IOException("Failed to connect: " + describe.getErrorMessage());
        }
        sessionId = describe.getHeader("Session");
        connected = true;
    }

    /**
     * Setup stream (TCP transport)
     */
    public void setup(int streamIndex, String transport) throws IOException {
        checkConnected();
        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .method(RtspMethod.SETUP)
                .build();

        RtspResponse response = execute(request);
        if (!response.isOk()) {
            throw new IOException("Setup failed: " + response.getErrorMessage());
        }
    }

    /**
     * Play stream
     */
    public void play() throws IOException {
        play("00:00:00.00"); // Start from beginning
    }

    /**
     * Play from a specific time
     */
    public void play(String range) throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .method(RtspMethod.PLAY)
                .range(range)
                .build();

        RtspResponse response = execute(request);
        if (!response.isOk()) {
            throw new IOException("Play failed: " + response.getErrorMessage());
        }

        streaming = true;
    }

    /**
     * Pause stream
     */
    public void pause() throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .method(RtspMethod.PAUSE)
                .build();

        RtspResponse response = execute(request);
        if (!response.isOk()) {
            throw new IOException("Pause failed: " + response.getErrorMessage());
        }

        streaming = false;
    }

    /**
     * Resume stream
     */
    public void resume() throws IOException {
        play();
    }

    /**
     * Stop playing and teardown session
     */
    public void teardown() throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .method(RtspMethod.TEARDOWN)
                .build();

        RtspResponse response = execute(request);

        close();
        connected = false;
    }

    /**
     * Describe stream
     */
    public RtspResponse describe() throws IOException {
        checkOpen();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .method(RtspMethod.DESCRIBE)
                .build();

        return execute(request);
    }

    /**
     * Get/Set parameters
     */
    public RtspResponse getParameter(String parameter) throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .cseq(incrementAndGetCseq())
                .method(RtspMethod.GET_PARAMETER)
                .data(parameter)
                .build();

        return execute(request);
    }

    /**
     * Set parameter
     */
    public RtspResponse setParameter(String parameter, String value) throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .cseq(incrementAndGetCseq())
                .method(RtspMethod.SET_PARAMETER)
                .data(parameter + "=" + value)
                .build();

        return execute(request);
    }

    /**
     * Record stream
     */
    public RtspResponse record() throws IOException {
        checkConnected();

        RtspRequest request = RtspRequest.newBuilder()
                .url(uri.toString())
                .sessionId(sessionId)
                .cseq(incrementAndGetCseq())
                .method(RtspMethod.RECORD)
                .data("Record stream")
                .build();

        return execute(request);
    }

    /**
     * Check if connected
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Check if streaming
     */
    public boolean isStreaming() {
        return streaming;
    }

    /**
     * Get session ID
     */
    public String getSessionId() {
        return sessionId;
    }

    /**
     * Get current CSeq
     */
    public int getCseq() {
        return cseq;
    }

    /**
     * Close connection
     */
    @Override
    public void close() {
        closed = true;
        connected = false;
        streaming = false;
    }

    // ========== Internal Methods ==========

    /**
     * Execute RTSP request
     */
    private RtspResponse execute(RtspRequest request) throws IOException {
        String requestString = request.toRequestString();

        try {
            // Send RTSP request over TCP
            String response = JNet.tcp(
                    uri.getHost(),
                    resolvePort(uri),
                    requestString
            );

            // Parse RTSP response
            return RtspResponse.parse(response);

        } catch (IOException e) {
            throw e;
        }
    }

    /**
     * Check connection state
     */
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

    private int resolvePort(URI uri) {
        int port = uri.getPort();
        return port > 0 ? port : 554;
    }

    /**
     * Increment CSeq counter
     */
    private int incrementAndGetCseq() {
        synchronized (this) {
            cseq = (cseq + 1) % 65535;
            return cseq;
        }
    }

    // ========== Builder ==========

    /**
     * RTSP Client Builder
     */
    public static class Builder {
        private String url;
        private Duration timeout = Duration.ofSeconds(5);
        private String username;
        private String password;
        private String userAgent;

        /**
         * Set RTSP server URL
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Set connection timeout
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * Set credentials
         */
        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        /**
         * Set custom User-Agent
         */
        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        /**
         * Build RtspClient
         */
        public RtspClient build() {
            if (url == null) {
                throw new IllegalStateException("URL must be set");
            }
            return new RtspClient(this);
        }
    }
}
