package com.jnet.rtsp;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Persistent RTSP socket framing, including TLS and interleaved-frame skipping. */
final class RtspTransport {
    private static final int DEFAULT_RTSP_PORT = 554;
    private static final int DEFAULT_RTSPS_PORT = 322;
    private static final int MAX_HEADER_BYTES = 64 * 1024;
    private static final int MAX_BODY_BYTES = 16 * 1024 * 1024;

    private final URI endpoint;
    private final int timeoutMillis;
    private final Object stateLock = new Object();

    private Socket socket;
    private BufferedInputStream input;
    private BufferedOutputStream output;
    private boolean shutdown;

    RtspTransport(URI endpoint, int timeoutMillis) {
        this.endpoint = endpoint;
        this.timeoutMillis = timeoutMillis;
    }

    boolean isOpen() {
        synchronized (stateLock) {
            return !shutdown
                    && input != null
                    && output != null
                    && isUsable(socket);
        }
    }

    RtspResponse exchange(RtspRequest request) throws IOException {
        ensureOpen();
        Streams streams = streams();
        byte[] requestBytes = request.toRequestString().getBytes(StandardCharsets.UTF_8);
        long started = System.nanoTime();
        streams.output.write(requestBytes);
        streams.output.flush();

        byte[] headerBytes = readHeader(streams.input);
        String headerText = new String(headerBytes, StandardCharsets.ISO_8859_1);
        byte[] bodyBytes = readExactly(streams.input, contentLength(headerText));
        return RtspResponse.parse(headerBytes, bodyBytes, elapsedMillis(started), request);
    }

    void close() {
        closeConnection(false);
    }

    void shutdown() {
        closeConnection(true);
    }

    private void ensureOpen() throws IOException {
        if (isOpen()) {
            return;
        }
        close();
        checkNotShutdown();

        Socket connected = connectSocket();
        try {
            BufferedInputStream connectedInput = new BufferedInputStream(connected.getInputStream());
            BufferedOutputStream connectedOutput = new BufferedOutputStream(connected.getOutputStream());
            synchronized (stateLock) {
                if (shutdown || socket != connected) {
                    throw closedException();
                }
                input = connectedInput;
                output = connectedOutput;
            }
        } catch (IOException e) {
            closeQuietly(connected);
            clearIfCurrent(connected);
            throw e;
        }
    }

    private Socket connectSocket() throws IOException {
        int port = endpoint.getPort() > 0 ? endpoint.getPort() : defaultPort();
        Socket plain = new Socket();
        Socket active = plain;
        try {
            registerConnecting(active);
            plain.connect(new InetSocketAddress(endpoint.getHost(), port), timeoutMillis);
            plain.setSoTimeout(timeoutMillis);
            plain.setTcpNoDelay(true);
            if (!isSecure()) {
                return plain;
            }

            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            SSLSocket secure = (SSLSocket) factory
                    .createSocket(plain, endpoint.getHost(), port, true);
            active = secure;
            registerConnecting(active);
            SSLParameters parameters = secure.getSSLParameters();
            parameters.setEndpointIdentificationAlgorithm("HTTPS");
            secure.setSSLParameters(parameters);
            secure.setSoTimeout(timeoutMillis);
            secure.startHandshake();
            return secure;
        } catch (IOException | RuntimeException e) {
            closeQuietly(active);
            clearIfCurrent(active);
            throw e;
        }
    }

    private byte[] readHeader(BufferedInputStream currentInput) throws IOException {
        while (true) {
            int first = currentInput.read();
            if (first < 0) {
                throw new EOFException("RTSP server closed the connection");
            }
            if (first == '$') {
                skipInterleavedFrame(currentInput);
                continue;
            }

            ByteArrayOutputStream header = new ByteArrayOutputStream(512);
            header.write(first);
            int state = first == '\r' ? 1 : 0;
            while (state < 4) {
                int value = currentInput.read();
                if (value < 0) {
                    throw new EOFException("RTSP server closed during response headers");
                }
                header.write(value);
                if (header.size() > MAX_HEADER_BYTES) {
                    throw new IOException("RTSP response headers exceed " + MAX_HEADER_BYTES + " bytes");
                }
                state = nextHeaderState(state, value);
            }
            return header.toByteArray();
        }
    }

    private void skipInterleavedFrame(BufferedInputStream currentInput) throws IOException {
        int channel = currentInput.read();
        int high = currentInput.read();
        int low = currentInput.read();
        if (channel < 0 || high < 0 || low < 0) {
            throw new EOFException("Incomplete interleaved RTP frame header");
        }
        skipExactly(currentInput, (high << 8) | low);
    }

    private int contentLength(String headers) throws IOException {
        Integer found = null;
        for (String line : headers.split("\\r\\n")) {
            int separator = line.indexOf(':');
            if (separator <= 0
                    || !"content-length".equalsIgnoreCase(line.substring(0, separator).trim())) {
                continue;
            }
            String value = line.substring(separator + 1).trim();
            final long length;
            try {
                length = Long.parseLong(value);
            } catch (NumberFormatException e) {
                throw new IOException("Invalid RTSP Content-Length: " + value, e);
            }
            if (length < 0 || length > MAX_BODY_BYTES) {
                throw new IOException("Invalid RTSP Content-Length: " + value);
            }
            if (found != null && found != (int) length) {
                throw new IOException("Conflicting RTSP Content-Length headers");
            }
            found = (int) length;
        }
        return found == null ? 0 : found;
    }

    private byte[] readExactly(BufferedInputStream currentInput, int length) throws IOException {
        byte[] result = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = currentInput.read(result, offset, length - offset);
            if (read < 0) {
                throw new EOFException("RTSP server closed during response body");
            }
            if (read == 0) {
                continue;
            }
            offset += read;
        }
        return result;
    }

    private void skipExactly(BufferedInputStream currentInput, int length) throws IOException {
        int remaining = length;
        while (remaining > 0) {
            long skipped = currentInput.skip(remaining);
            if (skipped > 0) {
                remaining -= (int) skipped;
            } else if (currentInput.read() < 0) {
                throw new EOFException("RTSP server closed during interleaved RTP frame");
            } else {
                remaining--;
            }
        }
    }

    private Streams streams() throws IOException {
        synchronized (stateLock) {
            if (shutdown || !isUsable(socket) || input == null || output == null) {
                throw closedException();
            }
            return new Streams(input, output);
        }
    }

    private void registerConnecting(Socket candidate) throws IOException {
        synchronized (stateLock) {
            if (shutdown) {
                closeQuietly(candidate);
                throw closedException();
            }
            socket = candidate;
            input = null;
            output = null;
        }
    }

    private void checkNotShutdown() throws IOException {
        synchronized (stateLock) {
            if (shutdown) {
                throw closedException();
            }
        }
    }

    private void clearIfCurrent(Socket candidate) {
        synchronized (stateLock) {
            if (socket == candidate) {
                socket = null;
                input = null;
                output = null;
            }
        }
    }

    private void closeConnection(boolean permanently) {
        Socket current;
        synchronized (stateLock) {
            if (permanently) {
                shutdown = true;
            }
            current = socket;
            socket = null;
            input = null;
            output = null;
        }
        closeQuietly(current);
    }

    private static boolean isUsable(Socket candidate) {
        return candidate != null
                && candidate.isConnected()
                && !candidate.isClosed()
                && !candidate.isInputShutdown()
                && !candidate.isOutputShutdown();
    }

    private static IOException closedException() {
        return new IOException("RTSP transport is closed");
    }

    private static void closeQuietly(Socket candidate) {
        if (candidate == null) {
            return;
        }
        try {
            candidate.close();
        } catch (IOException ignored) {
            // Closing is best effort.
        }
    }

    private boolean isSecure() {
        return "rtsps".equalsIgnoreCase(endpoint.getScheme());
    }

    private int defaultPort() {
        return isSecure() ? DEFAULT_RTSPS_PORT : DEFAULT_RTSP_PORT;
    }

    private static int nextHeaderState(int state, int value) {
        if (state == 0) {
            return value == '\r' ? 1 : 0;
        }
        if (state == 1) {
            return value == '\n' ? 2 : value == '\r' ? 1 : 0;
        }
        if (state == 2) {
            return value == '\r' ? 3 : 0;
        }
        return value == '\n' ? 4 : value == '\r' ? 1 : 0;
    }

    private static long elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }

    private static final class Streams {
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        private Streams(BufferedInputStream input, BufferedOutputStream output) {
            this.input = input;
            this.output = output;
        }
    }
}
