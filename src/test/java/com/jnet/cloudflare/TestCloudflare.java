package com.jnet.cloudflare;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cloudflare 模块测试套件
 */
@DisplayName("Cloudflare Tests")
class TestCloudflare {

    @Test
    @DisplayName("UserAgentRotator: 获取随机 UA")
    void testGetRandomUserAgent() {
        UserAgentRotator rotator = new UserAgentRotator();
        
        String ua1 = rotator.getRandomUserAgent();
        assertNotNull(ua1);
        assertTrue(ua1.contains("Mozilla"));
    }

    @Test
    @DisplayName("UserAgentRotator: 随机性验证")
    void testUserAgentRandomness() {
        UserAgentRotator rotator = new UserAgentRotator();
        
        Set<String> uas = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            uas.add(rotator.getRandomUserAgent());
        }
        
        // 应该有多个不同的 UA
        assertTrue(uas.size() > 1, "Should have multiple different UAs");
    }

    @Test
    @DisplayName("UserAgentRotator: 添加自定义 UA")
    void testAddCustomUserAgent() {
        UserAgentRotator rotator = new UserAgentRotator();
        String customUA = "CustomBot/1.0";
        
        rotator.addUserAgent(customUA);
        
        Set<String> uas = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            uas.add(rotator.getRandomUserAgent());
        }
        
        assertTrue(uas.contains(customUA), "Should contain custom UA");
    }

    @Test
    @DisplayName("BrowserFingerprint: Chrome 指纹")
    void testChromeFingerprint() {
        Map<String, String> headers = BrowserFingerprint.chromeHeaders();
        
        assertNotNull(headers);
        assertTrue(headers.containsKey("Accept"));
        assertTrue(headers.containsKey("Accept-Language"));
        assertTrue(headers.containsKey("Accept-Encoding"));
        
        assertEquals("gzip, deflate, br", headers.get("Accept-Encoding"));
    }

    @Test
    @DisplayName("BrowserFingerprint: Firefox 指纹")
    void testFirefoxFingerprint() {
        Map<String, String> headers = BrowserFingerprint.firefoxHeaders();
        
        assertNotNull(headers);
        assertTrue(headers.containsKey("Accept"));
        assertTrue(headers.containsKey("Accept-Language"));
    }

    @Test
    @DisplayName("BrowserFingerprint: Safari 指纹")
    void testSafariFingerprint() {
        Map<String, String> headers = BrowserFingerprint.safariHeaders();
        
        assertNotNull(headers);
        assertTrue(headers.containsKey("Accept"));
    }

    @Test
    @DisplayName("BrowserFingerprint: 指纹完整性")
    void testFingerprintCompleteness() {
        Map<String, String> chrome = BrowserFingerprint.chromeHeaders();
        
        // 验证必要的头部存在
        assertNotNull(chrome.get("Accept"));
        assertNotNull(chrome.get("Accept-Language"));
        assertNotNull(chrome.get("Accept-Encoding"));
        assertNotNull(chrome.get("User-Agent"));
    }

    @Test
    @DisplayName("RequestTimingInterceptor: 延迟范围验证")
    void testTimingInterceptorDelayRange() {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(100, 500);
        assertNotNull(interceptor);
    }

    @Test
    @DisplayName("RequestTimingInterceptor: 无效参数抛出异常")
    void testTimingInterceptorInvalidParams() {
        assertThrows(IllegalArgumentException.class, () -> {
            new RequestTimingInterceptor(-1, 100);
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            new RequestTimingInterceptor(500, 100); // max < min
        });
    }

    @Test
    @DisplayName("RequestTimingInterceptor: 默认构造函数")
    void testTimingInterceptorDefaultConstructor() {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor();
        assertNotNull(interceptor);
    }

    @Test
    @DisplayName("RequestTimingInterceptor: Reset 功能")
    void testTimingInterceptorReset() {
        RequestTimingInterceptor interceptor = new RequestTimingInterceptor(100, 500);
        interceptor.reset();
        // Reset should work without throwing
    }

    @Test
    @DisplayName("CloudflareInterceptor: 默认构造函数")
    void testCloudflareInterceptorDefaultConstructor() {
        CloudflareInterceptor interceptor = new CloudflareInterceptor();
        assertNotNull(interceptor);
    }

    @Test
    @DisplayName("CloudflareInterceptor: 自定义参数")
    void testCloudflareInterceptorCustomParams() {
        CloudflareInterceptor interceptor = new CloudflareInterceptor(5, 3000);
        assertNotNull(interceptor);
    }

    @Test
    @DisplayName("BrowserFingerprint: 所有浏览器指纹可用")
    void testAllBrowserFingerprints() {
        assertNotNull(BrowserFingerprint.chromeHeaders());
        assertNotNull(BrowserFingerprint.firefoxHeaders());
        assertNotNull(BrowserFingerprint.safariHeaders());
    }

    @Test
    @DisplayName("UserAgentRotator: 多线程安全")
    void testUserAgentRotatorThreadSafety() throws InterruptedException {
        UserAgentRotator rotator = new UserAgentRotator();
        List<String> results = Collections.synchronizedList(new ArrayList<>());
        
        Thread[] threads = new Thread[10];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < 100; j++) {
                    results.add(rotator.getRandomUserAgent());
                }
            });
            threads[i].start();
        }
        
        for (Thread thread : threads) {
            thread.join();
        }
        
        assertEquals(1000, results.size());
        // All should be non-null
        assertTrue(results.stream().allMatch(Objects::nonNull));
    }
}
