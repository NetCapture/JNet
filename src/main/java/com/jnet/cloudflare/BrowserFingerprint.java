package com.jnet.cloudflare;

import java.util.HashMap;
import java.util.Map;

/**
 * Provides browser fingerprint headers to mimic real browsers.
 */
public class BrowserFingerprint {

    /**
     * Returns a map of headers mimicking a modern Chrome browser.
     *
     * @return map of HTTP headers
     */
    public static Map<String, String> chromeHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"");
        headers.put("Sec-Ch-Ua-Mobile", "?0");
        headers.put("Sec-Ch-Ua-Platform", "\"Windows\"");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Cache-Control", "max-age=0");
        return headers;
    }

    /**
     * Returns a map of headers mimicking a modern Firefox browser.
     *
     * @return map of HTTP headers
     */
    public static Map<String, String> firefoxHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        headers.put("Sec-Fetch-User", "?1");
        headers.put("Upgrade-Insecure-Requests", "1");
        headers.put("Te", "trailers"); // Often sent by Firefox
        return headers;
    }

    /**
     * Returns a map of headers mimicking a modern Safari browser on macOS.
     *
     * @return map of HTTP headers
     */
    public static Map<String, String> safariHeaders() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Sec-Fetch-Dest", "document");
        headers.put("Sec-Fetch-Mode", "navigate");
        headers.put("Sec-Fetch-Site", "none");
        return headers;
    }
}
