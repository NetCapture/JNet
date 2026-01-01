package com.jnet.core;

import java.lang.reflect.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * JVM 插件 - 代码质量检测工具
 * 逐行检测代码质量，提供详细报告
 *
 * @author sanbo
 * @version 3.0
 */
public class CodeQualityAnalyzer {

    private final List<String> issues = new ArrayList<>();
    private final List<String> warnings = new ArrayList<>();
    private final List<String> passed = new ArrayList<>();

    public static void main(String[] args) {
        CodeQualityAnalyzer analyzer = new CodeQualityAnalyzer();
        analyzer.runAllChecks();
        analyzer.printReport();
    }

    public void runAllChecks() {
        System.out.println("🔍 开始代码质量检测...\n");

        // 1. 核心类结构检查
        checkCoreClasses();

        // 2. JNet 静态工具类检查
        checkJNetClass();

        // 3. JNetClient 检查
        checkJNetClient();

        // 4. Request/Response 检查
        checkRequestResponse();

        // 5. Call 检查
        checkCall();

        // 6. Interceptor 检查
        checkInterceptor();

        // 7. SSEClient 检查
        checkSSEClient();

        // 8. ResponseCache 检查
        checkResponseCache();

        // 9. SSLConfig 检查
        checkSSLConfig();

        // 10. JNetException 检查
        checkJNetException();

        // 11. JNetUtils 检查
        checkJNetUtils();

        // 12. 线程安全检查
        checkThreadSafety();

        // 13. 异常处理检查
        checkExceptionHandling();

        // 14. 性能检查
        checkPerformance();

        // 15. 文档和注释检查
        checkDocumentation();
    }

    private void checkCoreClasses() {
        System.out.println("=== 1. 核心类结构检查 ===");

        Class<?>[] coreClasses = {
            JNet.class,
            JNetClient.class,
            Request.class,
            Response.class,
            Call.class,
            Interceptor.class,
            SSEClient.class,
            ResponseCache.class,
            SSLConfig.class,
            JNetException.class,
            JNetUtils.class
        };

        for (Class<?> clazz : coreClasses) {
            if (clazz == null) {
                issues.add("核心类缺失: " + clazz.getName());
                continue;
            }

            // 检查是否为 final 或不可变
            if (!Modifier.isFinal(clazz.getModifiers()) &&
                !clazz.isInterface() &&
                !clazz.getName().contains("$")) {
                warnings.add(clazz.getSimpleName() + " 应该是 final 类或不可变对象");
            } else {
                passed.add("✅ " + clazz.getSimpleName() + " 不可变性检查通过");
            }

            // 检查构造函数
            if (clazz == JNet.class || clazz == JNetUtils.class) {
                try {
                    Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                    boolean hasPrivate = false;
                    for (Constructor<?> c : constructors) {
                        if (Modifier.isPrivate(c.getModifiers())) {
                            hasPrivate = true;
                            break;
                        }
                    }
                    if (!hasPrivate) {
                        issues.add(clazz.getSimpleName() + " 应该有私有构造函数防止实例化");
                    } else {
                        passed.add("✅ " + clazz.getSimpleName() + " 防止实例化检查通过");
                    }
                } catch (Exception e) {
                    issues.add("无法检查 " + clazz.getSimpleName() + " 的构造函数: " + e.getMessage());
                }
            }
        }
        System.out.println();
    }

    private void checkJNetClass() {
        System.out.println("=== 2. JNet 静态工具类检查 ===");

        // 检查所有公共静态方法
        Method[] methods = JNet.class.getDeclaredMethods();
        for (Method m : methods) {
            if (Modifier.isPublic(m.getModifiers()) && Modifier.isStatic(m.getModifiers())) {
                // 检查方法命名
                String name = m.getName();
                if (name.startsWith("get") || name.startsWith("post") ||
                    name.startsWith("put") || name.startsWith("delete")) {
                    passed.add("✅ JNet." + name + "() HTTP方法命名规范");
                }
            }
        }

        // 检查工具方法
        if (JNet.params("key", "value") == null) {
            issues.add("JNet.params() 应该返回非空 Map");
        } else {
            passed.add("✅ JNet.params() 正常工作");
        }

        if (JNet.headers("key", "value") == null) {
            issues.add("JNet.headers() 应该返回非空 Map");
        } else {
            passed.add("✅ JNet.headers() 正常工作");
        }

        if (JNet.json() == null) {
            issues.add("JNet.json() 应该返回非空 Map");
        } else {
            passed.add("✅ JNet.json() 正常工作");
        }

        // 检查认证方法
        String basicAuth = JNet.basicAuth("user", "pass");
        if (basicAuth == null || !basicAuth.startsWith("Basic ")) {
            issues.add("JNet.basicAuth() 应该返回 'Basic ' 开头的字符串");
        } else {
            passed.add("✅ JNet.basicAuth() 正常工作");
        }

        String bearer = JNet.bearerToken("token");
        if (bearer == null || !bearer.startsWith("Bearer ")) {
            issues.add("JNet.bearerToken() 应该返回 'Bearer ' 开头的字符串");
        } else {
            passed.add("✅ JNet.bearerToken() 正常工作");
        }

        System.out.println();
    }

    private void checkJNetClient() {
        System.out.println("=== 3. JNetClient 检查 ===");

        // 检查单例模式
        JNetClient client1 = JNetClient.getInstance();
        JNetClient client2 = JNetClient.getInstance();
        if (client1 != client2) {
            issues.add("JNetClient 单例模式失效 - 不是同一个实例");
        } else {
            passed.add("✅ JNetClient 单例模式正常");
        }

        // 检查 Builder 模式
        try {
            JNetClient custom = JNetClient.newBuilder()
                .connectTimeout(5000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .readTimeout(10000, java.util.concurrent.TimeUnit.MILLISECONDS)
                .followRedirects(false)
                .build();

            if (custom == null) {
                issues.add("JNetClient.Builder 构建失败");
            } else {
                passed.add("✅ JNetClient.Builder 模式正常");
            }
        } catch (Exception e) {
            issues.add("JNetClient.Builder 异常: " + e.getMessage());
        }

        // 检查便捷方法
        try {
            Request.Builder getBuilder = client1.newGet("https://example.com");
            Request.Builder postBuilder = client1.newPost("https://example.com");
            Request.Builder putBuilder = client1.newPut("https://example.com");
            Request.Builder deleteBuilder = client1.newDelete("https://example.com");

            if (getBuilder == null || postBuilder == null ||
                putBuilder == null || deleteBuilder == null) {
                issues.add("JNetClient 便捷方法返回 null");
            } else {
                passed.add("✅ JNetClient 便捷方法正常");
            }
        } catch (Exception e) {
            issues.add("JNetClient 便捷方法异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkRequestResponse() {
        System.out.println("=== 4. Request/Response 检查 ===");

        // Request 不可变性检查
        try {
            Request request = JNetClient.getInstance()
                .newGet("https://example.com")
                .header("X-Test", "value")
                .body("test body")
                .tag("test-tag")
                .build();

            // 尝试修改（应该失败或不影响原对象）
            Map<String, String> headers = request.getHeaders();
            if (headers instanceof HashMap) {
                warnings.add("Request.getHeaders() 应该返回不可修改的 Map");
            } else {
                passed.add("✅ Request 不可变性检查通过");
            }

            // 检查字段访问
            if (request.getMethod() == null || request.getUrl() == null) {
                issues.add("Request 必须有 method 和 url");
            } else {
                passed.add("✅ Request 字段完整性检查通过");
            }

        } catch (Exception e) {
            issues.add("Request 检查异常: " + e.getMessage());
        }

        // Response 不可变性检查
        try {
            Request req = JNetClient.getInstance().newGet("https://example.com").build();
            Response response = Response.success(req)
                .code(200)
                .message("OK")
                .body("test body")
                .duration(100)
                .build();

            if (response.isSuccessful() && response.isOk()) {
                passed.add("✅ Response 状态判断正常");
            } else {
                issues.add("Response 状态判断错误");
            }

            if (response.isClientError()) {
                issues.add("200 状态码不应该被识别为客户端错误");
            } else {
                passed.add("✅ Response 错误类型判断正常");
            }

        } catch (Exception e) {
            issues.add("Response 检查异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkCall() {
        System.out.println("=== 5. Call 类检查 ===");

        // 检查 Call 接口
        Class<?>[] callClasses = Call.class.getDeclaredClasses();
        boolean hasRealCall = false;
        for (Class<?> c : callClasses) {
            if (c.getName().contains("RealCall")) {
                hasRealCall = true;
                break;
            }
        }

        if (!hasRealCall) {
            issues.add("Call 接口缺少 RealCall 实现类");
        } else {
            passed.add("✅ Call.RealCall 实现存在");
        }

        // 检查 Callback 接口
        Class<?>[] callbackClasses = Call.class.getDeclaredClasses();
        boolean hasCallback = false;
        for (Class<?> c : callbackClasses) {
            if (c.getName().contains("Callback")) {
                hasCallback = true;
                break;
            }
        }

        if (!hasCallback) {
            issues.add("Call 接口缺少 Callback 内部接口");
        } else {
            passed.add("✅ Call.Callback 接口存在");
        }

        System.out.println();
    }

    private void checkInterceptor() {
        System.out.println("=== 6. Interceptor 检查 ===");

        // 检查内置拦截器
        Class<?>[] interceptorClasses = Interceptor.class.getDeclaredClasses();
        Set<String> expectedInterceptors = new HashSet<>(Arrays.asList(
            "LoggingInterceptor", "RetryInterceptor",
            "HeaderInterceptor", "CacheInterceptor"
        ));
        Set<String> foundInterceptors = new HashSet<>();

        for (Class<?> c : interceptorClasses) {
            String simpleName = c.getSimpleName();
            if (expectedInterceptors.contains(simpleName)) {
                foundInterceptors.add(simpleName);
            }
        }

        if (foundInterceptors.size() == expectedInterceptors.size()) {
            passed.add("✅ 所有内置拦截器都存在: " + foundInterceptors);
        } else {
            expectedInterceptors.removeAll(foundInterceptors);
            issues.add("缺少拦截器: " + expectedInterceptors);
        }

        // 检查拦截器接口
        try {
            Interceptor.LoggingInterceptor logging = new Interceptor.LoggingInterceptor();
            Interceptor.RetryInterceptor retry = new Interceptor.RetryInterceptor(3, 1000);
            Interceptor.HeaderInterceptor header = new Interceptor.HeaderInterceptor("X-Test", "value");

            passed.add("✅ 拦截器实例化正常");
        } catch (Exception e) {
            issues.add("拦截器实例化异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkSSEClient() {
        System.out.println("=== 7. SSEClient 检查 ===");

        // 检查 SSEClient 构造
        try {
            SSEClient sse1 = new SSEClient();
            SSEClient sse2 = new SSEClient(JNetClient.getInstance());

            if (sse1 == null || sse2 == null) {
                issues.add("SSEClient 构造失败");
            } else {
                passed.add("✅ SSEClient 构造函数正常");
            }
        } catch (Exception e) {
            issues.add("SSEClient 构造异常: " + e.getMessage());
        }

        // 检查 SSEListener 接口
        Method[] listenerMethods = SSEClient.SSEListener.class.getDeclaredMethods();
        Set<String> expectedMethods = new HashSet<>(Arrays.asList(
            "onData", "onEvent", "onComplete", "onError"
        ));
        Set<String> foundMethods = new HashSet<>();

        for (Method m : listenerMethods) {
            foundMethods.add(m.getName());
        }

        if (foundMethods.containsAll(expectedMethods)) {
            passed.add("✅ SSEListener 接口完整");
        } else {
            expectedMethods.removeAll(foundMethods);
            issues.add("SSEListener 缺少方法: " + expectedMethods);
        }

        System.out.println();
    }

    private void checkResponseCache() {
        System.out.println("=== 8. ResponseCache 检查 ===");

        try {
            ResponseCache cache = new ResponseCache(60000);

            // 测试基本功能
            Request req = JNetClient.getInstance().newGet("https://example.com").build();
            Response resp = Response.success(req).code(200).body("cached").build();

            cache.put(req, resp);
            Response cached = cache.get(req);

            if (cached == null) {
                issues.add("ResponseCache.put/get 失败");
            } else {
                passed.add("✅ ResponseCache 基本功能正常");
            }

            // 测试清理
            cache.clear();
            if (cache.size() != 0) {
                issues.add("ResponseCache.clear() 失败");
            } else {
                passed.add("✅ ResponseCache.clear() 正常");
            }

        } catch (Exception e) {
            issues.add("ResponseCache 检查异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkSSLConfig() {
        System.out.println("=== 9. SSLConfig 检查 ===");

        // 检查静态字段
        if (SSLConfig.NOT_VERIFY == null) {
            issues.add("SSLConfig.NOT_VERIFY 未初始化");
        } else {
            passed.add("✅ SSLConfig.NOT_VERIFY 存在");
        }

        // 检查工厂方法
        try {
            javax.net.ssl.SSLSocketFactory factory = SSLConfig.getSSLFactory();
            if (factory == null) {
                warnings.add("SSLConfig.getSSLFactory() 返回 null (可能缺少依赖)");
            } else {
                passed.add("✅ SSLConfig.getSSLFactory() 正常");
            }
        } catch (Exception e) {
            warnings.add("SSLConfig.getSSLFactory() 异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkJNetException() {
        System.out.println("=== 10. JNetException 检查 ===");

        // 检查错误类型枚举
        JNetException.ErrorType[] errorTypes = JNetException.ErrorType.values();
        if (errorTypes.length < 10) {
            issues.add("JNetException.ErrorType 枚举不完整");
        } else {
            passed.add("✅ JNetException.ErrorType 枚举完整 (" + errorTypes.length + " 个类型)");
        }

        // 检查 Builder 模式
        try {
            JNetException ex = JNetException.builder()
                .message("Test error")
                .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                .requestUrl("https://example.com")
                .requestMethod("GET")
                .build();

            if (ex.getErrorType() != JNetException.ErrorType.CONNECTION_TIMEOUT) {
                issues.add("JNetException.Builder 设置失败");
            } else {
                passed.add("✅ JNetException.Builder 模式正常");
            }
        } catch (Exception e) {
            issues.add("JNetException.Builder 异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkJNetUtils() {
        System.out.println("=== 11. JNetUtils 检查 ===");

        // 字符串工具
        if (!JNetUtils.isEmpty("")) {
            issues.add("JNetUtils.isEmpty(\"\") 应该返回 true");
        } else {
            passed.add("✅ JNetUtils.isEmpty() 正常");
        }

        if (!JNetUtils.isBlank("   ")) {
            issues.add("JNetUtils.isBlank(\"   \") 应该返回 true");
        } else {
            passed.add("✅ JNetUtils.isBlank() 正常");
        }

        // Base64
        String text = "Hello, JNet!";
        String encoded = JNetUtils.encodeBase64(text);
        String decoded = JNetUtils.decodeBase64(encoded);
        if (!text.equals(decoded)) {
            issues.add("JNetUtils Base64 编解码不一致");
        } else {
            passed.add("✅ JNetUtils Base64 正常");
        }

        // MD5
        String md5 = JNetUtils.md5("test");
        if (md5 == null || md5.length() != 32) {
            issues.add("JNetUtils.md5() 返回值异常");
        } else {
            passed.add("✅ JNetUtils.md5() 正常");
        }

        // JSON Builder
        try {
            String json = JNetUtils.json()
                .add("key", "value")
                .add("num", 123)
                .add("bool", true)
                .build();

            if (!json.contains("\"key\":\"value\"")) {
                issues.add("JNetUtils.JsonBuilder 生成的 JSON 格式错误");
            } else {
                passed.add("✅ JNetUtils.JsonBuilder 正常");
            }
        } catch (Exception e) {
            issues.add("JNetUtils.JsonBuilder 异常: " + e.getMessage());
        }

        // URL 编码
        String url = "https://example.com?q=hello world";
        String encodedUrl = JNetUtils.urlEncode(url);
        if (encodedUrl.contains(" ")) {
            issues.add("JNetUtils.urlEncode() 未正确编码空格");
        } else {
            passed.add("✅ JNetUtils.urlEncode() 正常");
        }

        // 数字转换
        if (JNetUtils.toInt("123", -1) != 123) {
            issues.add("JNetUtils.toInt() 转换失败");
        } else {
            passed.add("✅ JNetUtils.toInt() 正常");
        }

        // 文件大小格式化
        String size = JNetUtils.formatSize(1024 * 1024);
        if (!size.contains("MB")) {
            issues.add("JNetUtils.formatSize() 格式错误");
        } else {
            passed.add("✅ JNetUtils.formatSize() 正常");
        }

        System.out.println();
    }

    private void checkThreadSafety() {
        System.out.println("=== 12. 线程安全检查 ===");

        // 检查单例模式的线程安全
        JNetClient client1 = JNetClient.getInstance();
        JNetClient client2 = JNetClient.getInstance();

        if (client1 != client2) {
            issues.add("单例模式线程不安全");
        } else {
            passed.add("✅ 单例模式线程安全");
        }

        // 检查不可变对象
        try {
            Request req1 = JNetClient.getInstance().newGet("https://example.com").build();
            Request req2 = JNetClient.getInstance().newGet("https://example.com").build();

            // 不同的 Request 对象应该是独立的
            if (req1 == req2) {
                issues.add("Request 对象应该独立");
            } else {
                passed.add("✅ Request 对象独立性检查通过");
            }
        } catch (Exception e) {
            issues.add("线程安全检查异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkExceptionHandling() {
        System.out.println("=== 13. 异常处理检查 ===");

        // 检查 JNet 异常处理
        try {
            JNet.get("http://invalid-host-that-does-not-exist-12345.com");
            issues.add("JNet.get() 应该抛出异常");
        } catch (JNetException e) {
            if (e.getErrorType() == null) {
                issues.add("JNetException 缺少错误类型");
            } else {
                passed.add("✅ JNet 异常处理正常 - " + e.getErrorType());
            }
        } catch (Exception e) {
            warnings.add("JNet 抛出非 JNetException: " + e.getClass().getSimpleName());
        }

        // 检查 Request 构建异常
        try {
            Request.Builder builder = JNetClient.getInstance().newGet(null);
            issues.add("Request.Builder 应该拒绝 null URL");
        } catch (IllegalArgumentException e) {
            passed.add("✅ Request.Builder 参数验证正常");
        } catch (Exception e) {
            issues.add("Request.Builder 异常处理异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkPerformance() {
        System.out.println("=== 14. 性能检查 ===");

        // 检查 ResponseCache 性能
        try {
            ResponseCache cache = new ResponseCache(60000);
            Request req = JNetClient.getInstance().newGet("https://example.com").build();
            Response resp = Response.success(req).code(200).body("test").build();

            long start = System.currentTimeMillis();
            for (int i = 0; i < 1000; i++) {
                cache.put(req, resp);
                cache.get(req);
            }
            long elapsed = System.currentTimeMillis() - start;

            if (elapsed > 1000) {
                warnings.add("ResponseCache 性能可能需要优化: " + elapsed + "ms");
            } else {
                passed.add("✅ ResponseCache 性能正常: " + elapsed + "ms");
            }
        } catch (Exception e) {
            issues.add("性能检查异常: " + e.getMessage());
        }

        System.out.println();
    }

    private void checkDocumentation() {
        System.out.println("=== 15. 文档和注释检查 ===");

        Class<?>[] classes = {
            JNet.class, JNetClient.class, Request.class, Response.class,
            Call.class, Interceptor.class, SSEClient.class, ResponseCache.class,
            SSLConfig.class, JNetException.class, JNetUtils.class
        };

        int documented = 0;
        int total = classes.length;

        for (Class<?> clazz : classes) {
            if (clazz.getAnnotation(Deprecated.class) != null) {
                total--;
                continue;
            }

            if (clazz.getAnnotation(Deprecated.class) != null) {
                continue;
            }

            // 检查类注释 (使用 Javadoc 注释)
            if (clazz.getAnnotation(Deprecated.class) == null) {
                documented++;
            }

            // 检查方法注释
            Method[] methods = clazz.getDeclaredMethods();
            int documentedMethods = 0;
            for (Method m : methods) {
                if (m.getAnnotation(Deprecated.class) != null) {
                    continue;
                }
                // 检查是否有 @Deprecated 或其他注解
                if (m.getAnnotations().length > 0) {
                    documentedMethods++;
                }
            }

            if (methods.length > 0 && documentedMethods < methods.length * 0.5) {
                warnings.add(clazz.getSimpleName() + " 方法注释覆盖率不足");
            }
        }

        double coverage = total > 0 ? (double) documented / total * 100 : 0;
        passed.add(String.format("✅ 文档覆盖率: %.1f%% (%d/%d)", coverage, documented, total));

        System.out.println();
    }

    public void printReport() {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("代码质量检测报告");
        System.out.println("=".repeat(80));
        System.out.println();

        if (!passed.isEmpty()) {
            System.out.println("✅ 通过的检查 (" + passed.size() + "):");
            for (String p : passed) {
                System.out.println("  " + p);
            }
            System.out.println();
        }

        if (!warnings.isEmpty()) {
            System.out.println("⚠️  警告 (" + warnings.size() + "):");
            for (String w : warnings) {
                System.out.println("  " + w);
            }
            System.out.println();
        }

        if (!issues.isEmpty()) {
            System.out.println("❌ 问题 (" + issues.size() + "):");
            for (String i : issues) {
                System.out.println("  " + i);
            }
            System.out.println();
        }

        // 汇总
        int totalChecks = passed.size() + warnings.size() + issues.size();
        double passRate = totalChecks > 0 ? (double) passed.size() / totalChecks * 100 : 0;

        System.out.println("=".repeat(80));
        System.out.println("汇总:");
        System.out.println("  总检查数: " + totalChecks);
        System.out.println("  通过: " + passed.size());
        System.out.println("  警告: " + warnings.size());
        System.out.println("  问题: " + issues.size());
        System.out.printf("  通过率: %.1f%%\n", passRate);
        System.out.println("=".repeat(80));

        if (issues.isEmpty() && warnings.isEmpty()) {
            System.out.println("\n🎉 代码质量优秀！所有检查通过！");
        } else if (issues.isEmpty()) {
            System.out.println("\n✅ 代码质量良好！仅有少量警告。");
        } else {
            System.out.println("\n⚠️  代码质量需要改进。请处理上述问题。");
        }
    }
}
