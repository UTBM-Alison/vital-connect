package vitalconnect.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Raw VitalRecorder data structure matching the TypeScript VitalData interface.
 * This represents the unprocessed data received from the Socket.IO connection.
 */
public record VitalData(
        @JsonProperty("vrcode") String vrCode,
        @JsonProperty("rooms") List<VitalRoom> rooms
) {
    /**
     * Room data containing tracks and events.
     */
    public record VitalRoom(
            @JsonProperty("seqid") Integer seqId,
            @JsonProperty("roomname") String roomName,
            @JsonProperty("trks") List<VitalTrack> tracks,
            @JsonProperty("evts") List<VitalEvent> events
    ) {}

    /**
     * Track data representing a vital sign measurement channel.
     */
    public record VitalTrack(
            @JsonProperty("id") String id,
            @JsonProperty("name") String name,
            @JsonProperty("type") String type,  // "wav", "num", "str"
            @JsonProperty("unit") String unit,
            @JsonProperty("montype") String monType,
            @JsonProperty("dname") String displayName,
            @JsonProperty("srate") Double sampleRate,
            @JsonProperty("recs") List<VitalRecord> records
    ) {}

    /**
     * Individual measurement record.
     */
    public record VitalRecord(
            @JsonProperty("val") Object value,  // Can be number, array of numbers, or string
            @JsonProperty("dt") Long timestamp,  // Unix timestamp
            @JsonProperty("time") Long time
    ) {
        /**
         * Get the effective timestamp (dt or time).
         */
        public Long getEffectiveTimestamp() {
            return timestamp != null ? timestamp : time;
        }
    }

    /**
     * Event data.
     */
    public record VitalEvent(
            @JsonProperty("dt") Long timestamp,
            @JsonProperty("val") String value
    ) {}
}
