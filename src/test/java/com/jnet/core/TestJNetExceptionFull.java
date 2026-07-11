package com.jnet.core;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JNetException 完整单元测试
 *
 * @author sanbo
 * @version 3.0
 */
@DisplayName("【JNetException】异常完整测试")
public class TestJNetExceptionFull {

    // ========== 构造函数测试 ==========

    @Nested
    @DisplayName("构造函数")
    class ConstructorTest {

        @Test
        @DisplayName("简单消息构造")
        void testSimpleMessage() {
            JNetException ex = new JNetException("Test error");
            assertEquals("Test error", ex.getMessage());
            assertEquals(JNetException.ErrorType.UNKNOWN, ex.getErrorType());
        }

        @Test
        @DisplayName("消息和原因构造")
        void testMessageAndCause() {
            Exception cause = new Exception("Root cause");
            JNetException ex = new JNetException("Test error", cause);

            assertEquals("Test error", ex.getMessage());
            assertEquals(cause, ex.getCause());
            assertEquals(JNetException.ErrorType.UNKNOWN, ex.getErrorType());
        }

        @Test
        @DisplayName("Builder 构造")
        void testBuilder() {
            JNetException ex = JNetException.builder()
                    .message("Connection timeout")
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .statusCode(504)
                    .build();

            assertEquals("Connection timeout", ex.getMessage());
            assertEquals(JNetException.ErrorType.CONNECTION_TIMEOUT, ex.getErrorType());
            assertEquals("https://example.com", ex.getRequestUrl());
            assertEquals("GET", ex.getRequestMethod());
            assertEquals(504, ex.getStatusCode());
        }

        @Test
        @DisplayName("Builder 部分字段")
        void testBuilderPartial() {
            JNetException ex = JNetException.builder()
                    .message("Simple error")
                    .build();

            assertEquals("Simple error", ex.getMessage());
            assertEquals(JNetException.ErrorType.UNKNOWN, ex.getErrorType());
            assertNull(ex.getRequestUrl());
            assertEquals(-1, ex.getStatusCode());
        }
    }

    // ========== 错误类型测试 ==========

    @Nested
    @DisplayName("错误类型")
    class ErrorTypeTest {

        @ParameterizedTest
        @EnumSource(JNetException.ErrorType.class)
        @DisplayName("所有错误类型都存在")
        void testAllErrorTypes(JNetException.ErrorType type) {
            assertNotNull(type);
            assertNotNull(type.getDescription());
            assertTrue(type.getDescription().length() > 0);
        }

        @Test
        @DisplayName("错误类型枚举数量")
        void testErrorTypeCount() {
            JNetException.ErrorType[] types = JNetException.ErrorType.values();
            assertTrue(types.length >= 12, "应该有至少12种错误类型");
        }

        @Test
        @DisplayName("特定错误类型")
        void testSpecificErrorTypes() {
            assertEquals("网络不可用", JNetException.ErrorType.NETWORK_UNAVAILABLE.getDescription());
            assertEquals("连接被拒绝", JNetException.ErrorType.CONNECTION_REFUSED.getDescription());
            assertEquals("连接超时", JNetException.ErrorType.CONNECTION_TIMEOUT.getDescription());
            assertEquals("读取超时", JNetException.ErrorType.READ_TIMEOUT.getDescription());
            assertEquals("SSL握手失败", JNetException.ErrorType.SSL_HANDSHAKE_FAILED.getDescription());
            assertEquals("HTTP协议错误", JNetException.ErrorType.HTTP_PROTOCOL_ERROR.getDescription());
            assertEquals("HTTP客户端错误", JNetException.ErrorType.HTTP_CLIENT_ERROR.getDescription());
            assertEquals("HTTP服务器错误", JNetException.ErrorType.HTTP_SERVER_ERROR.getDescription());
            assertEquals("响应解析错误", JNetException.ErrorType.RESPONSE_PARSING_ERROR.getDescription());
            assertEquals("请求构建错误", JNetException.ErrorType.REQUEST_BUILD_ERROR.getDescription());
            assertEquals("IO错误", JNetException.ErrorType.IO_ERROR.getDescription());
            assertEquals("请求被中断", JNetException.ErrorType.INTERRUPTED.getDescription());
            assertEquals("未知错误", JNetException.ErrorType.UNKNOWN.getDescription());
        }

        @ParameterizedTest
        @ValueSource(strings = {
            "NETWORK_UNAVAILABLE",
            "CONNECTION_REFUSED",
            "CONNECTION_TIMEOUT",
            "READ_TIMEOUT",
            "SSL_HANDSHAKE_FAILED",
            "HTTP_PROTOCOL_ERROR",
            "HTTP_CLIENT_ERROR",
            "HTTP_SERVER_ERROR",
            "RESPONSE_PARSING_ERROR",
            "REQUEST_BUILD_ERROR",
            "IO_ERROR",
            "INTERRUPTED",
            "UNKNOWN"
        })
        @DisplayName("错误类型名称")
        void testErrorTypeNames(String name) {
            JNetException.ErrorType type = JNetException.ErrorType.valueOf(name);
            assertNotNull(type);
            assertEquals(name, type.name());
        }
    }

    // ========== Builder 测试 ==========

    @Nested
    @DisplayName("Builder模式")
    class BuilderTest {

        @Test
        @DisplayName("Builder 链式调用")
        void testBuilderChain() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .cause(new Exception("Cause"))
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .statusCode(504)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .build();

            assertNotNull(ex);
            assertEquals("Test", ex.getMessage());
            assertNotNull(ex.getCause());
            assertEquals(JNetException.ErrorType.CONNECTION_TIMEOUT, ex.getErrorType());
            assertEquals(504, ex.getStatusCode());
            assertEquals("https://example.com", ex.getRequestUrl());
            assertEquals("GET", ex.getRequestMethod());
        }

        @Test
        @DisplayName("Builder 默认值")
        void testBuilderDefaults() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .build();

            assertEquals(JNetException.ErrorType.UNKNOWN, ex.getErrorType());
            assertEquals(-1, ex.getStatusCode());
            assertNull(ex.getRequestUrl());
            assertNull(ex.getRequestMethod());
        }

        @Test
        @DisplayName("Builder 可选字段")
        void testBuilderOptionalFields() {
            // 只设置部分字段
            JNetException ex1 = JNetException.builder()
                    .message("Test")
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .build();

            JNetException ex2 = JNetException.builder()
                    .message("Test")
                    .requestUrl("https://example.com")
                    .build();

            JNetException ex3 = JNetException.builder()
                    .message("Test")
                    .statusCode(404)
                    .build();

            assertNotNull(ex1);
            assertNotNull(ex2);
            assertNotNull(ex3);
        }

        @Test
        @DisplayName("Builder 空值处理")
        void testBuilderNullValues() {
            JNetException ex = JNetException.builder()
                    .message(null)
                    .cause(null)
                    .errorType(null)
                    .requestUrl(null)
                    .requestMethod(null)
                    .build();

            // 应该能处理 null 值
            assertNotNull(ex);
        }
    }

    // ========== Getter 测试 ==========

    @Nested
    @DisplayName("Getter方法")
    class GetterTest {

        @Test
        @DisplayName("获取错误类型")
        void testGetErrorType() {
            JNetException ex = JNetException.builder()
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .build();

            assertEquals(JNetException.ErrorType.CONNECTION_TIMEOUT, ex.getErrorType());
        }

        @Test
        @DisplayName("获取状态码")
        void testGetStatusCode() {
            JNetException ex = JNetException.builder()
                    .statusCode(404)
                    .build();

            assertEquals(404, ex.getStatusCode());
        }

        @Test
        @DisplayName("获取请求URL")
        void testGetRequestUrl() {
            JNetException ex = JNetException.builder()
                    .requestUrl("https://example.com")
                    .build();

            assertEquals("https://example.com", ex.getRequestUrl());
        }

        @Test
        @DisplayName("获取请求方法")
        void testGetRequestMethod() {
            JNetException ex = JNetException.builder()
                    .requestMethod("POST")
                    .build();

            assertEquals("POST", ex.getRequestMethod());
        }

        @Test
        @DisplayName("获取消息")
        void testGetMessage() {
            JNetException ex = new JNetException("Error message");
            assertEquals("Error message", ex.getMessage());
        }

        @Test
        @DisplayName("获取原因")
        void testGetCause() {
            Exception cause = new Exception("Root cause");
            JNetException ex = new JNetException("Error", cause);
            assertEquals(cause, ex.getCause());
        }
    }

    // ========== toString 测试 ==========

    @Nested
    @DisplayName("toString方法")
    class ToStringTest {

        @Test
        @DisplayName("完整信息 toString")
        void testToStringComplete() {
            JNetException ex = JNetException.builder()
                    .message("Connection timeout")
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .statusCode(504)
                    .requestMethod("GET")
                    .requestUrl("https://example.com")
                    .build();

            String str = ex.toString();
            assertNotNull(str);
            assertTrue(str.contains("JNetException"));
            assertTrue(str.contains("CONNECTION_TIMEOUT"));
            assertTrue(str.contains("504"));
            assertTrue(str.contains("GET"));
            assertTrue(str.contains("example.com"));
        }

        @Test
        @DisplayName("部分信息 toString")
        void testToStringPartial() {
            JNetException ex = JNetException.builder()
                    .message("Simple error")
                    .build();

            String str = ex.toString();
            assertNotNull(str);
            assertTrue(str.contains("JNetException"));
            assertTrue(str.contains("Simple error"));
        }

        @Test
        @DisplayName("带原因的 toString")
        void testToStringWithCause() {
            Exception cause = new Exception("Root cause");
            JNetException ex = JNetException.builder()
                    .message("Test error")
                    .cause(cause)
                    .build();

            String str = ex.toString();
            assertTrue(str.contains("Root cause"));
        }

        @Test
        @DisplayName("toString 格式")
        void testToStringFormat() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .statusCode(500)
                    .requestMethod("POST")
                    .requestUrl("https://api.example.com/endpoint")
                    .build();

            String str = ex.toString();
            // 验证格式
            assertTrue(str.startsWith("JNetException{"));
            assertTrue(str.endsWith("}"));
            assertTrue(str.contains("type=IO_ERROR"));
            assertTrue(str.contains("status=500"));
            assertTrue(str.contains("method=POST"));
            assertTrue(str.contains("url=https://api.example.com/endpoint"));
            assertTrue(str.contains("message=Test"));
        }
    }

    // ========== 异常继承测试 ==========

    @Nested
    @DisplayName("异常继承")
    class InheritanceTest {

        @Test
        @DisplayName("继承自 RuntimeException")
        void testExtendsRuntimeException() {
            JNetException ex = new JNetException("Test");
            assertTrue(ex instanceof RuntimeException);
        }

        @Test
        @DisplayName("可以被 catch")
        void testCanBeCaught() {
            try {
                throw JNetException.builder()
                        .message("Test exception")
                        .errorType(JNetException.ErrorType.UNKNOWN)
                        .build();
            } catch (JNetException e) {
                assertEquals("Test exception", e.getMessage());
            }
        }

        @Test
        @DisplayName("可以被抛出")
        void testCanBeThrown() {
            assertThrows(JNetException.class, () -> {
                throw JNetException.builder()
                        .message("Test")
                        .build();
            });
        }

        @Test
        @DisplayName("可以作为 cause")
        void testCanBeCause() {
            JNetException cause = JNetException.builder()
                    .message("Root cause")
                    .build();

            Exception wrapper = new Exception("Wrapper", cause);
            assertEquals(cause, wrapper.getCause());
        }
    }

    // ========== 实际场景测试 ==========

    @Nested
    @DisplayName("实际场景")
    class RealWorldScenarios {

        @Test
        @DisplayName("网络错误场景")
        void testNetworkError() {
            JNetException ex = JNetException.builder()
                    .message("Connection refused: http://example.com")
                    .errorType(JNetException.ErrorType.CONNECTION_REFUSED)
                    .requestUrl("http://example.com")
                    .requestMethod("GET")
                    .cause(new java.net.ConnectException("Connection refused"))
                    .build();

            assertEquals(JNetException.ErrorType.CONNECTION_REFUSED, ex.getErrorType());
            assertTrue(ex.getMessage().contains("Connection refused"));
        }

        @Test
        @DisplayName("超时场景")
        void testTimeout() {
            JNetException ex = JNetException.builder()
                    .message("Request timeout: https://example.com")
                    .errorType(JNetException.ErrorType.CONNECTION_TIMEOUT)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .cause(new java.net.SocketTimeoutException("Read timed out"))
                    .build();

            assertEquals(JNetException.ErrorType.CONNECTION_TIMEOUT, ex.getErrorType());
        }

        @Test
        @DisplayName("HTTP错误场景")
        void testHttpError() {
            JNetException ex = JNetException.builder()
                    .message("HTTP 404: Not Found")
                    .errorType(JNetException.ErrorType.HTTP_CLIENT_ERROR)
                    .requestUrl("https://example.com/notfound")
                    .requestMethod("GET")
                    .statusCode(404)
                    .build();

            assertEquals(JNetException.ErrorType.HTTP_CLIENT_ERROR, ex.getErrorType());
            assertEquals(404, ex.getStatusCode());
        }

        @Test
        @DisplayName("SSL错误场景")
        void testSslError() {
            JNetException ex = JNetException.builder()
                    .message("SSL handshake failed")
                    .errorType(JNetException.ErrorType.SSL_HANDSHAKE_FAILED)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .cause(new javax.net.ssl.SSLHandshakeException("Certificate invalid"))
                    .build();

            assertEquals(JNetException.ErrorType.SSL_HANDSHAKE_FAILED, ex.getErrorType());
        }

        @Test
        @DisplayName("请求构建错误场景")
        void testRequestBuildError() {
            JNetException ex = JNetException.builder()
                    .message("Invalid URL: not-a-url")
                    .errorType(JNetException.ErrorType.REQUEST_BUILD_ERROR)
                    .requestUrl("not-a-url")
                    .requestMethod("GET")
                    .cause(new IllegalArgumentException("Malformed URL"))
                    .build();

            assertEquals(JNetException.ErrorType.REQUEST_BUILD_ERROR, ex.getErrorType());
        }

        @Test
        @DisplayName("IO错误场景")
        void testIoError() {
            JNetException ex = JNetException.builder()
                    .message("IO error during read")
                    .errorType(JNetException.ErrorType.IO_ERROR)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .cause(new java.io.IOException("Stream closed"))
                    .build();

            assertEquals(JNetException.ErrorType.IO_ERROR, ex.getErrorType());
        }

        @Test
        @DisplayName("中断场景")
        void testInterrupted() {
            JNetException ex = JNetException.builder()
                    .message("Request interrupted")
                    .errorType(JNetException.ErrorType.INTERRUPTED)
                    .requestUrl("https://example.com")
                    .requestMethod("GET")
                    .cause(new InterruptedException())
                    .build();

            assertEquals(JNetException.ErrorType.INTERRUPTED, ex.getErrorType());
        }

        @Test
        @DisplayName("未知错误场景")
        void testUnknownError() {
            JNetException ex = JNetException.builder()
                    .message("Unknown error occurred")
                    .errorType(JNetException.ErrorType.UNKNOWN)
                    .cause(new RuntimeException("Unexpected error"))
                    .build();

            assertEquals(JNetException.ErrorType.UNKNOWN, ex.getErrorType());
        }
    }

    // ========== 边界情况 ==========

    @Nested
    @DisplayName("边界情况")
    class BoundaryTest {

        @Test
        @DisplayName("空消息")
        void testEmptyMessage() {
            JNetException ex = new JNetException("");
            assertEquals("", ex.getMessage());
        }

        @Test
        @DisplayName("null 消息")
        void testNullMessage() {
            JNetException ex = new JNetException(null);
            assertNull(ex.getMessage());
        }

        @Test
        @DisplayName("超长消息")
        void testLongMessage() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 10000; i++) {
                sb.append("x");
            }

            JNetException ex = new JNetException(sb.toString());
            assertEquals(10000, ex.getMessage().length());
        }

        @Test
        @DisplayName("特殊字符消息")
        void testSpecialCharsMessage() {
            String message = "Error: \n\t\"test\" @#$%^&*()";
            JNetException ex = new JNetException(message);
            assertEquals(message, ex.getMessage());
        }

        @Test
        @DisplayName("Unicode 消息")
        void testUnicodeMessage() {
            String message = "错误：无法连接到服务器 🚫";
            JNetException ex = new JNetException(message);
            assertEquals(message, ex.getMessage());
        }

        @Test
        @DisplayName("负状态码")
        void testNegativeStatusCode() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .statusCode(-1)
                    .build();

            assertEquals(-1, ex.getStatusCode());
        }

        @Test
        @DisplayName("极大状态码")
        void testLargeStatusCode() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .statusCode(999999)
                    .build();

            assertEquals(999999, ex.getStatusCode());
        }

        @Test
        @DisplayName("空 URL")
        void testEmptyUrl() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .requestUrl("")
                    .build();

            assertEquals("", ex.getRequestUrl());
        }

        @Test
        @DisplayName("空方法")
        void testEmptyMethod() {
            JNetException ex = JNetException.builder()
                    .message("Test")
                    .requestMethod("")
                    .build();

            assertEquals("", ex.getRequestMethod());
        }
    }

    // ========== Builder 边界 ==========

    @Nested
    @DisplayName("Builder边界")
    class BuilderBoundaryTest {

        @Test
        @DisplayName("多次构建")
        void testMultipleBuilds() {
            JNetException.Builder builder = JNetException.builder();

            JNetException ex1 = builder.message("Error 1").build();
            JNetException ex2 = builder.message("Error 2").build();

            // 两个异常应该独立
            assertEquals("Error 1", ex1.getMessage());
            assertEquals("Error 2", ex2.getMessage());
        }

        @Test
        @DisplayName("Builder 复用")
        void testBuilderReuse() {
            JNetException.Builder builder = JNetException.builder()
                    .errorType(JNetException.ErrorType.IO_ERROR);

            JNetException ex1 = builder.message("Error 1").build();
            JNetException ex2 = builder.message("Error 2").statusCode(500).build();

            assertEquals(JNetException.ErrorType.IO_ERROR, ex1.getErrorType());
            assertEquals(JNetException.ErrorType.IO_ERROR, ex2.getErrorType());
            assertEquals(500, ex2.getStatusCode());
        }

        @Test
        @DisplayName("Builder 静态方法")
        void testBuilderStaticMethod() {
            // 验证 builder() 静态方法存在
            JNetException.Builder builder = JNetException.builder();
            assertNotNull(builder);

            // 验证可以构建
            JNetException ex = builder.message("Test").build();
            assertNotNull(ex);
        }
    }

    @Nested
    @DisplayName("ExceptionMapper")
    class ExceptionMapperTest {

        @Test
        void mapsJdkHttpTimeoutsToReadTimeout() {
            JNetException mapped = ExceptionMapper.map(
                    new java.net.http.HttpTimeoutException("timed out"),
                    "GET",
                    "https://example.com");

            assertEquals(JNetException.ErrorType.READ_TIMEOUT, mapped.getErrorType());
        }

        @Test
        void classifiesKnownNestedNetworkCauses() {
            JNetException mapped = ExceptionMapper.map(
                    new java.io.IOException(new javax.net.ssl.SSLHandshakeException("bad certificate")),
                    "GET",
                    "https://example.com");

            assertEquals(JNetException.ErrorType.SSL_HANDSHAKE_FAILED, mapped.getErrorType());
        }

        @Test
        void restoresTheInterruptFlagForNestedInterruptions() {
            try {
                JNetException mapped = ExceptionMapper.map(
                        new java.io.IOException(new InterruptedException("stop")),
                        "GET",
                        "https://example.com");

                assertEquals(JNetException.ErrorType.INTERRUPTED, mapped.getErrorType());
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }
}
