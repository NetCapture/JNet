package com.jnet.core;

/**
 * 带调试的 JsonBuilder 测试
 */
public class TestJsonBuilderDebug {
    public static void main(String[] args) {
        System.out.println("=== 调试 JsonBuilder ===\n");

        try {
            // 模拟 JNetUtils.json() 的实现
            System.out.println("创建 JsonBuilder...");
            StringBuilder json = new StringBuilder();
            json.append("{");
            System.out.println("初始: " + json.toString());

            // 添加第一个字段
            System.out.println("\n添加 name 字段...");
            if (json.length() > 1) json.append(",");
            json.append("\"").append("name").append("\"");
            json.append(":");
            json.append("\"").append("Alice").append("\"");
            System.out.println("添加后: " + json.toString());

            // 添加第二个字段
            System.out.println("\n添加 age 字段...");
            if (json.length() > 1) json.append(",");
            json.append("\"").append("age").append("\"");
            json.append(":");
            json.append("30");
            System.out.println("添加后: " + json.toString());

            // 结束
            json.append("}");
            System.out.println("\n最终: " + json.toString());

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
