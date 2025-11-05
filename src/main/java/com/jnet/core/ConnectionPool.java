package com.jnet.core;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * HTTP连接池
 * 复用HTTP连接，提高性能
 *
 * @author sanbo
 * @version 3.0.0
 */
public class ConnectionPool {
    private static final int MAX_SIZE_PER_ROUTE = 5; // 每路由最大连接数
    private static final int MAX_TOTAL = 20; // 总最大连接数
    private static final long KEEP_ALIVE_DURATION = 5 * 60 * 1000; // 5分钟

    // 连接池: key为host+port，value为该路由的连接队列
    private final ConcurrentHashMap<String, Queue<PoolEntry>> pool = new ConcurrentHashMap<>();
    private volatile int totalConnections = 0;

    /**
     * 获取连接
     */
    public synchronized HttpURLConnection get(String url) throws IOException {
        URL u = new URL(url);
        String key = getPoolKey(u);
        long now = System.currentTimeMillis();

        // 清理过期连接
        cleanup(key, now);

        // 尝试从池中获取连接
        Queue<PoolEntry> queue = pool.get(key);
        if (queue != null) {
            PoolEntry entry = queue.poll();
            if (entry != null && !entry.isExpired(now)) {
                totalConnections--;
                return entry.connection;
            }
        }

        // 创建新连接
        if (totalConnections >= MAX_TOTAL) {
            throw new IOException("Connection pool exhausted");
        }

        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.setInstanceFollowRedirects(false);
        totalConnections++;

        return conn;
    }

    /**
     * 释放连接回池中
     */
    public synchronized void release(HttpURLConnection connection) {
        try {
            String key = getPoolKey(connection.getURL());
            long now = System.currentTimeMillis();

            // 检查连接是否有效
            int responseCode = connection.getResponseCode();
            if (responseCode == -1 || isConnectionClosed(connection)) {
                connection.disconnect();
                return;
            }

            // 清理过期连接
            cleanup(key, now);

            // 检查路由限制
            Queue<PoolEntry> queue = pool.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
            if (queue.size() >= MAX_SIZE_PER_ROUTE) {
                connection.disconnect();
                return;
            }

            // 添加到池中
            queue.offer(new PoolEntry(connection, now + KEEP_ALIVE_DURATION));
        } catch (Exception e) {
            // 释放失败时断开连接
            connection.disconnect();
        }
    }

    /**
     * 关闭池中所有连接
     */
    public synchronized void shutdown() {
        for (Queue<PoolEntry> queue : pool.values()) {
            for (PoolEntry entry : queue) {
                try {
                    entry.connection.disconnect();
                } catch (Exception e) {
                    // 忽略断开异常
                }
            }
        }
        pool.clear();
        totalConnections = 0;
    }

    /**
     * 清理过期连接
     */
    private void cleanup(String key, long now) {
        Queue<PoolEntry> queue = pool.get(key);
        if (queue == null) {
            return;
        }

        while (!queue.isEmpty()) {
            PoolEntry entry = queue.peek();
            if (entry == null || entry.isExpired(now)) {
                queue.poll();
                try {
                    entry.connection.disconnect();
                } catch (Exception e) {
                    // 忽略
                }
            } else {
                break;
            }
        }

        // 如果队列为空，移除key
        if (queue.isEmpty()) {
            pool.remove(key);
        }
    }

    /**
     * 获取连接池key（基于host和port）
     */
    private String getPoolKey(URL url) {
        return url.getProtocol() + "://" + url.getHost() + ":" + url.getPort();
    }

    /**
     * 检查连接是否已关闭
     */
    private boolean isConnectionClosed(HttpURLConnection conn) {
        try {
            return conn.getResponseCode() == -1;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * 连接池条目
     */
    private static class PoolEntry {
        final HttpURLConnection connection;
        final long expireTime;

        PoolEntry(HttpURLConnection connection, long expireTime) {
            this.connection = connection;
            this.expireTime = expireTime;
        }

        boolean isExpired(long now) {
            return now > expireTime;
        }
    }
}
