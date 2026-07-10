package com.jnet.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应缓存
 * 用于缓存HTTP响应，减少网络请求
 *
 * @author sanbo
 * @version 3.0.0
 */
public class ResponseCache {
    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final long defaultTtl; // 默认TTL（毫秒）
    private final List<String> varyHeaders;

    public ResponseCache() {
        this(5 * 60 * 1000); // 默认5分钟
    }

    public ResponseCache(long defaultTtl) {
        this(defaultTtl, defaultVaryHeaders());
    }

    public ResponseCache(long defaultTtl, List<String> varyHeaders) {
        this.defaultTtl = defaultTtl;
        this.varyHeaders = Collections.unmodifiableList(new ArrayList<>(
                varyHeaders != null ? varyHeaders : defaultVaryHeaders()));
    }

    /**
     * 缓存响应
     */
    public void put(Request request, Response response) {
        put(request, response, defaultTtl);
    }

    public void put(Request request, Response response, long ttlMillis) {
        String key = getCacheKey(request);
        long now = System.currentTimeMillis();
        long resolvedTtl = resolveTtlMillis(response, ttlMillis);
        long expireTime;
        if (resolvedTtl <= 0) {
            expireTime = now;
        } else if (Long.MAX_VALUE - now < resolvedTtl) {
            expireTime = Long.MAX_VALUE;
        } else {
            expireTime = now + resolvedTtl;
        }
        cache.put(key, new CacheEntry(response, expireTime));
    }

    private long resolveTtlMillis(Response response, long baseTtl) {
        if (baseTtl <= 0) {
            return baseTtl;
        }

        if (response == null) {
            return baseTtl;
        }

        long ttl = baseTtl;

        String cacheControl = response.getHeader("Cache-Control");
        if (cacheControl != null && !cacheControl.isEmpty()) {
            ttl = applyCacheControlTtl(cacheControl, ttl);
            if (ttl <= 0) {
                return 0;
            }
        }

        long expiresTtl = parseExpiresTtl(response.getHeader("Expires"));
        if (expiresTtl >= 0) {
            ttl = Math.min(ttl, expiresTtl);
        }

        return Math.max(ttl, 0);
    }

    private long applyCacheControlTtl(String cacheControl, long ttl) {
        long resolvedTtl = ttl;

        String[] directives = cacheControl.split(",");
        for (String rawDirective : directives) {
            String directive = rawDirective.trim().toLowerCase(Locale.ROOT);
            if (directive.isEmpty()) {
                continue;
            }

            if ("no-cache".equals(directive) || "no-store".equals(directive)) {
                return 0;
            }

            int index = directive.indexOf('=');
            if (index <= 0) {
                continue;
            }

            String key = directive.substring(0, index).trim();
            String rawValue = directive.substring(index + 1).trim();
            if (!("max-age".equals(key) || "s-maxage".equals(key))) {
                continue;
            }

            try {
                long directiveTtl = Long.parseLong(rawValue);
                if (directiveTtl <= 0) {
                    return 0;
                }
                long directiveTtlMs = Math.max(0L, directiveTtl) * 1000L;
                if (directiveTtlMs < resolvedTtl) {
                    resolvedTtl = directiveTtlMs;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed Cache-Control values.
            }
        }

        return resolvedTtl;
    }

    private long parseExpiresTtl(String expiresHeader) {
        if (expiresHeader == null || expiresHeader.isEmpty()) {
            return -1;
        }

        try {
            long expiresAt = ZonedDateTime.parse(expiresHeader, DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US))
                    .toInstant().toEpochMilli();
            long ttl = expiresAt - System.currentTimeMillis();
            return ttl > 0 ? ttl : 0;
        } catch (DateTimeParseException ignored) {
            return -1;
        }
    }

    /**
     * 获取缓存的响应
     */
    public Response get(Request request) {
        String key = getCacheKey(request);
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (System.currentTimeMillis() >= entry.expireTime) {
            cache.remove(key);
            return null;
        }

        return entry.response;
    }

    /**
     * 清除缓存
     */
    public void clear() {
        cache.clear();
    }

    /**
     * 清除过期的缓存条目
     */
    public void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(entry -> now >= entry.getValue().expireTime);
    }

    /**
     * 获取缓存大小
     */
    public int size() {
        return cache.size();
    }

    /**
     * 生成缓存键
     */
    private String getCacheKey(Request request) {
        StringBuilder key = new StringBuilder(request.getMethod())
                .append(":")
                .append(request.getUrlString())
                .append(":")
                .append(request.getBody());
        for (String headerName : varyHeaders) {
            key.append("|").append(headerName).append("=").append(request.getHeader(headerName));
        }
        return key.toString();
    }

    private static List<String> defaultVaryHeaders() {
        List<String> headers = new ArrayList<>();
        headers.add("Authorization");
        headers.add("Accept");
        return headers;
    }

    /**
     * 缓存条目
     */
    private static class CacheEntry {
        final Response response;
        final long expireTime;

        CacheEntry(Response response, long expireTime) {
            this.response = response;
            this.expireTime = expireTime;
        }
    }
}
