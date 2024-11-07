package com.platypii.baselinexr.events;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.tracks.TrackMetadata;

import java.io.File;

/**
 * Indicates that a track upload has completed, or sync status has changed
 */
public abstract class SyncEvent {

    public static abstract class DownloadEvent extends SyncEvent {
        public TrackMetadata track;
    }

    /* Downloads */
    public static class DownloadProgress extends DownloadEvent {
        public final int progress;
        public final int total;

        public DownloadProgress(@NonNull TrackMetadata track, int progress, int total) {
            this.track = track;
            this.progress = progress;
            this.total = total;
        }
    }

    public static class DownloadSuccess extends DownloadEvent {
        public final File trackFile;

        public DownloadSuccess(@NonNull TrackMetadata track, @NonNull File trackFile) {
            this.track = track;
            this.trackFile = trackFile;
        }
    }

    public static class DownloadFailure extends DownloadEvent {
        public final Exception error;

        public DownloadFailure(@NonNull TrackMetadata track, Exception error) {
            this.track = track;
            this.error = error;
        }
    }

    /**
     * Track listing updated event
     */
    public static class ListingSuccess extends SyncEvent {
    }

}
