package com.platypii.baselinexr.places;

import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.cloud.AuthException;
import com.platypii.baselinexr.cloud.AuthState;
import com.platypii.baselinexr.location.LatLngBounds;
import com.platypii.baselinexr.util.Exceptions;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Manages the place database
 */
public class Places {
    private static final String TAG = "Places";

    @Nullable
    private Context context;
    @Nullable
    private PlaceFile placeFile;

    @NonNull
    public final NearestPlace nearestPlace = new NearestPlace(this);

    // In-memory cache of places, lazy loaded on first call to getPlaces()
    @Nullable
    private List<Place> places = null;

    public void start(@NonNull Context context) {
        this.context = context;
        updateAsync(false);
        EventBus.getDefault().register(this);
    }

    /**
     * Load from place file, if necessary
     */
    @Nullable
    public List<Place> getPlaces() {
        if (places == null && placeFile != null && placeFile.exists()) {
            try {
                places = placeFile.parse();
                Log.i(TAG, "Loaded " + places.size() + " places");
            } catch (IOException e) {
                Log.e(TAG, "Error loading places", e);
                Exceptions.report(e);
            }
        }
        return places;
    }

    @NonNull
    public List<Place> getPlacesByArea(@NonNull LatLngBounds bounds) {
        final long start = System.currentTimeMillis();
        final List<Place> filtered = new ArrayList<>();
        final List<Place> places = getPlaces();
        if (places != null) {
            for (Place place : places) {
                if (bounds.contains(place.latLng())) {
                    filtered.add(place);
                }
            }
            final long duration = System.currentTimeMillis() - start;
            Log.i(TAG, "Got " + filtered.size() + "/" + places.size() + " places in view " + duration + " ms");
        }
        return filtered;
    }

    /**
     * Update places in background thread
     */
    private void updateAsync(boolean force) {
        final Context ctx = context;
        if (ctx == null) {
            Exceptions.report(new NullPointerException("Null context in Places.updateAsync(" + force + ")"));
            return;
        }
        AsyncTask.execute(() -> {
            if (placeFile == null) {
                // Place file is stored on internal storage
                placeFile = new PlaceFile(ctx);
            }
            // Fetch places from server, if we need to
            if (force || !placeFile.isFresh()) {
                try {
                    FetchPlaces.get(placeFile.file);
                    places = null; // Reload from place file
                } catch (IOException | AuthException e) {
                    Log.e(TAG, "Failed to fetch places", e);
                }
            } else {
                Log.i(TAG, "Places file is already fresh");
            }
        });
    }

    @Subscribe
    public void onSignIn(@NonNull AuthState.SignedIn event) {
        updateAsync(true);
    }

    @Subscribe
    public void onSignOut(@NonNull AuthState.SignedOut event) {
        if (placeFile != null) {
            placeFile.delete();
        }
        places = null;
        updateAsync(true);
    }

    public void stop() {
        EventBus.getDefault().unregister(this);
        context = null;
    }

}
