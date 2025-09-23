package vitalconnect.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import vitalconnect.domain.ProcessedData;
import vitalconnect.input.SocketIOServerInput;

import java.net.ServerSocket;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class SocketIOServerInputIntegrationTest {

    private SocketIOServerInput server;
    private WebSocketClient client;
    private int testPort;

    @BeforeEach
    void setUp() throws Exception {
        // Use a random available port to avoid conflicts
        try (ServerSocket socket = new ServerSocket(0)) {
            testPort = socket.getLocalPort();
        }
        server = new SocketIOServerInput(testPort);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (client != null && client.isOpen()) {
            client.close();
        }
        if (server != null) {
            server.stop();
        }
        Thread.sleep(100); // Give time for cleanup
    }

    @Test
    @DisplayName("Should start server and accept connections")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testServerStartAndConnection() throws Exception {
        CountDownLatch connectedLatch = new CountDownLatch(1);
        AtomicReference<String> connectResponse = new AtomicReference<>();

        server.start();
        Thread.sleep(500); // Give server time to start

        client = new WebSocketClient(new URI("ws://localhost:" + testPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connectedLatch.countDown();
            }

            @Override
            public void onMessage(String message) {
                if (message.startsWith("0{")) {
                    connectResponse.set(message);
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();
        assertThat(connectedLatch.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(100); // Wait for connection response
        assertThat(connectResponse.get()).isNotNull();
        assertThat(connectResponse.get()).contains("sid");
        assertThat(connectResponse.get()).contains("pingInterval");
    }

    @Test
    @DisplayName("Should handle ping-pong messages")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testPingPong() throws Exception {
        CountDownLatch pongLatch = new CountDownLatch(1);

        server.start();
        Thread.sleep(500);

        client = new WebSocketClient(new URI("ws://localhost:" + testPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                send("2"); // Send ping
            }

            @Override
            public void onMessage(String message) {
                if ("3".equals(message)) {
                    pongLatch.countDown();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();
        assertThat(pongLatch.await(2, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    @DisplayName("Should handle join_vr event")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testJoinVrEvent() throws Exception {
        server.start();
        Thread.sleep(500);

        CountDownLatch connectedLatch = new CountDownLatch(1);

        client = new WebSocketClient(new URI("ws://localhost:" + testPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                connectedLatch.countDown();
                // Send join_vr event
                send("42[\"join_vr\",\"VR12345\"]");
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();
        assertThat(connectedLatch.await(2, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(100); // Give time for event processing
        // Event should be processed without errors
    }

    @Test
    @DisplayName("Should process binary data event")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testBinaryDataProcessing() throws Exception {
        CountDownLatch dataReceivedLatch = new CountDownLatch(1);
        AtomicReference<ProcessedData> receivedData = new AtomicReference<>();

        server.setDataListener(data -> {
            receivedData.set(data);
            dataReceivedLatch.countDown();
        });

        server.start();
        Thread.sleep(500);

        client = new WebSocketClient(new URI("ws://localhost:" + testPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                // Send binary event placeholder
                send("451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

                // Send compressed JSON data
                String testJson = "{\"vrcode\":\"VR123\",\"rooms\":[]}";
                byte[] compressed = compressData(testJson.getBytes());
                send(compressed);
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onMessage(ByteBuffer buffer) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };

        client.connect();

        // Wait for data to be processed
        assertThat(dataReceivedLatch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedData.get()).isNotNull();
        assertThat(receivedData.get().vrCode()).isEqualTo("VR123");
    }

    @Test
    @DisplayName("Should handle multiple clients")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMultipleClients() throws Exception {
        server.start();
        Thread.sleep(500);

        CountDownLatch bothConnected = new CountDownLatch(2);

        WebSocketClient client1 = createTestClient(bothConnected);
        WebSocketClient client2 = createTestClient(bothConnected);

        client1.connect();
        client2.connect();

        assertThat(bothConnected.await(3, TimeUnit.SECONDS)).isTrue();

        client1.close();
        client2.close();
    }

    private WebSocketClient createTestClient(CountDownLatch latch) throws URISyntaxException {
        return new WebSocketClient(new URI("ws://localhost:" + testPort)) {
            @Override
            public void onOpen(ServerHandshake handshake) {
                latch.countDown();
            }

            @Override
            public void onMessage(String message) {}

            @Override
            public void onClose(int code, String reason, boolean remote) {}

            @Override
            public void onError(Exception ex) {}
        };
    }

    private byte[] compressData(byte[] data) {
        try {
            java.util.zip.Deflater deflater = new java.util.zip.Deflater();
            deflater.setInput(data);
            deflater.finish();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];

            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                baos.write(buffer, 0, count);
            }

            deflater.end();
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}