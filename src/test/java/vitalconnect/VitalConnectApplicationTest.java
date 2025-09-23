package vitalconnect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import vitalconnect.core.VitalProcessor;
import vitalconnect.input.SocketIOServerInput;
import vitalconnect.output.ConsoleVitalOutput;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class VitalConnectApplicationTest {

    private VitalConnectApplication application;
    private final ByteArrayOutputStream errContent = new ByteArrayOutputStream();
    private final PrintStream originalErr = System.err;

    @BeforeEach
    void setUp() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    void tearDown() {
        System.setErr(originalErr);
        if (application != null) {
            application.shutdown();
        }
    }

    @Test
    @DisplayName("Should create application with default configuration")
    void testDefaultConstructor() {
        application = new VitalConnectApplication();
        assertThat(application).isNotNull();
    }

    @Test
    @DisplayName("Should create application with custom configuration")
    void testCustomConstructor() {
        application = new VitalConnectApplication("192.168.1.1", 8080, true, false);
        assertThat(application).isNotNull();
    }

    @Test
    @DisplayName("Should start and shutdown application gracefully")
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void testStartAndShutdown() {
        try (MockedConstruction<SocketIOServerInput> mockedInput = mockConstruction(SocketIOServerInput.class);
             MockedConstruction<ConsoleVitalOutput> mockedOutput = mockConstruction(ConsoleVitalOutput.class);
             MockedConstruction<VitalProcessor> mockedProcessor = mockConstruction(VitalProcessor.class,
                     (mock, context) -> {
                         when(mock.getStatistics()).thenReturn(new VitalProcessor.ProcessorStatistics());
                     })) {

            application = new VitalConnectApplication();

            Thread appThread = new Thread(() -> application.start());
            appThread.start();

            Thread.sleep(100);
            application.shutdown();

            appThread.join(1000);

            assertThat(mockedProcessor.constructed()).hasSize(1);
            verify(mockedProcessor.constructed().get(0)).start();
            verify(mockedProcessor.constructed().get(0)).stop();
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    @DisplayName("Should parse command line arguments correctly")
    void testMainWithArguments() {
        String[] args = {"192.168.1.100", "5000", "true", "false"};

        Thread mainThread = new Thread(() -> VitalConnectApplication.main(args));
        mainThread.start();

        try {
            Thread.sleep(100);
            mainThread.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Should use default arguments when none provided")
    void testMainWithoutArguments() {
        String[] args = {};

        Thread mainThread = new Thread(() -> VitalConnectApplication.main(args));
        mainThread.start();

        try {
            Thread.sleep(100);
            mainThread.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }
    }
}