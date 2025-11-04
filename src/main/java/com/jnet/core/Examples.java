package com.jnet.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * JNet使用示例
 * 展示各种常见用法
 *
 * @author JNet Team
 * @version 3.0
 */
public class Examples {

    /**
     * 基本GET请求示例
     */
    public static void basicGetExample() throws Exception {
        // 1. 获取客户端实例
        JNetClient client = JNetClient.getInstance();

        // 2. 创建GET请求
        Request request = client.newGet("https://httpbin.org/get")
                .header("User-Agent", "JNet/3.0")
                .build();

        // 3. 执行请求
        Call call = request.newCall();
        Response response = call.execute();

        // 4. 处理响应
        if (response.isSuccessful()) {
            System.out.println("GET Response: " + response.getBody());
            System.out.println("Status: " + response.getCode() + " " + response.getMessage());
        }
    }

    /**
     * POST JSON请求示例
     */
    public static void postJsonExample() throws Exception {
        JNetClient client = JNetClient.getInstance();

        // 构建JSON数据
        String jsonData = JNetUtils.json()
                .add("name", "JNet")
                .add("version", "3.0")
                .add("author", "NetCapture")
                .add("isAwesome", true)
                .build();

        // 创建POST请求
        Request request = client.newPost("https://httpbin.org/post")
                .json()
                .body(jsonData)
                .build();

        // 执行请求
        Call call = request.newCall();
        Response response = call.execute();

        if (response.isSuccessful()) {
            System.out.println("POST JSON Response: " + response.getBody());
        }
    }

    /**
     * 表单数据提交示例
     */
    public static void formDataExample() throws Exception {
        JNetClient client = JNetClient.getInstance();

        // 构建表单数据
        String formData = "username=admin&password=123456&remember=true";

        // 创建POST请求
        Request request = client.newPost("https://httpbin.org/post")
                .form()
                .body(formData)
                .build();

        // 执行请求
        Call call = request.newCall();
        Response response = call.execute();

        if (response.isSuccessful()) {
            System.out.println("Form Response: " + response.getBody());
        }
    }

    /**
     * 批量添加请求头示例
     */
    public static void headersExample() throws Exception {
        JNetClient client = JNetClient.getInstance();

        // 批量设置请求头
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "JNet/3.0");
        headers.put("Accept", "application/json");
        headers.put("Accept-Language", "zh-CN,zh;q=0.9");
        headers.put("Accept-Encoding", "gzip, deflate");

        Request request = client.newGet("https://httpbin.org/headers")
                .headers(headers)
                .build();

        Call call = request.newCall();
        Response response = call.execute();

        if (response.isSuccessful()) {
            System.out.println("Headers Response: " + response.getBody());
        }
    }

    /**
     * 自定义客户端配置示例
     */
    public static void customClientExample() throws Exception {
        // 创建自定义配置的客户端
        JNetClient client = JNetClient.newBuilder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .followRedirects(false)
                .build();

        Request request = client.newGet("https://httpbin.org/get")
                .build();

        Call call = request.newCall();
        Response response = call.execute();

        if (response.isSuccessful()) {
            System.out.println("Custom Client Response: " + response.getBody());
        }
    }

    /**
     * 异步请求示例
     */
    public static void asyncExample() throws Exception {
        JNetClient client = JNetClient.getInstance();

        Request request = client.newGet("https://httpbin.org/delay/1")
                .build();

        Call call = request.newCall();

        // 异步执行
        CountDownLatch latch = new CountDownLatch(1);
        call.enqueue(new Call.Callback() {
            @Override
            public void onSuccess(Response response) {
                if (response.isSuccessful()) {
                    System.out.println("Async Response: " + response.getBody());
                }
                latch.countDown();
            }

            @Override
            public void onFailure(Exception e) {
                System.out.println("Async Error: " + e.getMessage());
                latch.countDown();
            }
        });

        // 等待异步完成
        latch.await(5, TimeUnit.SECONDS);
    }

    /**
     * 错误处理示例
     */
    public static void errorHandlingExample() throws Exception {
        JNetClient client = JNetClient.getInstance();

        Request request = client.newGet("https://httpbin.org/status/404")
                .build();

        Call call = request.newCall();
        Response response = call.execute();

        // 详细的状态检查
        System.out.println("Response Code: " + response.getCode());
        System.out.println("Is Successful: " + response.isSuccessful());
        System.out.println("Is OK (2xx): " + response.isOk());
        System.out.println("Is Client Error: " + response.isClientError());
        System.out.println("Is Server Error: " + response.isServerError());

        if (!response.isSuccessful()) {
            System.err.println("Error Response: " + response.getBody());
        }
    }

    /**
     * 工具类使用示例
     */
    public static void utilsExample() {
        // 字符串工具
        System.out.println("Is empty: " + JNetUtils.isEmpty(null));
        System.out.println("Is not empty: " + JNetUtils.isNotEmpty("JNet"));
        System.out.println("Is blank: " + JNetUtils.isBlank("   "));
        System.out.println("Trim: '" + JNetUtils.trim("  JNet  ") + "'");

        // Base64编解码
        String original = "Hello, JNet!";
        String encoded = JNetUtils.encodeBase64(original);
        String decoded = JNetUtils.decodeBase64(encoded);
        System.out.println("Original: " + original);
        System.out.println("Encoded: " + encoded);
        System.out.println("Decoded: " + decoded);

        // MD5哈希
        String md5 = JNetUtils.md5("JNet");
        System.out.println("MD5: " + md5);

        // JSON构建
        String json = JNetUtils.json()
                .add("name", "JNet")
                .add("version", 3.0)
                .add("awesome", true)
                .addNull("optional")
                .build();
        System.out.println("JSON: " + json);

        // URL编解码
        String urlEncoded = JNetUtils.urlEncode("Hello 世界");
        String urlDecoded = JNetUtils.urlDecode(urlEncoded);
        System.out.println("URL Encoded: " + urlEncoded);
        System.out.println("URL Decoded: " + urlDecoded);

        // 数字转换
        System.out.println("To Int: " + JNetUtils.toInt("123", 0));
        System.out.println("To Long: " + JNetUtils.toLong("123456789", 0L));
        System.out.println("To Double: " + JNetUtils.toDouble("3.14", 0.0));

        // 文件大小格式化
        System.out.println("Format Size: " + JNetUtils.formatSize(1024));
        System.out.println("Format Size: " + JNetUtils.formatSize(1024 * 1024));

        // 性能计时
        JNetUtils.StopWatch stopWatch = new JNetUtils.StopWatch();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        System.out.println("Elapsed: " + stopWatch);
    }

    /**
     * 主函数 - 运行所有示例
     */
    public static void main(String[] args) {
        try {
            System.out.println("=== JNet 3.0 使用示例 ===\n");

            // 基本GET请求
            System.out.println("1. 基本GET请求示例:");
            basicGetExample();
            System.out.println();

            // POST JSON请求
            System.out.println("2. POST JSON请求示例:");
            postJsonExample();
            System.out.println();

            // 表单数据
            System.out.println("3. 表单数据提交示例:");
            formDataExample();
            System.out.println();

            // 批量请求头
            System.out.println("4. 批量添加请求头示例:");
            headersExample();
            System.out.println();

            // 自定义客户端
            System.out.println("5. 自定义客户端配置示例:");
            customClientExample();
            System.out.println();

            // 异步请求
            System.out.println("6. 异步请求示例:");
            asyncExample();
            System.out.println();

            // 错误处理
            System.out.println("7. 错误处理示例:");
            errorHandlingExample();
            System.out.println();

            // 工具类
            System.out.println("8. 工具类使用示例:");
            utilsExample();
            System.out.println();

            System.out.println("=== 所有示例执行完成 ===");

        } catch (Exception e) {
            System.err.println("示例执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
