package vitalconnect.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import vitalconnect.domain.ProcessedData;
import vitalconnect.domain.ProcessedRoom;
import vitalconnect.domain.ProcessedTrack;
import vitalconnect.input.VitalInput;
import vitalconnect.output.VitalOutput;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VitalProcessorTest {

    @Mock
    private VitalInput mockInput;

    @Mock
    private VitalOutput mockOutput;

    private VitalProcessor processor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("Should create processor with single output")
    void testConstructorWithSingleOutput() {
        processor = new VitalProcessor(mockInput, mockOutput);
        assertThat(processor).isNotNull();
        verify(mockInput).setDataListener(any());
    }

    @Test
    @DisplayName("Should create processor with multiple outputs")
    void testConstructorWithMultipleOutputs() {
        VitalOutput mockOutput2 = mock(VitalOutput.class);
        processor = new VitalProcessor(mockInput, List.of(mockOutput, mockOutput2));
        assertThat(processor).isNotNull();
    }

    @Test
    @DisplayName("Should throw exception for null input")
    void testConstructorWithNullInput() {
        assertThatThrownBy(() -> new VitalProcessor(null, mockOutput))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("input cannot be null");
    }

    @Test
    @DisplayName("Should throw exception for empty outputs")
    void testConstructorWithEmptyOutputs() {
        assertThatThrownBy(() -> new VitalProcessor(mockInput, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("At least one output is required");
    }

    @Test
    @DisplayName("Should start processor successfully")
    void testStart() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        verify(mockOutput).initialize();
        verify(mockInput).start();
        assertThat(processor.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Should stop processor successfully")
    void testStop() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();
        processor.stop();

        verify(mockInput).stop();
        verify(mockOutput).close();
        assertThat(processor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should handle data processing")
    void testDataProcessing() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        // Capture the data listener
        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        // Create test data
        ProcessedTrack track = new ProcessedTrack(
                "Heart Rate", "72", 72.0, "bpm", Instant.now(),
                0, "Room 1", 0, 0, ProcessedTrack.TrackType.NUMBER
        );
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData testData = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        // Trigger data listener
        listenerCaptor.getValue().onDataReceived(testData);

        verify(mockOutput).send(testData);
        assertThat(processor.getLastData()).isEqualTo(testData);
    }

    @Test
    @DisplayName("Should add output dynamically")
    void testAddOutput() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        VitalOutput newOutput = mock(VitalOutput.class);
        processor.addOutput(newOutput);

        verify(newOutput).initialize();
    }

    @Test
    @DisplayName("Should remove output dynamically")
    void testRemoveOutput() {
        VitalOutput mockOutput2 = mock(VitalOutput.class);
        processor = new VitalProcessor(mockInput, List.of(mockOutput, mockOutput2));

        boolean removed = processor.removeOutput(mockOutput2);

        assertThat(removed).isTrue();
        verify(mockOutput2).close();
    }

    @Test
    @DisplayName("Should handle output errors gracefully")
    void testOutputError() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        doThrow(new RuntimeException("Output error")).when(mockOutput).send(any());

        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        ProcessedData testData = ProcessedData.create("VR123", List.of(), List.of());
        listenerCaptor.getValue().onDataReceived(testData);

        // Should not throw exception
        assertThat(processor.getStatistics().getTotalDataReceived()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should track statistics correctly")
    void testStatistics() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        ProcessedTrack track = new ProcessedTrack(
                "SPO2", "98", 98.0, "%", Instant.now(),
                0, "Room 1", 0, 0, ProcessedTrack.TrackType.NUMBER
        );
        ProcessedRoom room = new ProcessedRoom(0, "Room 1", List.of(track));
        ProcessedData testData = new ProcessedData("VR123", Instant.now(), List.of(room), List.of(track));

        listenerCaptor.getValue().onDataReceived(testData);

        VitalProcessor.ProcessorStatistics stats = processor.getStatistics();
        assertThat(stats.getTotalDataReceived()).isEqualTo(1);
        assertThat(stats.getTotalRoomsProcessed()).isEqualTo(1);
        assertThat(stats.getTotalTracksProcessed()).isEqualTo(1);
        assertThat(stats.getUptime()).isGreaterThan(0);
        assertThat(stats.toString()).contains("dataReceived=1");
    }
}