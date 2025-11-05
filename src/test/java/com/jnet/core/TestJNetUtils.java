package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JNetUtils 工具类单元测试
 *
 * @author sanbo
 * @version 3.0
 */
public class TestJNetUtils {

    @Test
    @DisplayName("测试 JSON 构建")
    void testJsonBuild() {
        String json = JNetUtils.json()
                .add("key1", "value1")
                .add("key2", 123)
                .add("key3", true)
                .build();

        assertNotNull(json, "JSON 构建应该成功");
        assertTrue(json.contains("key1"), "应该包含 key1");
        assertTrue(json.contains("value1"), "应该包含 value1");
    }

    @Test
    @DisplayName("测试 Base64 编码解码")
    void testBase64() {
        String text = "Hello, JNet!";
        String base64 = JNetUtils.encodeBase64(text);
        String decoded = JNetUtils.decodeBase64(base64);

        assertNotNull(base64, "Base64 编码应该成功");
        assertEquals(text, decoded, "解码后应该等于原文");
    }

    @Test
    @DisplayName("测试 URL 编码")
    void testUrlEncode() {
        String url = "https://example.com/search?q=java 网络";
        String encoded = JNetUtils.urlEncode(url);

        assertNotNull(encoded, "URL 编码应该成功");
        // 编码后应该不包含空格和特殊字符
        assertFalse(encoded.contains(" "), "编码后不应该包含空格");
    }

    @Test
    @DisplayName("测试 MD5 计算")
    void testMd5() {
        String md5 = JNetUtils.md5("JNet");
        assertNotNull(md5, "MD5 计算应该成功");
        assertEquals(32, md5.length(), "MD5 应该是 32 位");
    }

    @Test
    @DisplayName("测试字符串工具方法")
    void testStringUtils() {
        // 测试 isEmpty
        assertTrue(JNetUtils.isEmpty(""), "空字符串应该返回 true");
        assertFalse(JNetUtils.isEmpty("a"), "非空字符串应该返回 false");

        // 测试 isNotEmpty
        assertFalse(JNetUtils.isNotEmpty(""), "空字符串应该返回 false");
        assertTrue(JNetUtils.isNotEmpty("a"), "非空字符串应该返回 true");

        // 测试 isBlank
        assertTrue(JNetUtils.isBlank("   "), "只有空格的字符串应该返回 true");
        assertFalse(JNetUtils.isBlank("a"), "有内容的字符串应该返回 false");

        // 测试 trim
        assertEquals("a", JNetUtils.trim(" a "), "trim 应该去除首尾空格");
    }
}
