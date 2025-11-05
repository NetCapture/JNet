package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pair 工具类单元测试
 *
 * @author sanbo
 * @version 3.0
 */
public class TestPair {

    @Test
    @DisplayName("测试 Pair 创建")
    void testPairCreation() {
        Pair<String, Integer> pair = new Pair<>("key", 123);
        assertNotNull(pair, "Pair 创建应该成功");
    }

    @Test
    @DisplayName("测试 Pair.of 静态方法")
    void testPairOf() {
        Pair<String, String> pair = Pair.of("name", "JNet");
        assertNotNull(pair, "Pair.of 应该成功创建");
        assertEquals("name", pair.key, "key 应该正确");
        assertEquals("JNet", pair.value, "value 应该正确");
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        Pair<String, String> pair = Pair.of("name", "JNet");
        String str = pair.toString();
        assertNotNull(str, "toString 应该成功");
        assertTrue(str.contains("="), "toString 应该包含等号");
    }
}
