package com.jnet.webdav;

import com.jnet.core.JNet;
import java.util.HashMap;
import java.util.Map;

/**
 * WebDAV Client Facade
 * Provides support for WebDAV methods (PROPFIND, MKCOL, COPY, MOVE, etc.)
 */
public final class JWebDAV {

    private JWebDAV() {}

    // WebDAV Methods
    public static final String METHOD_PROPFIND = "PROPFIND";
    public static final String METHOD_MKCOL = "MKCOL";
    public static final String METHOD_COPY = "COPY";
    public static final String METHOD_MOVE = "MOVE";
    public static final String METHOD_LOCK = "LOCK";
    public static final String METHOD_UNLOCK = "UNLOCK";

    /**
     * PROPFIND - Retrieve properties for a resource
     */
    public static String propfind(String url) {
        return propfind(url, 1); // Default depth 1
    }

    public static String propfind(String url, int depth) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Depth", String.valueOf(depth));
        return JNet.request(METHOD_PROPFIND, url, null, headers);
    }

    /**
     * MKCOL - Create a new collection (directory)
     */
    public static String mkcol(String url) {
        return JNet.request(METHOD_MKCOL, url, null);
    }

    /**
     * COPY - Copy a resource to a destination
     */
    public static String copy(String sourceUrl, String destinationUrl) {
        return copy(sourceUrl, destinationUrl, true);
    }

    public static String copy(String sourceUrl, String destinationUrl, boolean overwrite) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Destination", destinationUrl);
        headers.put("Overwrite", overwrite ? "T" : "F");
        return JNet.request(METHOD_COPY, sourceUrl, null, headers);
    }

    /**
     * MOVE - Move a resource to a destination
     */
    public static String move(String sourceUrl, String destinationUrl) {
        return move(sourceUrl, destinationUrl, true);
    }

    public static String move(String sourceUrl, String destinationUrl, boolean overwrite) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Destination", destinationUrl);
        headers.put("Overwrite", overwrite ? "T" : "F");
        return JNet.request(METHOD_MOVE, sourceUrl, null, headers);
    }

    /**
     * LOCK - Lock a resource
     */
    public static String lock(String url, String owner, long timeoutSeconds) {
        String body = String.format(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:lockinfo xmlns:D='DAV:'>\n" +
            "  <D:lockscope><D:exclusive/></D:lockscope>\n" +
            "  <D:locktype><D:write/></D:locktype>\n" +
            "  <D:owner><D:href>%s</D:href></D:owner>\n" +
            "</D:lockinfo>", owner);

        Map<String, String> headers = new HashMap<>();
        headers.put("Timeout", "Second-" + timeoutSeconds);
        headers.put("Content-Type", "application/xml; charset=utf-8");

        return JNet.request(METHOD_LOCK, url, body, headers);
    }

    /**
     * UNLOCK - Unlock a resource
     */
    public static String unlock(String url, String lockToken) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Lock-Token", "<" + lockToken + ">");
        return JNet.request(METHOD_UNLOCK, url, null, headers);
    }
}
