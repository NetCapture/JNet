package com.jnet.core;

/**
 * 调试 curl 命令执行的简单类
 */
public class DebugCurl {
    public static void main(String[] args) throws Exception {
        System.out.println("=== 调试 curl 命令 ===\n");

        // 构造请求体
        String requestBody = "{\"model\":\"gpt-4.1-mini\",\"temperature\":1,\"messages\":[{\"role\":\"system\",\"content\":\"你现在是一名商业数据分析师，你精通数据分析方法和工具，能够从大量数据中提取出有价值的商业洞察。你对业务运营有深入的理解，并能提供数据驱动的优化建议。请在这个角色下为我解答以下问题。\"},{\"role\":\"user\",\"content\":\"hi\"}],\"stream\":true}";

        // 构建 curl 命令
        String API_URL = "https://tbai.xin/v1/chat/completions";
        String API_KEY = "sk-UEKyZyYbsOqsCVIdHaPYYk2XIYkE8z1KavNgInXNqWus5RWc";

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

        System.out.println("🔧 执行 curl 命令...");
        System.out.println();

        try {
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true); // 合并错误和输出流
            Process process = processBuilder.start();

            // 读取输出
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream())
            );

            String line;
            int lineCount = 0;
            int maxLines = 30;

            System.out.println("📥 开始读取输出...\n");

            while ((line = reader.readLine()) != null && lineCount < maxLines) {
                lineCount++;
                System.out.println("[" + String.format("%3d", lineCount) + "] " + line);
                System.out.flush();
            }

            System.out.println("\n⏹️  停止读取 (已读取 " + lineCount + " 行)");

            reader.close();
            process.waitFor();

            System.out.println("✅ 完成");

        } catch (Exception e) {
            System.err.println("❌ 错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
