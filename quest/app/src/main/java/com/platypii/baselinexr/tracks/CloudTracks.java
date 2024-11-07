package com.platypii.baselinexr.tracks;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.events.SyncEvent.DownloadEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains the list of tracks and their sync state
 */
public class CloudTracks {

    // Track files
    private final Map<TrackMetadata, DownloadEvent> trackState = new HashMap<>();

    public void start() {
        EventBus.getDefault().register(this);
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
    }

    @Subscribe
    public void onTrackDownload(@NonNull DownloadEvent event) {
        trackState.put(event.track, event);
    }

}
