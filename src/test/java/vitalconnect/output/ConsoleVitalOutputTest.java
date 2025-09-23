package vitalconnect.output;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import vitalconnect.domain.ProcessedData;
import vitalconnect.domain.ProcessedRoom;
import vitalconnect.domain.ProcessedTrack;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ConsoleVitalOutputTest {

    private ConsoleVitalOutput output;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
        System.setErr(originalErr);
    }

    @Test
    @DisplayName("Should create with default configuration")
    void testDefaultConstructor() {
        output = new ConsoleVitalOutput();
        assertThat(output).isNotNull();
    }

    @Test
    @DisplayName("Should create with custom configuration")
    void testCustomConstructor() {
        output = new ConsoleVitalOutput(true, false);
        assertThat(output).isNotNull();
    }

    @Test
    @DisplayName("Should print verbose output")
    void testVerboseOutput() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Heart Rate", "72", 72.0, "bpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("VITAL SIGNS UPDATE");
        assertThat(output).contains("VR123");
        assertThat(output).contains("Room 1");
        assertThat(output).contains("Heart Rate");
        assertThat(output).contains("72");
        assertThat(output).contains("bpm");
    }

    @Test
    @DisplayName("Should print compact output")
    void testCompactOutput() {
        output = new ConsoleVitalOutput(false, false);

        ProcessedTrack track = createTestTrack("SPO2", "98", 98.0, "%", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("Vital Update");
        assertThat(output).contains("1 tracks");
        assertThat(output).contains("SPO2");
        assertThat(output).contains("98 %");
    }

    @Test
    @DisplayName("Should handle waveform tracks")
    void testWaveformTrack() {
        output = new ConsoleVitalOutput(true, false);

        List<Double> waveform = List.of(1.0, 2.0, 3.0, 4.0, 5.0);
        ProcessedTrack track = createTestTrack("ECG", "5 points (1.000 to 5.000, avg: 3.000)",
                waveform, "mV", ProcessedTrack.TrackType.WAVEFORM);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("ECG");
        assertThat(output).contains("5 points");
    }

    @Test
    @DisplayName("Should handle string tracks")
    void testStringTrack() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Status", "Normal", "Normal", "", ProcessedTrack.TrackType.STRING);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("Status");
        assertThat(output).contains("Normal");
    }

    @Test
    @DisplayName("Should handle multiple rooms and tracks")
    void testMultipleRoomsAndTracks() {
        output = new ConsoleVitalOutput(false, false);

        ProcessedTrack track1 = createTestTrack("HR", "72", 72.0, "bpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track2 = createTestTrack("SPO2", "98", 98.0, "%", ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track3 = createTestTrack("Temp", "36.5", 36.5, "°C", ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track4 = createTestTrack("BP", "120/80", "120/80", "mmHg", ProcessedTrack.TrackType.STRING);
        ProcessedTrack track5 = createTestTrack("RR", "16", 16.0, "rpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track6 = createTestTrack("Status", "Stable", "Stable", "", ProcessedTrack.TrackType.STRING);

        ProcessedRoom room1 = new ProcessedRoom(0, "Room 1", List.of(track1, track2, track3));
        ProcessedRoom room2 = new ProcessedRoom(1, "Room 2", List.of(track4, track5, track6));
        ProcessedData data = new ProcessedData("VR123", Instant.now(),
                List.of(room1, room2),
                List.of(track1, track2, track3, track4, track5, track6));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("6 tracks");
        assertThat(output).contains("... and 1 more tracks");
    }

    @Test
    @DisplayName("Should handle colorized output")
    void testColorizedOutput() {
        output = new ConsoleVitalOutput(true, true);

        ProcessedTrack track = createTestTrack("Heart Rate", "72", 72.0, "bpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("\u001B["); // ANSI color codes
    }

    @Test
    @DisplayName("Should handle output errors gracefully")
    void testOutputError() {
        output = new ConsoleVitalOutput();

        // Save original System.out
        PrintStream originalOut = System.out;

        // Create a PrintStream that throws exception on write
        PrintStream errorStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Stream closed");
            }
        });

        System.setOut(errorStream);

        try {
            ProcessedData data = ProcessedData.create("VR123", List.of(), List.of());
            output.send(data); // Should not throw exception

            // Check error was logged to System.err
            String error = errContent.toString();
            assertThat(error).contains("Console output error");
        } finally {
            // Restore original System.out
            System.setOut(originalOut);
        }
    }

    @Test
    @DisplayName("Should close without errors")
    void testClose() {
        output = new ConsoleVitalOutput();
        assertThatCode(() -> output.close()).doesNotThrowAnyException();
    }

    private ProcessedTrack createTestTrack(String name, String displayValue, Object rawValue,
                                           String unit, ProcessedTrack.TrackType type) {
        return new ProcessedTrack(name, displayValue, rawValue, unit, Instant.now(),
                0, "Room 1", 0, 0, type);
    }
}