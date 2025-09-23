package vitalconnect.domain;

import java.time.Instant;

/**
 * Processed track representing a single vital sign measurement.
 */
public record ProcessedTrack(
        String name,
        String displayValue,
        Object rawValue,
        String unit,
        Instant timestamp,
        int roomIndex,
        String roomName,
        int trackIndex,
        int recordIndex,
        TrackType type
) {
    /**
     * Type of track data.
     */
    public enum TrackType {
        WAVEFORM,
        NUMBER,
        STRING,
        OTHER
    }
}