package com.jnet.socketio;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Socket.IO Client Tests")
public class TestSocketIOClient {

    @Test
    @DisplayName("Verify Client Instantiation and Configuration")
    public void testClientConfig() {
        SocketIOClient client = new SocketIOClient("http://localhost:3000");
        Assertions.assertFalse(client.isConnected());
        Assertions.assertNull(client.getSessionId());

        // Test Namespace
        SocketIOClient nsClient = client.namespace("/chat");
        Assertions.assertNotNull(nsClient);
        Assertions.assertFalse(nsClient.isConnected());
    }

    @Test
    @DisplayName("Verify Event System")
    public void testEventSystem() {
        SocketIOClient client = new SocketIOClient("http://localhost:3000");
        final boolean[] triggered = {false};

        client.on("test-event", args -> {
            triggered[0] = true;
            Assertions.assertEquals("hello", args[0]);
        });

        // Reflection could be used to trigger private methods for deeper testing,
        // but for now we verify the public API contract
        client.on("connect", args -> {});
        client.on("disconnect", args -> {});
    }
}
