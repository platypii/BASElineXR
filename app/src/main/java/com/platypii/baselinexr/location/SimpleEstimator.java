package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.location.WSE.calculateWingsuitAcceleration;
import static com.platypii.baselinexr.location.WSE.calculateWingsuitParameters;

import android.util.Log;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.tensor.Vector3;

public final class SimpleEstimator implements MotionEstimator {
    private static final String TAG = "SimpleEstimator";

    // Position weight: 0.1 = mostly predicted, 1.0 = exact gps
    private static final double alpha = 0.2; // 0..1 sample weight position
    // Take velocity straight from gps:
    private static final double beta = 0.9; // 0..1 sample weight velocity
    // Acceleration weight
    private static final double gamma = 0.2; // 0..1 sample weight velocity

    // Kalman parameters
    public Vector3 p = new Vector3();        // metres ENU
    public Vector3 v = new Vector3();        // m s⁻¹ ENU
    public Vector3 a = new Vector3();        // m s⁻² ENU

    // Wingsuit parameters
    public WSEParams wseParams = new WSEParams(0.01, 0.01, 0.0);

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
    @Override
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

        // Get current GPS measurements in ENU
        Vector3 pMeasured = gpsToEnu(gps);
        Vector3 vMeasured = new Vector3(gps.vE, gps.climb, gps.vN);
        Vector3 aMeasured = vMeasured.minus(v).div(dt);

        // Calculate predicted velocity for wingsuit acceleration
        Vector3 pPred = p.plus(v.mul(dt)).plus(a.mul(0.5 * dt * dt));
        Vector3 vPred = v.plus(a.mul(dt));

        // Calculate wingsuit params
        Vector3 aWSE = calculateWingsuitAcceleration(vPred, wseParams);

        // Complementary filter for acceleration using wingsuit prediction
        Vector3 aOld = a;
        a = a.mul(1 - gamma).plus(aMeasured.mul(gamma));

        // Predict current state using trapezoidal rule from last state
        // Trapezoidal rule for velocity: v_pred = v + (a_old + a_new) * dt / 2
        Vector3 vPredictedFinal = v.plus(aOld.plus(a).mul(dt / 2));

        // Trapezoidal rule for position: p_pred = p + (v_old + v_new) * dt / 2
        Vector3 pPredictedFinal = p.plus(v.plus(vPredictedFinal).mul(dt / 2));

        // Complementary filter for velocity (blend predicted with GPS measurement)
        v = vPredictedFinal.mul(1 - beta).plus(vMeasured.mul(beta));

        // Complementary filter for position (blend predicted with GPS measurement)
        p = pPredictedFinal.mul(1 - alpha).plus(pMeasured.mul(alpha));

        // Update wingsuit parameters based on final state
        wseParams = calculateWingsuitParameters(v, a, wseParams);

        positionDelta = p.minus(pMeasured);
//        Log.i(TAG, "ppred: " + pPred.y + " lastpos: " + lastPosition.y + " v: " + v.y + " err: " + (pPred.y - lastPosition.y));

        lastUpdate = gps;
    }

    /**
     * Predict position at some time in the future
     *
     * @param currentTimeMillis phone timestamp to predict position at
     */
    @Override
    public Vector3 predictDelta(long currentTimeMillis) {
        if (lastUpdate == null) return new Vector3();
        // Correct for phone/gps time skew
        final double adjustedCurrentTime = TimeOffset.phoneToGpsTime(currentTimeMillis);

        double dt = (adjustedCurrentTime - lastUpdate.millis) * 1e-3;
        if (dt < 0) dt = 0;                  // clamp if asked for the past
//        Log.i(TAG, "lastUpdate = " + lastUpdate.millis + " now = " + currentTimeMillis + " dt = " + dt);
        return positionDelta
                .plus(v.mul(dt))
                .plus(a.mul(0.5 * dt * dt));
    }

    @Override
    public MLocation getLastUpdate() {
        return lastUpdate;
    }

    @Override
    public double ld() {
        return wseParams.ld();
    }
}