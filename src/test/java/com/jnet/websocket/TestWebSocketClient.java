package com.jnet.websocket;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocketClient Tests")
class TestWebSocketClient {

    @Test
    @DisplayName("WebSocketClient: Builder 创建")
    void testBuilder() {
        WebSocketClient client = WebSocketClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .pingInterval(15000)
                .maxReconnectAttempts(3)
                .build();
        
        assertNotNull(client);
        assertFalse(client.isConnected());
    }

    @Test
    @DisplayName("WebSocketClient: Listener 设置")
    void testListener() {
        WebSocketClient.WebSocketListener listener = new WebSocketClient.WebSocketListener() {
            @Override
            public void onMessage(String message) {
                System.out.println("Message: " + message);
            }
        };
        
        WebSocketClient client = WebSocketClient.newBuilder()
                .listener(listener)
                .build();
        
        assertNotNull(client);
    }

    @Test
    @DisplayName("WebSocketClient: Ping 禁用")
    void testDisablePing() {
        WebSocketClient client = WebSocketClient.newBuilder()
                .disablePing()
                .build();
        
        assertNotNull(client);
    }

    @Test
    @DisplayName("WebSocketClient: 重连禁用")
    void testDisableReconnect() {
        WebSocketClient client = WebSocketClient.newBuilder()
                .disableReconnect()
                .build();
        
        assertNotNull(client);
        assertEquals(0, client.getReconnectAttempts());
    }

    @Test
    @DisplayName("WebSocketClient: 未连接发送失败")
    void testSendWithoutConnection() {
        WebSocketClient client = WebSocketClient.newBuilder().build();
        
        assertFalse(client.isConnected());
        
        CompletableFuture<WebSocket> future = client.sendText("test");
        assertTrue(future.isCompletedExceptionally());
    }

    @Test
    @DisplayName("WebSocketClient: Abort 方法")
    void testAbort() {
        WebSocketClient client = WebSocketClient.newBuilder().build();
        
        assertDoesNotThrow(client::abort);
    }

    @Test
    @DisplayName("WebSocketClient: Close 方法")
    void testClose() {
        WebSocketClient client = WebSocketClient.newBuilder().build();
        
        CompletableFuture<WebSocket> future = client.close();
        assertNotNull(future);
    }

    @Test
    @DisplayName("WebSocketClient: 自定义状态码关闭")
    void testCloseWithStatus() {
        WebSocketClient client = WebSocketClient.newBuilder().build();
        
        CompletableFuture<WebSocket> future = client.close(1000, "Normal closure");
        assertNotNull(future);
    }
}
