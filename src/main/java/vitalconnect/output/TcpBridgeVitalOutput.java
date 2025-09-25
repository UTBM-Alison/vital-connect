package vitalconnect.output;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.domain.ProcessedData;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * VitalOutput that forwards JSON data over TCP to the Rust bridge with auto-reconnect.
 */
public class TcpBridgeVitalOutput implements VitalOutput {
    private static final Logger logger = LoggerFactory.getLogger(TcpBridgeVitalOutput.class);

    private final String host;
    private final int port;

    private volatile Socket socket;
    private volatile OutputStream outputStream;

    private final ExecutorService senderExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "tcp-bridge-sender");
        t.setDaemon(true);
        return t;
    });

    private Thread reconnectThread;
    private final Object stateLock = new Object();
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private volatile boolean running = false;

    public TcpBridgeVitalOutput(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public void initialize() {
        running = true;
        startConnectionManager();
    }

    @Override
    public void send(ProcessedData data) {
        if (!connected.get()) {
            logger.debug("TCP bridge offline, dropping data");
            return;
        }

        senderExecutor.submit(() -> {
            try {
                OutputStream os = outputStream; // snapshot
                if (os == null) {
                    throw new IOException("OutputStream is null");
                }
                String json = data.toJSON() + "\n";
                os.write(json.getBytes(StandardCharsets.UTF_8));
                os.flush();
                logger.debug("Sent {} bytes to TCP bridge", json.length());
            } catch (IOException e) {
                handleDisconnect(e);
            } catch (Exception e) {
                // Catch-all to avoid killing the sender thread
                logger.error("Unexpected error while sending to TCP bridge", e);
                handleDisconnect(e);
            }
        });
    }

    @Override
    public void close() {
        running = false;
        senderExecutor.shutdownNow();
        if (reconnectThread != null) {
            reconnectThread.interrupt();
        }
        synchronized (stateLock) {
            closeResourcesNoStop();
        }
        logger.info("TCP bridge connection closed");
    }

    private void startConnectionManager() {
        reconnectThread = new Thread(this::reconnectLoop, "tcp-bridge-reconnector");
        reconnectThread.setDaemon(true);
        reconnectThread.start();
    }

    private void reconnectLoop() {
        long backoffMs = 1000L;
        final long maxBackoffMs = 30000L;

        logger.info("Connecting to TCP bridge at {}:{}", host, port);

        while (running) {
            if (!connected.get()) {
                try {
                    attemptConnect();
                    backoffMs = 1000L; // reset backoff after success
                    logger.info("✅ Connected to TCP bridge successfully");
                } catch (IOException e) {
                    logger.warn("Failed to connect to TCP bridge: {}. Retrying in {} ms",
                            e.getMessage(), backoffMs);
                    sleep(backoffMs);
                    backoffMs = Math.min(maxBackoffMs, backoffMs * 2);
                } catch (Exception e) {
                    logger.error("Unexpected error during TCP connect", e);
                    sleep(backoffMs);
                    backoffMs = Math.min(maxBackoffMs, backoffMs * 2);
                }
            } else {
                // Polling interval while connected
                sleep(1000L);
            }
        }
    }

    private void attemptConnect() throws IOException {
        Socket s = new Socket();
        s.setTcpNoDelay(true);
        s.setKeepAlive(true);
        s.connect(new InetSocketAddress(host, port), 3000);

        OutputStream os = new BufferedOutputStream(s.getOutputStream());

        synchronized (stateLock) {
            // Close any previous resources before swapping
            closeResourcesNoStop();
            socket = s;
            outputStream = os;
        }

        connected.set(true);
    }

    private void handleDisconnect(Exception cause) {
        if (connected.compareAndSet(true, false)) {
            logger.warn("Disconnected from TCP bridge: {}", cause.toString());
            synchronized (stateLock) {
                closeResourcesNoStop();
            }
            // The reconnect loop will pick this up and retry indefinitely
        }
    }

    private void closeResourcesNoStop() {
        try {
            if (outputStream != null) outputStream.close();
        } catch (IOException ignored) {
        } finally {
            outputStream = null;
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        } finally {
            socket = null;
        }
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
