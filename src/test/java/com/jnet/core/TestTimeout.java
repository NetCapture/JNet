package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Timeout Tests")
class TestTimeout {

    @Test
    @DisplayName("Timeout: 默认配置")
    void testDefaultTimeout() {
        Timeout timeout = Timeout.defaultTimeout();
        
        assertNotNull(timeout);
        assertEquals(Duration.ofSeconds(10), timeout.getConnectTimeout());
        assertEquals(Duration.ofSeconds(30), timeout.getReadTimeout());
        assertEquals(Duration.ofSeconds(30), timeout.getWriteTimeout());
    }

    @Test
    @DisplayName("Timeout: 无限超时")
    void testInfiniteTimeout() {
        Timeout timeout = Timeout.infinite();
        
        assertEquals(Duration.ZERO, timeout.getConnectTimeout());
        assertEquals(Duration.ZERO, timeout.getReadTimeout());
        assertEquals(Duration.ZERO, timeout.getWriteTimeout());
    }

    @Test
    @DisplayName("Timeout: 自定义配置")
    void testCustomTimeout() {
        Timeout timeout = Timeout.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(15))
                .writeTimeout(Duration.ofSeconds(20))
                .totalTimeout(Duration.ofSeconds(60))
                .build();
        
        assertEquals(Duration.ofSeconds(5), timeout.getConnectTimeout());
        assertEquals(Duration.ofSeconds(15), timeout.getReadTimeout());
        assertEquals(Duration.ofSeconds(20), timeout.getWriteTimeout());
        assertEquals(Duration.ofSeconds(60), timeout.getTotalTimeout());
    }

    @Test
    @DisplayName("Timeout: 所有超时统一设置")
    void testAllTimeouts() {
        Timeout timeout = Timeout.newBuilder()
                .allTimeouts(Duration.ofSeconds(10))
                .build();
        
        assertEquals(Duration.ofSeconds(10), timeout.getConnectTimeout());
        assertEquals(Duration.ofSeconds(10), timeout.getReadTimeout());
        assertEquals(Duration.ofSeconds(10), timeout.getWriteTimeout());
    }

    @Test
    @DisplayName("Timeout: 负数超时抛出异常")
    void testNegativeTimeout() {
        assertThrows(IllegalArgumentException.class, () -> {
            Timeout.newBuilder().connectTimeout(Duration.ofSeconds(-1));
        });
        
        assertThrows(IllegalArgumentException.class, () -> {
            Timeout.newBuilder().readTimeout(Duration.ofSeconds(-1));
        });
    }

    @Test
    @DisplayName("Timeout: toString 输出")
    void testToString() {
        Timeout timeout = Timeout.defaultTimeout();
        String str = timeout.toString();
        
        assertTrue(str.contains("connect"));
        assertTrue(str.contains("read"));
        assertTrue(str.contains("write"));
    }
}
