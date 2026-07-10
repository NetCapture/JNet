package com.jnet.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SSEClientEnhanced Tests")
class TestSSEClientEnhanced {

    @Test
    @DisplayName("SSEClientEnhanced: Builder 创建")
    void testBuilder() {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .maxRetries(10)
                .initialRetryDelay(2000)
                .heartbeatInterval(15000)
                .build();
        
        assertNotNull(client);
        assertEquals(0, client.getReconnectCount());
    }

    @Test
    @DisplayName("SSEClientEnhanced: Event Filter")
    void testEventFilter() {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder()
                .eventFilter(event -> "important".equals(event.getEvent()))
                .build();
        
        assertNotNull(client);
    }

    @Test
    @DisplayName("SSEClientEnhanced: Last Event ID tracking")
    void testLastEventId() {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder().build();
        
        assertNull(client.getLastEventId());
    }

    @Test
    @DisplayName("SSEEvent: 事件对象创建")
    void testSSEEvent() {
        SSEClientEnhanced.SSEEvent event = new SSEClientEnhanced.SSEEvent(
                "123", "message", "Hello World");
        
        assertEquals("123", event.getId());
        assertEquals("message", event.getEvent());
        assertEquals("Hello World", event.getData());
        assertTrue(event.getTimestamp() > 0);
    }

    @Test
    @DisplayName("SSEClientEnhanced: Disconnect")
    void testDisconnect() {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder().build();
        
        assertDoesNotThrow(client::disconnect);
    }

    @Test
    @DisplayName("SSEClientEnhanced: 默认配置")
    void testDefaultConfiguration() {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder().build();
        
        assertNotNull(client);
        assertEquals(0, client.getReconnectCount());
    }

    @Test
    @DisplayName("SSEClientEnhanced: 断开后可重连（仅语义验证）")
    void testDisconnectThenReconnect() throws InterruptedException {
        SSEClientEnhanced client = SSEClientEnhanced.newBuilder().heartbeatInterval(200).maxRetries(1).build();

        SSEClientEnhanced.EnhancedSSEListener listener = new SSEClientEnhanced.EnhancedSSEListener() {
            @Override
            public void onEvent(SSEClientEnhanced.SSEEvent event) {}

            @Override
            public void onError(Exception e) {}

            @Override
            public void onReconnect(int attempt) {
            }
        };

        client.connect("http://127.0.0.1:1", Collections.emptyMap(), listener);
        TimeUnit.MILLISECONDS.sleep(100);
        assertDoesNotThrow(client::disconnect);

        assertDoesNotThrow(() -> client.connect("http://127.0.0.1:1", Collections.emptyMap(), listener));
        TimeUnit.MILLISECONDS.sleep(100);

        assertDoesNotThrow(client::disconnect);
    }
}
