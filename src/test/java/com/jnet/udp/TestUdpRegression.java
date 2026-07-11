package com.jnet.udp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class TestUdpRegression {

    @Test
    @Timeout(3)
    void closeUnblocksEveryIndefiniteReceiveOnPort() throws Exception {
        UdpClient client = UdpClient.create();
        AtomicReference<Throwable> firstFailure = new AtomicReference<>();
        AtomicReference<Throwable> secondFailure = new AtomicReference<>();
        Thread first = receiveOnEphemeralPort(client, firstFailure);
        Thread second = receiveOnEphemeralPort(client, secondFailure);

        try {
            first.start();
            second.start();
            awaitDatagramReceive(first);
            awaitDatagramReceive(second);

            client.close();

            first.join(1_000);
            second.join(1_000);
            assertFalse(first.isAlive());
            assertFalse(second.isAlive());
            assertInstanceOf(IOException.class, firstFailure.get());
            assertInstanceOf(IOException.class, secondFailure.get());
        } finally {
            client.close();
        }
    }

    @Test
    @Timeout(3)
    void receiveZeroClearsThePreviousSocketTimeout() throws Exception {
        try (UdpClient client = UdpClient.newBuilder().timeout(Duration.ZERO).build();
             DatagramSocket sender = new DatagramSocket()) {
            assertThrows(SocketTimeoutException.class, () -> client.receive(25));

            Thread delayedSender = new Thread(() -> {
                try {
                    Thread.sleep(100);
                    byte[] data = "late packet".getBytes(StandardCharsets.UTF_8);
                    DatagramPacket packet = new DatagramPacket(
                            data,
                            data.length,
                            InetAddress.getLoopbackAddress(),
                            client.getSocket().getLocalPort());
                    sender.send(packet);
                } catch (Exception ignored) {
                    // The assertions below expose a failed send as a receive timeout/test timeout.
                }
            }, "udp-regression-sender");
            delayedSender.setDaemon(true);
            delayedSender.start();

            assertEquals("late packet", client.receive(0).getDataAsString());
        }
    }

    @Test
    @Timeout(3)
    void serverBindsDispatchesAndStops() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<String> message = new AtomicReference<>();
        AtomicReference<String> host = new AtomicReference<>();

        Server server = Server.newBuilder()
                .handler((data, remoteHost, remotePort) -> {
                    message.set(data);
                    host.set(remoteHost);
                    received.countDown();
                })
                .build();

        try (DatagramSocket sender = new DatagramSocket()) {
            server.start(0);
            assertTrue(server.isRunning());
            assertTrue(server.getPort() > 0);

            byte[] data = "hello UDP server".getBytes(StandardCharsets.UTF_8);
            sender.send(new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getLoopbackAddress(),
                    server.getPort()));

            assertTrue(received.await(1, TimeUnit.SECONDS));
            assertEquals("hello UDP server", message.get());
            assertNotNull(host.get());
        } finally {
            server.stop();
        }

        assertFalse(server.isRunning());
    }

    @Test
    void packetIsActuallyImmutableAndAlwaysProvidesUtf8Text() throws Exception {
        byte[] source = "abc".getBytes(StandardCharsets.UTF_8);
        UdpPacket packet = UdpPacket.newBuilder()
                .address(InetAddress.getLoopbackAddress(), 1234)
                .data(source)
                .build();

        source[0] = 'x';
        assertEquals("abc", packet.getDataAsString());

        byte[] exposed = packet.getData();
        exposed[1] = 'y';
        assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8), packet.getData());
    }

    @Test
    void configRejectsValuesUnsupportedByDatagramSockets() {
        assertThrows(IllegalArgumentException.class,
                () -> UdpConfig.newBuilder().timeout(Duration.ofMillis(-1)).build());
        assertThrows(IllegalArgumentException.class,
                () -> UdpConfig.newBuilder().receiveBufferSize(-1).build());
        assertThrows(IllegalArgumentException.class,
                () -> UdpConfig.newBuilder().trafficClass(256).build());
        assertThrows(IllegalArgumentException.class,
                () -> UdpConfig.newBuilder().multicastTtl(-1).build());
    }

    @Test
    @SuppressWarnings("deprecation")
    void legacyTrafficClassConstantsKeepTheirPublishedValues() {
        assertEquals(0, UdpConfig.Builder.UC_IPTOS_THROUGHPUT);
        assertEquals(1, UdpConfig.Builder.UC_IPTOS_LOWCOST);
        assertEquals(2, UdpConfig.Builder.UC_IPTOS_RELIABILITY);
        assertEquals(3, UdpConfig.Builder.UC_IPTOS_BULK);
    }

    @Test
    void multicastConfigurationIsAppliedToTheClientSocket() throws Exception {
        try (UdpClient client = UdpClient.newBuilder()
                .multicastTtl(7)
                .loopbackMode(true)
                .build()) {
            MulticastSocket socket = (MulticastSocket) client.getSocket();
            assertEquals(7, socket.getTimeToLive());
            assertFalse(socket.getLoopbackMode(), "false means multicast loopback is enabled");
        }
    }

    @Test
    @Timeout(2)
    void positiveSubMillisecondTimeoutDoesNotBecomeInfinite() throws Exception {
        try (UdpClient client = UdpClient.newBuilder().timeout(Duration.ofNanos(1)).build()) {
            assertThrows(SocketTimeoutException.class, client::receive);
        }
    }

    @Test
    @Timeout(3)
    void handlerFailureDoesNotTerminateTheReceiveLoop() throws Exception {
        CountDownLatch secondPacket = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        Server server = Server.newBuilder()
                .handler((data, host, port) -> {
                    if (calls.incrementAndGet() == 1) {
                        throw new IllegalStateException("bad packet");
                    }
                    secondPacket.countDown();
                })
                .build();

        try (DatagramSocket sender = new DatagramSocket()) {
            server.start(0);
            byte[] data = {1};
            DatagramPacket packet = new DatagramPacket(
                    data, data.length, InetAddress.getLoopbackAddress(), server.getPort());
            sender.send(packet);
            sender.send(packet);

            assertTrue(secondPacket.await(1, TimeUnit.SECONDS));
            assertTrue(server.isRunning());
        } finally {
            server.close();
        }
    }

    @Test
    @Timeout(3)
    void binaryHandlerReceivesDatagramsWithoutUtf8Conversion() throws Exception {
        CountDownLatch received = new CountDownLatch(1);
        AtomicReference<byte[]> payload = new AtomicReference<>();
        Server server = Server.newBuilder()
                .packetHandler(packet -> {
                    payload.set(packet.getData());
                    received.countDown();
                })
                .build();

        byte[] expected = {0, (byte) 0xff, 1};
        try (DatagramSocket sender = new DatagramSocket()) {
            server.start(0);
            sender.send(new DatagramPacket(
                    expected, expected.length, InetAddress.getLoopbackAddress(), server.getPort()));

            assertTrue(received.await(1, TimeUnit.SECONDS));
            assertArrayEquals(expected, payload.get());
        } finally {
            server.close();
        }
    }

    private static Thread receiveOnEphemeralPort(
            UdpClient client, AtomicReference<Throwable> failure) {
        Thread thread = new Thread(() -> {
            try {
                client.receiveOnPort(0, 0);
            } catch (Throwable error) {
                failure.set(error);
            }
        }, "udp-receive-on-port");
        thread.setDaemon(true);
        return thread;
    }

    private static void awaitDatagramReceive(Thread thread) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (thread.isAlive() && System.nanoTime() < deadline) {
            for (StackTraceElement frame : thread.getStackTrace()) {
                if (frame.getClassName().equals(DatagramSocket.class.getName())
                        && frame.getMethodName().equals("receive")) {
                    return;
                }
            }
            Thread.sleep(5);
        }
        fail("receiver did not block in DatagramSocket.receive");
    }
}
