package com.platypii.baselinexr.location;

import com.platypii.baselinexr.measurements.LatLngAlt;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

public final class MotionEstimator {
    private final double alpha;              // 0..1, picked once
    public Vector3 p = new Vector3();       // metres ENU
    public Vector3 v = new Vector3();       // m s⁻¹ ENU
    public Vector3 a = new Vector3();       // m s⁻² ENU
    private long tLastMillis = -1;           // monotonic clock
    private MLocation origin = null;         // GPS origin for ENU conversion
    private static final double EARTH_RADIUS_METERS = 6371000.0;

    public MotionEstimator(double alpha) { this.alpha = alpha; }

    /**
     * Converts GPS lat/lon/alt to ENU coordinates relative to the origin
     */
    private Vector3 gpsToEnu(MLocation gps) {
        if (origin == null) {
            return new Vector3(); // Return zero if no origin set
        }

        double latRad = Math.toRadians(gps.latitude);
        double lonRad = Math.toRadians(gps.longitude);
        double originLatRad = Math.toRadians(origin.latitude);
        double originLonRad = Math.toRadians(origin.longitude);

        double deltaLat = latRad - originLatRad;
        double deltaLon = lonRad - originLonRad;

        double north = deltaLat * EARTH_RADIUS_METERS;
        double east = deltaLon * EARTH_RADIUS_METERS * Math.cos(originLatRad);
        double up = gps.altitude_gps - origin.altitude_gps;

        return new Vector3(east, north, up);
    }

    /** Call every time a fresh GPS sample arrives */
    public void update(MLocation gps) {
        long tNow = gps.millis;
        if (tLastMillis < 0) {               // first fix sets origin
            origin = gps;                     // set GPS origin
            p = new Vector3();                // new origin = (0,0,0)
            v = new Vector3(gps.vE, gps.vN, gps.climb);
            a = new Vector3();
            tLastMillis = tNow;
            return;
        }

        double dt = (tNow - tLastMillis) * 1e-3;
        Vector3 vNew = new Vector3(gps.vE, gps.vN, gps.climb);
        Vector3 aRaw = vNew.minus(v).div(dt);
        a = a.mul(1 - alpha).plus(aRaw.mul(alpha));

        p = gpsToEnu(gps);                    // convert GPS to ENU coordinates
        v = vNew;
        tLastMillis = tNow;
    }

    /** Predict state at an arbitrary near-future instant */
    public State predict(long tQueryMillis) {
        double dt = (tQueryMillis - tLastMillis) * 1e-3;
        if (dt < 0) dt = 0;                  // clamp if asked for the past
        Vector3 pos = p
                .plus(v.mul(dt))
                .plus(a.mul(0.5 * dt * dt));
        Vector3 vel = v.plus(a.mul(dt));
        return new State(pos, vel, a);
    }

    /** Predict state at an arbitrary near-future instant */
    public LatLngAlt predictLatLng(long tQueryMillis) {
        double dt = (tQueryMillis - tLastMillis) * 1e-3;
        if (dt < 0) dt = 0;                  // clamp if asked for the past
        Vector3 position = p
                .plus(v.mul(dt))
                .plus(a.mul(0.5 * dt * dt));

        return positionToLatLng(position);
    }

    private LatLngAlt positionToLatLng(Vector3 position) {
        if (origin == null) {
            return new LatLngAlt(0.0, 0.0, 0.0);
        }

        double originLatRad = Math.toRadians(origin.latitude);
        double originLonRad = Math.toRadians(origin.longitude);

        double north = position.y;
        double east = position.x;
        double up = position.z;

        double deltaLat = north / EARTH_RADIUS_METERS;
        double deltaLon = east / (EARTH_RADIUS_METERS * Math.cos(originLatRad));

        double latitude = Math.toDegrees(originLatRad + deltaLat);
        double longitude = Math.toDegrees(originLonRad + deltaLon);
        double altitude = origin.altitude_gps + up;

        return new LatLngAlt(latitude, longitude, altitude);
    }

    /**
     * Represents a motion state with position, velocity, and acceleration
     */
    public record State(Vector3 position, Vector3 velocity, Vector3 acceleration) {
    }
}
