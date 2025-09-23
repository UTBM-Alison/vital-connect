package vitalconnect.output;

import vitalconnect.domain.ProcessedData;
import vitalconnect.domain.ProcessedRoom;
import vitalconnect.domain.ProcessedTrack;

import java.time.format.DateTimeFormatter;
import java.time.ZoneId;

/**
 * Console output implementation for displaying vital data.
 * Matches the TypeScript ConsoleOutput with colored and formatted display.
 */
public class ConsoleVitalOutput implements VitalOutput {

    private final boolean verbose;
    private final boolean colorized;
    private final DateTimeFormatter timeFormatter;

    // ANSI color codes
    private static final class Colors {
        static final String RESET = "\u001B[0m";
        static final String BRIGHT = "\u001B[1m";
        static final String DIM = "\u001B[2m";
        static final String RED = "\u001B[31m";
        static final String GREEN = "\u001B[32m";
        static final String YELLOW = "\u001B[33m";
        static final String BLUE = "\u001B[34m";
        static final String MAGENTA = "\u001B[35m";
        static final String CYAN = "\u001B[36m";
        static final String WHITE = "\u001B[37m";
    }

    /**
     * Create a console output with specified options.
     *
     * @param verbose if true, show detailed output; if false, show compact output
     * @param colorized if true, use ANSI colors in output
     */
    public ConsoleVitalOutput(boolean verbose, boolean colorized) {
        this.verbose = verbose;
        this.colorized = colorized;
        this.timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
                .withZone(ZoneId.systemDefault());
    }

    /**
     * Create a console output with default options (compact, colorized).
     */
    public ConsoleVitalOutput() {
        this(false, true);
    }

    @Override
    public void send(ProcessedData data) {
        try {
            if (verbose) {
                printVerbose(data);
            } else {
                printCompact(data);
            }
        } catch (Exception e) {
            System.err.println("Console output error: " + e.getMessage());
            return;
        }
        // PrintStream avale les IOExceptions et signale l'erreur via checkError().
        if (System.out.checkError()) {
            System.err.println("Console output error: write failure");
        }
    }

    @Override
    public void close() {
        // No resources to close for console output
    }

    /**
     * Print verbose output with all details.
     */
    private void printVerbose(ProcessedData data) {
        String cyan = color(Colors.CYAN);
        String bright = color(Colors.BRIGHT);
        String yellow = color(Colors.YELLOW);
        String magenta = color(Colors.MAGENTA);
        String dim = color(Colors.DIM);
        String reset = color(Colors.RESET);

        System.out.println(cyan + "\u2501".repeat(60) + reset);
        System.out.println(bright + "\ud83c\udfe5 VITAL SIGNS UPDATE" + reset);
        System.out.println(cyan + "\u2501".repeat(60) + reset);

        if (data.vrCode() != null) {
            System.out.println(yellow + "VR Code:" + reset + " " + data.vrCode());
        }
        System.out.println(yellow + "Timestamp:" + reset + " " +
                timeFormatter.format(data.timestamp()));
        System.out.println(yellow + "Total Tracks:" + reset + " " +
                data.allTracks().size());
        System.out.println();

        // Print by room
        for (ProcessedRoom room : data.rooms()) {
            if (!room.tracks().isEmpty()) {
                System.out.println(magenta + "\ud83d\udccd " + room.roomName() + reset);
                System.out.println(dim + "\u2500".repeat(50) + reset);

                for (ProcessedTrack track : room.tracks()) {
                    printTrack(track);
                }
                System.out.println();
            }
        }
    }

    /**
     * Print compact output with essential information.
     */
    private void printCompact(ProcessedData data) {
        String bright = color(Colors.BRIGHT);
        String cyan = color(Colors.CYAN);
        String dim = color(Colors.DIM);
        String reset = color(Colors.RESET);

        System.out.println(bright + "[" + timeFormatter.format(data.timestamp()) +
                "] \ud83c\udfe5 Vital Update - " + data.allTracks().size() + " tracks" + reset);

        // Show first 5 tracks only in compact mode
        int tracksToShow = Math.min(5, data.allTracks().size());
        for (int i = 0; i < tracksToShow; i++) {
            ProcessedTrack track = data.allTracks().get(i);
            System.out.println("  " + cyan + track.name() + ":" + reset + " " +
                    track.displayValue() + " " + track.unit() + " " +
                    dim + "(" + track.roomName() + ")" + reset);
        }

        if (data.allTracks().size() > 5) {
            System.out.println("  " + dim + "... and " +
                    (data.allTracks().size() - 5) + " more tracks" + reset);
        }
    }

    /**
     * Print individual track details.
     */
    private void printTrack(ProcessedTrack track) {
        String green = color(Colors.GREEN);
        String bright = color(Colors.BRIGHT);
        String blue = color(Colors.BLUE);
        String dim = color(Colors.DIM);
        String reset = color(Colors.RESET);

        String typeIcon = getTypeIcon(track.type());

        System.out.println("  " + typeIcon + " " + green + track.name() + reset);
        System.out.println("     " + bright + track.displayValue() + reset +
                " " + blue + track.unit() + reset);
        System.out.println("     " + dim + "Time: " +
                timeFormatter.format(track.timestamp()) + reset);
    }

    /**
     * Get icon for track type.
     */
    private String getTypeIcon(ProcessedTrack.TrackType type) {
        return switch (type) {
            case WAVEFORM -> "\ud83d\udcca";
            case NUMBER -> "\ud83d\udd22";
            case STRING -> "\ud83d\udcdd";
            default -> "\ud83d\udccc";
        };
    }

    /**
     * Apply color if colorization is enabled.
     */
    private String color(String colorCode) {
        return colorized ? colorCode : "";
    }
}