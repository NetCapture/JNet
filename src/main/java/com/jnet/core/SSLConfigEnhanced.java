package com.jnet.core;

import javax.net.ssl.*;
import java.io.*;
import java.security.*;
import java.security.cert.*;
import java.util.*;

/**
 * 增强版 SSL/TLS 配置
 * Phase 4 功能：TLS 1.3、自定义密码套件、证书锁定
 */
public class SSLConfigEnhanced {
    
    private final String[] protocols;
    private final String[] cipherSuites;
    private final Map<String, String> pinnedCertificates; // hostname -> SHA-256 fingerprint
    private final TrustManager[] trustManagers;
    private final KeyManager[] keyManagers;
    private final SSLContext sslContext;

    private SSLConfigEnhanced(Builder builder) throws Exception {
        this.protocols = builder.protocols;
        this.cipherSuites = builder.cipherSuites;
        this.pinnedCertificates = new HashMap<>(builder.pinnedCertificates);
        
        // Create trust managers
        if (builder.trustAllCertificates) {
            this.trustManagers = createTrustAllManagers();
        } else if (!pinnedCertificates.isEmpty()) {
            this.trustManagers = createPinningTrustManagers();
        } else if (builder.customTrustStore != null) {
            this.trustManagers = createCustomTrustManagers(builder.customTrustStore, builder.trustStorePassword);
        } else {
            this.trustManagers = null; // Use system default
        }
        
        // Create key managers for client certificates
        if (builder.clientCertificate != null) {
            this.keyManagers = createKeyManagers(builder.clientCertificate, builder.clientCertPassword);
        } else {
            this.keyManagers = null;
        }
        
        // Initialize SSL context
        this.sslContext = createSSLContext();
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * Phase 4.1: TLS 1.3 only configuration
     */
    public static SSLConfigEnhanced tls13Only() throws Exception {
        return newBuilder()
                .protocols("TLSv1.3")
                .strongCiphersOnly()
                .build();
    }

    /**
     * Default secure configuration
     */
    public static SSLConfigEnhanced defaultConfig() throws Exception {
        return newBuilder()
                .protocols("TLSv1.3", "TLSv1.2")
                .build();
    }

    private SSLContext createSSLContext() throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(keyManagers, trustManagers, new SecureRandom());
        return context;
    }

    public SSLContext getSSLContext() {
        return sslContext;
    }

    public SSLParameters getSSLParameters() {
        SSLParameters params = sslContext.getDefaultSSLParameters();
        if (protocols != null && protocols.length > 0) {
            params.setProtocols(protocols);
        }
        if (cipherSuites != null && cipherSuites.length > 0) {
            params.setCipherSuites(cipherSuites);
        }
        return params;
    }

    /**
     * Phase 4.3: Certificate pinning trust managers
     */
    private TrustManager[] createPinningTrustManagers() {
        return new TrustManager[] {
            new X509TrustManager() {
                private final X509TrustManager defaultTrustManager = getDefaultTrustManager();

                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) 
                        throws CertificateException {
                    defaultTrustManager.checkClientTrusted(chain, authType);
                }

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) 
                        throws CertificateException {
                    // First, validate with default trust manager
                    defaultTrustManager.checkServerTrusted(chain, authType);
                    
                    // Then check certificate pinning
                    if (!pinnedCertificates.isEmpty()) {
                        boolean pinMatched = false;
                        for (X509Certificate cert : chain) {
                            String fingerprint = getCertificateFingerprint(cert);
                            if (pinnedCertificates.containsValue(fingerprint)) {
                                pinMatched = true;
                                break;
                            }
                        }
                        if (!pinMatched) {
                            throw new CertificateException("Certificate pin validation failed");
                        }
                    }
                }

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return defaultTrustManager.getAcceptedIssuers();
                }

                private X509TrustManager getDefaultTrustManager() {
                    try {
                        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                                TrustManagerFactory.getDefaultAlgorithm());
                        tmf.init((KeyStore) null);
                        for (TrustManager tm : tmf.getTrustManagers()) {
                            if (tm instanceof X509TrustManager) {
                                return (X509TrustManager) tm;
                            }
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to get default trust manager", e);
                    }
                    throw new RuntimeException("No X509TrustManager found");
                }
            }
        };
    }

    /**
     * Trust all certificates (INSECURE - for development only)
     */
    private TrustManager[] createTrustAllManagers() {
        return new TrustManager[] {
            new X509TrustManager() {
                @Override
                public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
        };
    }

    private TrustManager[] createCustomTrustManagers(File trustStore, char[] password) 
            throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(trustStore)) {
            ks.load(fis, password);
        }
        
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(ks);
        return tmf.getTrustManagers();
    }

    private KeyManager[] createKeyManagers(File keyStore, char[] password) throws Exception {
        KeyStore ks = KeyStore.getInstance(KeyStore.getDefaultType());
        try (FileInputStream fis = new FileInputStream(keyStore)) {
            ks.load(fis, password);
        }
        
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, password);
        return kmf.getKeyManagers();
    }

    /**
     * Calculate SHA-256 fingerprint of certificate
     */
    private String getCertificateFingerprint(X509Certificate cert) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(cert.getEncoded());
            return bytesToHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate certificate fingerprint", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static class Builder {
        private String[] protocols = new String[] { "TLSv1.3", "TLSv1.2" };
        private String[] cipherSuites;
        private Map<String, String> pinnedCertificates = new HashMap<>();
        private boolean trustAllCertificates = false;
        private File customTrustStore;
        private char[] trustStorePassword;
        private File clientCertificate;
        private char[] clientCertPassword;

        /**
         * Phase 4.1: Set TLS protocols
         */
        public Builder protocols(String... protocols) {
            if (protocols == null || protocols.length == 0) {
                throw new IllegalArgumentException("Protocols cannot be null or empty");
            }
            this.protocols = protocols;
            return this;
        }

        /**
         * Phase 4.1: TLS 1.3 only
         */
        public Builder tls13Only() {
            return protocols("TLSv1.3");
        }

        /**
         * Phase 4.2: Set custom cipher suites
         */
        public Builder cipherSuites(String... suites) {
            this.cipherSuites = suites;
            return this;
        }

        /**
         * Phase 4.2: Use only strong ciphers (TLS 1.3 + strong TLS 1.2)
         */
        public Builder strongCiphersOnly() {
            this.cipherSuites = new String[] {
                // TLS 1.3 ciphers
                "TLS_AES_256_GCM_SHA384",
                "TLS_AES_128_GCM_SHA256",
                "TLS_CHACHA20_POLY1305_SHA256",
                // TLS 1.2 strong ciphers (fallback)
                "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
                "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256"
            };
            return this;
        }

        /**
         * Phase 4.3: Pin certificate by SHA-256 fingerprint
         */
        public Builder pinCertificate(String hostname, String sha256Fingerprint) {
            if (hostname == null || sha256Fingerprint == null) {
                throw new IllegalArgumentException("Hostname and fingerprint cannot be null");
            }
            pinnedCertificates.put(hostname, sha256Fingerprint.toLowerCase());
            return this;
        }

        /**
         * Trust all certificates (INSECURE - development only)
         */
        public Builder trustAllCertificates() {
            this.trustAllCertificates = true;
            return this;
        }

        /**
         * Use custom trust store
         */
        public Builder customTrustStore(File trustStore, char[] password) {
            this.customTrustStore = trustStore;
            this.trustStorePassword = password;
            return this;
        }

        /**
         * Set client certificate for mutual TLS
         */
        public Builder clientCertificate(File keyStore, char[] password) {
            this.clientCertificate = keyStore;
            this.clientCertPassword = password;
            return this;
        }

        public SSLConfigEnhanced build() throws Exception {
            return new SSLConfigEnhanced(this);
        }
    }

    @Override
    public String toString() {
        return String.format("SSLConfigEnhanced{protocols=%s, ciphers=%d, pins=%d}",
                Arrays.toString(protocols), 
                cipherSuites != null ? cipherSuites.length : 0,
                pinnedCertificates.size());
    }
}
