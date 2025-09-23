package vitalconnect.output;

import vitalconnect.domain.ProcessedData;

/**
 * Interface for vital data output destinations.
 * Implementations should handle data formatting and delivery to specific outputs.
 */
public interface VitalOutput {
    /**
     * Send processed vital data to the output destination.
     *
     * @param data the processed data to send
     * @throws RuntimeException if sending fails
     */
    void send(ProcessedData data);

    /**
     * Initialize the output if needed.
     * Called before first use.
     */
    default void initialize() {
        // Default no-op implementation
    }

    /**
     * Close and cleanup resources.
     * Should be idempotent.
     */
    void close();
}