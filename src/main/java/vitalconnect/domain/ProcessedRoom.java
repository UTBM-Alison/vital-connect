package vitalconnect.domain;

import java.util.List;

/**
 * Processed room data containing organized tracks.
 */
public record ProcessedRoom(
        int roomIndex,
        String roomName,
        List<ProcessedTrack> tracks
) {}