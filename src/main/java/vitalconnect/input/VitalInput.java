// File: src/main/java/vitalconnect/input/VitalInput.java
package vitalconnect.input;

import vitalconnect.domain.ProcessedData;

/**
 * Interface for vital data input sources.
 * Implementations should handle connection, data reception, and processing.
 */
public interface VitalInput {
    /**
     * Start receiving data from the input source.
     * @throws RuntimeException if connection fails
     */
    void start();

    /**
     * Stop receiving data and close connections.
     */
    void stop();

    /**
     * Check if the input is currently active.
     * @return true if receiving data, false otherwise
     */
    boolean isRunning();

    /**
     * Set a listener for processed data.
     * @param listener the data listener
     */
    void setDataListener(DataListener listener);

    /**
     * Functional interface for data reception callbacks.
     */
    @FunctionalInterface
    interface DataListener {
        void onDataReceived(ProcessedData data);
    }
}