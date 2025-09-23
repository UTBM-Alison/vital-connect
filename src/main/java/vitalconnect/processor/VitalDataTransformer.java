package vitalconnect.processor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import vitalconnect.domain.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Transforms raw VitalData into ProcessedData format.
 * Matches the TypeScript DataProcessor's transformData method.
 */
public class VitalDataTransformer {
    private static final Logger logger = LoggerFactory.getLogger(VitalDataTransformer.class);

    /**
     * Transform VitalData to ProcessedData with formatted timestamps and organized structure.
     *
     * @param vitalData the raw vital data
     * @return processed data ready for output
     */
    public ProcessedData transform(VitalData vitalData) {
        List<ProcessedRoom> processedRooms = new ArrayList<>();
        List<ProcessedTrack> allTracks = new ArrayList<>();

        if (vitalData.rooms() == null || vitalData.rooms().isEmpty()) {
            return ProcessedData.create(vitalData.vrCode(), processedRooms, allTracks);
        }

        // Process each room
        for (int roomIndex = 0; roomIndex < vitalData.rooms().size(); roomIndex++) {
            VitalData.VitalRoom room = vitalData.rooms().get(roomIndex);
            String roomName = room.roomName() != null ? room.roomName() : "Room " + roomIndex;
            List<ProcessedTrack> roomTracks = new ArrayList<>();

            if (room.tracks() != null) {
                for (int trackIndex = 0; trackIndex < room.tracks().size(); trackIndex++) {
                    VitalData.VitalTrack track = room.tracks().get(trackIndex);

                    if (track.records() != null) {
                        for (int recIndex = 0; recIndex < track.records().size(); recIndex++) {
                            VitalData.VitalRecord record = track.records().get(recIndex);

                            ProcessedTrack processedTrack = processTrack(
                                    track, record, roomIndex, roomName, trackIndex, recIndex
                            );

                            roomTracks.add(processedTrack);
                            allTracks.add(processedTrack);
                        }
                    }
                }
            }

            processedRooms.add(new ProcessedRoom(roomIndex, roomName, roomTracks));
        }

        return ProcessedData.create(vitalData.vrCode(), processedRooms, allTracks);
    }

    /**
     * Process a single track record into ProcessedTrack format.
     */
    private ProcessedTrack processTrack(
            VitalData.VitalTrack track,
            VitalData.VitalRecord record,
            int roomIndex,
            String roomName,
            int trackIndex,
            int recordIndex) {

        Object value = record.value();
        String displayValue;
        ProcessedTrack.TrackType valueType;

        // Determine value type and format display value
        if (value instanceof List<?> list) {
            valueType = ProcessedTrack.TrackType.WAVEFORM;
            displayValue = formatWaveform(list);
        } else if (value instanceof Number) {
            valueType = ProcessedTrack.TrackType.NUMBER;
            displayValue = formatNumber((Number) value);
        } else if (value instanceof String) {
            valueType = ProcessedTrack.TrackType.STRING;
            displayValue = (String) value;
        } else {
            valueType = ProcessedTrack.TrackType.OTHER;
            displayValue = value != null ? value.toString() : "null";
        }

        // Convert Unix timestamp to Instant
        Instant timestamp = convertTimestamp(record.getEffectiveTimestamp());

        // Get track name
        String trackName = track.name() != null ?
                track.name() : String.format("Track-%d-%d", roomIndex, trackIndex);

        return new ProcessedTrack(
                trackName,
                displayValue,
                value,
                track.unit() != null ? track.unit() : "",
                timestamp,
                roomIndex,
                roomName,
                trackIndex,
                recordIndex,
                valueType
        );
    }

    /**
     * Format waveform data for display.
     */
    private String formatWaveform(List<?> waveform) {
        if (waveform.isEmpty()) {
            return "0 points";
        }

        // Convert to doubles for calculation
        List<Double> values = new ArrayList<>();
        for (Object obj : waveform) {
            if (obj instanceof Number) {
                values.add(((Number) obj).doubleValue());
            }
        }

        if (values.isEmpty()) {
            return "0 points";
        }

        double min = values.stream().min(Double::compare).orElse(0.0);
        double max = values.stream().max(Double::compare).orElse(0.0);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        return String.format("%d points (%.3f to %.3f, avg: %.3f)",
                values.size(), min, max, avg);
    }

    /**
     * Format numeric value for display.
     */
    private String formatNumber(Number value) {
        if (value instanceof Double || value instanceof Float) {
            return String.format("%.3f", value.doubleValue());
        }
        return value.toString();
    }

    /**
     * Convert Unix timestamp (seconds) to Instant.
     */
    private Instant convertTimestamp(Long unixTimestamp) {
        if (unixTimestamp == null) {
            return Instant.now();
        }
        // Unix timestamp is in seconds, convert to milliseconds
        return Instant.ofEpochSecond(unixTimestamp);
    }
}