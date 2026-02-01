package com.jnet.cloudflare;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

public class TestCloudflareBypass {

    @Test
    public void testUserAgentRotator() {
        UserAgentRotator rotator = new UserAgentRotator();

        // Test basic retrieval
        String ua1 = rotator.getRandomUserAgent();
        assertNotNull(ua1);
        assertFalse(ua1.isEmpty());

        // Test multiple calls return valid strings (randomness is hard to test deterministically,
        // but we can ensure they are from the list or valid structure)
        String ua2 = rotator.getRandomUserAgent();
        assertNotNull(ua2);

        // Test adding custom UA
        String customUA = "MyCustomBot/1.0";
        rotator.addUserAgent(customUA);

        // With enough iterations, we should see the custom UA (not guaranteed quickly, but logic check)
        // Instead, let's clear and add only one to verify
        rotator.clear();
        rotator.addUserAgent(customUA);
        assertEquals(customUA, rotator.getRandomUserAgent());
    }

    @Test
    public void testBrowserFingerprintChrome() {
        Map<String, String> headers = BrowserFingerprint.chromeHeaders();

        assertNotNull(headers);
        assertTrue(headers.containsKey("Sec-Ch-Ua"));
        assertTrue(headers.containsKey("Accept"));
        assertTrue(headers.get("Sec-Fetch-Dest").equals("document"));

        // Verify key headers for cloudflare bypass
        assertNotNull(headers.get("Sec-Ch-Ua-Platform"));
        assertNotNull(headers.get("Upgrade-Insecure-Requests"));
    }

    @Test
    public void testBrowserFingerprintFirefox() {
        Map<String, String> headers = BrowserFingerprint.firefoxHeaders();

        assertNotNull(headers);
        assertFalse(headers.containsKey("Sec-Ch-Ua")); // Firefox usually doesn't send this or sends differently
        assertTrue(headers.containsKey("Accept"));
        assertEquals("trailers", headers.get("Te"));
    }

    @Test
    public void testBrowserFingerprintSafari() {
        Map<String, String> headers = BrowserFingerprint.safariHeaders();

        assertNotNull(headers);
        assertTrue(headers.containsKey("Accept"));
        assertEquals("navigate", headers.get("Sec-Fetch-Mode"));
    }
}
