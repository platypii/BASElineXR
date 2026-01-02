package com.platypii.baselinexr;

import androidx.annotation.Nullable;

public class MockTrackList {

    public static class TrackInfo {
        public final String filename;
        public final String displayName;

        public TrackInfo(String filename, String displayName) {
            this.filename = filename;
            this.displayName = displayName;
        }
    }

    // All available mock tracks
    public static final TrackInfo[] ALL_TRACKS = {
            new TrackInfo("eiger.csv", "Eiger"),
            new TrackInfo("kpow-prison.csv", "Prison"),
            new TrackInfo("seb_ff.csv", "Seb FF"),
            new TrackInfo("kpow-impact.csv", "Impact"),
            new TrackInfo("portal-run.csv", "Portal"),
    };

    /**
     * Get display name for a track filename
     * @param filename the CSV filename, or null for Live GPS
     */
    public static String getDisplayName(@Nullable String filename) {
        if (filename == null) {
            return "Live GPS";
        }
        for (TrackInfo track : ALL_TRACKS) {
            if (track.filename.equals(filename)) {
                return track.displayName;
            }
        }
        return filename; // fallback to filename if not found
    }

    /**
     * Cycle to next track: Live GPS -> eiger -> prison -> seb_ff -> impact -> portal -> Live GPS...
     * @param currentFilename current track filename, or null for Live GPS
     * @return next track filename, or null for Live GPS
     */
    @Nullable
    public static String getNextTrack(@Nullable String currentFilename) {
        if (currentFilename == null) {
            // Currently Live GPS, go to first track
            return ALL_TRACKS[0].filename;
        }
        for (int i = 0; i < ALL_TRACKS.length; i++) {
            if (ALL_TRACKS[i].filename.equals(currentFilename)) {
                if (i == ALL_TRACKS.length - 1) {
                    // Last track, cycle back to Live GPS
                    return null;
                }
                return ALL_TRACKS[i + 1].filename;
            }
        }
        // Not found, default to Live GPS
        return null;
    }
}
