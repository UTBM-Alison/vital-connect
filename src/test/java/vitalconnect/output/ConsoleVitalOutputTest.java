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
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

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
    @DisplayName("Should print verbose output with vrCode")
    void testVerboseOutputWithVrCode() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Heart Rate", "72", 72.0, "bpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("VITAL SIGNS UPDATE");
        assertThat(output).contains("VR Code:");
        assertThat(output).contains("VR123");
        assertThat(output).contains("Room 1");
        assertThat(output).contains("Heart Rate");
        assertThat(output).contains("72");
        assertThat(output).contains("bpm");
    }

    @Test
    @DisplayName("Should print verbose output without vrCode")
    void testVerboseOutputWithoutVrCode() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Test", "value", "value", "unit", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData(null, Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("VITAL SIGNS UPDATE");
        assertThat(output).doesNotContain("VR Code:");
        assertThat(output).contains("Test");
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
    @DisplayName("Should handle waveform tracks with proper icon")
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
        assertThat(output).contains("\ud83d\udcca"); // Waveform icon
    }

    @Test
    @DisplayName("Should handle number tracks with proper icon")
    void testNumberTrack() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Heart Rate", "72", 72.0, "bpm", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("Heart Rate");
        assertThat(output).contains("\ud83d\udd22"); // Number icon
    }

    @Test
    @DisplayName("Should handle string tracks with proper icon")
    void testStringTrack() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Status", "Normal", "Normal", "", ProcessedTrack.TrackType.STRING);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("Status");
        assertThat(output).contains("Normal");
        assertThat(output).contains("\ud83d\udcdd"); // String icon
    }

    @Test
    @DisplayName("Should handle OTHER track type with default icon")
    void testOtherTrackType() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedTrack track = createTestTrack("Unknown", "data", "data", "unit", ProcessedTrack.TrackType.OTHER);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("Unknown");
        assertThat(output).contains("data");
        assertThat(output).contains("\ud83d\udccc"); // Default icon for OTHER type
    }

    @Test
    @DisplayName("Should handle exception when processing track with null type")
    void testNullTrackTypeException() {
        output = new ConsoleVitalOutput(true, false);

        // Create a track with null type which will cause NPE in the switch
        ProcessedTrack track = createTestTrack("NullType", "value", "value", "unit", null);
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        output.send(data);

        // The NPE should be caught by the exception handler
        String error = errContent.toString();
        assertThat(error).contains("Console output error");
    }

    @Test
    @DisplayName("Should skip rooms with empty tracks in verbose mode")
    void testEmptyRoomTracksVerbose() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedRoom emptyRoom = new ProcessedRoom(0, "Empty Room", List.of());
        ProcessedTrack track = createTestTrack("Test", "value", "value", "unit", ProcessedTrack.TrackType.NUMBER);
        ProcessedRoom roomWithTracks = new ProcessedRoom(1, "Room With Tracks", List.of(track));

        ProcessedData data = new ProcessedData("VR123", Instant.now(),
                List.of(emptyRoom, roomWithTracks), List.of(track));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).doesNotContain("Empty Room");
        assertThat(output).contains("Room With Tracks");
    }

    @Test
    @DisplayName("Should handle all rooms with empty tracks")
    void testAllRoomsEmpty() {
        output = new ConsoleVitalOutput(true, false);

        ProcessedRoom emptyRoom1 = new ProcessedRoom(0, "Empty Room 1", List.of());
        ProcessedRoom emptyRoom2 = new ProcessedRoom(1, "Empty Room 2", List.of());

        ProcessedData data = new ProcessedData("VR123", Instant.now(),
                List.of(emptyRoom1, emptyRoom2), List.of());

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("VITAL SIGNS UPDATE");
        assertThat(output).doesNotContain("Empty Room");
    }

    @Test
    @DisplayName("Should handle more than 5 tracks in compact mode")
    void testMultipleTracksCompact() {
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
    @DisplayName("Should handle exactly 5 tracks in compact mode")
    void testExactlyFiveTracksCompact() {
        output = new ConsoleVitalOutput(false, false);

        List<ProcessedTrack> tracks = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            tracks.add(createTestTrack("Track" + i, String.valueOf(i), i, "unit", ProcessedTrack.TrackType.NUMBER));
        }

        ProcessedRoom room = new ProcessedRoom(0, "Room 1", tracks);
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room), tracks);

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("5 tracks");
        assertThat(output).contains("Track1");
        assertThat(output).contains("Track5");
        assertThat(output).doesNotContain("... and");
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
    @DisplayName("Should handle exception in send method")
    void testSendException() {
        output = new ConsoleVitalOutput(true, false);

        // Create a mock ProcessedData that throws exception
        ProcessedData mockData = mock(ProcessedData.class);
        when(mockData.timestamp()).thenThrow(new RuntimeException("Test exception"));

        output.send(mockData);

        String error = errContent.toString();
        assertThat(error).contains("Console output error: Test exception");
    }

    @Test
    @DisplayName("Should handle output stream errors")
    void testOutputStreamError() {
        output = new ConsoleVitalOutput();

        // Create a PrintStream that throws exception on write
        PrintStream errorStream = new PrintStream(new OutputStream() {
            @Override
            public void write(int b) throws IOException {
                throw new IOException("Stream closed");
            }
        });

        System.setOut(errorStream);

        ProcessedData data = ProcessedData.create("VR123", List.of(), List.of());
        output.send(data);

        String error = errContent.toString();
        assertThat(error).contains("Console output error: write failure");
    }

    @Test
    @DisplayName("Should close without errors")
    void testClose() {
        output = new ConsoleVitalOutput();
        assertThatCode(() -> output.close()).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Should test all track type icons in verbose mode")
    void testAllTrackTypeIcons() {
        output = new ConsoleVitalOutput(true, false);

        // Test all enum values
        ProcessedTrack waveformTrack = createTestTrack("Wave", "data", List.of(1.0, 2.0), "unit",
                ProcessedTrack.TrackType.WAVEFORM);
        ProcessedTrack numberTrack = createTestTrack("Number", "123", 123.0, "unit",
                ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack stringTrack = createTestTrack("String", "text", "text", "unit",
                ProcessedTrack.TrackType.STRING);
        ProcessedTrack otherTrack = createTestTrack("Other", "data", "data", "unit",
                ProcessedTrack.TrackType.OTHER);

        ProcessedRoom room = new ProcessedRoom(0, "Room 1",
                List.of(waveformTrack, numberTrack, stringTrack, otherTrack));
        ProcessedData data = new ProcessedData("VR123", Instant.now(), List.of(room),
                List.of(waveformTrack, numberTrack, stringTrack, otherTrack));

        output.send(data);

        String output = outContent.toString();
        assertThat(output).contains("\ud83d\udcca"); // Waveform icon
        assertThat(output).contains("\ud83d\udd22"); // Number icon
        assertThat(output).contains("\ud83d\udcdd"); // String icon
        assertThat(output).contains("\ud83d\udccc"); // Other/default icon
    }

    private ProcessedTrack createTestTrack(String name, String displayValue, Object rawValue,
                                           String unit, ProcessedTrack.TrackType type) {
        return new ProcessedTrack(name, displayValue, rawValue, unit, Instant.now(),
                0, "Room 1", 0, 0, type);
    }
}