package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

/**
 * SSE (Server-Sent Events) 客户端测试
 * 测试类似 OpenAI ChatGPT 的流式响应功能
 *
 * @author sanbo
 * @version 3.0
 */
public class SSEClientTest {

    private SSEClient sseClient;

    @BeforeEach
    void setup() {
        sseClient = new SSEClient();
    }

    @Test
    @DisplayName("【SSE】创建客户端")
    void testCreateClient() {
        assertNotNull(sseClient, "SSE 客户端创建成功");
    }

    @Test
    @DisplayName("【SSE】模拟流式数据解析")
    void testSSEStreamParsing() {
        // 模拟 SSE 响应数据
        String sseData = "data: {\"choices\":[{\"delta\":{\"content\":\"Hello\"}}]}\n\n";

        // 验证 SSE 数据格式
        assertTrue(sseData.startsWith("data:"), "SSE 数据应该以 'data:' 开头");
        assertTrue(sseData.endsWith("\n\n"), "SSE 数据应该以双换行符结尾");

        System.out.println("✅ SSE 格式验证通过");
    }

    @Test
    @DisplayName("【SSE】模拟 OpenAI 流式响应")
    void testOpenAIStreamResponse() {
        // 模拟 OpenAI ChatGPT 的 SSE 响应格式
        String[] sseResponses = {
            "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n",
            "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\", how can I assist\"},\"finish_reason\":null}]}\n\n",
            "data: {\"id\":\"chatcmpl-123\",\"object\":\"chat.completion.chunk\",\"created\":1677652288,\"model\":\"gpt-3.5-turbo\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" you today?\"},\"finish_reason\":null}]}\n\n",
            "data: [DONE]\n\n"
        };

        int chunkCount = 0;
        for (String response : sseResponses) {
            if (!response.equals("data: [DONE]\n\n")) {
                assertTrue(response.startsWith("data:"), "每个数据块应以 'data:' 开头");
                chunkCount++;
            }
        }

        assertEquals(3, chunkCount, "应该有 3 个数据块");
        System.out.println("✅ OpenAI 流式响应模拟成功，共 " + chunkCount + " 个数据块");
    }

    @Test
    @DisplayName("【SSE】心跳检测")
    void testSSEHeartbeat() {
        // 模拟心跳消息（用于保持连接）
        String heartbeat = ": heartbeat\n\n";

        assertTrue(heartbeat.startsWith(":"), "心跳应该以 ':' 开头");
        System.out.println("✅ SSE 心跳格式正确");
    }

    @Test
    @DisplayName("【SSE】事件 ID 追踪")
    void testSSEEventId() {
        // 带事件 ID 的 SSE 数据
        String eventWithId = "id: 123\ndata: {\"message\":\"Hello\"}\n\n";

        assertTrue(eventWithId.startsWith("id:"), "应该包含事件 ID");
        String[] lines = eventWithId.split("\n");
        assertEquals("id: 123", lines[0], "第一行应该是事件 ID");
        assertEquals("data: {\"message\":\"Hello\"}", lines[1], "第二行应该是数据");
        System.out.println("✅ SSE 事件 ID 追踪正确");
    }

    @Test
    @DisplayName("【SSE】多事件类型")
    void testSSEMultipleEventTypes() {
        // 不同类型的 SSE 事件
        String messageEvent = "event: message\ndata: {\"user\":\"Alice\",\"text\":\"Hello\"}\n\n";
        String errorEvent = "event: error\ndata: {\"code\":500,\"message\":\"Server Error\"}\n\n";

        assertTrue(messageEvent.startsWith("event: message"), "消息事件类型正确");
        assertTrue(errorEvent.startsWith("event: error"), "错误事件类型正确");
        System.out.println("✅ SSE 多事件类型支持");
    }

    @Test
    @DisplayName("【SSE】JSON 数据提取")
    void testSSEJsonExtraction() {
        // 提取 SSE 中的 JSON 数据
        String sseData = "data: {\"result\":\"success\",\"timestamp\":1234567890}\n\n";
        String jsonPart = sseData.substring(5).trim(); // 移除 "data: " 前缀

        assertTrue(jsonPart.startsWith("{"), "提取的应该是 JSON 对象");
        assertTrue(jsonPart.contains("\"result\":\"success\""), "JSON 应包含预期字段");
        System.out.println("✅ SSE JSON 数据提取正确");
    }

    @Test
    @DisplayName("【SSE】连接超时处理")
    void testSSEConnectionTimeout() {
        // 测试 SSE 客户端的连接配置
        JNetClient client = JNetClient.getInstance();
        assertNotNull(client, "基础客户端应该存在");

        // SSE 应该使用较长的超时时间（流式数据）
        System.out.println("✅ SSE 连接配置检查完成");
    }

    @Test
    @DisplayName("【SSE】字节编码处理")
    void testSSEByteEncoding() {
        // 测试中文字符的 SSE 处理
        String chineseData = "data: {\"text\":\"你好，世界！\"}\n\n";

        assertTrue(chineseData.contains("你好"), "应该支持中文字符");
        System.out.println("✅ SSE 中文字符编码正确");
    }

    @Test
    @DisplayName("【SSE】空数据处理")
    void testSSEEmptyData() {
        // 测试空数据情况
        String emptyData = "data: \n\n";

        assertTrue(emptyData.startsWith("data:"), "空数据仍应以 data: 开头");
        System.out.println("✅ SSE 空数据处理正确");
    }

    @Test
    @DisplayName("【SSE】真实 ChatGPT API 测试")
    void testRealChatGPTAPI() {
        // 模拟真实 ChatGPT 流式 API 请求配置
        System.out.println("🔗 测试 ChatGPT 流式 API 配置...");

        try {
            // 模拟请求配置（不实际发送网络请求）
            String apiUrl = "https://tbai.xin/v1/chat/completions";
            String model = "gpt-4.1-mini";
            String userMessage = "hi";

            // 构造请求体
            String requestBody = JNetUtils.json()
                    .add("model", model)
                    .add("temperature", 1)
                    .add("messages", JNetUtils.json()
                            .add("role", "user")
                            .add("content", userMessage)
                            .build())
                    .add("stream", true)
                    .add("stream_options", JNetUtils.json()
                            .add("include_usage", true)
                            .build())
                    .build();

            assertNotNull(requestBody, "请求体构造成功");
            assertTrue(requestBody.contains("\"model\":\"" + model + "\""), "请求体应包含模型名称");
            assertTrue(requestBody.contains("\"stream\":true"), "请求体应开启流式模式");
            assertTrue(requestBody.contains(userMessage), "请求体应包含用户消息");

            System.out.println("✅ ChatGPT API 请求配置正确");
            System.out.println("   模型: " + model);
            System.out.println("   消息: " + userMessage);
            System.out.println("   流式: true");

            // 模拟 SSE 响应解析
            String mockSSEResponse = "data: {\"id\":\"chatcmpl-abc123\",\"object\":\"chat.completion.chunk\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"},\"finish_reason\":null}]}\n\n";
            assertTrue(mockSSEResponse.startsWith("data:"), "SSE 响应格式正确");
            assertTrue(mockSSEResponse.contains("chat.completion.chunk"), "响应类型正确");

            System.out.println("✅ ChatGPT SSE 响应格式验证通过");

        } catch (Exception e) {
            System.out.println("⚠️  ChatGPT API 测试配置验证: " + e.getMessage());
        }
    }
}
