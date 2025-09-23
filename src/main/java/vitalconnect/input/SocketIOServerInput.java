package vitalconnect.input;

import com.corundumstudio.socketio.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.domain.ProcessedData;
import vitalconnect.domain.VitalData;
import vitalconnect.processor.VitalDataProcessor;
import vitalconnect.processor.VitalDataTransformer;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Socket.IO SERVER implementation for receiving VitalRecorder data.
 * This acts as a server that VitalRecorder connects to.
 */
public class SocketIOServerInput implements VitalInput {
    private static final Logger logger = LoggerFactory.getLogger(SocketIOServerInput.class);

    private final int port;
    private final VitalDataDecompressor decompressor;
    private final VitalDataProcessor processor;
    private final VitalDataTransformer transformer;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicInteger connectedClients = new AtomicInteger(0);

    private SocketIOServer server;
    private VitalInput.DataListener dataListener;

    /**
     * Create a new Socket.IO server input.
     *
     * @param port the server port to listen on
     */
    public SocketIOServerInput(int port) {
        this.port = port;
        this.decompressor = new VitalDataDecompressor();
        this.processor = new VitalDataProcessor();
        this.transformer = new VitalDataTransformer();
    }

    @Override
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                startServer();
                logger.info("Socket.IO Server started on port {}", port);
                logger.info("Configure Vital Recorder: SERVER_IP=127.0.0.1:{}", port);
            } catch (Exception e) {
                running.set(false);
                throw new RuntimeException("Failed to start Socket.IO server", e);
            }
        }
    }

    @Override
    public void stop() {
        if (running.compareAndSet(true, false)) {
            if (server != null) {
                server.stop();
                server = null;
            }
            logger.info("Socket.IO Server stopped");
        }
    }

    @Override
    public boolean isRunning() {
        return running.get() && server != null;
    }

    @Override
    public void setDataListener(VitalInput.DataListener listener) {
        this.dataListener = listener;
    }

    /**
     * Get the number of connected clients.
     */
    public int getConnectedClients() {
        return connectedClients.get();
    }

    private void startServer() {
        Configuration config = new Configuration();
        config.setHostname("0.0.0.0");  // Listen on all interfaces
        config.setPort(port);

        // Allow connections from any origin (for VitalRecorder)
        config.setOrigin("*");

        // Configure for binary data handling
        config.setMaxFramePayloadLength(1048576); // 1MB max frame size
        config.setMaxHttpContentLength(1048576);  // 1MB max HTTP content

        // Configure transports - WebSocket and Polling like the TypeScript version
        config.setTransports(Transport.WEBSOCKET, Transport.POLLING);
        config.setAllowCustomRequests(true);
        config.setUpgradeTimeout(10000);
        config.setPingTimeout(60000);
        config.setPingInterval(25000);

        server = new SocketIOServer(config);

        // Add connection listener
        server.addConnectListener(client -> {
            int count = connectedClients.incrementAndGet();
            logger.info("VitalRecorder connected: {} (Total connections: {})",
                    client.getSessionId(), count);
        });

        // Add disconnection listener
        server.addDisconnectListener(client -> {
            int count = connectedClients.decrementAndGet();
            logger.info("VitalRecorder disconnected: {} (Total connections: {})",
                    client.getSessionId(), count);
        });

        // Add listener for binary data - handle as String first since Socket.IO might send it as base64
        server.addEventListener("send_data", String.class, (client, data, ackRequest) -> {
            handleStringData(data, client);
        });

        // Also add listener for byte array in case it comes in that format
        server.addEventListener("send_data", byte[].class, (client, data, ackRequest) -> {
            handleByteArrayData(data, client);
        });

        // Add listener for generic Object to catch any other format
        server.addEventListener("send_data", Object.class, (client, data, ackRequest) -> {
            handleObjectData(data, client);
        });

        // Start the server
        server.start();

        logger.info("Socket.IO Server listening on port {}", port);
        logger.info("Waiting for VitalRecorder connections...");
    }

    private void handleStringData(String data, SocketIOClient client) {
        try {
            logger.debug("Received string data from client: {}", client.getSessionId());

            // Try to decode as base64
            byte[] dataToProcess;
            try {
                dataToProcess = java.util.Base64.getDecoder().decode(data);
                logger.debug("Successfully decoded base64 data");
            } catch (IllegalArgumentException e) {
                // Not base64, use as raw bytes
                dataToProcess = data.getBytes();
                logger.debug("Using string data as raw bytes");
            }

            processData(dataToProcess, client);
        } catch (Exception e) {
            logger.error("Failed to handle string data from client: {}",
                    client.getSessionId(), e);
        }
    }

    private void handleByteArrayData(byte[] data, SocketIOClient client) {
        try {
            logger.debug("Received byte array data from client: {}", client.getSessionId());
            processData(data, client);
        } catch (Exception e) {
            logger.error("Failed to handle byte array data from client: {}",
                    client.getSessionId(), e);
        }
    }

    private void handleObjectData(Object data, SocketIOClient client) {
        try {
            logger.debug("Received object data of type {} from client: {}",
                    data != null ? data.getClass().getName() : "null",
                    client.getSessionId());

            // Skip if already handled by more specific listeners
            if (data instanceof String || data instanceof byte[]) {
                return;
            }

            // Try to convert to bytes
            byte[] dataToProcess = null;
            if (data != null) {
                dataToProcess = data.toString().getBytes();
            }

            if (dataToProcess != null) {
                processData(dataToProcess, client);
            } else {
                logger.warn("Received unprocessable data from client: {}", client.getSessionId());
            }
        } catch (Exception e) {
            logger.error("Failed to handle object data from client: {}",
                    client.getSessionId(), e);
        }
    }

    private void processData(byte[] data, SocketIOClient client) {
        try {
            // Step 1: Decompress the data
            byte[] decompressed = decompressor.decompress(data);

            // Step 2: Process (parse) the JSON data
            VitalData vitalData = processor.process(decompressed);

            // Step 3: Transform to processed format
            ProcessedData processedData = transformer.transform(vitalData);

            // Step 4: Notify listener
            if (dataListener != null) {
                dataListener.onDataReceived(processedData);
            }

            logger.debug("Processed data: {} tracks from {} rooms",
                    processedData.allTracks().size(),
                    processedData.rooms().size());

        } catch (Exception e) {
            logger.error("Failed to process data from client: {}",
                    client.getSessionId(), e);
        }
    }
}
