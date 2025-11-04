package com.jnet.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * JNet 3.0 API 测试类
 * 验证新架构功能
 */
public class TestJNet {

    public static void main(String[] args) {
        System.out.println("=== JNet 3.0 API 测试 ===\n");

        testClientCreation();
        testRequestBuilder();
        testJNetUtils();

        System.out.println("\n=== 所有测试完成 ===");
    }

    /**
     * 测试客户端创建
     */
    private static void testClientCreation() {
        System.out.println("1. 测试客户端创建:");
        try {
            // 测试单例获取
            JNetClient client1 = JNetClient.getInstance();
            JNetClient client2 = JNetClient.getInstance();
            System.out.println("   ✅ 单例模式: " + (client1 == client2 ? "PASS" : "FAIL"));

            // 测试 Builder 模式
            JNetClient customClient = JNetClient.newBuilder()
                    .connectTimeout(5, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS)
                    .build();
            System.out.println("   ✅ Builder 模式: " + (customClient != null ? "PASS" : "FAIL"));

            // 测试便捷方法
            Request.Builder getBuilder = client1.newGet("https://httpbin.org/get");
            System.out.println("   ✅ 便捷GET方法: " + (getBuilder != null ? "PASS" : "FAIL"));

            Request.Builder postBuilder = client1.newPost("https://httpbin.org/post");
            System.out.println("   ✅ 便捷POST方法: " + (postBuilder != null ? "PASS" : "FAIL"));

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    /**
     * 测试请求构建器
     */
    private static void testRequestBuilder() {
        System.out.println("2. 测试请求构建器:");
        try {
            JNetClient client = JNetClient.getInstance();

            // 构建GET请求
            Request getRequest = client.newGet("https://httpbin.org/get")
                    .header("User-Agent", "JNet/3.0")
                    .header("Accept", "application/json")
                    .tag("test-get")
                    .build();

            System.out.println("   ✅ GET请求构建: " + (getRequest != null ? "PASS" : "FAIL"));
            System.out.println("      URL: " + getRequest.getUrlString());
            System.out.println("      Method: " + getRequest.getMethod());
            System.out.println("      Headers: " + getRequest.getHeaders().size() + " 个");

            // 构建POST请求
            String jsonBody = JNetUtils.json()
                    .add("name", "JNet")
                    .add("version", "3.0")
                    .build();

            Request postRequest = client.newPost("https://httpbin.org/post")
                    .header("Content-Type", "application/json")
                    .body(jsonBody)
                    .build();

            System.out.println("   ✅ POST请求构建: " + (postRequest != null ? "PASS" : "FAIL"));
            System.out.println("      Body 长度: " + (postRequest.getBody() != null ? postRequest.getBody().length() : 0));

            // 构建DELETE请求
            Request deleteRequest = client.newDelete("https://httpbin.org/delete")
                    .build();
            System.out.println("   ✅ DELETE请求构建: " + (deleteRequest != null ? "PASS" : "FAIL"));

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }

    /**
     * 测试工具类
     */
    private static void testJNetUtils() {
        System.out.println("3. 测试JNetUtils工具类:");
        try {
            // 测试 JSON 构建
            String json = JNetUtils.json()
                    .add("key1", "value1")
                    .add("key2", 123)
                    .add("key3", true)
                    .build();
            System.out.println("   ✅ JSON构建: " + (json != null ? "PASS" : "FAIL"));
            System.out.println("      结果: " + json);

            // 测试 Base64 编码
            String text = "Hello, JNet!";
            String base64 = JNetUtils.encodeBase64(text);
            System.out.println("   ✅ Base64编码: " + (base64 != null ? "PASS" : "FAIL"));
            System.out.println("      原文: " + text);
            System.out.println("      编码: " + base64);

            // 测试 Base64 解码
            String decoded = JNetUtils.decodeBase64(base64);
            System.out.println("   ✅ Base64解码: " + (text.equals(decoded) ? "PASS" : "FAIL"));
            System.out.println("      解码: " + decoded);

            // 测试 URL 编码
            String url = "https://example.com/search?q=java 网络";
            String encoded = JNetUtils.urlEncode(url);
            System.out.println("   ✅ URL编码: " + (encoded != null ? "PASS" : "FAIL"));
            System.out.println("      原文: " + url);
            System.out.println("      编码: " + encoded);

            // 测试 MD5
            String md5 = JNetUtils.md5("JNet");
            System.out.println("   ✅ MD5计算: " + (md5 != null ? "PASS" : "FAIL"));
            System.out.println("      结果: " + md5);

        } catch (Exception e) {
            System.out.println("   ❌ FAIL: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();
    }
}
