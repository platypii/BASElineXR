package com.platypii.baselinexr.tracks;

import android.content.Context;
import android.os.Handler;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.cloud.tasks.TaskType;

import org.greenrobot.eventbus.EventBus;

import java.util.List;

/**
 * Manages track uploads.
 * This includes queueing finished tracks, and retrying in the future.
 */
class SyncManager {
    private static final String TAG = "UploadManager";

    public void start(@NonNull Context context) {
        // Listen for track logging stops
        EventBus.getDefault().register(this);
        // Queue tracks for upload and download
        new Handler().postDelayed(() -> {
            downloadAll(context);
        }, 1000);
    }

    /**
     * Enqueue non-local tracks for downloading
     */
    void downloadAll(@NonNull Context context) {
        Services.tasks.removeType(TaskType.trackUpload);
        final List<TrackMetadata> cloudTracks = Services.tracks.cache.list();
        if (cloudTracks != null) {
            for (TrackMetadata track : cloudTracks) {
                if (track.starred && !track.abbrvFile(context).exists() && !track.localFile(context).exists()) {
                    Services.tasks.add(new DownloadTrackTask(track));
                }
            }
        }
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
    }

}
