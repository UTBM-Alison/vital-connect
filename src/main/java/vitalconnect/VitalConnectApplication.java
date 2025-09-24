package vitalconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.core.VitalProcessor;
import vitalconnect.input.SocketIOServerInput;
import vitalconnect.output.ConsoleVitalOutput;

import java.util.concurrent.CountDownLatch;

/**
 * Main application class for VitalConnect.
 * Orchestrates the setup and lifecycle of the vital data processing pipeline.
 */
public class VitalConnectApplication {
    private static final Logger logger = LoggerFactory.getLogger(VitalConnectApplication.class);

    private final VitalProcessor processor;
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);
    private final int port;
    private Runnable exitHook = (() -> System.exit(1));

    protected void setExitHook(Runnable exitHook) {
        this.exitHook = exitHook;
    }

    protected void exitApplication(int status) {
        if (exitHook != null) {
            // For testing, we'll use a different approach
            if (status != 1) {
                System.exit(status); // Only call System.exit for non-test statuses
            }
            exitHook.run();
        } else {
            System.exit(status);
        }
    }

    /**
     * Create the application with default configuration.
     */
    public VitalConnectApplication() {
        this("127.0.0.1", 3000, false, true);
    }

    /**
     * Create the application with custom configuration.
     *
     * @param host the server host (not used, server listens on all interfaces)
     * @param port the server port
     * @param verbose verbose console output
     * @param colorized colorized console output
     */
    public VitalConnectApplication(String host, int port, boolean verbose, boolean colorized) {
        this.port = port;

        // Use the new Socket.IO v4 WebSocket server
        SocketIOServerInput input = new SocketIOServerInput(port);

        // Create console output
        ConsoleVitalOutput consoleOutput = new ConsoleVitalOutput(verbose, colorized);

        // Create processor
        this.processor = new VitalProcessor(input, consoleOutput);

        // Setup shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
    }

    protected void exitApplication(int status) {
        System.exit(status);
    }

    /**
     * Start the application.
     */
    public void start() {
        try {
            logger.info("Starting VitalConnect Application...");
            logger.info("════════════════════════════════════════════════════════");
            logger.info("🏥 VitalConnect Java - VitalRecorder Data Listener");
            logger.info("════════════════════════════════════════════════════════");

            processor.start();

            logger.info("✅ Application started successfully");
            logger.info("📊 Configure Vital Recorder: SERVER_IP=127.0.0.1:{}", port);
            logger.info("🔌 Server is listening. Waiting for VitalRecorder connections...");
            logger.info("⏳ Press Ctrl+C to stop the server");
            logger.info("");

            // Keep application running
            try {
                shutdownLatch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.info("Application interrupted");
            }

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            exitApplication(1);
        }
    }

    /**
     * Shutdown the application gracefully.
     */
    public void shutdown() {
        logger.info("");
        logger.info("🛑 Shutting down VitalConnect Application...");

        try {
            processor.stop();

            // Print final statistics
            VitalProcessor.ProcessorStatistics stats = processor.getStatistics();
            logger.info("📊 Final Statistics:");
            logger.info("   • Total data received: {}", stats.getTotalDataReceived());
            logger.info("   • Total rooms processed: {}", stats.getTotalRoomsProcessed());
            logger.info("   • Total tracks processed: {}", stats.getTotalTracksProcessed());
            logger.info("   • Uptime: {} seconds", stats.getUptime() / 1000);

            logger.info("✅ Application shut down successfully");
        } catch (Exception e) {
            logger.error("Error during shutdown", e);
        } finally {
            shutdownLatch.countDown();
        }
    }

    /**
     * Main entry point.
     *
     * @param args command line arguments:
     *             [0] - host (default: 127.0.0.1)
     *             [1] - port (default: 3000)
     *             [2] - verbose (default: false)
     *             [3] - colorized (default: true)
     */
    public static void main(String[] args) {
        // Parse command line arguments
        String host = args.length > 0 ? args[0] : "127.0.0.1";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 3000;
        boolean verbose = args.length > 2 && Boolean.parseBoolean(args[2]);
        boolean colorized = args.length <= 3 || Boolean.parseBoolean(args[3]);

        // Print configuration
        logger.info("Configuration:");
        logger.info("  • Host: {}", host);
        logger.info("  • Port: {}", port);
        logger.info("  • Verbose: {}", verbose);
        logger.info("  • Colorized: {}", colorized);
        logger.info("");

        // Create and start application
        VitalConnectApplication app = new VitalConnectApplication(host, port, verbose, colorized);
        app.start();
    }
}