package com.jnet.core;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.File;
import java.io.FileInputStream;
import java.net.IDN;
import java.net.Socket;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Secure SSL/TLS configuration with optional hostname-scoped certificate pins. */
public class SSLConfigEnhanced {
    private final String[] protocols;
    private final String[] cipherSuites;
    private final Map<String, Set<String>> pinnedCertificates;
    private final SSLContext sslContext;

    private SSLConfigEnhanced(Builder builder) throws Exception {
        this.pinnedCertificates = immutablePins(builder.pinnedCertificates);

        TrustManager[] trustManagers;
        if (builder.trustAllCertificates) {
            trustManagers = createTrustAllManagers();
        } else if (builder.customTrustStore == null && pinnedCertificates.isEmpty()) {
            trustManagers = null;
        } else {
            X509TrustManager baseTrustManager = builder.customTrustStore == null
                    ? getDefaultTrustManager()
                    : createCustomTrustManager(builder.customTrustStore, builder.trustStorePassword);
            trustManagers = pinnedCertificates.isEmpty()
                    ? (builder.customTrustStore == null ? null : new TrustManager[] { baseTrustManager })
                    : new TrustManager[] { new PinningTrustManager(baseTrustManager) };
        }

        KeyManager[] keyManagers = builder.clientCertificate == null
                ? null
                : createKeyManagers(builder.clientCertificate, builder.clientCertPassword);
        this.sslContext = createSSLContext(keyManagers, trustManagers);

        SSLParameters supported = sslContext.getSupportedSSLParameters();
        this.protocols = resolveSupported(builder.protocols, supported.getProtocols(),
                builder.filterUnsupportedProtocols, "protocol");
        this.cipherSuites = builder.cipherSuites == null ? null
                : resolveSupported(builder.cipherSuites, supported.getCipherSuites(),
                        builder.filterUnsupportedCiphers, "cipher suite");
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static SSLConfigEnhanced tls13Only() throws Exception {
        return newBuilder().tls13Only().strongCiphersOnly().build();
    }

    public static SSLConfigEnhanced defaultConfig() throws Exception {
        return newBuilder().build();
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    /**
     * Returns fresh parameters with HTTPS endpoint identification enabled. Callers
     * using the raw {@link SSLContext} must apply these parameters to their socket
     * or engine before the TLS handshake.
     */
    public SSLParameters getSSLParameters() {
        SSLParameters parameters = sslContext.getDefaultSSLParameters();
        parameters.setProtocols(protocols.clone());
        if (cipherSuites != null) {
            parameters.setCipherSuites(cipherSuites.clone());
        }
        parameters.setEndpointIdentificationAlgorithm("HTTPS");
        return parameters;
    }

    private SSLContext createSSLContext(KeyManager[] keyManagers, TrustManager[] trustManagers)
            throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, new SecureRandom());
        return context;
    }

    private static X509TrustManager getDefaultTrustManager() throws Exception {
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init((KeyStore) null);
        return findX509TrustManager(factory.getTrustManagers());
    }

    private static TrustManager[] createTrustAllManagers() {
        return new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };
    }

    private static X509TrustManager createCustomTrustManager(File trustStore, char[] password)
            throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] passwordCopy = password == null ? null : password.clone();
        try (FileInputStream input = new FileInputStream(trustStore)) {
            keyStore.load(input, passwordCopy);
        } finally {
            if (passwordCopy != null) {
                Arrays.fill(passwordCopy, '\0');
            }
        }
        TrustManagerFactory factory = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        factory.init(keyStore);
        return findX509TrustManager(factory.getTrustManagers());
    }

    private static KeyManager[] createKeyManagers(File keyStoreFile, char[] password) throws Exception {
        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        char[] passwordCopy = password == null ? null : password.clone();
        try {
            try (FileInputStream input = new FileInputStream(keyStoreFile)) {
                keyStore.load(input, passwordCopy);
            }
            KeyManagerFactory factory = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            factory.init(keyStore, passwordCopy);
            return factory.getKeyManagers();
        } finally {
            if (passwordCopy != null) {
                Arrays.fill(passwordCopy, '\0');
            }
        }
    }

    private static X509TrustManager findX509TrustManager(TrustManager[] managers) {
        for (TrustManager manager : managers) {
            if (manager instanceof X509TrustManager) {
                return (X509TrustManager) manager;
            }
        }
        throw new IllegalStateException("No X509TrustManager available");
    }

    private static String[] resolveSupported(String[] requested, String[] supported,
            boolean filterUnsupported, String label) {
        Set<String> supportedValues = new HashSet<>(Arrays.asList(supported));
        List<String> resolved = new ArrayList<>(requested.length);
        for (String value : requested) {
            if (supportedValues.contains(value)) {
                resolved.add(value);
            } else if (!filterUnsupported) {
                throw new IllegalArgumentException("Unsupported TLS " + label + ": " + value);
            }
        }
        if (resolved.isEmpty()) {
            throw new IllegalArgumentException("No supported TLS " + label + " configured");
        }
        return resolved.toArray(new String[0]);
    }

    private static Map<String, Set<String>> immutablePins(Map<String, Set<String>> source) {
        Map<String, Set<String>> copy = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : source.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableSet(new LinkedHashSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String certificateFingerprint(X509Certificate certificate)
            throws CertificateException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(certificate.getEncoded()));
        } catch (Exception e) {
            throw new CertificateException("Failed to calculate certificate fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        char[] result = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = alphabet[value >>> 4];
            result[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(result);
    }

    private static String canonicalHostname(String hostname) {
        if (hostname == null || hostname.trim().isEmpty()) {
            throw new IllegalArgumentException("Hostname cannot be null or empty");
        }
        String normalized = hostname.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        if (normalized.indexOf('*') >= 0) {
            throw new IllegalArgumentException("Certificate pin hostname must be exact");
        }
        if (normalized.indexOf(':') < 0) {
            normalized = IDN.toASCII(normalized, IDN.USE_STD3_ASCII_RULES);
            while (normalized.endsWith(".")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Hostname cannot be empty");
        }
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static String normalizeFingerprint(String fingerprint) {
        if (fingerprint == null) {
            throw new IllegalArgumentException("Fingerprint cannot be null");
        }
        String normalized = fingerprint.trim().replace(":", "").toLowerCase(Locale.ROOT);
        if (normalized.length() != 64) {
            throw new IllegalArgumentException("SHA-256 fingerprint must contain 64 hexadecimal characters");
        }
        for (int i = 0; i < normalized.length(); i++) {
            char current = normalized.charAt(i);
            if (!((current >= '0' && current <= '9') || (current >= 'a' && current <= 'f'))) {
                throw new IllegalArgumentException("SHA-256 fingerprint contains a non-hex character");
            }
        }
        return normalized;
    }

    private final class PinningTrustManager extends X509ExtendedTrustManager {
        private final X509TrustManager delegate;

        private PinningTrustManager(X509TrustManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkClientTrusted(chain, authType);
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
            delegate.checkServerTrusted(chain, authType);
            throw new CertificateException("Peer hostname unavailable for certificate pin validation");
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) delegate).checkClientTrusted(chain, authType, socket);
            } else {
                delegate.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) delegate).checkServerTrusted(chain, authType, socket);
            } else {
                delegate.checkServerTrusted(chain, authType);
            }
            String hostname = null;
            if (socket instanceof SSLSocket) {
                SSLSession session = ((SSLSocket) socket).getHandshakeSession();
                if (session != null) {
                    hostname = session.getPeerHost();
                }
            }
            verifyPins(chain, hostname);
        }

        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) delegate).checkClientTrusted(chain, authType, engine);
            } else {
                delegate.checkClientTrusted(chain, authType);
            }
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine)
                throws CertificateException {
            if (delegate instanceof X509ExtendedTrustManager) {
                ((X509ExtendedTrustManager) delegate).checkServerTrusted(chain, authType, engine);
            } else {
                delegate.checkServerTrusted(chain, authType);
            }
            verifyPins(chain, engine == null ? null : engine.getPeerHost());
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            X509Certificate[] issuers = delegate.getAcceptedIssuers();
            return issuers == null ? new X509Certificate[0] : issuers.clone();
        }

        private void verifyPins(X509Certificate[] chain, String hostname) throws CertificateException {
            if (hostname == null || hostname.isEmpty()) {
                throw new CertificateException("Peer hostname unavailable for certificate pin validation");
            }
            final String canonical;
            try {
                canonical = canonicalHostname(hostname);
            } catch (IllegalArgumentException e) {
                throw new CertificateException("Invalid peer hostname for certificate pin validation", e);
            }
            Set<String> expected = pinnedCertificates.get(canonical);
            if (expected == null) {
                return;
            }
            if (chain == null || chain.length == 0) {
                throw new CertificateException("Peer did not provide a certificate chain");
            }
            for (X509Certificate certificate : chain) {
                if (expected.contains(certificateFingerprint(certificate))) {
                    return;
                }
            }
            throw new CertificateException("Certificate pin validation failed for " + canonical);
        }
    }

    public static class Builder {
        private String[] protocols = { "TLSv1.3", "TLSv1.2" };
        private boolean filterUnsupportedProtocols = true;
        private String[] cipherSuites;
        private boolean filterUnsupportedCiphers;
        private final Map<String, Set<String>> pinnedCertificates = new HashMap<>();
        private boolean trustAllCertificates;
        private File customTrustStore;
        private char[] trustStorePassword;
        private File clientCertificate;
        private char[] clientCertPassword;

        public Builder protocols(String... protocols) {
            requireValues(protocols, "Protocols");
            this.protocols = protocols.clone();
            this.filterUnsupportedProtocols = false;
            return this;
        }

        public Builder tls13Only() {
            return protocols("TLSv1.3");
        }

        public Builder cipherSuites(String... suites) {
            requireValues(suites, "Cipher suites");
            this.cipherSuites = suites.clone();
            this.filterUnsupportedCiphers = false;
            return this;
        }

        public Builder strongCiphersOnly() {
            this.cipherSuites = new String[] {
                    "TLS_AES_256_GCM_SHA384",
                    "TLS_AES_128_GCM_SHA256",
                    "TLS_CHACHA20_POLY1305_SHA256",
                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
            };
            this.filterUnsupportedCiphers = true;
            return this;
        }

        public Builder pinCertificate(String hostname, String sha256Fingerprint) {
            String canonical = canonicalHostname(hostname);
            String fingerprint = normalizeFingerprint(sha256Fingerprint);
            Set<String> pins = pinnedCertificates.get(canonical);
            if (pins == null) {
                pins = new LinkedHashSet<>();
                pinnedCertificates.put(canonical, pins);
            }
            pins.add(fingerprint);
            return this;
        }

        /** Explicitly disables certificate-chain validation. Never use in production. */
        @Deprecated
        public Builder trustAllCertificates() {
            this.trustAllCertificates = true;
            return this;
        }

        public Builder customTrustStore(File trustStore, char[] password) {
            if (trustStore == null) {
                throw new IllegalArgumentException("Trust store cannot be null");
            }
            this.customTrustStore = trustStore;
            this.trustStorePassword = replacePassword(this.trustStorePassword, password);
            return this;
        }

        public Builder clientCertificate(File keyStore, char[] password) {
            if (keyStore == null) {
                throw new IllegalArgumentException("Client key store cannot be null");
            }
            this.clientCertificate = keyStore;
            this.clientCertPassword = replacePassword(this.clientCertPassword, password);
            return this;
        }

        /**
         * Builds the configuration and clears the builder's owned password copies.
         * Set passwords again before reusing this builder for another build.
         */
        public SSLConfigEnhanced build() throws Exception {
            try {
                if (trustAllCertificates && (!pinnedCertificates.isEmpty() || customTrustStore != null)) {
                    throw new IllegalArgumentException(
                            "trustAllCertificates cannot be combined with certificate pins or a trust store");
                }
                return new SSLConfigEnhanced(this);
            } finally {
                wipe(trustStorePassword);
                wipe(clientCertPassword);
                trustStorePassword = null;
                clientCertPassword = null;
            }
        }

        private static char[] replacePassword(char[] previous, char[] replacement) {
            wipe(previous);
            return replacement == null ? null : replacement.clone();
        }

        private static void wipe(char[] password) {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
        }

        private static void requireValues(String[] values, String label) {
            if (values == null || values.length == 0) {
                throw new IllegalArgumentException(label + " cannot be null or empty");
            }
            for (String value : values) {
                if (value == null || value.trim().isEmpty()) {
                    throw new IllegalArgumentException(label + " cannot contain a null or empty value");
                }
            }
        }
    }

    @Override
    public String toString() {
        int pinCount = 0;
        for (Set<String> pins : pinnedCertificates.values()) {
            pinCount += pins.size();
        }
        return String.format("SSLConfigEnhanced{protocols=%s, ciphers=%d, pins=%d}",
                Arrays.toString(protocols), cipherSuites == null ? 0 : cipherSuites.length, pinCount);
    }
}
