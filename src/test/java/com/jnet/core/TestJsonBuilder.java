package com.jnet.core;

/**
 * 测试 JsonBuilder 的工作方式
 */
public class TestJsonBuilder {
    public static void main(String[] args) {
        System.out.println("=== 测试 JsonBuilder ===\n");

        // 测试 1: 简单的 JSON
        try {
            System.out.println("测试 1: 简单 JSON");
            String json1 = JNetUtils.json()
                    .add("name", "Alice")
                    .add("age", 30)
                    .add("city", "Beijing")
                    .build();
            System.out.println("结果: " + json1);
            System.out.println();
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }

        // 测试 2: 复杂的 JSON（类似 ChatGPT 请求）
        try {
            System.out.println("测试 2: 复杂 JSON");
            String messages = "[{\"role\":\"user\",\"content\":\"hello\"}]";
            String json2 = JNetUtils.json()
                    .add("model", "gpt-4")
                    .add("temperature", 1)
                    .add("messages", messages)
                    .add("stream", true)
                    .build();
            System.out.println("结果: " + json2);
            System.out.println();
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }

        // 测试 3: 多次调用 json() 方法
        try {
            System.out.println("测试 3: 多次调用 json() 方法");
            String json3a = JNetUtils.json()
                    .add("key1", "value1")
                    .build();
            String json3b = JNetUtils.json()
                    .add("key2", "value2")
                    .build();
            System.out.println("JSON A: " + json3a);
            System.out.println("JSON B: " + json3b);
            System.out.println();
        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
