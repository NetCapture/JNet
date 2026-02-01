package com.jnet.auth;

import com.jnet.core.Request;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * HTTP Digest 认证实现
 * 支持 RFC 7616 (Digest Access Authentication)
 */
public class DigestAuth implements Auth {
    private final String username;
    private final String password;
    private final AtomicInteger nc = new AtomicInteger(1);

    private volatile String realm;
    private volatile String nonce;
    private volatile String qop;
    private volatile String opaque;
    private volatile String algorithm = "MD5";

    public DigestAuth(String username, String password) {
        if (username == null || password == null) {
            throw new IllegalArgumentException("Username and password must not be null");
        }
        this.username = username;
        this.password = password;
    }

    public void parseChallenge(String wwwAuthenticate) {
        if (wwwAuthenticate == null || !wwwAuthenticate.startsWith("Digest ")) {
            throw new IllegalArgumentException("Invalid WWW-Authenticate header");
        }

        Map<String, String> params = parseAuthParams(wwwAuthenticate.substring(7));

        this.realm = params.get("realm");
        this.nonce = params.get("nonce");
        this.qop = params.get("qop");
        this.opaque = params.get("opaque");

        String algo = params.get("algorithm");
        if (algo != null) {
            this.algorithm = algo;
        }

        if (realm == null || nonce == null) {
            throw new IllegalArgumentException("Missing required parameters: realm and nonce");
        }
    }

    @Override
    public Request apply(Request request) {
        if (nonce == null) {
            return request;
        }

        String uri = request.getUri().getPath();
        if (request.getUri().getQuery() != null) {
            uri += "?" + request.getUri().getQuery();
        }

        String method = request.getMethod();
        String cnonce = generateCnonce();
        String ncValue = String.format("%08x", nc.getAndIncrement());

        String response = calculateResponse(method, uri, cnonce, ncValue);

        StringBuilder authHeader = new StringBuilder("Digest ");
        authHeader.append("username=\"").append(username).append("\", ");
        authHeader.append("realm=\"").append(realm).append("\", ");
        authHeader.append("nonce=\"").append(nonce).append("\", ");
        authHeader.append("uri=\"").append(uri).append("\", ");
        authHeader.append("response=\"").append(response).append("\"");

        if (qop != null) {
            authHeader.append(", qop=").append(qop);
            authHeader.append(", nc=").append(ncValue);
            authHeader.append(", cnonce=\"").append(cnonce).append("\"");
        }

        if (opaque != null) {
            authHeader.append(", opaque=\"").append(opaque).append("\"");
        }

        if (!"MD5".equals(algorithm)) {
            authHeader.append(", algorithm=").append(algorithm);
        }

        return request.toBuilder()
                .header("Authorization", authHeader.toString())
                .build();
    }

    private String calculateResponse(String method, String uri, String cnonce, String ncValue) {
        String ha1 = hash(username + ":" + realm + ":" + password);
        String ha2 = hash(method + ":" + uri);

        String response;
        if (qop == null) {
            response = hash(ha1 + ":" + nonce + ":" + ha2);
        } else if ("auth".equals(qop) || "auth-int".equals(qop)) {
            response = hash(ha1 + ":" + nonce + ":" + ncValue + ":" + cnonce + ":" + qop + ":" + ha2);
        } else {
            throw new IllegalStateException("Unsupported qop: " + qop);
        }

        return response;
    }

    private String hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm.replace("-sess", ""));
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Hash algorithm not available: " + algorithm, e);
        }
    }

    private String generateCnonce() {
        long timestamp = System.nanoTime();
        return hash(String.valueOf(timestamp)).substring(0, 16);
    }

    private Map<String, String> parseAuthParams(String params) {
        Map<String, String> result = new HashMap<>();
        String[] pairs = params.split(",\\s*");

        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                String key = pair.substring(0, eq).trim();
                String value = pair.substring(eq + 1).trim();

                if (value.startsWith("\"") && value.endsWith("\"")) {
                    value = value.substring(1, value.length() - 1);
                }

                result.put(key, value);
            }
        }

        return result;
    }

    public void reset() {
        this.realm = null;
        this.nonce = null;
        this.qop = null;
        this.opaque = null;
        this.algorithm = "MD5";
        this.nc.set(1);
    }

    public boolean isReady() {
        return nonce != null && realm != null;
    }
}
