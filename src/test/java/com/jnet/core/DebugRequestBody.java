package com.jnet.core;

import java.util.Scanner;

/**
 * 调试请求体生成的简单类
 */
public class DebugRequestBody {
    public static void main(String[] args) {
        System.out.println("=== 调试请求体生成 ===\n");

        // 获取用户输入
        String userMessage = getUserMessage(args);

        // 构造请求体
        try {
            String requestBody = JNetUtils.json()
                    .add("model", "gpt-4.1-mini")
                    .add("temperature", 1)
                    .add("messages", "[" +
                            "{\"role\":\"system\",\"content\":\"你现在是一名商业数据分析师，你精通数据分析方法和工具，能够从大量数据中提取出有价值的商业洞察。你对业务运营有深入的理解，并能提供数据驱动的优化建议。请在这个角色下为我解答以下问题。\"}," +
                            "{\"role\":\"user\",\"content\":\"" + escapeJson(userMessage) + "\"}" +
                            "]")
                    .add("stream", true)
                    .build();

            System.out.println("生成的请求体:");
            System.out.println("================================");
            System.out.println(requestBody);
            System.out.println("================================");
            System.out.println();
            System.out.println("请求体长度: " + requestBody.length() + " 字符");
        } catch (Exception e) {
            System.err.println("❌ 生成请求体失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 获取用户消息
     */
    private static String getUserMessage(String[] args) {
        if (args != null && args.length > 0) {
            return String.join(" ", args);
        }

        Scanner scanner = new Scanner(System.in);
        System.out.print("请输入你的问题: ");

        try {
            String message = scanner.nextLine();
            return message.isEmpty() ? "hi" : message;
        } catch (Exception e) {
            return "hi";
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * 转义 JSON 字符串
     */
    private static String escapeJson(String str) {
        return str.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
