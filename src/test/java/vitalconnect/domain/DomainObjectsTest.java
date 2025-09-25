package vitalconnect.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class DomainObjectsTest {

    @Test
    @DisplayName("ProcessedData should be created with factory method")
    void testProcessedDataFactory() {
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of());
        ProcessedData data = ProcessedData.create("VR123", List.of(room), List.of());

        assertThat(data.vrCode()).isEqualTo("VR123");
        assertThat(data.rooms()).hasSize(1);
        assertThat(data.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("ProcessedTrack should store all track information")
    void testProcessedTrack() {
        Instant now = Instant.now();
        ProcessedTrack track = new ProcessedTrack(
                "Temperature", "36.5", 36.5, "°C", now,
                0, "Room 1", 0, 0, ProcessedTrack.TrackType.NUMBER
        );

        assertThat(track.name()).isEqualTo("Temperature");
        assertThat(track.displayValue()).isEqualTo("36.5");
        assertThat(track.rawValue()).isEqualTo(36.5);
        assertThat(track.unit()).isEqualTo("°C");
        assertThat(track.type()).isEqualTo(ProcessedTrack.TrackType.NUMBER);
    }

    @Test
    @DisplayName("VitalData.VitalRecord should handle effective timestamp")
    void testVitalRecordTimestamp() {
        VitalData.VitalRecord record1 = new VitalData.VitalRecord(72, 1234567890L, null);
        assertThat(record1.getEffectiveTimestamp()).isEqualTo(1234567890L);

        VitalData.VitalRecord record2 = new VitalData.VitalRecord(72, null, 9876543210L);
        assertThat(record2.getEffectiveTimestamp()).isEqualTo(9876543210L);

        VitalData.VitalRecord record3 = new VitalData.VitalRecord(72, 1111111111L, 2222222222L);
        assertThat(record3.getEffectiveTimestamp()).isEqualTo(1111111111L);
    }

    @Test
    @DisplayName("ProcessedData.toJSON should serialize with ISO-8601 timestamp and nested content")
    void testProcessedDataToJSON() throws Exception {
        Instant fixed = Instant.parse("2024-01-01T00:00:00Z");
        ProcessedTrack track = new ProcessedTrack(
                "Temperature", "36.5", 36.5, "°C", fixed,
                0, "Room 1", 0, 0, ProcessedTrack.TrackType.NUMBER
        );
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", fixed, List.of(room), List.of(track));

        // Sérialise avec la méthode testée
        String json = data.toJSON();

        // Désérialise avec la même config Jackson que la prod
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        ProcessedData roundTrip = mapper.readValue(json, ProcessedData.class);

        // Assertions robustes (plus de NPE sur des clés absentes)
        assertThat(roundTrip.vrCode()).isEqualTo("VR123");
        assertThat(roundTrip.timestamp()).isEqualTo(fixed);
        assertThat(roundTrip.rooms()).hasSize(1);
        assertThat(roundTrip.allTracks()).hasSize(1);
        assertThat(roundTrip.allTracks().get(0).name()).isEqualTo("Temperature");
    }


    @Test
    @DisplayName("VitalData should handle complex structure")
    void testVitalDataStructure() {
        VitalData.VitalRecord record = new VitalData.VitalRecord(List.of(1, 2, 3), 1234567890L, null);
        VitalData.VitalTrack track = new VitalData.VitalTrack(
                "track1", "Heart Rate", "num", "bpm", "ECG", "HR", 100.0, List.of(record)
        );
        VitalData.VitalEvent event = new VitalData.VitalEvent(1234567890L, "Event occurred");
        VitalData.VitalRoom room = new VitalData.VitalRoom(1, "Room 1", List.of(track), List.of(event));
        VitalData data = new VitalData("VR123", List.of(room));

        assertThat(data.vrCode()).isEqualTo("VR123");
        assertThat(data.rooms()).hasSize(1);
        assertThat(data.rooms().get(0).tracks()).hasSize(1);
        assertThat(data.rooms().get(0).events()).hasSize(1);
    }
}