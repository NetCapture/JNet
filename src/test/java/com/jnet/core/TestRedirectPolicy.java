package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("RedirectPolicy Tests")
class TestRedirectPolicy {

    @Test
    @DisplayName("RedirectPolicy: 默认策略")
    void testDefaultPolicy() {
        RedirectPolicy policy = RedirectPolicy.defaultPolicy();
        
        assertTrue(policy.shouldFollowRedirects());
        assertEquals(10, policy.getMaxRedirects());
        assertTrue(policy.shouldFollowCrossDomain());
    }

    @Test
    @DisplayName("RedirectPolicy: 永不重定向")
    void testNeverPolicy() {
        RedirectPolicy policy = RedirectPolicy.never();
        
        assertFalse(policy.shouldFollowRedirects());
    }

    @Test
    @DisplayName("RedirectPolicy: 同域重定向")
    void testSameDomainOnly() {
        RedirectPolicy policy = RedirectPolicy.sameDomainOnly();
        
        assertTrue(policy.shouldFollowRedirects());
        assertFalse(policy.shouldFollowCrossDomain());
    }

    @Test
    @DisplayName("RedirectPolicy: 最大重定向限制")
    void testMaxRedirects() {
        RedirectPolicy policy = RedirectPolicy.newBuilder()
                .maxRedirects(3)
                .build();
        
        URI uri1 = URI.create("http://example.com/1");
        URI uri2 = URI.create("http://example.com/2");
        
        policy.addRedirect(uri1);
        policy.addRedirect(uri2);
        policy.addRedirect(uri1);
        
        assertTrue(policy.hasExceededMaxRedirects());
        assertEquals(3, policy.getRedirectCount());
    }

    @Test
    @DisplayName("RedirectPolicy: 跨域检查")
    void testCrossDomainCheck() {
        RedirectPolicy policy = RedirectPolicy.sameDomainOnly();
        
        URI from = URI.create("http://example.com/page");
        URI sameDomain = URI.create("http://example.com/other");
        URI crossDomain = URI.create("http://other.com/page");
        
        assertTrue(policy.shouldFollow(from, sameDomain));
        assertFalse(policy.shouldFollow(from, crossDomain));
    }

    @Test
    @DisplayName("RedirectPolicy: 重定向历史")
    void testRedirectHistory() {
        RedirectPolicy policy = RedirectPolicy.defaultPolicy();
        
        URI uri1 = URI.create("http://example.com/1");
        URI uri2 = URI.create("http://example.com/2");
        
        policy.addRedirect(uri1);
        policy.addRedirect(uri2);
        
        assertEquals(2, policy.getRedirectHistory().size());
        assertEquals(uri1, policy.getRedirectHistory().get(0));
        assertEquals(uri2, policy.getRedirectHistory().get(1));
    }

    @Test
    @DisplayName("RedirectPolicy: Reset 清空历史")
    void testReset() {
        RedirectPolicy policy = RedirectPolicy.defaultPolicy();
        
        policy.addRedirect(URI.create("http://example.com/1"));
        policy.addRedirect(URI.create("http://example.com/2"));
        
        assertEquals(2, policy.getRedirectCount());
        
        policy.reset();
        
        assertEquals(0, policy.getRedirectCount());
    }

    @Test
    @DisplayName("RedirectPolicy: 负数最大重定向抛出异常")
    void testNegativeMaxRedirects() {
        assertThrows(IllegalArgumentException.class, () -> {
            RedirectPolicy.newBuilder().maxRedirects(-1);
        });
    }
}
