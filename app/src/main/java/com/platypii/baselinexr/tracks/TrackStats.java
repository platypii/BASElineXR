package com.platypii.baselinexr.tracks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.location.LatLngBounds;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.Range;

import java.util.List;

/**
 * Compute track stats
 */
public class TrackStats {

    public final Range altitude = new Range();
    @Nullable
    public MLocation exit;
    @Nullable
    public MLocation deploy;
    @Nullable
    public MLocation land;

    @Nullable
    public final LatLngBounds bounds;

    TrackStats(@NonNull List<MLocation> trackData) {
        if (!trackData.isEmpty()) {
            // Detect exit, deploy, land
            final TrackLabels labels = TrackLabels.from(trackData);
            if (labels != null) {
                exit = trackData.get(labels.exit);
                deploy = trackData.get(labels.deploy);
                land = trackData.get(labels.land);
            }
            final LatLngBounds.Builder boundsBuilder = new LatLngBounds.Builder();
            for (MLocation loc : trackData) {
                altitude.expand(loc.altitude_gps);
                boundsBuilder.include(loc.latLng());
            }
            bounds = boundsBuilder.build();
        } else {
            bounds = null;
        }
    }

    public boolean isDefined() {
        return exit != null && deploy != null && land != null;
    }
}
