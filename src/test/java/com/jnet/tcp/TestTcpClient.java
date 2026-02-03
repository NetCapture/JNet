package com.jnet.tcp;

import com.jnet.tcp.TcpClient;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TCP Client Tests
 */
public class TestTcpClient {

    private TcpClient client;
    private int echoServerPort = 8765;
    private Thread echoServerThread;

    @BeforeEach
    void setUp() throws Exception {
        // Start simple echo server for testing
        echoServerThread = new Thread(this::startEchoServer);
        echoServerThread.start();

        // Wait for server to be ready
        Thread.sleep(100);

        client = TcpClient.newBuilder()
                .connectTimeout(java.time.Duration.ofSeconds(5))
                .readTimeout(java.time.Duration.ofSeconds(5))
                .writeTimeout(java.time.Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (echoServerThread != null && echoServerThread.isAlive()) {
            echoServerThread.interrupt();
        echoServerThread = null;
        Thread.sleep(100);
        }
        client.close();
    }

    // ========== Echo Server ==========
    private void startEchoServer() {
        try {
            java.net.ServerSocket serverSocket = new java.net.ServerSocket(echoServerPort);
            while (!Thread.currentThread().isInterrupted()) {
                java.net.Socket socket = serverSocket.accept();
                java.io.InputStream in = socket.getInputStream();
                java.io.OutputStream out = socket.getOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;

                while (!Thread.currentThread().isInterrupted()) {
                    bytesRead = in.read(buffer);
                    if (bytesRead == -1) {
                        socket.close();
                        continue;
                    }
                    // Echo back
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            }
        } catch (IOException e) {
            System.err.println("Echo server error: " + e.getMessage());
        }
    }

    @Test
    void testSimpleConnection() throws IOException {
        String response = TcpClient.send("localhost", echoServerPort, "Hello, Server!");
        assertNotNull(response);
        assertEquals("Hello, Server!", response.trim());
    }

    @Test
    void testTcpClientSingleton() {
        TcpClient client1 = TcpClient.getInstance();
        TcpClient client2 = TcpClient.getInstance();
        assertSame(client1, client2);
    }

    @Test
    void testTcpRequest() throws IOException {
        com.jnet.tcp.TcpRequest request = client.newRequest("localhost", echoServerPort)
                .data("Test Request")
                .build();
        com.jnet.tcp.TcpResponse response = client.execute(request);
        assertTrue(response.isSuccessful());
        assertEquals("Test Request", response.getDataAsString());
    }

    @Test
    void testTcpSession() throws IOException {
        com.jnet.tcp.TcpSession session = client.newSession("localhost", echoServerPort);

        session.send("First message");
        String first = session.receiveString();
        assertEquals("First message", first);

        session.send("Second message");
        String second = session.receiveString();
        assertEquals("Second message", second);

        session.close();
        assertTrue(session.isClosed());
    }

    @Test
    void testAutoReconnect() throws IOException {
        com.jnet.tcp.TcpClient autoClient = TcpClient.newBuilder()
                .autoReconnect(true)
                .maxReconnectAttempts(3)
                .reconnectDelay(500)
                .build();

        String response = autoClient.send("localhost", echoServerPort, "Reconnect Test");
        assertNotNull(response);
        assertEquals("Reconnect Test", response.trim());
    }

    @Test
    void testTimeout() {
        assertThrows(IOException.class, () -> {
            TcpClient.send("localhost", 9999, "Timeout Test");
        }, "Timeout should be thrown");
    }

    @Test
    void testMultipleConnections() throws Exception {
        int count = 10;
        CountDownLatch latch = new CountDownLatch(count);

        for (int i = 0; i < count; i++) {
            final int index = i;
            new Thread(() -> {
                try {
                    String response = TcpClient.send("localhost", echoServerPort, "Test");
                    latch.countDown();
                } catch (IOException e) {
                    System.err.println("Error in connection " + index + ": " + e.getMessage());
                    latch.countDown();
                }
            }).start();
        }

        latch.await();
    }
}
