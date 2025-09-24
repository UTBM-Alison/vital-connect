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

    @Mock
    private VitalOutput mockOutput2;

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

    // ============ ADDITIONAL TESTS FOR BETTER COVERAGE ============

    @Test
    @DisplayName("Should not start processor twice")
    void testStartTwice() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();
        processor.start(); // Second call should be ignored

        verify(mockInput, times(1)).start();
        verify(mockOutput, times(1)).initialize();
        assertThat(processor.isRunning()).isTrue();
    }

    @Test
    @DisplayName("Should not stop processor twice")
    void testStopTwice() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();
        processor.stop();
        processor.stop(); // Second call should be ignored

        verify(mockInput, times(1)).stop();
        verify(mockOutput, times(1)).close();
        assertThat(processor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should handle initialization failure")
    void testInitializationFailure() {
        doThrow(new RuntimeException("Init failed")).when(mockOutput).initialize();
        processor = new VitalProcessor(mockInput, mockOutput);

        assertThatThrownBy(() -> processor.start())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to start VitalProcessor");

        assertThat(processor.isRunning()).isFalse();
        verify(mockOutput).close(); // Should cleanup on failure
    }

    @Test
    @DisplayName("Should handle input start failure")
    void testInputStartFailure() {
        doThrow(new RuntimeException("Input start failed")).when(mockInput).start();
        processor = new VitalProcessor(mockInput, mockOutput);

        assertThatThrownBy(() -> processor.start())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to start VitalProcessor");

        assertThat(processor.isRunning()).isFalse();
        verify(mockOutput).close(); // Should cleanup on failure
    }

    @Test
    @DisplayName("Should not process data when stopped")
    void testDataProcessingWhenStopped() {
        processor = new VitalProcessor(mockInput, mockOutput);

        // Get the listener without starting
        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        ProcessedData testData = ProcessedData.create("VR123", List.of(), List.of());
        listenerCaptor.getValue().onDataReceived(testData);

        // Should not send to output when not running
        verify(mockOutput, never()).send(any());
        assertThat(processor.getLastData()).isNull();
    }

    @Test
    @DisplayName("Should handle null output in addOutput")
    void testAddNullOutput() {
        processor = new VitalProcessor(mockInput, mockOutput);

        assertThatThrownBy(() -> processor.addOutput(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("output cannot be null");
    }

    @Test
    @DisplayName("Should not initialize output when adding while stopped")
    void testAddOutputWhenStopped() {
        processor = new VitalProcessor(mockInput, mockOutput);

        VitalOutput newOutput = mock(VitalOutput.class);
        processor.addOutput(newOutput);

        verify(newOutput, never()).initialize();
    }

    @Test
    @DisplayName("Should handle close error when removing output")
    void testRemoveOutputWithCloseError() {
        VitalOutput failingOutput = mock(VitalOutput.class);
        doThrow(new RuntimeException("Close failed")).when(failingOutput).close();

        processor = new VitalProcessor(mockInput, List.of(mockOutput, failingOutput));

        boolean removed = processor.removeOutput(failingOutput);

        assertThat(removed).isTrue();
        verify(failingOutput).close(); // Should still attempt to close
    }

    @Test
    @DisplayName("Should return false when removing non-existent output")
    void testRemoveNonExistentOutput() {
        processor = new VitalProcessor(mockInput, mockOutput);

        VitalOutput nonExistent = mock(VitalOutput.class);
        boolean removed = processor.removeOutput(nonExistent);

        assertThat(removed).isFalse();
        verify(nonExistent, never()).close();
    }

    @Test
    @DisplayName("Should handle exception during stop")
    void testStopWithExceptions() {
        doThrow(new RuntimeException("Stop failed")).when(mockInput).stop();
        doThrow(new RuntimeException("Close failed")).when(mockOutput).close();

        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        // Should not throw, just log errors
        assertThatCode(() -> processor.stop()).doesNotThrowAnyException();
        assertThat(processor.isRunning()).isFalse();
    }

    @Test
    @DisplayName("Should handle multiple outputs with different failures")
    void testMultipleOutputsWithDifferentFailures() {
        VitalOutput failingOutput = mock(VitalOutput.class);
        doThrow(new RuntimeException("Send failed")).when(failingOutput).send(any());

        processor = new VitalProcessor(mockInput, List.of(mockOutput, failingOutput, mockOutput2));
        processor.start();

        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        ProcessedData testData = ProcessedData.create("VR123", List.of(), List.of());
        listenerCaptor.getValue().onDataReceived(testData);

        // Should send to all outputs, even if one fails
        verify(mockOutput).send(testData);
        verify(failingOutput).send(testData);
        verify(mockOutput2).send(testData);
    }

    @Test
    @DisplayName("Should track statistics with multiple rooms and tracks")
    void testStatisticsWithComplexData() {
        processor = new VitalProcessor(mockInput, mockOutput);
        processor.start();

        var listenerCaptor = ArgumentCaptor.forClass(VitalInput.DataListener.class);
        verify(mockInput).setDataListener(listenerCaptor.capture());

        // Create complex data
        ProcessedTrack track1 = new ProcessedTrack("HR", "72", 72.0, "bpm", Instant.now(),
                0, "Room 1", 0, 0, ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track2 = new ProcessedTrack("SPO2", "98", 98.0, "%", Instant.now(),
                0, "Room 1", 1, 0, ProcessedTrack.TrackType.NUMBER);
        ProcessedTrack track3 = new ProcessedTrack("Temp", "36.5", 36.5, "°C", Instant.now(),
                1, "Room 2", 0, 0, ProcessedTrack.TrackType.NUMBER);

        ProcessedRoom room1 = new ProcessedRoom(0, "Room 1", List.of(track1, track2));
        ProcessedRoom room2 = new ProcessedRoom(1, "Room 2", List.of(track3));

        ProcessedData testData = new ProcessedData("VR123", Instant.now(),
                List.of(room1, room2), List.of(track1, track2, track3));

        listenerCaptor.getValue().onDataReceived(testData);

        VitalProcessor.ProcessorStatistics stats = processor.getStatistics();
        assertThat(stats.getTotalRoomsProcessed()).isEqualTo(2);
        assertThat(stats.getTotalTracksProcessed()).isEqualTo(3);
    }

    @Test
    @DisplayName("Should handle statistics when not started")
    void testStatisticsWhenNotStarted() {
        processor = new VitalProcessor(mockInput, mockOutput);

        VitalProcessor.ProcessorStatistics stats = processor.getStatistics();
        assertThat(stats.getUptime()).isEqualTo(0);
        assertThat(stats.getTotalDataReceived()).isEqualTo(0);
        assertThat(stats.getTotalRoomsProcessed()).isEqualTo(0);
        assertThat(stats.getTotalTracksProcessed()).isEqualTo(0);
        assertThat(stats.getLastUpdateTime()).isNull();
    }

    @Test
    @DisplayName("Should handle close exception in closeResources during normal stop")
    void testCloseResourcesException() {
        // Create a mock output that throws exception on close
        VitalOutput failingOutput = mock(VitalOutput.class);
        doThrow(new RuntimeException("Close failed")).when(failingOutput).close();

        processor = new VitalProcessor(mockInput, List.of(mockOutput, failingOutput));
        processor.start();

        // This should trigger closeResources() and hit the catch block
        assertThatCode(() -> processor.stop()).doesNotThrowAnyException();

        verify(failingOutput).close();
        verify(mockOutput).close();
        assertThat(processor.isRunning()).isFalse();
    }
}