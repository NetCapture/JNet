package com.jnet.rtsp;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Stateful Basic/Digest authentication for one RTSP client. */
final class RtspAuthenticator {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String username;
    private final String password;
    private Challenge challenge;
    private int nonceCount;

    RtspAuthenticator(String username, String password) {
        this.username = username;
        this.password = password;
    }

    boolean hasCredentials() {
        return username != null && password != null;
    }

    String authorization(String explicit, RtspMethod method, String requestUri) throws IOException {
        if (explicit != null && !explicit.isEmpty()) {
            return explicit;
        }
        if (challenge == null || !hasCredentials()) {
            return null;
        }
        return challenge.authorization(username, password, method.getMethod(), requestUri, ++nonceCount);
    }

    boolean accept(String header) throws IOException {
        Challenge parsed = Challenge.parse(header);
        if (parsed == null) {
            return false;
        }
        challenge = parsed;
        nonceCount = 0;
        return true;
    }

    void reset() {
        challenge = null;
        nonceCount = 0;
    }

    private static final class Challenge {
        private final String scheme;
        private final Map<String, String> parameters;

        private Challenge(String scheme, Map<String, String> parameters) {
            this.scheme = scheme;
            this.parameters = parameters;
        }

        private static Challenge parse(String header) throws IOException {
            if (header == null || header.trim().isEmpty()) {
                return null;
            }
            String value = header.trim();
            int separator = 0;
            while (separator < value.length() && !Character.isWhitespace(value.charAt(separator))) {
                separator++;
            }
            String scheme = value.substring(0, separator);
            if (!"basic".equalsIgnoreCase(scheme) && !"digest".equalsIgnoreCase(scheme)) {
                throw new IOException("Unsupported RTSP authentication scheme: " + scheme);
            }
            Map<String, String> parameters = separator == value.length()
                    ? new LinkedHashMap<>()
                    : parseParameters(value.substring(separator + 1));
            return new Challenge(scheme.toLowerCase(Locale.ROOT), parameters);
        }

        private String authorization(String username, String password, String method,
                                     String requestUri, int nonceCount) throws IOException {
            if ("basic".equals(scheme)) {
                String token = username + ":" + password;
                return "Basic " + Base64.getEncoder()
                        .encodeToString(token.getBytes(StandardCharsets.UTF_8));
            }

            String realm = required("realm");
            String nonce = required("nonce");
            String algorithm = parameters.getOrDefault("algorithm", "MD5");
            String digestName = digestName(algorithm);
            String qop = selectQop(parameters.get("qop"));
            String cnonce = randomHex(16);
            String nc = String.format(Locale.ROOT, "%08x", nonceCount);

            String ha1 = hash(digestName, username + ":" + realm + ":" + password);
            boolean sessionAlgorithm = algorithm.toLowerCase(Locale.ROOT).endsWith("-sess");
            if (sessionAlgorithm) {
                ha1 = hash(digestName, ha1 + ":" + nonce + ":" + cnonce);
            }
            String ha2 = hash(digestName, method + ":" + requestUri);
            String response = qop == null
                    ? hash(digestName, ha1 + ":" + nonce + ":" + ha2)
                    : hash(digestName, ha1 + ":" + nonce + ":" + nc + ":" + cnonce
                            + ":" + qop + ":" + ha2);

            StringBuilder result = new StringBuilder("Digest username=\"")
                    .append(quote(username)).append("\", realm=\"").append(quote(realm))
                    .append("\", nonce=\"").append(quote(nonce)).append("\", uri=\"")
                    .append(quote(requestUri)).append("\", response=\"").append(response).append('"');
            appendParameters(result, algorithm, qop, nc, cnonce, sessionAlgorithm);
            return result.toString();
        }

        private void appendParameters(StringBuilder result, String algorithm, String qop,
                                      String nc, String cnonce, boolean sessionAlgorithm) {
            String opaque = parameters.get("opaque");
            if (opaque != null) {
                result.append(", opaque=\"").append(quote(opaque)).append('"');
            }
            if (parameters.containsKey("algorithm")) {
                result.append(", algorithm=").append(algorithm);
            }
            if (qop != null) {
                result.append(", qop=").append(qop)
                        .append(", nc=").append(nc)
                        .append(", cnonce=\"").append(cnonce).append('"');
            } else if (sessionAlgorithm) {
                result.append(", cnonce=\"").append(cnonce).append('"');
            }
        }

        private String required(String name) throws IOException {
            String value = parameters.get(name);
            if (value == null || value.isEmpty()) {
                throw new IOException("RTSP authentication challenge is missing " + name);
            }
            return value;
        }

        private static Map<String, String> parseParameters(String value) {
            Map<String, String> result = new LinkedHashMap<>();
            int index = 0;
            while (index < value.length()) {
                while (index < value.length()
                        && (value.charAt(index) == ',' || Character.isWhitespace(value.charAt(index)))) {
                    index++;
                }
                int equals = value.indexOf('=', index);
                if (equals < 0) {
                    break;
                }
                String name = value.substring(index, equals).trim().toLowerCase(Locale.ROOT);
                index = equals + 1;
                ParsedParameter parsed = parseParameter(value, index);
                index = parsed.nextIndex;
                if (!name.isEmpty()) {
                    result.put(name, parsed.value);
                }
            }
            return result;
        }

        private static ParsedParameter parseParameter(String source, int start) {
            while (start < source.length() && Character.isWhitespace(source.charAt(start))) {
                start++;
            }
            if (start < source.length() && source.charAt(start) == '"') {
                StringBuilder decoded = new StringBuilder();
                int index = start + 1;
                while (index < source.length()) {
                    char current = source.charAt(index++);
                    if (current == '\\' && index < source.length()) {
                        decoded.append(source.charAt(index++));
                    } else if (current == '"') {
                        break;
                    } else {
                        decoded.append(current);
                    }
                }
                return new ParsedParameter(decoded.toString(), index);
            }
            int comma = source.indexOf(',', start);
            int end = comma < 0 ? source.length() : comma;
            return new ParsedParameter(source.substring(start, end).trim(), end);
        }

        private static String selectQop(String value) throws IOException {
            if (value == null || value.trim().isEmpty()) {
                return null;
            }
            for (String option : value.split(",")) {
                if ("auth".equalsIgnoreCase(option.trim())) {
                    return "auth";
                }
            }
            throw new IOException("RTSP Digest authentication requires unsupported qop: " + value);
        }

        private static String digestName(String algorithm) throws IOException {
            String normalized = algorithm.toUpperCase(Locale.ROOT);
            if (normalized.endsWith("-SESS")) {
                normalized = normalized.substring(0, normalized.length() - 5);
            }
            if ("MD5".equals(normalized) || "SHA-256".equals(normalized)) {
                return normalized;
            }
            if ("SHA-512-256".equals(normalized) || "SHA-512/256".equals(normalized)) {
                return "SHA-512/256";
            }
            throw new IOException("Unsupported RTSP Digest algorithm: " + algorithm);
        }

        private static String hash(String algorithm, String value) throws IOException {
            try {
                return hex(MessageDigest.getInstance(algorithm)
                        .digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (NoSuchAlgorithmException e) {
                throw new IOException("Digest algorithm is unavailable: " + algorithm, e);
            }
        }

        private static String randomHex(int bytes) {
            byte[] value = new byte[bytes];
            RANDOM.nextBytes(value);
            return hex(value);
        }

        private static String hex(byte[] value) {
            StringBuilder result = new StringBuilder(value.length * 2);
            for (byte current : value) {
                result.append(Character.forDigit((current >>> 4) & 0x0f, 16));
                result.append(Character.forDigit(current & 0x0f, 16));
            }
            return result.toString();
        }

        private static String quote(String value) {
            return value.replace("\\", "\\\\").replace("\"", "\\\"");
        }
    }

    private static final class ParsedParameter {
        private final String value;
        private final int nextIndex;

        private ParsedParameter(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
