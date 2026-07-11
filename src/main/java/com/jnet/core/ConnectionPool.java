package com.jnet.core;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Compatibility wrapper for the connection API published by JNet 3.0.
 *
 * <p>{@link HttpURLConnection} instances cannot be reset and reused for a new
 * request. The JDK already pools their underlying HTTP connections, so this
 * class now only configures and tracks open wrappers for deterministic cleanup.</p>
 *
 * @deprecated Prefer {@link JNetClient}, which delegates pooling to the JDK HTTP client.
 */
@Deprecated
public class ConnectionPool {
    private final Set<HttpURLConnection> connections =
            Collections.newSetFromMap(new IdentityHashMap<>());

    public synchronized HttpURLConnection get(String url) throws IOException {
        URLConnection opened = new URL(url).openConnection();
        if (!(opened instanceof HttpURLConnection)) {
            throw new IOException("URL does not use HTTP: " + url);
        }

        HttpURLConnection connection = (HttpURLConnection) opened;
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setInstanceFollowRedirects(false);
        connections.add(connection);
        return connection;
    }

    public synchronized void release(HttpURLConnection connection) {
        if (connection == null) {
            return;
        }
        connections.remove(connection);
        connection.disconnect();
    }

    public synchronized void shutdown() {
        for (HttpURLConnection connection : connections) {
            connection.disconnect();
        }
        connections.clear();
    }
}
