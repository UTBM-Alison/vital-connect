package vitalconnect.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import vitalconnect.domain.*;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class VitalDataTransformerTest {

    private VitalDataTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new VitalDataTransformer();
    }

    @Test
    @DisplayName("Should transform VitalData to ProcessedData")
    void testTransformBasicData() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(72.5, 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "hr", "Heart Rate", "num", "bpm", "ECG", "HR", 100.0, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), List.of());
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        assertThat(result.vrCode()).isEqualTo("VR123");
        assertThat(result.rooms()).hasSize(1);
        assertThat(result.allTracks()).hasSize(1);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.name()).isEqualTo("Heart Rate");
        assertThat(processedTrack.displayValue()).isEqualTo("72.500");
        assertThat(processedTrack.unit()).isEqualTo("bpm");
        assertThat(processedTrack.type()).isEqualTo(ProcessedTrack.TrackType.NUMBER);
    }

    @Test
    @DisplayName("Should handle waveform data")
    void testTransformWaveformData() {
        List<Double> waveform = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        VitalData.VitalRecord record = new VitalData.VitalRecord(waveform, 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "ecg", "ECG", "wav", "mV", "ECG", "ECG", 250.0, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), List.of());
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.type()).isEqualTo(ProcessedTrack.TrackType.WAVEFORM);
        assertThat(processedTrack.displayValue()).contains("5 points");
        assertThat(processedTrack.displayValue()).contains("1.000 to 5.000");
        assertThat(processedTrack.displayValue()).contains("avg: 3.000");
    }

    @Test
    @DisplayName("Should handle string data")
    void testTransformStringData() {
        VitalData.VitalRecord record = new VitalData.VitalRecord("Normal", 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "status", "Status", "str", "", "Monitor", "Status", null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), List.of());
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.type()).isEqualTo(ProcessedTrack.TrackType.STRING);
        assertThat(processedTrack.displayValue()).isEqualTo("Normal");
    }

    @Test
    @DisplayName("Should handle multiple rooms and tracks")
    void testTransformMultipleRoomsAndTracks() {
        VitalData.VitalRecord record1 = new VitalData.VitalRecord(72, 1234567890L, null);
        VitalData.VitalRecord record2 = new VitalData.VitalRecord(98, 1234567891L, null);
        VitalData.VitalTrack track1 = new VitalData.VitalTrack(
                "hr", "Heart Rate", "num", "bpm", "ECG", "HR", 100.0, List.of(record1)
        );
        VitalData.VitalTrack track2 = new VitalData.VitalTrack(
                "spo2", "SPO2", "num", "%", "Pulse Ox", "SpO2", null, List.of(record2)
        );

        VitalData.VitalRoom room1 = new VitalData.VitalRoom(1, "Room 1", List.of(track1), List.of());
        VitalData.VitalRoom room2 = new VitalData.VitalRoom(2, "Room 2", List.of(track2), List.of());
        VitalData vitalData = new VitalData("VR123", List.of(room1, room2));

        ProcessedData result = transformer.transform(vitalData);

        assertThat(result.rooms()).hasSize(2);
        assertThat(result.allTracks()).hasSize(2);
        assertThat(result.rooms().get(0).roomName()).isEqualTo("Room 1");
        assertThat(result.rooms().get(1).roomName()).isEqualTo("Room 2");
    }

    @Test
    @DisplayName("Should handle empty rooms list")
    void testTransformEmptyRooms() {
        VitalData vitalData = new VitalData("VR123", List.of());

        ProcessedData result = transformer.transform(vitalData);

        assertThat(result.vrCode()).isEqualTo("VR123");
        assertThat(result.rooms()).isEmpty();
        assertThat(result.allTracks()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null rooms list")
    void testTransformNullRooms() {
        VitalData vitalData = new VitalData("VR123", null);

        ProcessedData result = transformer.transform(vitalData);

        assertThat(result.vrCode()).isEqualTo("VR123");
        assertThat(result.rooms()).isEmpty();
        assertThat(result.allTracks()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null track name")
    void testTransformNullTrackName() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(72, 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                null, null, "num", "bpm", null, null, null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, null, List.of(track), null);
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.name()).isEqualTo("Track-0-0");
        assertThat(result.rooms().get(0).roomName()).isEqualTo("Room 0");
    }

    @Test
    @DisplayName("Should handle integer values")
    void testTransformIntegerValues() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(100, 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "val", "Value", "num", "units", null, null, null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), null);
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.displayValue()).isEqualTo("100");
    }

    @Test
    @DisplayName("Should convert timestamp correctly")
    void testTimestampConversion() {
        long unixTimestamp = 1234567890L;
        VitalData.VitalRecord record = new VitalData.VitalRecord(72, unixTimestamp, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "hr", "Heart Rate", "num", "bpm", null, null, null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), null);
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.timestamp()).isEqualTo(Instant.ofEpochSecond(unixTimestamp));
    }

    @Test
    @DisplayName("Should handle null timestamp")
    void testNullTimestamp() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(72, null, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "hr", "Heart Rate", "num", "bpm", null, null, null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), null);
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.timestamp()).isNotNull();
        assertThat(processedTrack.timestamp()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    @DisplayName("Should handle empty waveform")
    void testEmptyWaveform() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(List.of(), 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "wav", "Waveform", "wav", "mV", null, null, null, List.of(record)
        );
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), null);
        VitalData vitalData = new VitalData("VR123", List.of(room));

        ProcessedData result = transformer.transform(vitalData);

        ProcessedTrack processedTrack = result.allTracks().get(0);
        assertThat(processedTrack.displayValue()).isEqualTo("0 points");
    }
}