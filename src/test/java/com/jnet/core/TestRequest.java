package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Request 构建器单元测试
 *
 * @author sanbo
 * @version 3.0
 */
public class TestRequest {

    @Test
    @DisplayName("测试构建 GET 请求")
    void testBuildGetRequest() {
        Request request = JNetClient.getInstance()
                .newGet("https://httpbin.org/get")
                .header("User-Agent", "JNet/3.0")
                .header("Accept", "application/json")
                .tag("test-get")
                .build();

        assertNotNull(request, "请求构建应该成功");
        assertEquals("GET", request.getMethod(), "请求方法应该是 GET");
        assertEquals("https://httpbin.org/get", request.getUrlString(), "URL 应该正确");
        assertEquals(2, request.getHeaders().size(), "应该有 2 个头部");
    }

    @Test
    @DisplayName("测试构建 POST 请求")
    void testBuildPostRequest() {
        String jsonBody = JNetUtils.json()
                .add("name", "JNet")
                .add("version", "3.0")
                .build();

        Request request = JNetClient.getInstance()
                .newPost("https://httpbin.org/post")
                .header("Content-Type", "application/json")
                .body(jsonBody)
                .build();

        assertNotNull(request, "POST 请求构建应该成功");
        assertEquals("POST", request.getMethod(), "请求方法应该是 POST");
        assertNotNull(request.getBody(), "请求体应该不为空");
        assertTrue(request.getBody().length() > 0, "请求体应该不为空");
    }

    @Test
    @DisplayName("测试构建 DELETE 请求")
    void testBuildDeleteRequest() {
        Request request = JNetClient.getInstance()
                .newDelete("https://httpbin.org/delete")
                .build();

        assertNotNull(request, "DELETE 请求构建应该成功");
        assertEquals("DELETE", request.getMethod(), "请求方法应该是 DELETE");
    }

    @Test
    @DisplayName("测试构建 PUT 请求")
    void testBuildPutRequest() {
        Request request = JNetClient.getInstance()
                .newPut("https://httpbin.org/put")
                .build();

        assertNotNull(request, "PUT 请求构建应该成功");
        assertEquals("PUT", request.getMethod(), "请求方法应该是 PUT");
    }
}
