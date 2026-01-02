package com.platypii.baselinexr;

import androidx.annotation.Nullable;

/**
 * Manages current mock track selection.
 * Defaults to Live GPS (null) on every app startup - no persistence.
 */
public class MockTrackOptions {

    // Current mock track filename, null means Live GPS
    @Nullable
    public static String current = null;

    /**
     * Get display name for current track
     */
    public static String getCurrentDisplayName() {
        return MockTrackList.getDisplayName(current);
    }

    /**
     * Cycle to next track
     */
    public static void cycleToNext() {
        current = MockTrackList.getNextTrack(current);
        // Clear dropzone so it auto-detects for new track
        DropzoneOptions.current = null;
    }
}
