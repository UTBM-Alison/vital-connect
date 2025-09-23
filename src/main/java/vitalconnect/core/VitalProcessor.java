package vitalconnect.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.domain.ProcessedData;
import vitalconnect.input.VitalInput;
import vitalconnect.output.VitalOutput;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Core processor that orchestrates data flow from input to outputs.
 * Manages the lifecycle and coordination between VitalInput and VitalOutputs.
 * This class follows the Single Responsibility Principle by focusing only on orchestration.
 */
public class VitalProcessor {
    private static final Logger logger = LoggerFactory.getLogger(VitalProcessor.class);

    private final VitalInput input;
    private final List<VitalOutput> outputs;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<ProcessedData> lastData = new AtomicReference<>();
    private final ProcessorStatistics statistics = new ProcessorStatistics();

    /**
     * Create a VitalProcessor with single output.
     *
     * @param input the vital data input source
     * @param output the vital data output destination
     */
    public VitalProcessor(VitalInput input, VitalOutput output) {
        this(input, List.of(output));
    }

    /**
     * Create a VitalProcessor with multiple outputs.
     *
     * @param input the vital data input source
     * @param outputs the list of vital data output destinations
     */
    public VitalProcessor(VitalInput input, List<VitalOutput> outputs) {
        this.input = Objects.requireNonNull(input, "input cannot be null");
        this.outputs = new CopyOnWriteArrayList<>(
                Objects.requireNonNull(outputs, "outputs cannot be null"));

        if (outputs.isEmpty()) {
            throw new IllegalArgumentException("At least one output is required");
        }

        // Set up the data listener
        this.input.setDataListener(this::handleProcessedData);
    }

    /**
     * Start the processor.
     * Initializes outputs and starts the input source.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            try {
                // Initialize all outputs
                initializeOutputs();

                // Start the input
                input.start();

                statistics.recordStart();
                logger.info("VitalProcessor started with {} output(s)", outputs.size());
            } catch (Exception e) {
                running.set(false);
                closeResources();
                throw new RuntimeException("Failed to start VitalProcessor", e);
            }
        }
    }

    /**
     * Stop the processor.
     * Stops the input source and closes all outputs.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            try {
                // Stop the input
                input.stop();

                // Close all outputs
                closeResources();

                statistics.recordStop();
                logger.info("VitalProcessor stopped. Statistics: {}", statistics);
            } catch (Exception e) {
                logger.error("Error during VitalProcessor shutdown", e);
            }
        }
    }

    /**
     * Check if the processor is running.
     *
     * @return true if running, false otherwise
     */
    public boolean isRunning() {
        return running.get();
    }

    /**
     * Get the last processed data.
     *
     * @return the last processed data or null if no data has been processed
     */
    public ProcessedData getLastData() {
        return lastData.get();
    }

    /**
     * Get processor statistics.
     *
     * @return statistics about processing
     */
    public ProcessorStatistics getStatistics() {
        return statistics;
    }

    /**
     * Add a new output dynamically.
     *
     * @param output the output to add
     */
    public void addOutput(VitalOutput output) {
        Objects.requireNonNull(output, "output cannot be null");
        if (running.get()) {
            output.initialize();
        }
        outputs.add(output);
        logger.info("Added new output: {}", output.getClass().getSimpleName());
    }

    /**
     * Remove an output dynamically.
     *
     * @param output the output to remove
     * @return true if removed, false if not found
     */
    public boolean removeOutput(VitalOutput output) {
        boolean removed = outputs.remove(output);
        if (removed) {
            try {
                output.close();
            } catch (Exception e) {
                logger.warn("Error closing removed output", e);
            }
            logger.info("Removed output: {}", output.getClass().getSimpleName());
        }
        return removed;
    }

    /**
     * Handle processed data from the input.
     * This method is called by the VitalInput when data is received.
     */
    private void handleProcessedData(ProcessedData data) {
        if (!running.get()) {
            return;
        }

        lastData.set(data);
        statistics.recordDataReceived(data);

        // Distribute to all outputs
        distributeToOutputs(data);
    }

    /**
     * Distribute data to all configured outputs.
     */
    private void distributeToOutputs(ProcessedData data) {
        for (VitalOutput output : outputs) {
            try {
                output.send(data);
                statistics.recordOutputSuccess(output);
            } catch (Exception e) {
                statistics.recordOutputError(output);
                logger.error("Error sending to output: {}",
                        output.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Initialize all outputs.
     */
    private void initializeOutputs() {
        for (VitalOutput output : outputs) {
            try {
                output.initialize();
                logger.debug("Initialized output: {}", output.getClass().getSimpleName());
            } catch (Exception e) {
                logger.error("Failed to initialize output: {}",
                        output.getClass().getSimpleName(), e);
                throw new RuntimeException("Failed to initialize output", e);
            }
        }
    }

    /**
     * Close all output resources.
     */
    private void closeResources() {
        for (VitalOutput output : outputs) {
            try {
                output.close();
            } catch (Exception e) {
                logger.warn("Error closing output: {}",
                        output.getClass().getSimpleName(), e);
            }
        }
    }

    /**
     * Statistics tracking for the processor.
     */
    public static class ProcessorStatistics {
        private long startTime;
        private long stopTime;
        private long startNano;
        private long totalDataReceived;
        private long totalRoomsProcessed;
        private long totalTracksProcessed;
        private final AtomicReference<String> lastUpdateTime = new AtomicReference<>();

        void recordStart() {
            startTime = System.currentTimeMillis();
            startNano = System.nanoTime();
        }

        void recordStop() {
            stopTime = System.currentTimeMillis();
        }

        void recordDataReceived(ProcessedData data) {
            totalDataReceived++;
            totalRoomsProcessed += data.rooms().size();
            totalTracksProcessed += data.allTracks().size();
            lastUpdateTime.set(data.timestamp().toString());
        }

        void recordOutputSuccess(VitalOutput output) {
            // Could track per-output statistics here
        }

        void recordOutputError(VitalOutput output) {
            // Could track per-output error statistics here
        }

        public long getUptime() {
            if (startTime == 0) return 0;
            if (stopTime > 0) {
                return stopTime - startTime;
            }
            long elapsedMs = (System.nanoTime() - startNano) / 1_000_000L;
            return elapsedMs > 0 ? elapsedMs : 1L;
        }

        public long getTotalDataReceived() {
            return totalDataReceived;
        }

        public long getTotalRoomsProcessed() {
            return totalRoomsProcessed;
        }

        public long getTotalTracksProcessed() {
            return totalTracksProcessed;
        }

        public String getLastUpdateTime() {
            return lastUpdateTime.get();
        }

        @Override
        public String toString() {
            return String.format("ProcessorStatistics{uptime=%dms, dataReceived=%d, " +
                            "roomsProcessed=%d, tracksProcessed=%d, lastUpdate=%s}",
                    getUptime(), totalDataReceived, totalRoomsProcessed,
                    totalTracksProcessed, lastUpdateTime.get());
        }
    }
}