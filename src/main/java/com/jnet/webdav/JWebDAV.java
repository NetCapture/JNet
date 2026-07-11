package com.jnet.webdav;

import com.jnet.core.JNet;
import com.jnet.core.Response;
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
        return propfindResponse(url, depth).getBody();
    }

    public static Response propfindResponse(String url, int depth) {
        if (depth != 0 && depth != 1) {
            throw new IllegalArgumentException("depth must be 0 or 1");
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Depth", String.valueOf(depth));
        return JNet.requestResponse(METHOD_PROPFIND, url, null, headers);
    }

    /**
     * MKCOL - Create a new collection (directory)
     */
    public static String mkcol(String url) {
        return mkcolResponse(url).getBody();
    }

    public static Response mkcolResponse(String url) {
        return JNet.requestResponse(METHOD_MKCOL, url, null);
    }

    /**
     * COPY - Copy a resource to a destination
     */
    public static String copy(String sourceUrl, String destinationUrl) {
        return copy(sourceUrl, destinationUrl, true);
    }

    public static String copy(String sourceUrl, String destinationUrl, boolean overwrite) {
        return copyResponse(sourceUrl, destinationUrl, overwrite).getBody();
    }

    public static Response copyResponse(String sourceUrl, String destinationUrl, boolean overwrite) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Destination", destinationUrl);
        headers.put("Overwrite", overwrite ? "T" : "F");
        return JNet.requestResponse(METHOD_COPY, sourceUrl, null, headers);
    }

    /**
     * MOVE - Move a resource to a destination
     */
    public static String move(String sourceUrl, String destinationUrl) {
        return move(sourceUrl, destinationUrl, true);
    }

    public static String move(String sourceUrl, String destinationUrl, boolean overwrite) {
        return moveResponse(sourceUrl, destinationUrl, overwrite).getBody();
    }

    public static Response moveResponse(String sourceUrl, String destinationUrl, boolean overwrite) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Destination", destinationUrl);
        headers.put("Overwrite", overwrite ? "T" : "F");
        return JNet.requestResponse(METHOD_MOVE, sourceUrl, null, headers);
    }

    /**
     * LOCK - Lock a resource
     */
    public static String lock(String url, String owner, long timeoutSeconds) {
        return lockResponse(url, owner, timeoutSeconds).getBody();
    }

    public static Response lockResponse(String url, String owner, long timeoutSeconds) {
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("timeoutSeconds must be positive");
        }
        String body = String.format(
            "<?xml version=\"1.0\" encoding=\"utf-8\" ?>\n" +
            "<D:lockinfo xmlns:D='DAV:'>\n" +
            "  <D:lockscope><D:exclusive/></D:lockscope>\n" +
            "  <D:locktype><D:write/></D:locktype>\n" +
            "  <D:owner><D:href>%s</D:href></D:owner>\n" +
            "</D:lockinfo>", escapeXml(owner == null ? "" : owner));

        Map<String, String> headers = new HashMap<>();
        headers.put("Timeout", "Second-" + timeoutSeconds);
        headers.put("Content-Type", "application/xml; charset=utf-8");

        return JNet.requestResponse(METHOD_LOCK, url, body, headers);
    }

    /**
     * UNLOCK - Unlock a resource
     */
    public static String unlock(String url, String lockToken) {
        return unlockResponse(url, lockToken).getBody();
    }

    public static Response unlockResponse(String url, String lockToken) {
        if (lockToken == null || lockToken.trim().isEmpty()) {
            throw new IllegalArgumentException("lockToken cannot be null or empty");
        }
        String token = lockToken.trim();
        if (token.startsWith("<") && token.endsWith(">")) {
            token = token.substring(1, token.length() - 1);
        }
        if (token.isEmpty() || token.indexOf('<') >= 0 || token.indexOf('>') >= 0) {
            throw new IllegalArgumentException("lockToken must contain one non-empty state token");
        }
        for (int i = 0; i < token.length(); i++) {
            if (Character.isWhitespace(token.charAt(i)) || Character.isISOControl(token.charAt(i))) {
                throw new IllegalArgumentException("lockToken must not contain whitespace or control characters");
            }
        }
        Map<String, String> headers = new HashMap<>();
        headers.put("Lock-Token", "<" + token + ">");
        return JNet.requestResponse(METHOD_UNLOCK, url, null, headers);
    }

    private static String escapeXml(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 16);
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (!isXmlCharacter(codePoint)) {
                throw new IllegalArgumentException("XML value contains an illegal character");
            }
            switch (codePoint) {
                case '&': escaped.append("&amp;"); break;
                case '<': escaped.append("&lt;"); break;
                case '>': escaped.append("&gt;"); break;
                case '"': escaped.append("&quot;"); break;
                case '\'': escaped.append("&apos;"); break;
                default: escaped.appendCodePoint(codePoint);
            }
        }
        return escaped.toString();
    }

    private static boolean isXmlCharacter(int codePoint) {
        return codePoint == 0x9 || codePoint == 0xA || codePoint == 0xD
                || (codePoint >= 0x20 && codePoint <= 0xD7FF)
                || (codePoint >= 0xE000 && codePoint <= 0xFFFD)
                || (codePoint >= 0x10000 && codePoint <= 0x10FFFF);
    }
}
