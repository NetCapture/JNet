package com.jnet.core;

import java.util.Scanner;

/**
 * ChatGPT SSE 流式测试 - 调试版本
 * 带有详细日志输出
 */
public class ChatGPTSSETestDebug {

    // ===== 配置区域 - 请修改这些值 =====
    private static final String API_KEY = "sk-UEKyZyYbsOqsCVIdHaPYYk2XIYkE8z1KavNgInXNqWus5RWc";
    private static final String API_URL = "https://tbai.xin/v1/chat/completions";
    private static final String MODEL = "gpt-4.1-mini";
    // ==================================

    public static void main(String[] args) {
        System.out.println("=== ChatGPT SSE 流式测试 - 调试版 ===\n");

        // 检查 API key
        if (API_KEY.equals("YOUR_API_KEY_HERE") || API_KEY.isEmpty()) {
            System.out.println("❌ 请先配置 API_KEY！");
            return;
        }

        // 获取用户输入
        String userMessage = getUserMessage(args);

        try {
            // 发送 SSE 请求
            sendSSERequest(userMessage);

        } catch (Exception e) {
            System.err.println("❌ 请求失败: " + e.getMessage());
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
        System.out.print("请输入你的问题 (回车发送, 输入 quit 退出): ");

        try {
            String message = scanner.nextLine();

            if (message.equalsIgnoreCase("quit") || message.equalsIgnoreCase("q")) {
                System.out.println("👋 再见！");
                System.exit(0);
            }

            return message.isEmpty() ? "你好，请介绍一下你自己" : message;
        } catch (Exception e) {
            System.out.println("⚠️  读取输入失败，使用默认问题");
            return "你好，请介绍一下你自己";
        } finally {
            if (scanner != null) {
                scanner.close();
            }
        }
    }

    /**
     * 发送 SSE 请求（使用 curl 命令）
     */
    private static void sendSSERequest(String userMessage) throws Exception {
        System.out.println("🔄 正在连接 ChatGPT...");
        System.out.println("🤖 模型: " + MODEL);
        System.out.println("💬 问题: " + userMessage);
        System.out.println("\n" + "=".repeat(50) + "\n");

        // 构造请求体
        System.out.println("📝 生成请求体...");
        String messagesJson = "[" +
                "{\"role\":\"system\",\"content\":\"你现在是一名商业数据分析师，你精通数据分析方法和工具，能够从大量数据中提取出有价值的商业洞察。你对业务运营有深入的理解，并能提供数据驱动的优化建议。请在这个角色下为我解答以下问题。\"}," +
                "{\"role\":\"user\",\"content\":\"" + escapeJson(userMessage) + "\"}" +
                "]";
        System.out.println("  - messages JSON: " + messagesJson.substring(0, Math.min(50, messagesJson.length())) + "...");

        // 手动构造完整 JSON（避免 messages 被当作字符串）
        String requestBody = "{" +
                "\"model\":\"" + MODEL + "\"," +
                "\"temperature\":1," +
                "\"messages\":" + messagesJson + "," +
                "\"stream\":true" +
                "}";
        System.out.println("  - 完整请求体: " + requestBody);
        System.out.println("  - 请求体长度: " + requestBody.length() + " 字符");

        System.out.println("\n📤 使用 curl 发送请求...");
        System.out.println("🔑 API Key: " + maskApiKey(API_KEY));
        System.out.println("🌐 URL: " + API_URL);
        System.out.println();

        try {
            // 使用 ProcessBuilder 执行 curl 命令
            System.out.println("🔧 构建 curl 命令...");
            String[] curlCommand = buildCurlCommandArray(requestBody);
            System.out.println("  - 命令长度: " + curlCommand.length + " 个参数");
            System.out.println("  - 第1个参数: " + curlCommand[0]);
            System.out.println("  - 第2个参数: " + curlCommand[1]);
            System.out.println("  - 第3个参数: " + curlCommand[2]);
            System.out.println("  - 第4个参数: " + curlCommand[3]);

            System.out.println("\n🚀 启动进程...");
            ProcessBuilder processBuilder = new ProcessBuilder(curlCommand);
            processBuilder.redirectErrorStream(true); // 合并错误和输出流，避免阻塞
            Process process = processBuilder.start();
            System.out.println("  - 进程已启动: " + process);

            // 读取流式输出
            System.out.println("\n📥 开始读取输出...");
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            String line;
            int chunkCount = 0;
            int maxLines = 30;

            System.out.println("✅ 连接成功! 开始接收流式数据...\n");

            while ((line = reader.readLine()) != null && chunkCount < maxLines) {
                chunkCount++;
                System.out.println("[" + String.format("%3d", chunkCount) + "] " + line);

                line = line.trim();

                if (line.isEmpty()) {
                    continue;
                }

                // 处理 SSE 数据
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();

                    if (data.equals("[DONE]")) {
                        System.out.println("\n" + "=".repeat(50));
                        System.out.println("🎉 响应完成! 共接收 " + chunkCount + " 个数据块");
                        break;
                    }

                    // 解析 JSON 并提取 content
                    if (data.startsWith("{")) {
                        String content = extractContentFromJson(data);

                        if (content != null && !content.isEmpty()) {
                            System.out.print(">>> 内容: " + content + "\n");
                            System.out.flush();
                        }
                    }
                } else if (line.startsWith("< HTTP/")) {
                    // 显示状态码
                    if (line.contains("200")) {
                        System.out.println("✅ 状态码: 200 OK");
                    }
                }
            }

            System.out.println("\n⏹️  停止读取 (已读取 " + chunkCount + " 行)");

            reader.close();
            int exitCode = process.waitFor();

            System.out.println("  - 进程退出码: " + exitCode);

            if (exitCode != 0) {
                System.err.println("\n⚠️  curl 命令退出码: " + exitCode);
            }

        } catch (Exception e) {
            throw new Exception("SSE 请求失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建 curl 命令（数组形式，用于 ProcessBuilder）
     */
    private static String[] buildCurlCommandArray(String requestBody) {
        java.util.List<String> command = new java.util.ArrayList<>();
        command.add("curl");
        command.add("-s");
        command.add("-N");
        command.add(API_URL);
        command.add("-H");
        command.add("accept: */*");
        command.add("-H");
        command.add("accept-language: zh-CN");
        command.add("-H");
        command.add("authorization: Bearer " + API_KEY);
        command.add("-H");
        command.add("content-type: application/json");
        command.add("-H");
        command.add("http-referer: https://cherry-ai.com");
        command.add("-H");
        command.add("sec-ch-ua: Not)A;Brand;v=8, Chromium;v=138");
        command.add("-H");
        command.add("sec-ch-ua-mobile: ?0");
        command.add("-H");
        command.add("sec-ch-ua-platform: macOS");
        command.add("-H");
        command.add("user-agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) CherryStudio/1.6.7 Chrome/138.0.7204.251 Electron/37.6.0 Safari/537.36");
        command.add("-H");
        command.add("x-title: Cherry Studio");
        command.add("--data-raw");
        command.add(requestBody);

        return command.toArray(new String[0]);
    }

    /**
     * 从 JSON 中提取 content 字段（ChatGPT 格式）
     */
    private static String extractContentFromJson(String json) {
        try {
            // ChatGPT SSE 格式: {"choices":[{"delta":{"content":"..."}}]}
            int deltaIndex = json.indexOf("\"delta\":{");
            if (deltaIndex == -1) return null;

            int contentIndex = json.indexOf("\"content\":\"", deltaIndex);
            if (contentIndex == -1) return null;

            int contentStart = contentIndex + 11; // 跳过 "\"content\":\""
            int contentEnd = contentStart;

            // 找到结束引号，处理转义
            while (contentEnd < json.length()) {
                char c = json.charAt(contentEnd);
                if (c == '\"') {
                    // 检查转义
                    int backslashCount = 0;
                    for (int i = contentEnd - 1; i >= contentStart && json.charAt(i) == '\\'; i--) {
                        backslashCount++;
                    }
                    if (backslashCount % 2 == 0) break; // 非转义引号
                }
                contentEnd++;
            }

            if (contentEnd > contentStart) {
                String content = json.substring(contentStart, contentEnd);
                // 处理转义字符
                content = content.replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                                .replace("\\t", "\t");
                return content;
            }
        } catch (Exception e) {
            System.err.println("⚠️  JSON 解析错误: " + e.getMessage());
        }
        return null;
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

    /**
     * 隐藏 API Key
     */
    private static String maskApiKey(String apiKey) {
        if (apiKey.length() <= 10) {
            return "****";
        }
        return apiKey.substring(0, 8) + "..." + apiKey.substring(apiKey.length() - 4);
    }
}
