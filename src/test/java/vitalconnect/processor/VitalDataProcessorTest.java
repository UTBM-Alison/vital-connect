package vitalconnect.processor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import vitalconnect.domain.VitalData;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

class VitalDataProcessorTest {

    private VitalDataProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new VitalDataProcessor();
    }

    @Test
    @DisplayName("Should process valid JSON data")
    void testProcessValidJson() {
        String json = """
            {
                "vrcode": "VR123",
                "rooms": [
                    {
                        "roomname": "Room 1",
                        "trks": [
                            {
                                "id": "track1",
                                "name": "Heart Rate",
                                "type": "num",
                                "unit": "bpm",
                                "recs": [
                                    {"val": 72, "dt": 1234567890}
                                ]
                            }
                        ],
                        "evts": []
                    }
                ]
            }
            """;

        VitalData result = processor.process(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.vrCode()).isEqualTo("VR123");
        assertThat(result.rooms()).hasSize(1);
        assertThat(result.rooms().get(0).roomName()).isEqualTo("Room 1");
        assertThat(result.rooms().get(0).tracks()).hasSize(1);
        assertThat(result.rooms().get(0).tracks().get(0).name()).isEqualTo("Heart Rate");
    }

    @Test
    @DisplayName("Should clean JSON string with control characters")
    void testCleanJsonWithControlCharacters() {
        String dirtyJson = "{\"test\":\u0000\"value\u001F\",\"vrcode\":\"VR123\",\"rooms\":[]}";

        VitalData result = processor.process(dirtyJson.getBytes(StandardCharsets.UTF_8));

        assertThat(result.vrCode()).isEqualTo("VR123");
    }

    @Test
    @DisplayName("Should replace NaN and Infinity with null")
    void testReplaceNaNAndInfinity() {
        String json = """
            {
                "vrcode": "VR123",
                "rooms": [{
                    "trks": [{
                        "recs": [
                            {"val": NaN, "dt": 1234567890},
                            {"val": Infinity, "dt": 1234567891},
                            {"val": -Infinity, "dt": 1234567892}
                        ]
                    }]
                }]
            }
            """;

        VitalData result = processor.process(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.rooms().get(0).tracks().get(0).records()).hasSize(3);
        // Values should be null after processing
        assertThat(result.rooms().get(0).tracks().get(0).records().get(0).value()).isNull();
    }

    @Test
    @DisplayName("Should fix decimal separators")
    void testFixDecimalSeparators() {
        String json = """
            {
                "vrcode": "VR123",
                "rooms": [{
                    "trks": [{
                        "recs": [
                            {"val": 123,456, "dt": 1234567890}
                        ]
                    }]
                }]
            }
            """;

        VitalData result = processor.process(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result.rooms().get(0).tracks().get(0).records().get(0).value()).isEqualTo(123.456);
    }

    @Test
    @DisplayName("Should handle arrays with decimal separator issues")
    void testArrayDecimalSeparators() {
        String json = """
            {
                "vrcode": "VR123",
                "rooms": [{
                    "trks": [{
                        "recs": [
                            {"val": [123,456, 789,012], "dt": 1234567890}
                        ]
                    }]
                }]
            }
            """;

        VitalData result = processor.process(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.rooms()).hasSize(1);
    }

    @Test
    @DisplayName("Should throw exception for invalid JSON")
    void testInvalidJson() {
        String invalidJson = "{ this is not valid JSON }";

        assertThatThrownBy(() -> processor.process(invalidJson.getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Data processing failed");
    }

    @Test
    @DisplayName("Should handle missing optional fields")
    void testMissingOptionalFields() {
        String json = """
            {
                "vrcode": "VR123",
                "rooms": [
                    {
                        "trks": [
                            {
                                "recs": [
                                    {"val": 72}
                                ]
                            }
                        ]
                    }
                ]
            }
            """;

        VitalData result = processor.process(json.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isNotNull();
        assertThat(result.rooms().get(0).roomName()).isNull();
        assertThat(result.rooms().get(0).tracks().get(0).name()).isNull();
    }
}