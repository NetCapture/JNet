package com.jnet.auth;

import com.jnet.core.Request;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HTTP Digest authentication supporting the RFC 7616 MD5, SHA-256 and
 * SHA-512/256 algorithm families.
 */
public class DigestAuth implements Auth {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final String username;
    private final String password;
    private volatile Challenge challenge;

    public DigestAuth(String username, String password) {
        requireCredential(username, "Username");
        requireCredential(password, "Password");
        String normalizedUsername = Normalizer.normalize(username, Normalizer.Form.NFC);
        if (normalizedUsername.indexOf(':') >= 0) {
            throw new IllegalArgumentException("Username must not contain ':'");
        }
        this.username = normalizedUsername;
        this.password = Normalizer.normalize(password, Normalizer.Form.NFC);
    }

    public void parseChallenge(String wwwAuthenticate) {
        if (wwwAuthenticate == null) {
            throw new IllegalArgumentException("WWW-Authenticate header must not be null");
        }
        rejectLineBreaks(wwwAuthenticate, "WWW-Authenticate header");

        int index = 0;
        while (index < wwwAuthenticate.length() && isOptionalWhitespace(wwwAuthenticate.charAt(index))) {
            index++;
        }
        int schemeStart = index;
        while (index < wwwAuthenticate.length() && isTokenCharacter(wwwAuthenticate.charAt(index))) {
            index++;
        }
        if (schemeStart == index
                || !"Digest".equalsIgnoreCase(wwwAuthenticate.substring(schemeStart, index))
                || index >= wwwAuthenticate.length()
                || !isOptionalWhitespace(wwwAuthenticate.charAt(index))) {
            throw new IllegalArgumentException("Invalid WWW-Authenticate header");
        }

        Map<String, String> params = parseAuthParams(wwwAuthenticate.substring(index));
        String realm = params.get("realm");
        String nonce = params.get("nonce");
        if (realm == null || nonce == null || nonce.isEmpty()) {
            throw new IllegalArgumentException("Missing required parameters: realm and nonce");
        }
        rejectLineBreaks(realm, "Digest realm");
        rejectLineBreaks(nonce, "Digest nonce");

        String charset = params.get("charset");
        if (charset != null && !"UTF-8".equalsIgnoreCase(charset)) {
            throw new IllegalArgumentException("Unsupported Digest charset: " + charset);
        }

        DigestAlgorithm algorithm = DigestAlgorithm.parse(params.get("algorithm"));
        String qop = selectQop(params.get("qop"));
        String opaque = params.get("opaque");
        if (opaque != null) {
            rejectLineBreaks(opaque, "Digest opaque value");
        }
        String userhashValue = params.get("userhash");
        boolean userhash = false;
        if (userhashValue != null) {
            if ("true".equalsIgnoreCase(userhashValue)) {
                userhash = true;
            } else if (!"false".equalsIgnoreCase(userhashValue)) {
                throw new IllegalArgumentException("Invalid Digest userhash value: " + userhashValue);
            }
        }
        this.challenge = new Challenge(realm, nonce, qop, opaque, algorithm, userhash);
    }

    @Override
    public Request apply(Request request) {
        if (request == null) {
            throw new IllegalArgumentException("Request must not be null");
        }
        Challenge current = challenge;
        if (current == null) {
            return request;
        }
        if ("auth-int".equals(current.qop)
                && request.getBodyPublisher() != null && request.getBody() == null) {
            throw new IllegalStateException(
                    "Digest qop=auth-int requires a replayable String request body");
        }

        String uri = digestUri(request);
        boolean needsCnonce = current.qop != null || current.algorithm.session;
        String cnonce = needsCnonce ? generateCnonce() : null;
        String ncValue = current.qop != null ? current.nextNonceCount() : null;
        String response = calculateResponse(current, request, uri, cnonce, ncValue);

        StringBuilder authorization = new StringBuilder(192);
        if (current.userhash) {
            authorization.append("Digest username=")
                    .append(quote(hash(current.algorithm, username + ":" + current.realm)));
        } else if (containsNonAscii(username)) {
            authorization.append("Digest username*=UTF-8''").append(encodeExtendedValue(username));
        } else {
            authorization.append("Digest username=").append(quote(username));
        }
        authorization.append(", realm=").append(quote(current.realm));
        authorization.append(", nonce=").append(quote(current.nonce));
        authorization.append(", uri=").append(quote(uri));
        authorization.append(", response=").append(quote(response));
        authorization.append(", algorithm=").append(current.algorithm.token);

        if (current.qop != null) {
            authorization.append(", qop=").append(current.qop);
            authorization.append(", nc=").append(ncValue);
        }
        if (cnonce != null) {
            authorization.append(", cnonce=").append(quote(cnonce));
        }
        if (current.opaque != null) {
            authorization.append(", opaque=").append(quote(current.opaque));
        }
        if (current.userhash) {
            authorization.append(", userhash=true");
        }

        return request.toBuilder()
                .header("Authorization", authorization.toString())
                .build();
    }

    public void reset() {
        this.challenge = null;
    }

    public boolean isReady() {
        return challenge != null;
    }

    private String calculateResponse(Challenge current, Request request, String uri,
            String cnonce, String ncValue) {
        String ha1 = hash(current.algorithm, username + ":" + current.realm + ":" + password);
        if (current.algorithm.session) {
            ha1 = hash(current.algorithm, ha1 + ":" + current.nonce + ":" + cnonce);
        }

        String a2 = request.getMethod() + ":" + uri;
        if ("auth-int".equals(current.qop)) {
            String entityBody = request.getBody() == null ? "" : request.getBody();
            a2 += ":" + hash(current.algorithm, entityBody);
        }
        String ha2 = hash(current.algorithm, a2);

        if (current.qop == null) {
            return hash(current.algorithm, ha1 + ":" + current.nonce + ":" + ha2);
        }
        return hash(current.algorithm, ha1 + ":" + current.nonce + ":" + ncValue + ":"
                + cnonce + ":" + current.qop + ":" + ha2);
    }

    private static String digestUri(Request request) {
        String path = request.getUri().getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        String query = request.getUri().getRawQuery();
        return query == null ? path : path + "?" + query;
    }

    private static String selectQop(String offeredQop) {
        if (offeredQop == null) {
            return null;
        }
        boolean authInt = false;
        for (String option : offeredQop.split(",")) {
            String normalized = option.trim().toLowerCase(Locale.ROOT);
            if ("auth".equals(normalized)) {
                return "auth";
            }
            if ("auth-int".equals(normalized)) {
                authInt = true;
            }
        }
        if (authInt) {
            return "auth-int";
        }
        throw new IllegalArgumentException("Unsupported Digest qop: " + offeredQop);
    }

    private static Map<String, String> parseAuthParams(String input) {
        Map<String, String> result = new LinkedHashMap<>();
        int index = 0;
        while (true) {
            while (index < input.length()
                    && (input.charAt(index) == ',' || isOptionalWhitespace(input.charAt(index)))) {
                index++;
            }
            if (index >= input.length()) {
                return result;
            }

            int nameStart = index;
            while (index < input.length() && isTokenCharacter(input.charAt(index))) {
                index++;
            }
            if (nameStart == index) {
                throw new IllegalArgumentException("Malformed Digest authentication parameter");
            }
            String name = input.substring(nameStart, index).toLowerCase(Locale.ROOT);
            while (index < input.length() && isOptionalWhitespace(input.charAt(index))) {
                index++;
            }
            if (index >= input.length() || input.charAt(index) != '=') {
                throw new IllegalArgumentException("Missing '=' after Digest parameter: " + name);
            }
            index++;
            while (index < input.length() && isOptionalWhitespace(input.charAt(index))) {
                index++;
            }
            if (index >= input.length()) {
                throw new IllegalArgumentException("Missing value for Digest parameter: " + name);
            }

            String value;
            if (input.charAt(index) == '"') {
                StringBuilder decoded = new StringBuilder();
                index++;
                boolean closed = false;
                while (index < input.length()) {
                    char current = input.charAt(index++);
                    if (current == '"') {
                        closed = true;
                        break;
                    }
                    if (current == '\\') {
                        if (index >= input.length()) {
                            throw new IllegalArgumentException("Incomplete quoted Digest parameter: " + name);
                        }
                        current = input.charAt(index++);
                    }
                    decoded.append(current);
                }
                if (!closed) {
                    throw new IllegalArgumentException("Unterminated quoted Digest parameter: " + name);
                }
                value = decoded.toString();
                while (index < input.length() && isOptionalWhitespace(input.charAt(index))) {
                    index++;
                }
                if (index < input.length() && input.charAt(index) != ',') {
                    throw new IllegalArgumentException("Malformed Digest parameter after: " + name);
                }
            } else {
                int valueStart = index;
                while (index < input.length() && input.charAt(index) != ',') {
                    index++;
                }
                value = input.substring(valueStart, index).trim();
                if (value.isEmpty()) {
                    throw new IllegalArgumentException("Missing value for Digest parameter: " + name);
                }
            }

            if (result.putIfAbsent(name, value) != null) {
                throw new IllegalArgumentException("Duplicate Digest parameter: " + name);
            }
        }
    }

    private static String hash(DigestAlgorithm algorithm, String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm.jcaName);
            return toHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Hash algorithm not available: " + algorithm.token, e);
        }
    }

    private static String generateCnonce() {
        byte[] random = new byte[16];
        SECURE_RANDOM.nextBytes(random);
        return toHex(random);
    }

    private static String toHex(byte[] bytes) {
        char[] hex = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            hex[i * 2] = alphabet[value >>> 4];
            hex[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(hex);
    }

    private static boolean containsNonAscii(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) > 0x7f) {
                return true;
            }
        }
        return false;
    }

    private static String encodeExtendedValue(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder encoded = new StringBuilder(bytes.length * 3);
        char[] hex = "0123456789ABCDEF".toCharArray();
        for (byte current : bytes) {
            int unsigned = current & 0xff;
            if (isAttributeCharacter(unsigned)) {
                encoded.append((char) unsigned);
            } else {
                encoded.append('%')
                        .append(hex[unsigned >>> 4])
                        .append(hex[unsigned & 0x0f]);
            }
        }
        return encoded.toString();
    }

    private static boolean isAttributeCharacter(int value) {
        return (value >= 'a' && value <= 'z') || (value >= 'A' && value <= 'Z')
                || (value >= '0' && value <= '9') || value == '!' || value == '#'
                || value == '$' || value == '&' || value == '+' || value == '-'
                || value == '.' || value == '^' || value == '_' || value == '`'
                || value == '|' || value == '~';
    }

    private static String quote(String value) {
        StringBuilder quoted = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '"' || current == '\\') {
                quoted.append('\\');
            }
            quoted.append(current);
        }
        return quoted.append('"').toString();
    }

    private static void requireCredential(String value, String label) {
        if (value == null) {
            throw new IllegalArgumentException(label + " must not be null");
        }
        rejectLineBreaks(value, label);
    }

    private static void rejectLineBreaks(String value, String label) {
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == 0x7f || current < 0x20) {
                throw new IllegalArgumentException(label + " contains a prohibited control character");
            }
        }
    }

    private static boolean isOptionalWhitespace(char value) {
        return value == ' ' || value == '\t';
    }

    private static boolean isTokenCharacter(char value) {
        if ((value >= '0' && value <= '9') || (value >= 'A' && value <= 'Z')
                || (value >= 'a' && value <= 'z')) {
            return true;
        }
        switch (value) {
            case '!':
            case '#':
            case '$':
            case '%':
            case '&':
            case '\'':
            case '*':
            case '+':
            case '-':
            case '.':
            case '^':
            case '_':
            case '`':
            case '|':
            case '~':
                return true;
            default:
                return false;
        }
    }

    private static final class Challenge {
        private final String realm;
        private final String nonce;
        private final String qop;
        private final String opaque;
        private final DigestAlgorithm algorithm;
        private final boolean userhash;
        private final AtomicLong nonceCount = new AtomicLong();

        private Challenge(String realm, String nonce, String qop, String opaque,
                DigestAlgorithm algorithm, boolean userhash) {
            this.realm = realm;
            this.nonce = nonce;
            this.qop = qop;
            this.opaque = opaque;
            this.algorithm = algorithm;
            this.userhash = userhash;
        }

        private String nextNonceCount() {
            long value = nonceCount.incrementAndGet();
            if (value <= 0 || value > 0xffffffffL) {
                throw new IllegalStateException("Digest nonce-count exhausted");
            }
            char[] result = new char[8];
            char[] alphabet = "0123456789abcdef".toCharArray();
            for (int i = result.length - 1; i >= 0; i--) {
                result[i] = alphabet[(int) (value & 0x0f)];
                value >>>= 4;
            }
            return new String(result);
        }
    }

    private enum DigestAlgorithm {
        MD5("MD5", "MD5", false),
        MD5_SESS("MD5-sess", "MD5", true),
        SHA_256("SHA-256", "SHA-256", false),
        SHA_256_SESS("SHA-256-sess", "SHA-256", true),
        SHA_512_256("SHA-512-256", "SHA-512/256", false),
        SHA_512_256_SESS("SHA-512-256-sess", "SHA-512/256", true);

        private final String token;
        private final String jcaName;
        private final boolean session;

        DigestAlgorithm(String token, String jcaName, boolean session) {
            this.token = token;
            this.jcaName = jcaName;
            this.session = session;
        }

        private static DigestAlgorithm parse(String token) {
            if (token == null || token.isEmpty()) {
                return MD5;
            }
            for (DigestAlgorithm candidate : values()) {
                if (candidate.token.equalsIgnoreCase(token)) {
                    return candidate;
                }
            }
            throw new IllegalArgumentException("Unsupported Digest algorithm: " + token);
        }
    }
}
