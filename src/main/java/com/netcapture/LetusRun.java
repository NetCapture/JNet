package com.netcapture;

import com.jnet.core.Examples;

/**
 * JNet 主程序入口
 * 提供各种网络请求示例和测试功能
 *
 * @author sanbo
 * @version 3.0.0
 */
public class LetusRun {

    public static void main(String[] args) {
        System.out.println("=== JNet v3.0.0 - 网络请求库测试 ===");
        System.out.println("GitHub: https://github.com/NetCapture/JNet");
        System.out.println();

        if (args.length == 0) {
            showHelp();
            return;
        }

        String command = args[0].toLowerCase();
        switch (command) {
            case "get":
                runGetExample();
                break;
            case "post":
                runPostExample();
                break;
            case "all":
                runAllExamples();
                break;
            case "help":
            case "--help":
            case "-h":
                showHelp();
                break;
            default:
                System.out.println("未知命令: " + command);
                showHelp();
                break;
        }
    }

    private static void showHelp() {
        System.out.println("用法: java -jar jnt-*.jar [命令]");
        System.out.println();
        System.out.println("命令:");
        System.out.println("  get    运行 GET 请求示例");
        System.out.println("  post   运行 POST 请求示例");
        System.out.println("  all    运行所有示例");
        System.out.println("  help   显示此帮助信息");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  java -jar jnt-3.0.0-jar-with-dependencies.jar get");
    }

    private static void runGetExample() {
        try {
            System.out.println("运行 GET 请求示例...");
            Examples.basicGetExample();
            System.out.println("✅ GET 示例完成");
        } catch (Exception e) {
            System.err.println("❌ GET 示例失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runPostExample() {
        try {
            System.out.println("运行 POST 请求示例...");
            Examples.postJsonExample();
            System.out.println("✅ POST 示例完成");
        } catch (Exception e) {
            System.err.println("❌ POST 示例失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void runAllExamples() {
        System.out.println("运行所有示例...");
        runGetExample();
        System.out.println();
        runPostExample();
        System.out.println();
        System.out.println("✅ 所有示例完成");
    }
}
