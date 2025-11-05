package com.jnet.core;

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

    public ResponseCache() {
        this(5 * 60 * 1000); // 默认5分钟
    }

    public ResponseCache(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    /**
     * 缓存响应
     */
    public void put(Request request, Response response) {
        String key = getCacheKey(request);
        long expireTime = System.currentTimeMillis() + defaultTtl;
        cache.put(key, new CacheEntry(response, expireTime));
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

        if (System.currentTimeMillis() > entry.expireTime) {
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
        cache.entrySet().removeIf(entry -> now > entry.getValue().expireTime);
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
        return request.getMethod() + ":" + request.getUrlString() + ":" + request.getBody();
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
