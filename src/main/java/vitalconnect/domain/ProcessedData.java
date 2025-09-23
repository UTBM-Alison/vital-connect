package vitalconnect.domain;

import java.time.Instant;
import java.util.List;

/**
 * Processed and transformed vital data ready for output.
 * This structure matches the TypeScript ProcessedData interface.
 */
public record ProcessedData(
        String vrCode,
        Instant timestamp,
        List<ProcessedRoom> rooms,
        List<ProcessedTrack> allTracks
) {
    /**
     * Create a new ProcessedData with current timestamp.
     */
    public static ProcessedData create(String vrCode, List<ProcessedRoom> rooms, List<ProcessedTrack> allTracks) {
        return new ProcessedData(vrCode, Instant.now(), rooms, allTracks);
    }
}