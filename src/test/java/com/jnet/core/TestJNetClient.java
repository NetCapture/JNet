package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JNet 客户端单元测试
 * 标准 JUnit 5 测试案例
 *
 * @author sanbo
 * @version 3.0
 */
public class TestJNetClient {

    private JNetClient client;

    @BeforeEach
    void setUp() {
        client = JNetClient.getInstance();
    }

    @Test
    @DisplayName("测试单例模式")
    void testSingleton() {
        JNetClient client1 = JNetClient.getInstance();
        JNetClient client2 = JNetClient.getInstance();
        assertSame(client1, client2, "应该是同一个实例");
    }

    @Test
    @DisplayName("测试 Builder 模式")
    void testBuilder() {
        JNetClient customClient = JNetClient.newBuilder()
                .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();

        assertNotNull(customClient, "客户端构建应该成功");
    }

    @Test
    @DisplayName("测试便捷 GET 方法")
    void testNewGet() {
        Request.Builder getBuilder = client.newGet("https://httpbin.org/get");
        assertNotNull(getBuilder, "GET 构建器应该不为空");
    }

    @Test
    @DisplayName("测试便捷 POST 方法")
    void testNewPost() {
        Request.Builder postBuilder = client.newPost("https://httpbin.org/post");
        assertNotNull(postBuilder, "POST 构建器应该不为空");
    }

    @Test
    @DisplayName("测试便捷 PUT 方法")
    void testNewPut() {
        Request.Builder putBuilder = client.newPut("https://httpbin.org/put");
        assertNotNull(putBuilder, "PUT 构建器应该不为空");
    }

    @Test
    @DisplayName("测试便捷 DELETE 方法")
    void testNewDelete() {
        Request.Builder deleteBuilder = client.newDelete("https://httpbin.org/delete");
        assertNotNull(deleteBuilder, "DELETE 构建器应该不为空");
    }
}
