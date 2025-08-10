package com.platypii.baselinexr.location;

import android.util.Log;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

public final class MotionEstimator {
    private static final String TAG = "MotionEstimator";

    private static final double alpha = 0.1; // 0..1, picked once
    public Vector3 p = new Vector3();        // metres ENU
    public Vector3 v = new Vector3();        // m s⁻¹ ENU
    public Vector3 a = new Vector3();        // m s⁻² ENU
    public MLocation lastUpdate = null;     // last GPS update
    private Vector3 positionDelta = null;    // Difference between last gps point and last estimated position
    private MLocation origin = null;         // GPS origin for ENU conversion
    private static final double EARTH_RADIUS_METERS = 6371000.0;

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

        return new Vector3(east, up, north);
    }

    /** Call every time a fresh GPS sample arrives */
    public void update(MLocation gps) {
        long tNow = gps.millis;
        if (lastUpdate == null) {             // first fix sets origin
            origin = gps;                     // set GPS origin
            p = new Vector3();                // new origin = (0,0,0)
            v = new Vector3(gps.vE, gps.climb, gps.vN);
            a = new Vector3();
            lastUpdate = gps;
            positionDelta = new Vector3();
            return;
        }

        double dt = (tNow - lastUpdate.millis) * 1e-3;
        Vector3 vNew = new Vector3(gps.vE, gps.climb, gps.vN);
        Vector3 aRaw = vNew.minus(v).div(dt);
        a = a.mul(1 - alpha).plus(aRaw.mul(alpha));

        Vector3 lastPosition = gpsToEnu(gps);

        // 1) predict (constant-acceleration)
        Vector3 pPred = p.plus(v.mul(dt)).plus(a.mul(0.5 * dt * dt));
//        Vector3 vPred = v.plus(a.mul(dt));

        // 2) update using measurement as evidence (complementary filter)
        p = pPred.mul(1 - alpha).plus(lastPosition.mul(alpha));
//        v = vPred.mul(1 - alpha).plus(vNew.mul(alpha));
        v = vNew; // Take velocity straight from gps

        positionDelta = p.minus(lastPosition);
//        Log.i(TAG, "ppred: " + pPred.y + " lastpos: " + lastPosition.y + " v: " + v.y + " err: " + (pPred.y - lastPosition.y));

        lastUpdate = gps;
    }

    public Vector3 predictDelta(long tQueryMillis) {
        if (lastUpdate == null) return new Vector3();

        double dt = (tQueryMillis - lastUpdate.millis) * 1e-3;
        if (dt < 0) dt = 0;                  // clamp if asked for the past
//        Log.i(TAG, "lastUpdate = " + lastUpdate.millis + " now = " + tQueryMillis + " dt = " + dt);
        return positionDelta
                .plus(v.mul(dt))
                .plus(a.mul(0.5 * dt * dt));
    }

    /**
     * Represents a motion state with position, velocity, and acceleration
     */
    public record State(Vector3 position, Vector3 velocity, Vector3 acceleration) {
    }
}
