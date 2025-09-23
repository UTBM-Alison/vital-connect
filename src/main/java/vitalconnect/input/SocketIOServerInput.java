package vitalconnect.input;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket server that handles Socket.IO v4 protocol with binary data
 */
public class SocketIOServerInput implements VitalInput {
    private static final Logger logger = LoggerFactory.getLogger(SocketIOServerInput.class);

    private final int port;
    private WebSocketServer server;
    private DataListener dataListener;
    private final java.util.Map<WebSocket, SocketIOClient> clients = new ConcurrentHashMap<>();

    public SocketIOServerInput(int port) {
        this.port = port;
    }

    @Override
    public void start() {
        server = new WebSocketServer(new InetSocketAddress(port)) {
            @Override
            public void onOpen(WebSocket conn, ClientHandshake handshake) {
                logger.info("New Socket.IO v4 connection: {}", conn.getRemoteSocketAddress());
                clients.put(conn, new SocketIOClient());

                // Send connection acknowledgement immediately
                String connectResponse = "0{\"sid\":\"" + java.util.UUID.randomUUID() +
                        "\",\"upgrades\":[],\"pingInterval\":25000,\"pingTimeout\":5000}";
                conn.send(connectResponse);
                logger.debug("Sent connection response: {}", connectResponse);
            }

            @Override
            public void onMessage(WebSocket conn, String message) {
                handleTextMessage(conn, message);
            }

            @Override
            public void onMessage(WebSocket conn, ByteBuffer binaryMessage) {
                handleBinaryMessage(conn, binaryMessage);
            }

            @Override
            public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                logger.info("Socket.IO v4 connection closed: {} - {}", code, reason);
                clients.remove(conn);
            }

            @Override
            public void onError(WebSocket conn, Exception ex) {
                logger.error("Socket.IO v4 WebSocket error", ex);
            }

            @Override
            public void onStart() {

            }
        };

        server.start();
        logger.info("Socket.IO v4 WebSocket server started on port {}", port);
    }

    @Override
    public void stop() {
        if (server != null) {
            try {
                server.stop();
            } catch (Exception e) {
                logger.error("Error stopping server", e);
            }
        }
    }

    @Override
    public boolean isRunning() {
        return server != null;
    }

    @Override
    public void setDataListener(DataListener listener) {
        this.dataListener = listener;
    }

    private void handleTextMessage(WebSocket conn, String message) {
        logger.debug("Received text message: {}", message);

        try {
            SocketIOClient client = clients.get(conn);
            if (client == null) {
                logger.warn("Received message from unknown client");
                return;
            }

            // Handle different message types based on content
            if ("40".equals(message)) {
                // Ping packet - respond with pong
                handlePing(conn);
            } else if (message.startsWith("42")) {
                // Event packet: 42["event_name", ...args]
                handleEvent(conn, message.substring(2)); // Remove "42" prefix
            } else if (message.startsWith("451-")) {
                // Binary event placeholder: 451-["send_data",{"_placeholder":true,"num":0}]
                handleBinaryEventPlaceholder(conn, message.substring(4)); // Remove "451-" prefix
            } else if (message.startsWith("2")) {
                // Ping packet (alternative format)
                handlePing(conn);
            } else {
                logger.warn("Unknown message format: {}", message);
            }

        } catch (Exception e) {
            logger.error("Error handling text message: {}", message, e);
        }
    }

    private void handleBinaryMessage(WebSocket conn, ByteBuffer binaryMessage) {
        logger.debug("Received binary message, length: {}", binaryMessage.remaining());

        SocketIOClient client = clients.get(conn);
        if (client == null) {
            logger.warn("Received binary data from unknown client");
            return;
        }

        // Convert to byte array for processing
        byte[] binaryData = new byte[binaryMessage.remaining()];
        binaryMessage.get(binaryData);

        // If we have a pending binary event, process it
        if (client.expectingBinaryData && client.pendingEvent != null) {
            processBinaryEvent(conn, client.pendingEvent, binaryData);
            client.expectingBinaryData = false;
            client.pendingEvent = null;
        } else {
            // Store binary data for when the event arrives
            client.pendingBinaryData = binaryData;
        }
    }

    private void handlePing(WebSocket conn) {
        logger.debug("Handling ping");
        // Respond with pong
        conn.send("3");
    }

    private void handleEvent(WebSocket conn, String eventData) {
        logger.debug("Handling event: {}", eventData);

        try {
            // Parse JSON array - simple parsing for ["event_name", ...args]
            if (eventData.startsWith("[")) {
                // Extract event name from array
                int firstQuote = eventData.indexOf('"');
                int secondQuote = eventData.indexOf('"', firstQuote + 1);

                if (firstQuote != -1 && secondQuote != -1) {
                    String eventName = eventData.substring(firstQuote + 1, secondQuote);
                    logger.info("Event received: {}", eventName);

                    if ("join_vr".equals(eventName)) {
                        // Extract VR code
                        int thirdQuote = eventData.indexOf('"', secondQuote + 1);
                        int fourthQuote = eventData.indexOf('"', thirdQuote + 1);
                        if (thirdQuote != -1 && fourthQuote != -1) {
                            String vrCode = eventData.substring(thirdQuote + 1, fourthQuote);
                            logger.info("VR joined: {}", vrCode);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Error parsing event data: {}", eventData, e);
        }
    }

    private void handleBinaryEventPlaceholder(WebSocket conn, String placeholderData) {
        logger.debug("Handling binary event placeholder: {}", placeholderData);

        SocketIOClient client = clients.get(conn);
        if (client == null) return;

        client.expectingBinaryData = true;
        client.pendingEvent = placeholderData;

        // If binary data already arrived, process it immediately
        if (client.pendingBinaryData != null) {
            processBinaryEvent(conn, placeholderData, client.pendingBinaryData);
            client.expectingBinaryData = false;
            client.pendingBinaryData = null;
            client.pendingEvent = null;
        }
    }

    private void processBinaryEvent(WebSocket conn, String eventData, byte[] binaryData) {
        try {
            logger.info("Processing binary event: {} with data length: {}", eventData, binaryData.length);

            // Check if this is a send_data event
            if (eventData.contains("send_data")) {
                // Process the binary data through your existing pipeline
                if (dataListener != null) {
                    processVitalData(binaryData, conn);
                }
            }
        } catch (Exception e) {
            logger.error("Error processing binary event", e);
        }
    }

    private void processVitalData(byte[] binaryData, WebSocket conn) {
        try {
            // Use your existing decompression and processing logic
            VitalDataDecompressor decompressor = new VitalDataDecompressor();
            vitalconnect.processor.VitalDataProcessor processor = new vitalconnect.processor.VitalDataProcessor();
            vitalconnect.processor.VitalDataTransformer transformer = new vitalconnect.processor.VitalDataTransformer();

            // Step 1: Decompress
            byte[] decompressed = decompressor.decompress(binaryData);
            logger.debug("Decompressed data length: {}", decompressed.length);

            // Step 2: Process JSON
            vitalconnect.domain.VitalData vitalData = processor.process(decompressed);

            // Step 3: Transform
            vitalconnect.domain.ProcessedData processedData = transformer.transform(vitalData);

            // Step 4: Send to listener
            if (dataListener != null) {
                dataListener.onDataReceived(processedData);
            }

            logger.info("Successfully processed vital data: {} rooms, {} tracks",
                    processedData.rooms().size(), processedData.allTracks().size());

        } catch (Exception e) {
            logger.error("Error processing vital data", e);

            // Hex dump for debugging
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < Math.min(binaryData.length, 100); i++) {
                hex.append(String.format("%02x ", binaryData[i]));
                if ((i + 1) % 16 == 0) hex.append("\n");
            }
            logger.info("First 100 bytes of binary data:\n{}", hex.toString());
        }
    }

    // Client state tracking
    private static class SocketIOClient {
        boolean expectingBinaryData = false;
        String pendingEvent = null;
        byte[] pendingBinaryData = null;
    }
}