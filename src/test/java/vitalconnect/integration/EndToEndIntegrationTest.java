package vitalconnect.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import vitalconnect.core.VitalProcessor;
import vitalconnect.domain.*;
import vitalconnect.input.VitalInput;
import vitalconnect.output.VitalOutput;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

class EndToEndIntegrationTest {

    @Test
    @DisplayName("Should process data through entire pipeline")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testEndToEndDataFlow() throws Exception {
        CountDownLatch outputReceived = new CountDownLatch(1);
        AtomicReference<ProcessedData> outputData = new AtomicReference<>();

        // Create test input
        TestInput input = new TestInput();

        // Create test output
        TestOutput output = new TestOutput(data -> {
            outputData.set(data);
            outputReceived.countDown();
        });

        // Create processor
        VitalProcessor processor = new VitalProcessor(input, output);
        processor.start();

        // Create test data
        ProcessedTrack track = new ProcessedTrack(
                "Test Track", "100", 100.0, "units", Instant.now(),
                0, "Test Room", 0, 0, ProcessedTrack.TrackType.NUMBER
        );
        ProcessedRoom room = new ProcessedRoom(0, "Test Room", List.of(track));
        ProcessedData testData = new ProcessedData("VR999", Instant.now(), List.of(room), List.of(track));

        // Send data through pipeline
        input.sendData(testData);

        // Verify output received
        assertThat(outputReceived.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(outputData.get()).isNotNull();
        assertThat(outputData.get().vrCode()).isEqualTo("VR999");
        assertThat(outputData.get().allTracks()).hasSize(1);

        processor.stop();
    }

    @Test
    @DisplayName("Should handle multiple outputs")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testMultipleOutputs() throws Exception {
        CountDownLatch allOutputsReceived = new CountDownLatch(3);

        TestInput input = new TestInput();

        TestOutput output1 = new TestOutput(data -> allOutputsReceived.countDown());
        TestOutput output2 = new TestOutput(data -> allOutputsReceived.countDown());
        TestOutput output3 = new TestOutput(data -> allOutputsReceived.countDown());

        VitalProcessor processor = new VitalProcessor(input, List.of(output1, output2, output3));
        processor.start();

        ProcessedData testData = ProcessedData.create("VR888", List.of(), List.of());
        input.sendData(testData);

        assertThat(allOutputsReceived.await(2, TimeUnit.SECONDS)).isTrue();

        processor.stop();
    }

    @Test
    @DisplayName("Should recover from output errors")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testOutputErrorRecovery() throws Exception {
        CountDownLatch goodOutputReceived = new CountDownLatch(2);

        TestInput input = new TestInput();

        // Create failing output
        TestOutput failingOutput = new TestOutput(data -> {
            throw new RuntimeException("Output failed!");
        });

        // Create working output
        TestOutput workingOutput = new TestOutput(data -> goodOutputReceived.countDown());

        VitalProcessor processor = new VitalProcessor(input, List.of(failingOutput, workingOutput));
        processor.start();

        // Send two messages
        ProcessedData testData1 = ProcessedData.create("VR777", List.of(), List.of());
        ProcessedData testData2 = ProcessedData.create("VR778", List.of(), List.of());

        input.sendData(testData1);
        input.sendData(testData2);

        // Working output should receive both messages despite failing output
        assertThat(goodOutputReceived.await(2, TimeUnit.SECONDS)).isTrue();

        processor.stop();
    }

    // Test helper classes
    static class TestInput implements VitalInput {
        private DataListener listener;
        private boolean running = false;

        @Override
        public void start() {
            running = true;
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void setDataListener(DataListener listener) {
            this.listener = listener;
        }

        public void sendData(ProcessedData data) {
            if (listener != null) {
                listener.onDataReceived(data);
            }
        }
    }

    static class TestOutput implements VitalOutput {
        private final java.util.function.Consumer<ProcessedData> onReceive;

        TestOutput(java.util.function.Consumer<ProcessedData> onReceive) {
            this.onReceive = onReceive;
        }

        @Override
        public void send(ProcessedData data) {
            onReceive.accept(data);
        }

        @Override
        public void close() {}
    }
}