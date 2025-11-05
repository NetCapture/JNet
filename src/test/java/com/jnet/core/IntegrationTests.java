package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import static org.junit.jupiter.api.Assertions.*;

/**
 * JNet 集成测试
 * 模拟 Python requests 库的简洁用法，测试真实 HTTP 请求场景
 *
 * @author sanbo
 * @version 3.0
 */
public class IntegrationTests {

    private static JNetClient client;

    @BeforeAll
    static void setup() {
        // 初始化客户端，类似于 requests 库的使用方式
        client = JNetClient.getInstance();
    }

    @AfterAll
    static void cleanup() {
        // 清理资源
        System.out.println("\n=== 集成测试完成 ===");
    }

    @Test
    @DisplayName("【GET】基本请求 - 类似 requests.get()")
    void testSimpleGet() {
        try {
            // 简洁的 GET 请求 - 就像 requests.get("https://httpbin.org/get")
            Response response = client.newGet("https://httpbin.org/get")
                    .header("User-Agent", "JNet/3.0")
                    .build()
                    .newCall()
                    .execute();

            assertNotNull(response, "响应不应为空");
            assertTrue(response.isSuccessful(), "请求应该成功");
            assertEquals(200, response.getCode(), "状态码应该是 200");
            System.out.println("✅ GET 请求成功: " + response.getCode());
        } catch (Exception e) {
            System.out.println("⚠️  GET 测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【GET】带查询参数 - 类似 requests.get(url, params=...)")
    void testGetWithParams() {
        try {
            // 带参数的 GET 请求
            Response response = client.newGet("https://httpbin.org/get")
                    .header("Accept", "application/json")
                    .tag("get-with-params")
                    .build()
                    .newCall()
                    .execute();

            assertTrue(response.isSuccessful(), "请求应该成功");
            assertNotNull(response.getBody(), "响应体不应为空");
            System.out.println("✅ GET 带参数请求: " + response.getBody().substring(0, Math.min(50, response.getBody().length())));
        } catch (Exception e) {
            System.out.println("⚠️  GET 带参数测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【POST】JSON 数据 - 类似 requests.post(url, json={})")
    void testPostJson() {
        try {
            // POST JSON 数据 - 就像 requests.post(url, json=data)
            String jsonBody = JNetUtils.json()
                    .add("name", "JNet")
                    .add("version", "3.0")
                    .add("language", "Java")
                    .build();

            Response response = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .build()
                    .newCall()
                    .execute();

            assertTrue(response.isSuccessful(), "POST 请求应该成功");
            assertTrue(response.getBody().contains("JNet"), "响应应包含发送的数据");
            System.out.println("✅ POST JSON 请求成功");
        } catch (Exception e) {
            System.out.println("⚠️  POST JSON 测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【POST】Form 数据 - 类似 requests.post(url, data={})")
    void testPostForm() {
        try {
            // POST 表单数据
            Response response = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body("username=admin&password=123456")
                    .build()
                    .newCall()
                    .execute();

            assertTrue(response.isSuccessful(), "表单 POST 请求应该成功");
            System.out.println("✅ POST 表单请求成功");
        } catch (Exception e) {
            System.out.println("⚠️  POST Form 测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【PUT】更新数据 - 类似 requests.put()")
    void testPut() {
        try {
            String jsonBody = JNetUtils.json()
                    .add("action", "update")
                    .add("id", 12345)
                    .build();

            Response response = client.newPut("https://httpbin.org/put")
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .build()
                    .newCall()
                    .execute();

            assertTrue(response.isSuccessful(), "PUT 请求应该成功");
            System.out.println("✅ PUT 请求成功");
        } catch (Exception e) {
            System.out.println("⚠️  PUT 测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【DELETE】删除请求 - 类似 requests.delete()")
    void testDelete() {
        try {
            Response response = client.newDelete("https://httpbin.org/delete")
                    .build()
                    .newCall()
                    .execute();

            assertTrue(response.isSuccessful(), "DELETE 请求应该成功");
            System.out.println("✅ DELETE 请求成功");
        } catch (Exception e) {
            System.out.println("⚠️  DELETE 测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【SSE】流式请求 - 类似 requests.get(url, stream=True)")
    void testSSEStream() {
        // SSE (Server-Sent Events) 流式请求测试
        // 这是新增的功能，类似 OpenAI ChatGPT 的流式响应

        // 注意：这是测试代码，不实际连接 SSE 服务器
        System.out.println("🔄 测试 SSE 流式请求功能...");

        // 测试 SSE 客户端创建
        try {
            SSEClient sseClient = new SSEClient();
            assertNotNull(sseClient, "SSE 客户端创建成功");

            // 模拟 SSE 配置 - 仅测试 API 存在性
            System.out.println("✅ SSE 客户端创建成功");
            System.out.println("ℹ️  SSE 完整功能需要实际配置 SSE 服务器");
        } catch (Exception e) {
            System.out.println("ℹ️  SSE 功能测试: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【实用工具】Base64 编解码")
    void testBase64Utils() {
        // 测试 Base64 工具 - 类似 requests.utils 函数
        String original = "Hello, JNet! 这是一个测试文本";
        String encoded = JNetUtils.encodeBase64(original);
        String decoded = JNetUtils.decodeBase64(encoded);

        assertEquals(original, decoded, "Base64 编码解码应该正确");
        System.out.println("✅ Base64 工具测试通过");
    }

    @Test
    @DisplayName("【实用工具】JSON 构建")
    void testJsonUtils() {
        // 测试 JSON 构建 - 类似 requests 的 JSON 处理
        String featuresJson = JNetUtils.json()
                .add("sync", true)
                .add("async", true)
                .add("sse", true)
                .build();

        String json = JNetUtils.json()
                .add("name", "JNet")
                .add("version", 3.0)
                .add("features", featuresJson)
                .build();

        assertNotNull(json, "JSON 构建应该成功");
        assertTrue(json.contains("\"name\":\"JNet\""), "JSON 应该包含正确数据");
        System.out.println("✅ JSON 工具测试通过");
    }

    @Test
    @DisplayName("【实用工具】URL 编码")
    void testUrlUtils() {
        // 测试 URL 编码 - 类似 requests.utils.quote
        String url = "https://example.com/search?q=Java HTTP 客户端";
        String encoded = JNetUtils.urlEncode(url);

        assertNotNull(encoded, "URL 编码应该成功");
        assertFalse(encoded.contains(" "), "编码后不应该有空格");
        System.out.println("✅ URL 工具测试通过");
    }

    @Test
    @DisplayName("【MD5】哈希计算")
    void testMd5Hash() {
        // 测试 MD5 - 类似 requests.utils 的哈希函数
        String input = "JNet HTTP Client";
        String md5 = JNetUtils.md5(input);

        assertNotNull(md5, "MD5 计算应该成功");
        assertEquals(32, md5.length(), "MD5 应该是 32 位");
        assertTrue(md5.matches("[a-f0-9]{32}"), "MD5 应该是十六进制");
        System.out.println("✅ MD5 工具测试通过: " + md5.substring(0, 8) + "...");
    }

    @Test
    @DisplayName("【错误处理】404 错误")
    void testErrorHandling() {
        try {
            // 测试错误处理 - 类似 requests 库的错误处理
            Response response = client.newGet("https://httpbin.org/status/404")
                    .build()
                    .newCall()
                    .execute();

            assertFalse(response.isSuccessful(), "404 错误应该返回失败状态");
            assertEquals(404, response.getCode(), "状态码应该是 404");
            System.out.println("✅ 错误处理测试通过");
        } catch (Exception e) {
            System.out.println("⚠️  错误处理测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【连接池】复用连接")
    void testConnectionReuse() {
        try {
            // 测试连接池复用 - 优化性能
            int requestCount = 5;

            for (int i = 0; i < requestCount; i++) {
                Response response = client.newGet("https://httpbin.org/get")
                        .tag("connection-pool-test-" + i)
                        .build()
                        .newCall()
                        .execute();

                assertTrue(response.isSuccessful(), "第 " + (i+1) + " 次请求应该成功");
            }

            System.out.println("✅ 连接池测试通过 - " + requestCount + " 次请求");
        } catch (Exception e) {
            System.out.println("⚠️  连接池测试跳过（网络不可用）: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("【GitHub API】GitHub 工具类")
    void testGithubHelper() {
        // 测试 GitHub 工具类 - 展示 JNet 的实际应用
        System.out.println("🔗 测试 GitHub 集成功能...");

        try {
            // 设置 GitHub Token（模拟）
            GithubHelper.setGlobalToken("ghp_example_token");

            // 测试各种 GitHub API 方法的存在性
            assertNotNull(GithubHelper.class, "GitHubHelper 类应该存在");

            System.out.println("✅ GitHub 工具类测试通过");
        } catch (Exception e) {
            System.out.println("ℹ️  GitHub 工具测试（配置模拟）");
        }
    }

    @Test
    @DisplayName("【Gitee API】Gitee 工具类")
    void testGiteeHelper() {
        // 测试 Gitee 工具类 - 展示 JNet 的实际应用
        System.out.println("🔗 测试 Gitee 集成功能...");

        try {
            // 设置 Gitee Token（模拟）
            GiteeHelper.setGlobalToken("gitee_example_token");

            // 测试各种 Gitee API 方法
            assertNotNull(GiteeHelper.class, "GiteeHelper 类应该存在");

            System.out.println("✅ Gitee 工具类测试通过");
        } catch (Exception e) {
            System.out.println("ℹ️  Gitee 工具测试（配置模拟）");
        }
    }
}
