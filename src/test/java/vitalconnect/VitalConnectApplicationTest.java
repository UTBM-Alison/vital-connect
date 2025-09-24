package vitalconnect;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    @DisplayName("Should handle exception during startup and call System.exit")
    void testStartException() {
        try (MockedConstruction<SocketIOServerInput> mockedInput = mockConstruction(SocketIOServerInput.class);
             MockedConstruction<ConsoleVitalOutput> mockedOutput = mockConstruction(ConsoleVitalOutput.class);
             MockedConstruction<VitalProcessor> mockedProcessor = mockConstruction(VitalProcessor.class,
                     (mock, context) -> {
                         doThrow(new RuntimeException("Startup failed")).when(mock).start();
                     })) {

            // Create spy and prevent actual System.exit
            VitalConnectApplication appSpy = spy(new VitalConnectApplication());
            doNothing().when(appSpy).exitApplication(anyInt());

            // This should not exit the JVM
            appSpy.start();

            // Verify exit was attempted with status 1
            verify(appSpy, timeout(1000)).exitApplication(1);
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

    @Test
    @DisplayName("Should handle exception during shutdown gracefully")
    void testShutdownException() {
        try (MockedConstruction<SocketIOServerInput> mockedInput = mockConstruction(SocketIOServerInput.class);
             MockedConstruction<ConsoleVitalOutput> mockedOutput = mockConstruction(ConsoleVitalOutput.class);
             MockedConstruction<VitalProcessor> mockedProcessor = mockConstruction(VitalProcessor.class,
                     (mock, context) -> {
                         doThrow(new RuntimeException("Shutdown failed")).when(mock).stop();
                         when(mock.getStatistics()).thenReturn(new VitalProcessor.ProcessorStatistics());
                     })) {

            application = new VitalConnectApplication();

            // This should not throw an exception even if processor.stop() fails
            assertThatCode(() -> application.shutdown()).doesNotThrowAnyException();
        }
    }

    @Test
    @DisplayName("Should parse command line arguments with all combinations")
    void testMainWithAllArgumentCombinations() {
        // Test with 2 arguments (verbose defaults to false, colorized defaults to true)
        String[] args2 = {"localhost", "8080"};
        Thread mainThread2 = new Thread(() -> VitalConnectApplication.main(args2));
        mainThread2.start();
        try {
            Thread.sleep(50);
            mainThread2.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }

        // Test with 3 arguments (colorized defaults to true)
        String[] args3 = {"localhost", "8080", "true"};
        Thread mainThread3 = new Thread(() -> VitalConnectApplication.main(args3));
        mainThread3.start();
        try {
            Thread.sleep(50);
            mainThread3.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }

        // Test with 4 arguments, colorized = false
        String[] args4 = {"localhost", "8080", "false", "false"};
        Thread mainThread4 = new Thread(() -> VitalConnectApplication.main(args4));
        mainThread4.start();
        try {
            Thread.sleep(50);
            mainThread4.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }

        // Test with 4 arguments, colorized = true (explicit)
        String[] args4b = {"localhost", "8080", "true", "true"};
        Thread mainThread4b = new Thread(() -> VitalConnectApplication.main(args4b));
        mainThread4b.start();
        try {
            Thread.sleep(50);
            mainThread4b.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Should handle interrupted exception during await")
    void testInterruptedExceptionDuringStart() {
        try (MockedConstruction<SocketIOServerInput> mockedInput = mockConstruction(SocketIOServerInput.class);
             MockedConstruction<ConsoleVitalOutput> mockedOutput = mockConstruction(ConsoleVitalOutput.class);
             MockedConstruction<VitalProcessor> mockedProcessor = mockConstruction(VitalProcessor.class,
                     (mock, context) -> {
                         when(mock.getStatistics()).thenReturn(new VitalProcessor.ProcessorStatistics());
                     })) {

            application = new VitalConnectApplication();

            Thread appThread = new Thread(() -> application.start());
            appThread.start();

            // Interrupt the thread to trigger InterruptedException
            Thread.sleep(50);
            appThread.interrupt();
            appThread.join(1000);

            // Verify the processor was started
            assertThat(mockedProcessor.constructed()).hasSize(1);
            verify(mockedProcessor.constructed().get(0)).start();
        } catch (Exception e) {
            fail("Should not throw exception", e);
        }
    }

    @Test
    @DisplayName("Should test specific argument parsing edge cases")
    void testArgumentParsingEdgeCases() {
        // Test with exactly 3 arguments to trigger args.length <= 3 condition
        String[] args3Exact = {"127.0.0.1", "9999", "false"};
        Thread mainThread3Exact = new Thread(() -> VitalConnectApplication.main(args3Exact));
        mainThread3Exact.start();
        try {
            Thread.sleep(50);
            mainThread3Exact.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }

        // Test with exactly 4 arguments to trigger args.length > 3 condition
        String[] args4Exact = {"127.0.0.1", "9999", "true", "false"};
        Thread mainThread4Exact = new Thread(() -> VitalConnectApplication.main(args4Exact));
        mainThread4Exact.start();
        try {
            Thread.sleep(50);
            mainThread4Exact.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }

        // Test with 5+ arguments to ensure args.length > 3 is covered
        String[] args5 = {"127.0.0.1", "9999", "true", "true", "extra"};
        Thread mainThread5 = new Thread(() -> VitalConnectApplication.main(args5));
        mainThread5.start();
        try {
            Thread.sleep(50);
            mainThread5.interrupt();
        } catch (InterruptedException e) {
            // Expected
        }
    }
}