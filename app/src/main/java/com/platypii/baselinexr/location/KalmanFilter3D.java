package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.location.WSE.calculateWingsuitAcceleration;
import static com.platypii.baselinexr.location.WSE.calculateWingsuitParameters;

import android.util.Log;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.jarvis.FlightComputer;
import com.platypii.baselinexr.jarvis.FlightMode;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.LatLngAlt;
import com.platypii.baselinexr.GeoUtils;
import com.platypii.baselinexr.util.tensor.Vector3;

/**
 * 12D state Kalman filter for ENU position/velocity/acceleration and wingsuit parameters.
 *
 * State vector (size 12):
 *   [0..2]  position  (x=east, y=up, z=north)
 *   [3..5]  velocity  (vx, vy, vz)
 *   [6..8]  accel     (ax, ay, az)
 *   [9]     kl
 *   [10]    kd
 *   [11]    roll (radians)
 *
 * Measurements (size 6): [x, y, z, vx, vy, vz]
 *
 * Notes:
 * - Only the "standard" stepper is implemented: pos += v*dt; vel += a*dt; a := WSE(v_next, params)
 * - No trapezoidal branch (discarded by request).
 * - Uses small in-place linear algebra helpers. No dependencies.
 */
public final class KalmanFilter3D implements MotionEstimator {
    private static final String TAG = "KalmanFilter3D";

    // --- State (12) ---
    private final double[] x = new double[12];

    // Covariances and noise
    private double[][] P; // 12x12
    private double[][] Q; // 12x12
    private double[][] R; // 6x6

    // Last timing & reference
    private MLocation origin = null;
    private MLocation lastGps = null;

    // For plotting / inspection
    private Vector3 aMeasured = new Vector3();
    private Vector3 aWSE = new Vector3();
    private Vector3 kalmanStep = new Vector3();

    // Constants
    private static final double MAX_STEP = 0.1; // seconds
    private static final double accelerationLimit = 9.81 * 3.0; // 3 g on each axis limit for stability
    private static final boolean groundModeEnabled = false;// set ground accel to 0
    private static final boolean stepSmoothing = true;

    public KalmanFilter3D() {
        // Initial state
        for (int i = 0; i < x.length; i++) x[i] = 0.0;
        x[9]  = 0.01; // kl
        x[10] = 0.01; // kd
        x[11] = 0.0;  // roll

        // Initial covariance
        P = LinearAlgebra.identity(12);
        for (int i = 0; i < 9; i++) P[i][i] = 1000.0;
        P[9][9]   = 0.1;
        P[10][10] = 0.1;
        P[11][11] = 0.005;

        // Process noise
        Q = LinearAlgebra.identity(12);
        // pos
        Q[0][0] = 0.04; Q[1][1] =0.04; Q[2][2] = 0.04;
        // vel
        Q[3][3] = 0.4226; Q[4][4] = 0.4226; Q[5][5] = 0.4226;
        // accel (higher)
        Q[6][6] = 68.5; Q[7][7] = 68.5; Q[8][8] = 68.5;
        // wingsuit params (slow)
        Q[9][9]   = 0.01;
        Q[10][10] = 0.01;
        Q[11][11] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 1.2; R[1][1] = 1.2; R[2][2] = 1.2;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;
/*
// 5 hz settings
        // Process noise
        Q = LinearAlgebra.identity(12);
        // pos
        Q[0][0] = 0.12; Q[1][1] =0.12; Q[2][2] = 0.12;
        // vel
        Q[3][3] = 9.4226; Q[4][4] = 9.4226; Q[5][5] = 9.4226;
        // accel (higher)
        Q[6][6] = 470; Q[7][7] = 470; Q[8][8] = 470;
        // wingsuit params (slow)
        Q[9][9]   = 0.01;
        Q[10][10] = 0.01;
        Q[11][11] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 8.7; R[1][1] = 8.7; R[2][2] = 8.7;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;

// other5 hz settings
        // Process noise
        Q = LinearAlgebra.identity(12);
        // pos
        Q[0][0] = 0.12; Q[1][1] =0.12; Q[2][2] = 0.12;
        // vel
        Q[3][3] = 7.8401; Q[4][4] = 7.8401; Q[5][5] = 7.8401;
        // accel (higher)
        Q[6][6] = 26.4501; Q[7][7] = 26.4501; Q[8][8] = 26.4501;
        // wingsuit params (slow)
        Q[9][9]   = 0.01;
        Q[10][10] = 0.01;
        Q[11][11] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 4.7; R[1][1] = 4.7; R[2][2] = 4.7;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;
*/

    }

    /** Initialize on first fix, then run predict+update on subsequent fixes. */
    public void update(MLocation gps) {
        final long tNow = gps.millis;
        if (lastGps == null) {
            origin = gps;
            // State: p=0, v from GPS, a=0, params pre-seeded
            x[0] = 0; x[1] = 0; x[2] = 0;
            x[3] = gps.vE; x[4] = gps.climb; x[5] = gps.vN;
            x[6] = 0; x[7] = 0; x[8] = 0;
            x[9] = 0.01; x[10] = 0.01; x[11] = 0.0;
            lastGps = gps;
            return;
        }

        // Measurement preparation
        final Vector3 pMeas = gpsToEnu(gps);
        final double vx = gps.vE, vy = gps.climb, vz = gps.vN;

        // Measured acceleration by finite difference of measured velocity
        final double dt = Math.max(0.0, 1e-3 * (tNow - lastGps.millis));
        if (dt > 0.0 && lastGps != null) {
            aMeasured = new Vector3(
                    (vx - lastGps.vE) / dt,
                    (vy - lastGps.climb) / dt,
                    (vz - lastGps.vN) / dt
            );
        }

        // Predict to now
        if (dt > 0.0) predict(dt);


        // --- Measurement update (position + velocity) ---
        // H: 6x12 maps [p,v] to measurement
        final double[][] H = LinearAlgebra.zeros(6, 12);
        H[0][0] = 1; H[1][1] = 1; H[2][2] = 1;   // position
        H[3][3] = 1; H[4][4] = 1; H[5][5] = 1;   // velocity

        // z: measurement vector
        final double[] z = new double[] {
                pMeas.x, pMeas.y, pMeas.z, vx, vy, vz
        };

        // h(x): expected measurement from state
        final double[] hx = new double[] { x[0], x[1], x[2], x[3], x[4], x[5] };

        // Innovation y = z - h(x)
        final double[] y = new double[6];
        for (int i = 0; i < 6; i++) y[i] = z[i] - hx[i];

        // S = H P H^T + R
        final double[][] HP   = LinearAlgebra.mul(H, P);
        final double[][] HPHT = LinearAlgebra.mul(HP, LinearAlgebra.transpose(H));
        final double[][] S    = LinearAlgebra.add(HPHT, R);

        // K = P H^T S^-1
        final double[][] PHT  = LinearAlgebra.mul(P, LinearAlgebra.transpose(H));
        final double[][] Sinv = LinearAlgebra.inverse(S);
        final double[][] K    = LinearAlgebra.mul(PHT, Sinv);

        // x = x + K y
        final double[] Ky = LinearAlgebra.mul(K, y);
        for (int i = 0; i < 12; i++) x[i] += Ky[i];

        // save kalman step
        kalmanStep.x = Ky[0];
        kalmanStep.y = Ky[1];
        kalmanStep.z = Ky[2];

        // For plotting: WSE accel from measured velocity
        aWSE = calculateWingsuitAcceleration(new Vector3(vx, vy, vz), new WSEParams(x[9], x[10], x[11]));

        // P = (I - K H) P
        final double[][] KH   = LinearAlgebra.mul(K, H);
        final double[][] I_KH = LinearAlgebra.sub(LinearAlgebra.identity(12), KH);
        P = LinearAlgebra.mul(I_KH, P);

        // Update wingsuit parameters from current Kalman v,a (in ENU)
        updateWingsuitParameters();


        // Bookkeeping
        lastGps = gps;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Update @ " + tNow +
                    " p=(" + x[0] + "," + x[1] + "," + x[2] + ")" +
                    " v=(" + x[3] + "," + x[4] + "," + x[5] + ")" +
                    " a=(" + x[6] + "," + x[7] + "," + x[8] + ")" +
                    " kl=" + x[9] + " kd=" + x[10] + " roll=" + Math.toDegrees(x[11]));
        }
    }

    /** Predict the state forward by deltaTime seconds (standard integrator). */
    public void predict(double deltaTime) {
        if (deltaTime <= 0.0) return;

        double remaining = deltaTime;
        while (remaining > 0.0) {
            final double step = Math.min(remaining, MAX_STEP);
            integrateStep(step);
            remaining -= step;
        }

        // Simple Jacobian for p,v,a with constant-parameter dynamics
        final double[][] F = LinearAlgebra.calculateJacobian(deltaTime);

        // P = F P F^T + Q
        final double[][] FP   = LinearAlgebra.mul(F, P);
        final double[][] FPFT = LinearAlgebra.mul(FP, LinearAlgebra.transpose(F));
        // Scale Q by deltaTime for proper discrete-time process noise
        final double[][] Q_scaled = new double[12][12];
        for (int i = 0; i < 12; i++) {
            for (int j = 0; j < 12; j++) {
                Q_scaled[i][j] = this.Q[i][j] * deltaTime;
            }
        }
        P = LinearAlgebra.add(FPFT, Q_scaled);
    }

    /** Predict the state (without mutating this filter) at a given wallclock time in ms. */
    public Vector3 predictDelta(long currentTimeMillis) {
        if (lastGps == null) return new Vector3();
        // Correct for phone/gps time skew
        final double adjustedCurrentTime = TimeOffset.phoneToGpsTime(currentTimeMillis);

        double dt = (adjustedCurrentTime - lastGps.millis) * 1e-3;
        Log.i(TAG,"predictDelta dt=" + dt + " adjustedcurrentTimeMillis=" + adjustedCurrentTime + "lastGps " + lastGps);
                // clamp if asked for the past
        if (dt <= 0) return new Vector3(x[0], x[1], x[2]);

        // Clone state and step forward in small increments
        double[] s = x.clone();
        double remaining = dt;
        while (remaining > 0.0) {
            final double step = Math.min(remaining, MAX_STEP);
            s = integrateState(s, step);
            remaining -= step;
        }
        Log.i(TAG,s[0] + "," + s[1] + "," + s[2]);

        if(!stepSmoothing) return new Vector3(s[0]-x[0], s[1]-x[1], s[2]-x[2]);

        // smooth kalman step position for 20hz
        double alpha = 1-(dt * 20);//Services.location.refreshRate.refreshRate; // todo, use gps update rate
        if (alpha < 0) alpha = 0;
        if (alpha > 1) alpha = 1;
        Vector3 ps = kalmanStep.mul(alpha);
        //return new Vector3(s[0]-x[0], s[1]-x[1], s[2]-x[2]);
        return new Vector3(s[0]-x[0]-ps.x, s[1]-x[1]-ps.y, s[2]-x[2]-ps.z);
    }

    /** Current state snapshot. */
    public KFState getState() {
        return toState(x);
    }

    /** One "standard" step: p += v dt; v += a dt; a := WSE(v_new, params) */
    private void integrateStep(double dt) {
        // Unpack
        final double px = x[0], py = x[1], pz = x[2];
        final double vx = x[3], vy = x[4], vz = x[5];
        final double ax = x[6], ay = x[7], az = x[8];
        final double kl = x[9], kd = x[10], roll = x[11];

        // Position from current velocity
        final double nx = px + vx * dt;
        final double ny = py + vy * dt;
        final double nz = pz + vz * dt;

        // Velocity from current acceleration
        final double nvx = vx + ax * dt;
        final double nvy = vy + ay * dt;
        final double nvz = vz + az * dt;

        // Accel from WSE given the updated velocity
        final Vector3 aWse = calculateWingsuitAcceleration(new Vector3(nvx, nvy, nvz), new WSEParams(kl, kd, roll));

        // check acceleration components are < 2g, fallback to last accel
        applyAccelLimits(aWse, ax, ay, az);

        // Write back
        x[0] = nx; x[1] = ny; x[2] = nz;
        x[3] = nvx; x[4] = nvy; x[5] = nvz;
        x[6] = aWse.x; x[7] = aWse.y; x[8] = aWse.z;
        // x[9], x[10], x[11] unchanged in prediction
    }

    /** Pure function variant of integrateStep for predictAt() */
    private double[] integrateState(double[] s, double dt) {
        final double px = s[0], py = s[1], pz = s[2];
        final double vx = s[3], vy = s[4], vz = s[5];
        final double ax = s[6], ay = s[7], az = s[8];
        final double kl = s[9], kd = s[10], roll = s[11];

        final double nx = px + vx * dt;
        final double ny = py + vy * dt;
        final double nz = pz + vz * dt;

        final double nvx = vx + ax * dt;
        final double nvy = vy + ay * dt;
        final double nvz = vz + az * dt;

        final Vector3 aWse = calculateWingsuitAcceleration(new Vector3(nvx, nvy, nvz), new WSEParams(kl, kd, roll));

        // check acceleration components are < 3g, fallback to last accel
        applyAccelLimits(aWse, ax, ay, az);

        final double[] out = s.clone();
        out[0] = nx; out[1] = ny; out[2] = nz;
        out[3] = nvx; out[4] = nvy; out[5] = nvz;
        out[6] = aWse.x; out[7] = aWse.y; out[8] = aWse.z;
        // params unchanged
        //Log.i(TAG,"integrateState " + out[9] + "," + out[10] + "," + out[11]);

        return out;
    }

    private void applyAccelLimits(Vector3 accelerationVec, double prevAx, double prevAy, double prevAz) {
        // Apply acceleration magnitude limit
        if (Math.abs(accelerationVec.x) > accelerationLimit ||
                Math.abs(accelerationVec.y) > accelerationLimit ||
                Math.abs(accelerationVec.z) > accelerationLimit) {
            // Log.d(TAG, "Acceleration limit exceeded. Falling back to previous acceleration.");
            accelerationVec.x = prevAx;
            accelerationVec.y = prevAy;
            accelerationVec.z = prevAz;
        }
        int fm =Services.flightComputer.flightMode;
        //Log.d(TAG, "flight mode: "+Services.flightComputer.flightMode);
        // Apply ground mode (set acceleration to zero if on the ground and enabled)
        if(groundModeEnabled && (fm == FlightMode.MODE_GROUND)){
            accelerationVec.x = 0.0;
            accelerationVec.y = 0.0;accelerationVec.z = 0.0;
        }
    }

    /** Update wingsuit parameters from current Kalman v,a (skip at very low speed). */
    private void updateWingsuitParameters() {
        final Vector3 vKal = new Vector3(x[3], x[4], x[5]);
        final Vector3 aKal = new Vector3(x[6], x[7], x[8]);
        final double speed = Math.sqrt(vKal.x * vKal.x + vKal.y * vKal.y + vKal.z * vKal.z);
        if (speed < 1.0) return;

        final WSEParams updated = calculateWingsuitParameters(vKal, aKal, new WSEParams(x[9], x[10], x[11]));
        x[9] = updated.kl();
        x[10] = updated.kd();
        x[11] = updated.roll();
    }

    /** ENU relative to the first fix (origin). */
    private Vector3 gpsToEnu(MLocation gps) {
        if (origin == null) return new Vector3();

        LatLngAlt from = origin.toLatLngAlt();
        LatLngAlt to = gps.toLatLngAlt();
        com.meta.spatial.core.Vector3 offset = GeoUtils.calculateOffset(from, to);

        // Convert from Meta Spatial Vector3 to tensor Vector3 (ENU format)
        return new Vector3(offset.getX(), offset.getY(), offset.getZ());
    }
    private Vector3 getKalmanStep(){                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        
        return kalmanStep;
    }

    /** Convert any state vector into an immutable snapshot. */
    private KFState toState(double[] s) {
        return new KFState(
                new Vector3(s[0], s[1], s[2]),
                new Vector3(s[3], s[4], s[5]),
                new Vector3(s[6], s[7], s[8]),
                aMeasured,
                aWSE,
                s[9], s[10], s[11]
        );
    }

    /** Kalman state */
    public record KFState(
            Vector3 position,
            Vector3 velocity,
            Vector3 acceleration,
            Vector3 aMeasured,
            Vector3 aWSE,
            double kl,
            double kd,
            double roll
    ) {}

    public double ld() {
        return getState().kl / getState().kd;
    }

    public MLocation getLastUpdate() {
        return lastGps;
    }

}
