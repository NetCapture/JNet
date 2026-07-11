package com.jnet.socketio;

import com.jnet.core.JNetClient;
import com.jnet.core.Response;
import com.jnet.core.org.json.JSONArray;
import com.jnet.core.org.json.JSONObject;
import com.jnet.websocket.WebSocketClient;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Dependency-free Socket.IO v4 client using an Engine.IO v4 WebSocket upgrade.
 * Binary attachments, acknowledgements and polling-only servers are not part of
 * this deliberately small client.
 */
public class SocketIOClient implements AutoCloseable {
    private static final int ENGINE_IO_VERSION = 4;
    private static final int MAX_HANDSHAKE_BYTES = 64 * 1024;
    private static final long DISCONNECT_TIMEOUT_MILLIS = 1_000;
    private static final long DEFAULT_CONNECT_PHASE_TIMEOUT_MILLIS = 10_000;
    private static final long MAX_CONNECT_PHASE_TIMEOUT_MILLIS = 30_000;
    private static final ScheduledThreadPoolExecutor CONNECT_DEADLINES = createDeadlineExecutor();
    private static final JNetClient HANDSHAKE_HTTP_CLIENT = JNetClient.newBuilder()
            .maxResponseBytes(MAX_HANDSHAKE_BYTES)
            .build();

    private final URI endpoint;
    private final HandshakeClient handshakeClient;
    private final TransportFactory transportFactory;
    private final Map<String, List<Consumer<Object[]>>> eventListeners = new ConcurrentHashMap<>();
    private final AtomicLong transportGeneration = new AtomicLong();

    private volatile String namespace;
    private volatile String sessionId;
    private volatile Transport transport;
    private volatile State state = State.DISCONNECTED;
    private ScheduledFuture<?> connectDeadline;

    public SocketIOClient(String url) {
        this(url, SocketIOClient::loadHandshake, DefaultTransport::new);
    }

    SocketIOClient(String url, HandshakeClient handshakeClient, TransportFactory transportFactory) {
        this.endpoint = normalizeEndpoint(url);
        this.namespace = "/";
        this.handshakeClient = Objects.requireNonNull(handshakeClient, "handshakeClient");
        this.transportFactory = Objects.requireNonNull(transportFactory, "transportFactory");
    }

    /** Starts the polling handshake and then probes the WebSocket transport. */
    public void connect() {
        disconnectTransport(false);
        long attempt;
        synchronized (this) {
            state = State.HANDSHAKING;
            attempt = transportGeneration.incrementAndGet();
        }
        try {
            String payload = handshakeClient.get(withQuery(httpEndpoint(), "EIO=" + ENGINE_IO_VERSION
                    + "&transport=polling"));
            if (payload == null || payload.length() > MAX_HANDSHAKE_BYTES) {
                throw new IllegalArgumentException("Engine.IO handshake exceeds "
                        + MAX_HANDSHAKE_BYTES + " characters");
            }
            JSONObject handshake = new JSONObject(openPacket(payload));
            String openedSession = handshake.getString("sid");
            if (openedSession.trim().isEmpty()) {
                throw new IllegalArgumentException("Engine.IO handshake has an empty session id");
            }
            if (!offersWebSocket(handshake)) {
                throw new IllegalStateException("Engine.IO server does not offer WebSocket upgrade");
            }
            openWebSocket(openedSession, attempt, connectPhaseTimeout(handshake));
        } catch (Exception e) {
            if (failHandshake(attempt)) {
                triggerEvent("connect_error", new Object[] {e});
            }
        }
    }

    private void openWebSocket(String openedSession, long attempt, long phaseTimeoutMillis) {
        Transport next = Objects.requireNonNull(transportFactory.create(), "transport");
        long selectedGeneration = -1;
        String selectedUrl = null;
        boolean stale;
        synchronized (this) {
            stale = state != State.HANDSHAKING || transportGeneration.get() != attempt;
            if (!stale) {
                sessionId = openedSession;
                state = State.PROBING;
                selectedGeneration = transportGeneration.incrementAndGet();
                transport = next;
                selectedUrl = withQuery(webSocketEndpoint(), "EIO=" + ENGINE_IO_VERSION
                        + "&transport=websocket&sid=" + encode(openedSession));
                scheduleConnectDeadlineLocked(next, selectedGeneration, State.PROBING,
                        phaseTimeoutMillis, "Engine.IO probe");
            }
        }
        if (stale) {
            next.close();
            return;
        }
        final long generation = selectedGeneration;
        final String webSocketUrl = selectedUrl;
        TransportListener listener = new TransportListener() {
            @Override
            public void onOpen() {
                sendIfCurrent(next, generation, State.PROBING, "2probe");
            }

            @Override
            public void onMessage(String message) {
                handleEngineIOMessage(next, generation, phaseTimeoutMillis, message);
            }

            @Override
            public void onClose(String reason) {
                handleTransportClose(next, generation, reason);
            }

            @Override
            public void onError(Throwable error) {
                handleTransportError(next, generation, error);
            }
        };
        try {
            next.connect(webSocketUrl, listener);
        } catch (RuntimeException error) {
            handleTransportError(next, generation, error);
        } finally {
            if (!isCurrentTransport(next, generation)) {
                next.close();
            }
        }
    }

    private boolean failHandshake(long attempt) {
        synchronized (this) {
            if (state != State.HANDSHAKING || transportGeneration.get() != attempt) {
                return false;
            }
            state = State.DISCONNECTED;
            sessionId = null;
            transportGeneration.incrementAndGet();
            return true;
        }
    }

    private static String loadHandshake(String url) throws IOException {
        Response response = HANDSHAKE_HTTP_CLIENT.newGet(url).build().newCall().execute();
        if (!response.isOk()) {
            throw new IOException("Engine.IO handshake failed with HTTP " + response.getCode());
        }
        String body = response.getBody();
        if (body == null) {
            throw new IOException("Engine.IO handshake returned an empty response");
        }
        return body;
    }

    private void handleEngineIOMessage(Transport expected, long generation, long phaseTimeoutMillis,
            String message) {
        if (message == null || message.isEmpty() || !isCurrentTransport(expected, generation)) {
            return;
        }
        char packetType = message.charAt(0);
        String payload = message.substring(1);
        switch (packetType) {
            case '0':
                handleOpenPacket(expected, generation, phaseTimeoutMillis, payload);
                break;
            case '1':
                handleEngineClose(expected, generation);
                break;
            case '2':
                sendIfCurrent(expected, generation, null, "3" + payload);
                break;
            case '3':
                handleProbeResponse(expected, generation, phaseTimeoutMillis, payload);
                break;
            case '4':
                handleSocketIOMessage(expected, generation, payload);
                break;
            case '5':
            case '6':
                break;
            default:
                triggerEventIfCurrent(expected, generation, "error", new Object[] {
                        new IllegalArgumentException("Unknown Engine.IO packet: " + packetType)});
        }
    }

    private void handleOpenPacket(Transport expected, long generation, long phaseTimeoutMillis,
            String payload) {
        try {
            JSONObject open = new JSONObject(payload);
            String openedSession = open.optString("sid", null);
            boolean connectNamespace = false;
            synchronized (this) {
                if (!isCurrentTransport(expected, generation)) {
                    return;
                }
                if (openedSession != null) {
                    sessionId = openedSession;
                }
                if (state == State.PROBING) {
                    state = State.NAMESPACE_CONNECTING;
                    scheduleConnectDeadlineLocked(expected, generation, State.NAMESPACE_CONNECTING,
                            phaseTimeoutMillis, "Socket.IO namespace acknowledgement");
                    connectNamespace = true;
                }
            }
            if (connectNamespace) {
                sendConnectPacket(expected, generation);
            }
        } catch (RuntimeException e) {
            triggerEventIfCurrent(expected, generation, "connect_error", new Object[] {e});
        }
    }

    private void handleProbeResponse(Transport expected, long generation, long phaseTimeoutMillis,
            String payload) {
        boolean accepted = false;
        synchronized (this) {
            if (isCurrentTransport(expected, generation)
                    && state == State.PROBING && "probe".equals(payload)) {
                state = State.NAMESPACE_CONNECTING;
                scheduleConnectDeadlineLocked(expected, generation, State.NAMESPACE_CONNECTING,
                        phaseTimeoutMillis, "Socket.IO namespace acknowledgement");
                accepted = true;
            }
        }
        if (accepted && sendIfCurrent(expected, generation, State.NAMESPACE_CONNECTING, "5")) {
            sendConnectPacket(expected, generation);
        }
    }

    private void sendConnectPacket(Transport expected, long generation) {
        sendIfCurrent(expected, generation, State.NAMESPACE_CONNECTING, "40" + namespaceSuffix());
    }

    private void handleSocketIOMessage(Transport expected, long generation, String payload) {
        try {
            SocketPacket packet = SocketPacket.parse(payload);
            if (!namespace.equals(packet.namespace)) {
                return;
            }
            switch (packet.type) {
                case '0':
                    boolean connected = false;
                    synchronized (this) {
                        if (isCurrentTransport(expected, generation)
                                && state == State.NAMESPACE_CONNECTING) {
                            state = State.CONNECTED;
                            cancelConnectDeadlineLocked();
                            connected = true;
                        }
                    }
                    if (connected) {
                        triggerEvent("connect", new Object[0]);
                    }
                    break;
                case '1':
                    handleNamespaceDisconnect(expected, generation);
                    break;
                case '2':
                    parseAndTriggerEvent(expected, generation, packet.data);
                    break;
                case '3':
                    triggerEventIfCurrent(expected, generation, "ack",
                            new Object[] {packet.ackId, packet.data});
                    break;
                case '4':
                    handleNamespaceConnectError(expected, generation, packet.data);
                    break;
                case '5':
                case '6':
                    triggerEventIfCurrent(expected, generation, "error", new Object[] {
                            new UnsupportedOperationException("Binary Socket.IO packets are not supported")});
                    break;
                default:
                    triggerEventIfCurrent(expected, generation, "error", new Object[] {
                            new IllegalArgumentException("Unknown Socket.IO packet: " + packet.type)});
            }
        } catch (RuntimeException e) {
            triggerEventIfCurrent(expected, generation, "error", new Object[] {e});
        }
    }

    private void parseAndTriggerEvent(Transport expected, long generation, String data) {
        JSONArray array = new JSONArray(data);
        if (array.length() == 0) {
            throw new IllegalArgumentException("Socket.IO event payload is empty");
        }
        String eventName = array.getString(0);
        Object[] args = new Object[array.length() - 1];
        for (int i = 1; i < array.length(); i++) {
            args[i - 1] = array.get(i);
        }
        boolean deliver;
        synchronized (this) {
            deliver = isCurrentTransport(expected, generation) && state == State.CONNECTED;
        }
        if (deliver) {
            triggerEvent(eventName, args);
        }
    }

    public void on(String event, Consumer<Object[]> listener) {
        if (event == null || event.isEmpty()) {
            throw new IllegalArgumentException("event cannot be null or empty");
        }
        Consumer<Object[]> checked = Objects.requireNonNull(listener, "listener");
        List<Consumer<Object[]>> listeners = eventListeners.get(event);
        if (listeners == null) {
            List<Consumer<Object[]>> created = new CopyOnWriteArrayList<>();
            List<Consumer<Object[]>> existing = eventListeners.putIfAbsent(event, created);
            listeners = existing == null ? created : existing;
        }
        listeners.add(checked);
    }

    public void off(String event, Consumer<Object[]> listener) {
        List<Consumer<Object[]>> listeners = eventListeners.get(event);
        if (listeners != null) {
            listeners.remove(listener);
            if (listeners.isEmpty()) {
                eventListeners.remove(event, listeners);
            }
        }
    }

    public void emit(String event, Object... args) {
        if (!isConnected()) {
            throw new IllegalStateException("Not connected to Socket.IO server");
        }
        if (event == null || event.isEmpty()) {
            throw new IllegalArgumentException("event cannot be null or empty");
        }
        JSONArray payload = new JSONArray().put(event);
        if (args != null) {
            for (Object arg : args) {
                payload.put(arg);
            }
        }
        send("42" + namespaceSuffix() + payload);
    }

    /** Convenience event; rooms are an application convention, not a wire-level Socket.IO feature. */
    public void join(String room) {
        emit("join", room);
    }

    /** Convenience event; rooms are an application convention, not a wire-level Socket.IO feature. */
    public void leave(String room) {
        emit("leave", room);
    }

    public SocketIOClient namespace(String namespace) {
        SocketIOClient client = new SocketIOClient(endpoint.toString(), handshakeClient, transportFactory);
        client.namespace = normalizeNamespace(namespace);
        return client;
    }

    public void disconnect() {
        Transport current;
        boolean wasConnected;
        synchronized (this) {
            current = transport;
            transport = null;
            wasConnected = state == State.CONNECTED;
            state = State.DISCONNECTED;
            sessionId = null;
            transportGeneration.incrementAndGet();
            cancelConnectDeadlineLocked();
        }
        if (current != null && wasConnected) {
            try {
                CompletableFuture<?> goodbye = current.sendText("41" + namespaceSuffix());
                if (goodbye == null) {
                    current.close();
                } else {
                    goodbye.orTimeout(DISCONNECT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                            .whenComplete((ignored, error) -> current.close());
                }
            } catch (RuntimeException error) {
                current.close();
                triggerEvent("error", new Object[] {error});
            }
        } else if (current != null) {
            current.close();
        }
        if (wasConnected) {
            triggerEvent("disconnect", new Object[0]);
        }
    }

    @Override
    public void close() {
        disconnect();
    }

    public boolean isConnected() {
        return state == State.CONNECTED;
    }

    public String getSessionId() {
        return sessionId;
    }

    private void send(String packet) {
        Transport current;
        long generation;
        synchronized (this) {
            current = transport;
            generation = transportGeneration.get();
            if (current == null) {
                throw new IllegalStateException("Socket.IO transport is not connected");
            }
        }
        sendOnTransport(current, generation, packet);
    }

    private boolean sendIfCurrent(Transport expected, long generation, State requiredState, String packet) {
        synchronized (this) {
            if (!isCurrentTransport(expected, generation)
                    || requiredState != null && state != requiredState) {
                return false;
            }
        }
        try {
            sendOnTransport(expected, generation, packet);
            return true;
        } catch (RuntimeException error) {
            handleTransportError(expected, generation, error);
            return false;
        }
    }

    private void sendOnTransport(Transport current, long generation, String packet) {
        CompletableFuture<?> future = current.sendText(packet);
        if (future != null) {
            future.whenComplete((ignored, error) -> {
                if (error != null && isCurrentTransport(current, generation)) {
                    triggerEvent("error", new Object[] {error});
                }
            });
        }
    }

    private void disconnectTransport(boolean notify) {
        Transport current;
        State previousState;
        synchronized (this) {
            current = transport;
            transport = null;
            previousState = state;
            state = State.DISCONNECTED;
            sessionId = null;
            transportGeneration.incrementAndGet();
            cancelConnectDeadlineLocked();
        }
        if (current != null) {
            current.close();
        }
        if (notify) {
            if (previousState == State.CONNECTED) {
                triggerEvent("disconnect", new Object[0]);
            } else if (previousState != State.DISCONNECTED) {
                triggerEvent("connect_error", new Object[] {"Engine.IO transport closed"});
            }
        }
    }

    private boolean isCurrentTransport(Transport expected, long generation) {
        return transport == expected && transportGeneration.get() == generation;
    }

    private State detachCurrentTransport(Transport expected, long generation) {
        synchronized (this) {
            if (!isCurrentTransport(expected, generation)) {
                return null;
            }
            State previousState = state;
            transport = null;
            state = State.DISCONNECTED;
            sessionId = null;
            transportGeneration.incrementAndGet();
            cancelConnectDeadlineLocked();
            return previousState;
        }
    }

    private void handleEngineClose(Transport expected, long generation) {
        State previousState = detachCurrentTransport(expected, generation);
        if (previousState == null) {
            return;
        }
        expected.close();
        if (previousState == State.CONNECTED) {
            triggerEvent("disconnect", new Object[0]);
        } else if (previousState != State.DISCONNECTED) {
            triggerEvent("connect_error", new Object[] {"Engine.IO transport closed"});
        }
    }

    private void handleNamespaceDisconnect(Transport expected, long generation) {
        State previousState = detachCurrentTransport(expected, generation);
        if (previousState == null) {
            return;
        }
        expected.close();
        if (previousState == State.CONNECTED) {
            triggerEvent("disconnect", new Object[0]);
        } else if (previousState != State.DISCONNECTED) {
            triggerEvent("connect_error", new Object[] {"Socket.IO namespace disconnected"});
        }
    }

    private void handleNamespaceConnectError(Transport expected, long generation, String error) {
        State previousState = detachCurrentTransport(expected, generation);
        if (previousState == null) {
            return;
        }
        expected.close();
        triggerEvent("connect_error", new Object[] {error});
    }

    private void handleTransportClose(Transport expected, long generation, String reason) {
        State previousState = detachCurrentTransport(expected, generation);
        if (previousState == null) {
            return;
        }
        if (previousState == State.CONNECTED) {
            triggerEvent("disconnect", new Object[] {reason});
        } else {
            triggerEvent("connect_error", new Object[] {reason});
        }
    }

    private void handleTransportError(Transport expected, long generation, Throwable error) {
        State previousState = detachCurrentTransport(expected, generation);
        if (previousState == null) {
            return;
        }
        expected.close();
        if (previousState == State.CONNECTED) {
            triggerEvent("error", new Object[] {error});
            triggerEvent("disconnect", new Object[] {error});
        } else {
            triggerEvent("connect_error", new Object[] {error});
        }
    }

    private void scheduleConnectDeadlineLocked(Transport expected, long generation, State expectedState,
            long timeoutMillis, String phase) {
        cancelConnectDeadlineLocked();
        connectDeadline = CONNECT_DEADLINES.schedule(
                () -> handleConnectDeadline(expected, generation, expectedState, timeoutMillis, phase),
                timeoutMillis, TimeUnit.MILLISECONDS);
    }

    private void cancelConnectDeadlineLocked() {
        ScheduledFuture<?> current = connectDeadline;
        connectDeadline = null;
        if (current != null) {
            current.cancel(false);
        }
    }

    private void handleConnectDeadline(Transport expected, long generation, State expectedState,
            long timeoutMillis, String phase) {
        boolean expired = false;
        synchronized (this) {
            if (isCurrentTransport(expected, generation) && state == expectedState) {
                transport = null;
                state = State.DISCONNECTED;
                sessionId = null;
                transportGeneration.incrementAndGet();
                cancelConnectDeadlineLocked();
                expired = true;
            }
        }
        if (expired) {
            expected.close();
            triggerEvent("connect_error", new Object[] {
                    new TimeoutException(phase + " timed out after " + timeoutMillis + " ms")});
        }
    }

    private void triggerEventIfCurrent(Transport expected, long generation, String event, Object[] args) {
        synchronized (this) {
            if (!isCurrentTransport(expected, generation)) {
                return;
            }
        }
        triggerEvent(event, args);
    }

    private void triggerEvent(String event, Object[] args) {
        List<Consumer<Object[]>> listeners = eventListeners.get(event);
        if (listeners == null) {
            return;
        }
        for (Consumer<Object[]> listener : listeners) {
            try {
                listener.accept(args);
            } catch (RuntimeException listenerFailure) {
                if (!"error".equals(event)) {
                    triggerEvent("error", new Object[] {listenerFailure});
                }
            }
        }
    }

    private String namespaceSuffix() {
        return "/".equals(namespace) ? "" : namespace + ",";
    }

    private URI httpEndpoint() {
        return withScheme(endpoint, "https".equalsIgnoreCase(endpoint.getScheme()) ? "https" : "http");
    }

    private URI webSocketEndpoint() {
        return withScheme(endpoint, "https".equalsIgnoreCase(endpoint.getScheme()) ? "wss" : "ws");
    }

    private static ScheduledThreadPoolExecutor createDeadlineExecutor() {
        ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1, runnable -> {
            Thread thread = new Thread(runnable, "jnet-socketio-deadline");
            thread.setDaemon(true);
            return thread;
        });
        executor.setRemoveOnCancelPolicy(true);
        executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
        return executor;
    }

    private static long connectPhaseTimeout(JSONObject handshake) {
        long configured = handshake.optLong("pingTimeout", DEFAULT_CONNECT_PHASE_TIMEOUT_MILLIS);
        if (configured <= 0) {
            return DEFAULT_CONNECT_PHASE_TIMEOUT_MILLIS;
        }
        return Math.min(configured, MAX_CONNECT_PHASE_TIMEOUT_MILLIS);
    }

    private static URI normalizeEndpoint(String url) {
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("url cannot be null or empty");
        }
        URI value = URI.create(url.trim());
        String scheme = value.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)
                || "ws".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Socket.IO URL must use http, https, ws or wss");
        }
        if (value.getHost() == null) {
            throw new IllegalArgumentException("Socket.IO URL must include a host");
        }
        if (value.getRawUserInfo() != null || value.getRawFragment() != null || value.getPort() > 65_535) {
            throw new IllegalArgumentException("Invalid Socket.IO URL");
        }
        String httpScheme = "https".equalsIgnoreCase(scheme) || "wss".equalsIgnoreCase(scheme)
                ? "https" : "http";
        String path = value.getRawPath();
        if (path == null || path.isEmpty() || "/".equals(path)) {
            path = "/socket.io/";
        } else if (!path.endsWith("/")) {
            path += "/";
        }
        StringBuilder normalized = new StringBuilder(httpScheme).append("://")
                .append(value.getRawAuthority()).append(path);
        if (value.getRawQuery() != null) {
            normalized.append('?').append(value.getRawQuery());
        }
        return URI.create(normalized.toString());
    }

    private static URI withScheme(URI value, String scheme) {
        String raw = value.toString();
        return URI.create(scheme + raw.substring(raw.indexOf(':')));
    }

    private static String withQuery(URI value, String query) {
        String base = value.toString();
        return base + (value.getRawQuery() == null ? "?" : "&") + query;
    }

    private static String openPacket(String payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Empty Engine.IO handshake");
        }
        for (String packet : payload.split("\\u001e")) {
            String candidate = packet.trim();
            int open = candidate.indexOf("0{");
            if (open >= 0) {
                return candidate.substring(open + 1);
            }
        }
        throw new IllegalArgumentException("Invalid Engine.IO handshake payload");
    }

    private static boolean offersWebSocket(JSONObject handshake) {
        JSONArray upgrades = handshake.optJSONArray("upgrades");
        if (upgrades == null) {
            return false;
        }
        for (Object upgrade : upgrades) {
            if ("websocket".equalsIgnoreCase(String.valueOf(upgrade))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeNamespace(String value) {
        if (value == null || value.trim().isEmpty() || "/".equals(value.trim())) {
            return "/";
        }
        String namespace = value.trim();
        if (!namespace.startsWith("/")) {
            namespace = "/" + namespace;
        }
        if (namespace.indexOf(',') >= 0 || namespace.indexOf('?') >= 0 || namespace.indexOf('#') >= 0) {
            throw new IllegalArgumentException("Invalid Socket.IO namespace");
        }
        return namespace;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private enum State {
        DISCONNECTED,
        HANDSHAKING,
        PROBING,
        NAMESPACE_CONNECTING,
        CONNECTED
    }

    private static final class SocketPacket {
        private final char type;
        private final String namespace;
        private final Integer ackId;
        private final String data;

        private SocketPacket(char type, String namespace, Integer ackId, String data) {
            this.type = type;
            this.namespace = namespace;
            this.ackId = ackId;
            this.data = data;
        }

        private static SocketPacket parse(String value) {
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException("Empty Socket.IO packet");
            }
            char type = value.charAt(0);
            int index = 1;
            String namespace = "/";
            if (index < value.length() && value.charAt(index) == '/') {
                int comma = value.indexOf(',', index);
                if (comma < 0) {
                    return new SocketPacket(type, value.substring(index), null, "");
                }
                namespace = value.substring(index, comma);
                index = comma + 1;
            }
            int ackStart = index;
            while (index < value.length() && Character.isDigit(value.charAt(index))) {
                index++;
            }
            Integer ackId = index == ackStart ? null : Integer.valueOf(value.substring(ackStart, index));
            return new SocketPacket(type, namespace, ackId, value.substring(index));
        }
    }

    @FunctionalInterface
    interface HandshakeClient {
        String get(String url) throws Exception;
    }

    @FunctionalInterface
    interface TransportFactory {
        Transport create();
    }

    interface Transport {
        void connect(String url, TransportListener listener);
        CompletableFuture<?> sendText(String text);
        void close();
    }

    interface TransportListener {
        void onOpen();
        void onMessage(String message);
        void onClose(String reason);
        void onError(Throwable error);
    }

    private static final class DefaultTransport implements Transport {
        private volatile WebSocketClient client;

        @Override
        public void connect(String url, TransportListener listener) {
            WebSocketClient next = WebSocketClient.newBuilder()
                    .disableReconnect()
                    .listener(new WebSocketClient.WebSocketListener() {
                        @Override
                        public void onOpen(java.net.http.WebSocket webSocket) {
                            listener.onOpen();
                        }

                        @Override
                        public void onMessage(String message) {
                            listener.onMessage(message);
                        }

                        @Override
                        public void onClose(int statusCode, String reason) {
                            listener.onClose(reason);
                        }

                        @Override
                        public void onError(Throwable error) {
                            listener.onError(error);
                        }
                    })
                    .build();
            client = next;
            CompletableFuture<?> connection = next.connect(url);
            if (client != next) {
                next.close();
            }
            connection.whenComplete((ignored, error) -> {
                if (error != null) {
                    listener.onError(error);
                }
            });
        }

        @Override
        public CompletableFuture<?> sendText(String text) {
            WebSocketClient current = client;
            return current == null
                    ? failedFuture(new IllegalStateException("WebSocket not connected"))
                    : current.sendText(text);
        }

        @Override
        public void close() {
            WebSocketClient current = client;
            client = null;
            if (current != null) {
                current.close();
            }
        }
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable error) {
        CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(error);
        return future;
    }
}
