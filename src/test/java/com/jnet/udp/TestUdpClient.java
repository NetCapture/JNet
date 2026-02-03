package com.jnet.udp;

import com.jnet.udp.UdpClient;
import com.jnet.udp.UdpPacket;
import com.jnet.udp.UdpConfig;

import java.io.IOException;
import java.net.InetAddress;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import java.net.SocketTimeoutException;
import java.util.concurrent.CountDownLatch;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UDP Client Tests
 */
public class TestUdpClient {

    private UdpClient client;
    private int echoPort = 8766;

    @BeforeEach
    void setUp() {
        UdpConfig config = UdpConfig.newBuilder()
                .timeout(java.time.Duration.ofSeconds(2))
                .build();
        client = UdpClient.newInstance(config);
    }

    @AfterEach
    void tearDown() {
        if (client != null) {
            try {
                client.close();
            } catch (Exception ignored) {
                // Ignore close errors
            }
        }
    }

    @Test
    void testUdpSingleton() {
        UdpClient client1 = UdpClient.getInstance();
        UdpClient client2 = UdpClient.getInstance();
        assertSame(client1, client2);
    }

    @Test
    void testSimpleSend() throws IOException {
        UdpPacket packet = client.send(new UdpPacket.Builder()
                .address(InetAddress.getByName("localhost"), echoPort)
                .data("Hello UDP!")
                .build());

        assertNotNull(packet);
        assertEquals("Hello UDP!", packet.getDataAsString());
    }

    @Test
    void testReceive() throws IOException {
        // Send first
        UdpPacket sent = client.send(new UdpPacket.Builder()
                .address(InetAddress.getByName("localhost"), echoPort)
                .data("Receive Test")
                .build());

        // Receive
        UdpPacket received = client.receive(2000);
        assertNotNull(received);
    }

    @Test
    void testBroadcast() throws IOException {
        UdpClient broadcastClient = UdpClient.newBuilder()
                .broadcast(true)
                .build();

        UdpPacket packet = broadcastClient.send(new UdpPacket.Builder()
                .address(InetAddress.getByName("255.255.255.255"), echoPort)
                .data("Broadcast!")
                .build());

        assertNotNull(packet);
    }

    @Test
    void testReceiveTimeout() {
        assertThrows(SocketTimeoutException.class, () -> {
            client.receive(2000);
        }, "Timeout should be thrown");
    }

    @Test
    void testBufferSize() throws IOException {
        UdpConfig bigBufferConfig = UdpConfig.newBuilder()
                .receiveBufferSize(131072)
                .build();

        UdpClient client = UdpClient.newInstance(bigBufferConfig);

        // Create big packet
        byte[] bigData = new byte[10000];
        UdpPacket packet = new UdpPacket.Builder()
                .address(InetAddress.getByName("localhost"), echoPort)
                .data(bigData)
                .build();

        client.send(packet);

        UdpPacket received = client.receive(2000);
        assertNotNull(received);
        assertEquals(10000, received.getDataLength());
    }
}
