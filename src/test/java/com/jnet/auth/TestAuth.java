package com.jnet.auth;

import com.jnet.core.Request;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 认证模块测试套件
 * 覆盖 BasicAuth, BearerAuth, DigestAuth
 * 目标：80%+ 测试覆盖率
 */
@DisplayName("Authentication Tests")
class TestAuth {

    // ==================== BasicAuth Tests ====================

    @Test
    @DisplayName("BasicAuth: 正确编码用户名和密码")
    void testBasicAuthEncoding() throws Exception {
        BasicAuth auth = new BasicAuth("user", "pass");
        Request request = Request.newBuilder()
                .url("https://api.example.com/data")
                .build();

        Request authenticated = auth.apply(request);

        String authHeader = authenticated.getHeaders().get("Authorization");
        assertNotNull(authHeader, "Authorization header should be present");
        assertTrue(authHeader.startsWith("Basic "), "Should start with 'Basic '");

        // Decode and verify
        String encoded = authHeader.substring(6);
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user:pass", decoded, "Decoded credentials should match");
    }

    @Test
    @DisplayName("BasicAuth: 处理特殊字符")
    void testBasicAuthSpecialChars() throws Exception {
        BasicAuth auth = new BasicAuth("user@domain.com", "p@ss:word!");
        Request request = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(request);
        String authHeader = authenticated.getHeaders().get("Authorization");

        String encoded = authHeader.substring(6);
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user@domain.com:p@ss:word!", decoded);
    }

    @Test
    @DisplayName("BasicAuth: 空密码")
    void testBasicAuthEmptyPassword() throws Exception {
        BasicAuth auth = new BasicAuth("user", "");
        Request request = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(request);
        String authHeader = authenticated.getHeaders().get("Authorization");

        String encoded = authHeader.substring(6);
        String decoded = new String(Base64.getDecoder().decode(encoded), StandardCharsets.UTF_8);
        assertEquals("user:", decoded);
    }

    // ==================== BearerAuth Tests ====================

    @Test
    @DisplayName("BearerAuth: 正确添加 Bearer token")
    void testBearerAuthToken() throws Exception {
        String token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test.signature";
        BearerAuth auth = new BearerAuth(token);
        Request request = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(request);
        String authHeader = authenticated.getHeaders().get("Authorization");

        assertEquals("Bearer " + token, authHeader);
    }

    @Test
    @DisplayName("BearerAuth: 短 token")
    void testBearerAuthShortToken() throws Exception {
        BearerAuth auth = new BearerAuth("abc123");
        Request request = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(request);
        assertEquals("Bearer abc123", authenticated.getHeaders().get("Authorization"));
    }

    // ==================== DigestAuth Tests ====================

    @Test
    @DisplayName("DigestAuth: 未初始化时返回原始请求")
    void testDigestAuthUninitialized() throws Exception {
        DigestAuth auth = new DigestAuth("user", "pass");
        Request request = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(request);

        // 未解析 challenge 时应返回原始请求
        assertNull(authenticated.getHeaders().get("Authorization"));
        assertFalse(auth.isReady(), "Should not be ready without challenge");
    }

    @Test
    @DisplayName("DigestAuth: 解析 WWW-Authenticate 头")
    void testDigestAuthParseChallenge() {
        DigestAuth auth = new DigestAuth("user", "pass");
        String challenge = "Digest realm=\"test-realm\", nonce=\"abc123\", qop=\"auth\", opaque=\"xyz\"";

        auth.parseChallenge(challenge);

        assertTrue(auth.isReady(), "Should be ready after parsing challenge");
    }

    @Test
    @DisplayName("DigestAuth: 解析最小 challenge (无 qop)")
    void testDigestAuthMinimalChallenge() {
        DigestAuth auth = new DigestAuth("user", "pass");
        String challenge = "Digest realm=\"test\", nonce=\"123\"";

        auth.parseChallenge(challenge);
        assertTrue(auth.isReady());
    }

    @Test
    @DisplayName("DigestAuth: 解析失败 - 无 Digest 前缀")
    void testDigestAuthInvalidPrefix() {
        DigestAuth auth = new DigestAuth("user", "pass");

        assertThrows(IllegalArgumentException.class, () -> {
            auth.parseChallenge("Basic realm=\"test\"");
        });
    }

    @Test
    @DisplayName("DigestAuth: 解析失败 - 缺少 realm")
    void testDigestAuthMissingRealm() {
        DigestAuth auth = new DigestAuth("user", "pass");

        assertThrows(IllegalArgumentException.class, () -> {
            auth.parseChallenge("Digest nonce=\"123\"");
        });
    }

    @Test
    @DisplayName("DigestAuth: 生成 Authorization 头")
    void testDigestAuthGenerateHeader() throws Exception {
        DigestAuth auth = new DigestAuth("user", "pass");
        auth.parseChallenge("Digest realm=\"test\", nonce=\"abc123\", qop=\"auth\"");

        Request request = Request.newBuilder()
                .url("https://api.example.com/data")
                .method("GET")
                .build();

        Request authenticated = auth.apply(request);
        String authHeader = authenticated.getHeaders().get("Authorization");

        assertNotNull(authHeader);
        assertTrue(authHeader.startsWith("Digest "));
        assertTrue(authHeader.contains("username=\"user\""));
        assertTrue(authHeader.contains("realm=\"test\""));
        assertTrue(authHeader.contains("nonce=\"abc123\""));
        assertTrue(authHeader.contains("uri=\"/data\""));
        assertTrue(authHeader.contains("response=\""));
        assertTrue(authHeader.contains("qop=auth"));
    }

    @Test
    @DisplayName("DigestAuth: Reset 功能")
    void testDigestAuthReset() {
        DigestAuth auth = new DigestAuth("user", "pass");
        auth.parseChallenge("Digest realm=\"test\", nonce=\"abc123\", qop=\"auth\"");

        assertTrue(auth.isReady());

        auth.reset();

        assertFalse(auth.isReady(), "Should not be ready after reset");
    }

    @Test
    @DisplayName("DigestAuth: 构造函数参数验证")
    void testDigestAuthNullUsername() {
        assertThrows(IllegalArgumentException.class, () -> {
            new DigestAuth(null, "pass");
        });
    }

    @Test
    @DisplayName("Auth: 不修改原始 Request")
    void testAuthImmutability() throws Exception {
        BasicAuth auth = new BasicAuth("user", "pass");
        Request original = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = auth.apply(original);

        // Original should not be modified
        assertNull(original.getHeaders().get("Authorization"));
        assertNotNull(authenticated.getHeaders().get("Authorization"));
        assertNotSame(original, authenticated, "Should return new instance");
    }

    @Test
    @DisplayName("Auth: 函数式接口使用")
    void testAuthFunctionalInterface() throws Exception {
        // Custom auth using lambda
        Auth customAuth = request -> request.toBuilder()
                .header("Authorization", "Custom token")
                .build();

        Request original = Request.newBuilder()
                .url("https://api.example.com")
                .build();

        Request authenticated = customAuth.apply(original);
        assertEquals("Custom token", authenticated.getHeaders().get("Authorization"));
    }
}
