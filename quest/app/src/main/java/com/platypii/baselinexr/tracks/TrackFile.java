package com.platypii.baselinexr.tracks;

import android.util.Log;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Represents a track file on the local device (pre-upload)
 */
public class TrackFile {
    private static final String TAG = "TrackFile";

    // TrackFile info
    public final File file;

    public TrackFile(File file) {
        this.file = file;
    }

    @NonNull
    public String getName() {
        return file.getName()
                .replaceAll(".csv.gz", "")
                .replaceAll("_", " ");
    }

    @NonNull
    public String getSize() {
        if (!file.exists()) {
            Log.e(TAG, "Missing file in TrackFile.getSize()");
        } else if (file.length() == 0) {
            Log.e(TAG, "Zero length file in TrackFile.getSize()");
        }
        final long size = file.length() >> 10;
        return size + " kb";
    }

    /**
     * Move the track file to track directory
     */
    public void archive(@NonNull File destination) {
        Log.i(TAG, "Archiving track file " + getName() + " to " + destination);
        // Move form source to destination
        // Ensure track directory exists
        final File trackDir = destination.getParentFile();
        if (!trackDir.exists()) {
            if (!trackDir.mkdirs()) {
                Log.e(TAG, "Failed to make track directory " + trackDir);
            }
        }
        // Move track file to track directory
        if (!file.renameTo(destination)) {
            Log.e(TAG, "Failed to move track file " + file + " to " + destination);
        }
    }

    @NonNull
    @Override
    public String toString() {
        return file.getName();
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof TrackFile && file.equals(((TrackFile) obj).file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }

}
