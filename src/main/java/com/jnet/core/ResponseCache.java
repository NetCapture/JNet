package com.jnet.core;

import java.io.IOException;
import java.net.CookieHandler;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应缓存
 * 用于缓存HTTP响应，减少网络请求
 *
 * @author sanbo
 * @version 3.0.0
 */
public class ResponseCache {
    private static final int DEFAULT_MAX_ENTRIES = 4_096;
    private static final long DEFAULT_MAX_BODY_CHARS = 64L * 1024L * 1024L;

    private final ConcurrentHashMap<CacheKey, CacheEntry> cache = new ConcurrentHashMap<>();
    private final Object mutationLock = new Object();
    private final long defaultTtl; // 默认TTL（毫秒）
    private final List<String> varyHeaders;
    private final int maxEntries;
    private final long maxBodyChars;
    private long totalBodyChars;

    public ResponseCache() {
        this(5 * 60 * 1000); // 默认5分钟
    }

    public ResponseCache(long defaultTtl) {
        this(defaultTtl, defaultVaryHeaders());
    }

    public ResponseCache(long defaultTtl, List<String> varyHeaders) {
        this(defaultTtl, varyHeaders, DEFAULT_MAX_ENTRIES);
    }

    /**
     * Creates a bounded cache. Expired entries are preferred for eviction;
     * otherwise an arbitrary entry is removed to keep writes constant-space.
     */
    public ResponseCache(long defaultTtl, List<String> varyHeaders, int maxEntries) {
        this(defaultTtl, varyHeaders, maxEntries, DEFAULT_MAX_BODY_CHARS);
    }

    /**
     * Creates a cache bounded by both entry count and the total number of cached body characters.
     * Responses larger than the complete body budget are not cached.
     */
    public ResponseCache(long defaultTtl, List<String> varyHeaders, int maxEntries,
            long maxBodyChars) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        if (maxBodyChars < 0) {
            throw new IllegalArgumentException("maxBodyChars must be non-negative");
        }
        this.defaultTtl = defaultTtl;
        this.varyHeaders = Collections.unmodifiableList(new ArrayList<>(
                varyHeaders != null ? varyHeaders : defaultVaryHeaders()));
        this.maxEntries = maxEntries;
        this.maxBodyChars = maxBodyChars;
    }

    /**
     * 缓存响应
     */
    public void put(Request request, Response response) {
        put(request, response, defaultTtl);
    }

    public void put(Request request, Response response, long ttlMillis) {
        Objects.requireNonNull(request, "request");
        if (response == null) {
            return;
        }
        CacheKey key = getCacheKey(request);
        if (key == null) {
            return;
        }
        if (isVaryAll(response)) {
            remove(key);
            return;
        }
        long resolvedTtl = resolveTtlMillis(response, ttlMillis);
        if (resolvedTtl <= 0) {
            remove(key);
            return;
        }
        CacheEntry entry = new CacheEntry(
                response, System.nanoTime(), toNanos(resolvedTtl), bodyChars(response));
        synchronized (mutationLock) {
            removeLocked(key, null);
            if (entry.bodyChars > maxBodyChars) {
                return;
            }
            if (cache.size() >= maxEntries || exceedsBodyBudget(entry.bodyChars)) {
                cleanupExpiredLocked(System.nanoTime());
            }
            while (cache.size() >= maxEntries || exceedsBodyBudget(entry.bodyChars)) {
                if (!evictOneLocked()) {
                    return;
                }
            }
            cache.put(key, entry);
            totalBodyChars += entry.bodyChars;
        }
    }

    private boolean isVaryAll(Response response) {
        if (response == null) {
            return false;
        }
        List<String> varyValues = response.getHeaderValues("Vary");
        if (varyValues == null || varyValues.isEmpty()) {
            return false;
        }
        for (String vary : varyValues) {
            for (String value : vary.split(",")) {
                if ("*".equals(value.trim())) {
                    return true;
                }
            }
        }
        return false;
    }

    private long resolveTtlMillis(Response response, long baseTtl) {
        if (baseTtl <= 0) {
            return baseTtl;
        }

        if (response == null) {
            return baseTtl;
        }

        long ttl = baseTtl;

        List<String> cacheControlValues = response.getHeaderValues("Cache-Control");
        if (cacheControlValues != null) {
            for (String cacheControl : cacheControlValues) {
                if (cacheControl != null && !cacheControl.isEmpty()) {
                    ttl = applyCacheControlTtl(cacheControl, ttl);
                    if (ttl <= 0) {
                        return 0;
                    }
                }
            }
        }

        long ageMillis = parseAgeMillis(response.getHeaderValues("Age"));
        if (ageMillis >= ttl) {
            return 0;
        }
        ttl -= ageMillis;

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

            int index = directive.indexOf('=');
            String key = (index < 0 ? directive : directive.substring(0, index)).trim();
            if ("no-cache".equals(key) || "no-store".equals(key) || "private".equals(key)) {
                return 0;
            }

            if (index <= 0) {
                continue;
            }

            String rawValue = directive.substring(index + 1).trim();
            if (!("max-age".equals(key) || "s-maxage".equals(key))) {
                continue;
            }

            try {
                if (rawValue.length() >= 2 && rawValue.charAt(0) == '"'
                        && rawValue.charAt(rawValue.length() - 1) == '"') {
                    rawValue = rawValue.substring(1, rawValue.length() - 1).trim();
                }
                long directiveTtl = Long.parseLong(rawValue);
                if (directiveTtl <= 0) {
                    return 0;
                }
                long directiveTtlMs = directiveTtl > Long.MAX_VALUE / 1000L
                        ? Long.MAX_VALUE
                        : directiveTtl * 1000L;
                if (directiveTtlMs < resolvedTtl) {
                    resolvedTtl = directiveTtlMs;
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed Cache-Control values.
            }
        }

        return resolvedTtl;
    }

    private static long parseAgeMillis(List<String> ageValues) {
        long largestAge = 0;
        if (ageValues == null) {
            return largestAge;
        }
        for (String value : ageValues) {
            if (value == null) {
                continue;
            }
            try {
                long seconds = Long.parseLong(value.trim());
                if (seconds > 0) {
                    long millis = seconds > Long.MAX_VALUE / 1000L
                            ? Long.MAX_VALUE
                            : seconds * 1000L;
                    largestAge = Math.max(largestAge, millis);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed Age values.
            }
        }
        return largestAge;
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
        Objects.requireNonNull(request, "request");
        CacheKey key = getCacheKey(request);
        if (key == null) {
            return null;
        }
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            return null;
        }

        if (entry.isExpired(System.nanoTime())) {
            synchronized (mutationLock) {
                removeLocked(key, entry);
            }
            return null;
        }

        return entry.response;
    }

    /**
     * 清除缓存
     */
    public void clear() {
        synchronized (mutationLock) {
            cache.clear();
            totalBodyChars = 0;
        }
    }

    /**
     * 清除过期的缓存条目
     */
    public void cleanup() {
        synchronized (mutationLock) {
            cleanupExpiredLocked(System.nanoTime());
        }
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
    private CacheKey getCacheKey(Request request) {
        if (request.getBodyPublisher() != null && request.getBody() == null) {
            return null;
        }
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> entry : request.getHeaders().entrySet()) {
            if (entry.getKey() != null) {
                headers.put(entry.getKey(), entry.getValue());
            }
        }
        for (String headerName : varyHeaders) {
            if (headerName != null && !headers.containsKey(headerName)) {
                headers.put(headerName, request.getHeader(headerName));
            }
        }
        String implicitCookies = resolveImplicitCookies(request, headers);
        if (implicitCookies == null) {
            return null;
        }
        if (!implicitCookies.isEmpty()) {
            String explicitCookies = headers.get("Cookie");
            headers.put("Cookie", explicitCookies == null || explicitCookies.isEmpty()
                    ? implicitCookies
                    : explicitCookies + "; " + implicitCookies);
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null) {
                normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
            }
        }
        return new CacheKey(request.getClient(), request.getMethod(), request.getUrlString(),
                request.getBody(), normalized);
    }

    private static String resolveImplicitCookies(Request request, Map<String, String> requestHeaders) {
        JNetClient client = request.getClient();
        if (client == null) {
            return "";
        }
        CookieHandler handler = client.getHttpClient().cookieHandler().orElse(null);
        if (handler == null) {
            return "";
        }
        Map<String, List<String>> headerValues = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            String value = entry.getValue();
            headerValues.put(entry.getKey(),
                    value == null ? Collections.emptyList() : Collections.singletonList(value));
        }
        try {
            Map<String, List<String>> cookies = handler.get(request.getUri(), headerValues);
            if (cookies == null || cookies.isEmpty()) {
                return "";
            }
            StringBuilder result = new StringBuilder();
            for (Map.Entry<String, List<String>> entry : cookies.entrySet()) {
                if (entry.getKey() != null && ("cookie".equalsIgnoreCase(entry.getKey())
                        || "cookie2".equalsIgnoreCase(entry.getKey()))) {
                    for (String value : entry.getValue()) {
                        if (value != null && !value.isEmpty()) {
                            if (result.length() > 0) {
                                result.append("; ");
                            }
                            result.append(value);
                        }
                    }
                }
            }
            return result.toString();
        } catch (IOException | RuntimeException e) {
            // If the actual outbound Cookie header is unknown, a cache hit is unsafe.
            return null;
        }
    }

    private void remove(CacheKey key) {
        synchronized (mutationLock) {
            removeLocked(key, null);
        }
    }

    private void cleanupExpiredLocked(long nowNanos) {
        for (Map.Entry<CacheKey, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().isExpired(nowNanos)) {
                removeLocked(entry.getKey(), entry.getValue());
            }
        }
    }

    private boolean evictOneLocked() {
        java.util.Iterator<Map.Entry<CacheKey, CacheEntry>> entries = cache.entrySet().iterator();
        while (entries.hasNext()) {
            Map.Entry<CacheKey, CacheEntry> entry = entries.next();
            if (removeLocked(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        return false;
    }

    private boolean removeLocked(CacheKey key, CacheEntry expected) {
        CacheEntry removed;
        if (expected == null) {
            removed = cache.remove(key);
        } else if (cache.remove(key, expected)) {
            removed = expected;
        } else {
            removed = null;
        }
        if (removed == null) {
            return false;
        }
        totalBodyChars -= removed.bodyChars;
        if (totalBodyChars < 0) {
            totalBodyChars = 0;
        }
        return true;
    }

    private boolean exceedsBodyBudget(long bodyChars) {
        return bodyChars > maxBodyChars - totalBodyChars;
    }

    private static long bodyChars(Response response) {
        String body = response.getBody();
        return body == null ? 0L : body.length();
    }

    private static long toNanos(long millis) {
        return millis >= Long.MAX_VALUE / 1_000_000L
                ? Long.MAX_VALUE
                : millis * 1_000_000L;
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
    private static final class CacheEntry {
        final Response response;
        final long createdNanos;
        final long ttlNanos;
        final long bodyChars;

        CacheEntry(Response response, long createdNanos, long ttlNanos, long bodyChars) {
            this.response = response;
            this.createdNanos = createdNanos;
            this.ttlNanos = ttlNanos;
            this.bodyChars = bodyChars;
        }

        boolean isExpired(long nowNanos) {
            return nowNanos - createdNanos >= ttlNanos;
        }
    }

    private static final class CacheKey {
        private final JNetClient client;
        private final String method;
        private final String url;
        private final String body;
        private final Map<String, String> headers;
        private final int hashCode;

        private CacheKey(JNetClient client, String method, String url, String body,
                         Map<String, String> headers) {
            this.client = client;
            this.method = method;
            this.url = url;
            this.body = body;
            this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(headers));
            this.hashCode = 31 * System.identityHashCode(client)
                    + Objects.hash(method, url, body, this.headers);
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof CacheKey)) {
                return false;
            }
            CacheKey that = (CacheKey) other;
            return client == that.client
                    && Objects.equals(method, that.method)
                    && Objects.equals(url, that.url)
                    && Objects.equals(body, that.body)
                    && headers.equals(that.headers);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }
}
