package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Response 类单元测试
 *
 * @author sanbo
 * @version 3.0
 */
public class TestResponse {

    @Test
    @DisplayName("测试创建成功响应")
    void testCreateSuccessResponse() {
        Request request = JNetClient.getInstance().newGet("https://httpbin.org/get").build();

        Response response = Response.success(request)
                .code(200)
                .message("OK")
                .body("{\"test\": \"success\"}")
                .header("Content-Type", "application/json")
                .duration(100)
                .build();

        assertNotNull(response, "响应创建应该成功");
        assertEquals(200, response.getCode(), "状态码应该是 200");
        assertEquals("OK", response.getMessage(), "消息应该是 OK");
        assertTrue(response.isSuccessful(), "应该表示成功");
        assertTrue(response.isOk(), "应该是 OK 状态");
        assertEquals(100, response.getDuration(), "耗时应该是 100ms");
    }

    @Test
    @DisplayName("测试创建失败响应")
    void testCreateFailureResponse() {
        Request request = JNetClient.getInstance().newGet("https://httpbin.org/get").build();

        Response response = Response.failure(request)
                .code(404)
                .message("Not Found")
                .build();

        assertNotNull(response, "响应创建应该成功");
        assertEquals(404, response.getCode(), "状态码应该是 404");
        assertFalse(response.isSuccessful(), "应该表示失败");
    }
}
