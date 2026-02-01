package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SSLConfigEnhanced Tests")
class TestSSLConfigEnhanced {

    @Test
    @DisplayName("SSLConfigEnhanced: TLS 1.3 only configuration")
    void testTls13Only() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.tls13Only();
        
        assertNotNull(config);
        assertNotNull(config.getSSLContext());
        
        SSLParameters params = config.getSSLParameters();
        assertArrayEquals(new String[] { "TLSv1.3" }, params.getProtocols());
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Default configuration")
    void testDefaultConfig() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.defaultConfig();
        
        assertNotNull(config);
        SSLContext context = config.getSSLContext();
        assertNotNull(context);
        assertEquals("TLS", context.getProtocol());
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Custom protocols")
    void testCustomProtocols() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .protocols("TLSv1.3", "TLSv1.2")
                .build();
        
        SSLParameters params = config.getSSLParameters();
        assertEquals(2, params.getProtocols().length);
        assertEquals("TLSv1.3", params.getProtocols()[0]);
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Strong ciphers only")
    void testStrongCiphersOnly() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .strongCiphersOnly()
                .build();
        
        SSLParameters params = config.getSSLParameters();
        String[] ciphers = params.getCipherSuites();
        
        assertNotNull(ciphers);
        assertTrue(ciphers.length > 0);
        
        // Check for TLS 1.3 ciphers
        boolean hasTls13Cipher = false;
        for (String cipher : ciphers) {
            if (cipher.contains("TLS_AES") || cipher.contains("TLS_CHACHA20")) {
                hasTls13Cipher = true;
                break;
            }
        }
        assertTrue(hasTls13Cipher, "Should have at least one TLS 1.3 cipher");
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Certificate pinning")
    void testCertificatePinning() throws Exception {
        String fingerprint = "abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890";
        
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .pinCertificate("example.com", fingerprint)
                .build();
        
        assertNotNull(config);
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Multiple certificate pins")
    void testMultiplePins() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .pinCertificate("example.com", "abc123")
                .pinCertificate("test.com", "def456")
                .build();
        
        assertNotNull(config);
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Trust all certificates")
    void testTrustAll() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .trustAllCertificates()
                .build();
        
        assertNotNull(config);
        assertNotNull(config.getSSLContext());
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Invalid protocols throw exception")
    void testInvalidProtocols() {
        assertThrows(IllegalArgumentException.class, () -> {
            SSLConfigEnhanced.newBuilder().protocols();
        });
    }

    @Test
    @DisplayName("SSLConfigEnhanced: toString output")
    void testToString() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .protocols("TLSv1.3")
                .strongCiphersOnly()
                .build();
        
        String str = config.toString();
        assertTrue(str.contains("TLSv1.3"));
        assertTrue(str.contains("protocols"));
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Builder TLS 1.3 only method")
    void testBuilderTls13Only() throws Exception {
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .tls13Only()
                .build();
        
        SSLParameters params = config.getSSLParameters();
        assertEquals(1, params.getProtocols().length);
        assertEquals("TLSv1.3", params.getProtocols()[0]);
    }

    @Test
    @DisplayName("SSLConfigEnhanced: Custom cipher suites")
    void testCustomCipherSuites() throws Exception {
        String[] customCiphers = {
            "TLS_AES_256_GCM_SHA384",
            "TLS_AES_128_GCM_SHA256"
        };
        
        SSLConfigEnhanced config = SSLConfigEnhanced.newBuilder()
                .cipherSuites(customCiphers)
                .build();
        
        SSLParameters params = config.getSSLParameters();
        assertArrayEquals(customCiphers, params.getCipherSuites());
    }
}
