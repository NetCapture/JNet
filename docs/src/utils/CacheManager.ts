/**
 * JNet GitHub Pages - Cache Management System
 * In-memory caching with TTL support and statistics
 */

import {
    ICacheManager,
    CacheEntry,
    CacheManagerConfig
} from '../types/index.js';

/**
 * CacheManager - Handles in-memory caching with TTL
 */
export class CacheManager implements ICacheManager {
    private static instance: CacheManager | null = null;
    private cache: Map<string, CacheEntry<any>>;
    private config: CacheManagerConfig;
    private stats: { hits: number; misses: number };
    private cleanupInterval: NodeJS.Timeout | null = null;

    private constructor() {
        this.cache = new Map();
        this.config = {
            maxSize: 100,
            defaultTTL: 5 * 60 * 1000, // 5 minutes
            cleanupInterval: 60 * 1000 // 1 minute
        };
        this.stats = { hits: 0, misses: 0 };
    }

    /**
     * Get singleton instance
     */
    static getInstance(): CacheManager {
        if (!CacheManager.instance) {
            CacheManager.instance = new CacheManager();
        }
        return CacheManager.instance;
    }

    /**
     * Initialize cache manager
     */
    async initialize(): Promise<void> {
        console.log('✅ CacheManager initialized');
        this.startCleanupInterval();
    }

    /**
     * Clean up resources
     */
    destroy(): void {
        if (this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
            this.cleanupInterval = null;
        }
        this.cache.clear();
        CacheManager.instance = null;
    }

    /**
     * Get module name
     */
    getName(): string {
        return 'CacheManager';
    }

    /**
     * Get module version
     */
    getVersion(): string {
        return '1.0.0';
    }

    /**
     * Get data from cache
     */
    get<T>(key: string): T | null {
        const entry = this.cache.get(key);

        if (!entry) {
            this.stats.misses++;
            return null;
        }

        // Check if expired
        if (this.isExpired(entry)) {
            this.cache.delete(key);
            this.stats.misses++;
            return null;
        }

        this.stats.hits++;
        return entry.data as T;
    }

    /**
     * Set data in cache
     */
    set<T>(key: string, data: T, ttl?: number): void {
        // Check cache size limit
        if (this.cache.size >= this.config.maxSize) {
            this.evictOldest();
        }

        const entry: CacheEntry<T> = {
            data,
            timestamp: Date.now(),
            expiry: Date.now() + (ttl || this.config.defaultTTL)
        };

        this.cache.set(key, entry);
    }

    /**
     * Check if key exists in cache
     */
    has(key: string): boolean {
        const entry = this.cache.get(key);
        if (!entry) return false;

        if (this.isExpired(entry)) {
            this.cache.delete(key);
            return false;
        }

        return true;
    }

    /**
     * Delete specific key from cache
     */
    delete(key: string): boolean {
        return this.cache.delete(key);
    }

    /**
     * Clear entire cache
     */
    clear(): void {
        this.cache.clear();
        this.stats = { hits: 0, misses: 0 };
    }

    /**
     * Get cache statistics
     */
    getStats(): { size: number; hits: number; misses: number } {
        return {
            size: this.cache.size,
            hits: this.stats.hits,
            misses: this.stats.misses
        };
    }

    /**
     * Get cache hit rate
     */
    getHitRate(): number {
        const total = this.stats.hits + this.stats.misses;
        if (total === 0) return 0;
        return (this.stats.hits / total) * 100;
    }

    /**
     * Update cache configuration
     */
    updateConfig(config: Partial<CacheManagerConfig>): void {
        this.config = { ...this.config, ...config };

        // Restart cleanup interval if changed
        if (config.cleanupInterval && this.cleanupInterval) {
            clearInterval(this.cleanupInterval);
            this.startCleanupInterval();
        }
    }

    /**
     * Get all cache keys
     */
    getKeys(): string[] {
        return Array.from(this.cache.keys());
    }

    /**
     * Get cache entry metadata
     */
    getMetadata(key: string): { timestamp: number; expiry: number; ttl: number } | null {
        const entry = this.cache.get(key);
        if (!entry) return null;

        return {
            timestamp: entry.timestamp,
            expiry: entry.expiry,
            ttl: entry.expiry - Date.now()
        };
    }

    /**
     * Prefetch data into cache
     */
    async prefetch<T>(key: string, fetchFn: () => Promise<T>, ttl?: number): Promise<T> {
        const cached = this.get<T>(key);
        if (cached !== null) {
            return cached;
        }

        const data = await fetchFn();
        this.set(key, data, ttl);
        return data;
    }

    /**
     * Get or fetch data (cache-aside pattern)
     */
    async getOrFetch<T>(
        key: string,
        fetchFn: () => Promise<T>,
        ttl?: number
    ): Promise<T> {
        const cached = this.get<T>(key);
        if (cached !== null) {
            return cached;
        }

        const data = await fetchFn();
        this.set(key, data, ttl);
        return data;
    }

    /**
     * Batch set multiple items
     */
    setMultiple<T>(items: Array<{ key: string; data: T; ttl?: number }>): void {
        for (const item of items) {
            this.set(item.key, item.data, item.ttl);
        }
    }

    /**
     * Batch get multiple items
     */
    getMultiple<T>(keys: string[]): Record<string, T | null> {
        const result: Record<string, T | null> = {};

        for (const key of keys) {
            result[key] = this.get<T>(key);
        }

        return result;
    }

    /**
     * Delete multiple keys
     */
    deleteMultiple(keys: string[]): number {
        let deleted = 0;
        for (const key of keys) {
            if (this.delete(key)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Get cache size in bytes (approximate)
     */
    getApproximateSize(): number {
        let totalSize = 0;

        for (const [key, entry] of this.cache) {
            // Approximate size: key length + data stringified length
            totalSize += key.length * 2; // UTF-16 characters
            try {
                totalSize += JSON.stringify(entry.data).length;
            } catch (e) {
                // If data can't be stringified, estimate
                totalSize += 100;
            }
        }

        return totalSize;
    }

    /**
     * Export cache data (for debugging)
     */
    export(): Record<string, any> {
        const exported: Record<string, any> = {};

        for (const [key, entry] of this.cache) {
            exported[key] = {
                data: entry.data,
                timestamp: entry.timestamp,
                expiry: entry.expiry,
                ttl: entry.expiry - Date.now()
            };
        }

        return exported;
    }

    /**
     * Import cache data
     */
    import(data: Record<string, any>): void {
        for (const [key, entry] of Object.entries(data)) {
            if (entry && entry.data && entry.expiry > Date.now()) {
                this.cache.set(key, {
                    data: entry.data,
                    timestamp: entry.timestamp || Date.now(),
                    expiry: entry.expiry
                });
            }
        }
    }

    // ==================== Private Helper Methods ====================

    private isExpired(entry: CacheEntry<any>): boolean {
        return Date.now() > entry.expiry;
    }

    private evictOldest(): void {
        if (this.cache.size === 0) return;

        let oldestKey: string | null = null;
        let oldestTimestamp = Infinity;

        for (const [key, entry] of this.cache) {
            if (entry.timestamp < oldestTimestamp) {
                oldestTimestamp = entry.timestamp;
                oldestKey = key;
            }
        }

        if (oldestKey) {
            this.cache.delete(oldestKey);
        }
    }

    private startCleanupInterval(): void {
        this.cleanupInterval = setInterval(() => {
            this.cleanupExpired();
        }, this.config.cleanupInterval);
    }

    private cleanupExpired(): void {
        const now = Date.now();
        const expiredKeys: string[] = [];

        for (const [key, entry] of this.cache) {
            if (entry.expiry < now) {
                expiredKeys.push(key);
            }
        }

        expiredKeys.forEach(key => this.cache.delete(key));

        if (expiredKeys.length > 0) {
            console.log(`[CacheManager] Cleaned up ${expiredKeys.length} expired entries`);
        }
    }
}

/**
 * Convenience function for cache operations
 */
export function cache<T>(key: string, data?: T, ttl?: number): T | null {
    const cacheManager = CacheManager.getInstance();

    if (data !== undefined) {
        cacheManager.set(key, data, ttl);
        return null;
    }

    return cacheManager.get<T>(key);
}

/**
 * Cache decorator for methods
 */
export function cacheable(ttl?: number) {
    return function (target: any, propertyKey: string, descriptor: PropertyDescriptor) {
        const originalMethod = descriptor.value;

        descriptor.value = function (...args: any[]) {
            const cacheManager = CacheManager.getInstance();
            const key = `${propertyKey}:${JSON.stringify(args)}`;

            const cached = cacheManager.get<any>(key);
            if (cached !== null) {
                return cached;
            }

            const result = originalMethod.apply(this, args);

            if (result instanceof Promise) {
                return result.then(data => {
                    cacheManager.set(key, data, ttl);
                    return data;
                });
            }

            cacheManager.set(key, result, ttl);
            return result;
        };

        return descriptor;
    };
}

/**
 * Cache-aside pattern helper
 */
export async function withCache<T>(
    key: string,
    operation: () => Promise<T>,
    ttl?: number
): Promise<T> {
    const cacheManager = CacheManager.getInstance();

    const cached = cacheManager.get<T>(key);
    if (cached !== null) {
        return cached;
    }

    const result = await operation();
    cacheManager.set(key, result, ttl);

    return result;
}