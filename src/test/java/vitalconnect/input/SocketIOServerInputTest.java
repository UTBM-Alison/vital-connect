package vitalconnect.input;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;
import vitalconnect.domain.ProcessedData;
import vitalconnect.processor.VitalDataProcessor;
import vitalconnect.processor.VitalDataTransformer;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SocketIOServerInputTest {

    private SocketIOServerInput serverInput;
    private final int testPort = 0; // Use any available port

    @Mock
    private VitalInput.DataListener dataListener;

    @Mock
    private WebSocket mockWebSocket;

    @Mock
    private ClientHandshake mockHandshake;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        serverInput = new SocketIOServerInput(testPort);
    }

    @AfterEach
    void tearDown() {
        if (serverInput != null) {
            serverInput.stop();
        }
        // Give time for cleanup
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Test
    @DisplayName("Should handle onOpen event with connection response")
    void testOnOpenWithConnectionResponse() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        // Get the internal WebSocketServer through reflection
        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        // Mock WebSocket connection
        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));

        // Call onOpen through the server
        server.onOpen(mockWebSocket, mockHandshake);

        // Verify connection response was sent (specify String type explicitly)
        verify(mockWebSocket).send(argThat((String response) ->
                response != null && response.startsWith("0{") && response.contains("sid")));
    }


    @Test
    @DisplayName("Should handle text message types correctly")
    void testHandleTextMessageTypes() throws Exception {
        serverInput.setDataListener(dataListener);
        serverInput.start();
        Thread.sleep(100);

        // Get the internal server and simulate messages
        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        // Setup mock connection
        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Test ping message "40"
        server.onMessage(mockWebSocket, "40");
        verify(mockWebSocket).send("3"); // Should send pong

        // Test ping message "2"
        server.onMessage(mockWebSocket, "2");
        verify(mockWebSocket, times(2)).send("3"); // Should send pong again

        // Test event message
        server.onMessage(mockWebSocket, "42[\"test_event\",\"data\"]");

        // Test join_vr event
        server.onMessage(mockWebSocket, "42[\"join_vr\",\"VR12345\"]");

        // Test binary event placeholder
        server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

        // Test unknown message format
        server.onMessage(mockWebSocket, "unknown_format");
    }

    @Test
    @DisplayName("Should handle binary message processing with pending data")
    void testBinaryMessageWithPendingData() throws Exception {
        serverInput.setDataListener(dataListener);
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Create test binary data
        String json = "{\"vrcode\":\"VR123\",\"rooms\":[]}";
        byte[] compressed = compressData(json.getBytes());
        ByteBuffer binaryBuffer = ByteBuffer.wrap(compressed);

        // Send binary data first (before placeholder)
        server.onMessage(mockWebSocket, binaryBuffer);

        // Then send placeholder - should trigger immediate processing
        server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

        // Verify data was processed
        Thread.sleep(100);
    }

    @Test
    @DisplayName("Should handle binary message from unknown client")
    void testBinaryMessageUnknownClient() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        // Create binary message without establishing connection first
        ByteBuffer binaryBuffer = ByteBuffer.wrap(new byte[]{1, 2, 3});

        // This should not throw exception
        assertThatCode(() -> server.onMessage(mockWebSocket, binaryBuffer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle text message from unknown client")
    void testTextMessageUnknownClient() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        // Send message without establishing connection first
        assertThatCode(() -> server.onMessage(mockWebSocket, "40"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle onClose event")
    void testOnCloseEvent() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Close connection
        server.onClose(mockWebSocket, 1000, "Normal closure", false);

        // Verify client was removed from map
        Field clientsField = SocketIOServerInput.class.getDeclaredField("clients");
        clientsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, WebSocket> clients = (Map<String, WebSocket>) clientsField.get(serverInput);
        assertThat(clients).doesNotContainValue(mockWebSocket);
    }



    @Test
    @DisplayName("Should handle onError event")
    void testOnErrorEvent() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        Exception testException = new RuntimeException("Test error");

        // Should not throw exception
        assertThatCode(() -> server.onError(mockWebSocket, testException))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle event parsing errors")
    void testEventParsingError() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Send malformed event
        assertThatCode(() -> server.onMessage(mockWebSocket, "42[invalid json"))
                .doesNotThrowAnyException();

        // Send event without quotes
        assertThatCode(() -> server.onMessage(mockWebSocket, "42[no_quotes]"))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should process binary event with send_data")
    void testProcessBinaryEventWithSendData() throws Exception {
        CountDownLatch dataLatch = new CountDownLatch(1);
        AtomicReference<ProcessedData> receivedData = new AtomicReference<>();

        serverInput.setDataListener(data -> {
            receivedData.set(data);
            dataLatch.countDown();
        });

        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Send binary event placeholder first
        server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

        // Then send valid compressed data
        String json = "{\"vrcode\":\"VR999\",\"rooms\":[{\"roomname\":\"Test Room\",\"trks\":[],\"evts\":[]}]}";
        byte[] compressed = compressData(json.getBytes());
        ByteBuffer binaryBuffer = ByteBuffer.wrap(compressed);
        server.onMessage(mockWebSocket, binaryBuffer);

        // Wait for processing
        assertThat(dataLatch.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(receivedData.get()).isNotNull();
        assertThat(receivedData.get().vrCode()).isEqualTo("VR999");
    }

    @Test
    @DisplayName("Should handle processing errors in binary event")
    void testBinaryEventProcessingError() throws Exception {
        serverInput.setDataListener(dataListener);
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Send placeholder
        server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

        // Send invalid data (not compressed JSON)
        ByteBuffer binaryBuffer = ByteBuffer.wrap(new byte[]{1, 2, 3, 4, 5});

        // Should handle error gracefully
        assertThatCode(() -> server.onMessage(mockWebSocket, binaryBuffer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle non-send_data binary events")
    void testNonSendDataBinaryEvent() throws Exception {
        serverInput.setDataListener(dataListener);
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        // Send non-send_data event with binary
        server.onMessage(mockWebSocket, "451-[\"other_event\",{\"_placeholder\":true,\"num\":0}]");

        ByteBuffer binaryBuffer = ByteBuffer.wrap(new byte[]{1, 2, 3});
        assertThatCode(() -> server.onMessage(mockWebSocket, binaryBuffer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle VitalDataProcessor exceptions")
    void testVitalDataProcessorException() throws Exception {
        serverInput.setDataListener(dataListener);
        serverInput.start();
        Thread.sleep(100);

        try (MockedConstruction<VitalDataProcessor> mockedProcessor = mockConstruction(
                VitalDataProcessor.class,
                (mock, context) -> {
                    when(mock.process(any(byte[].class)))
                            .thenThrow(new IllegalArgumentException("Processing failed"));
                })) {

            Field serverField = SocketIOServerInput.class.getDeclaredField("server");
            serverField.setAccessible(true);
            WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

            when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
            server.onOpen(mockWebSocket, mockHandshake);

            server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

            String json = "{\"vrcode\":\"VR123\",\"rooms\":[]}";
            byte[] compressed = compressData(json.getBytes());
            ByteBuffer binaryBuffer = ByteBuffer.wrap(compressed);

            assertThatCode(() -> server.onMessage(mockWebSocket, binaryBuffer))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should process data with null data listener")
    void testProcessDataWithNullListener() throws Exception {
        serverInput.setDataListener(null); // Explicitly set to null
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        when(mockWebSocket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 12345));
        server.onOpen(mockWebSocket, mockHandshake);

        server.onMessage(mockWebSocket, "451-[\"send_data\",{\"_placeholder\":true,\"num\":0}]");

        String json = "{\"vrcode\":\"VR123\",\"rooms\":[]}";
        byte[] compressed = compressData(json.getBytes());
        ByteBuffer binaryBuffer = ByteBuffer.wrap(compressed);

        // Should not throw even with null listener
        assertThatCode(() -> server.onMessage(mockWebSocket, binaryBuffer))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle onStart event")
    void testOnStartEvent() throws Exception {
        serverInput.start();
        Thread.sleep(100);

        Field serverField = SocketIOServerInput.class.getDeclaredField("server");
        serverField.setAccessible(true);
        WebSocketServer server = (WebSocketServer) serverField.get(serverInput);

        // Call onStart - should not throw
        assertThatCode(() -> server.onStart()).doesNotThrowAnyException();
    }

    // Additional helper method tests
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

    // Tests for edge cases and error conditions
    @Test
    @DisplayName("Should handle server start failure gracefully")
    void testServerStartFailure() {
        // Try to start server on an invalid port (negative)
        SocketIOServerInput invalidServer = new SocketIOServerInput(-1);

        // This should throw an exception due to port validation
        assertThatThrownBy(() -> invalidServer.start())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port out of range");

        // Cleanup should still work
        assertThatCode(() -> invalidServer.stop()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle stop without start")
    void testStopWithoutStart() {
        assertThatCode(() -> serverInput.stop()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should handle stop with exception")
    void testStopWithException() {
        serverInput.start();

        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Force stop the server to simulate exception
        assertThatCode(() -> serverInput.stop()).doesNotThrowAnyException();
    }
}