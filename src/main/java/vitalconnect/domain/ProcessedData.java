package vitalconnect.domain;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

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
    // ObjectMapper partagé, configuré pour les dates Java Time en ISO‑8601
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Convert this record to its JSON representation.
     */
    public String toJSON() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Erreur de sérialisation JSON", e);
        }
    }

    /**
     * Create a new ProcessedData with current timestamp.
     */
    public static ProcessedData create(String vrCode, List<ProcessedRoom> rooms, List<ProcessedTrack> allTracks) {
        return new ProcessedData(vrCode, Instant.now(), rooms, allTracks);
    }
}
