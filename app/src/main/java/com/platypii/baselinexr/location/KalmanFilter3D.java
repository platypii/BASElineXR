package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.location.WSE.calculateWingsuitAcceleration;
import static com.platypii.baselinexr.location.WSE.calculateWingsuitParameters;

import android.util.Log;

import com.platypii.baselinexr.location.WSEAnalytical;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.jarvis.FlightComputer;
import com.platypii.baselinexr.jarvis.FlightMode;
import com.platypii.baselinexr.location.AtmosphericModel;
import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.measurements.LatLngAlt;
import com.platypii.baselinexr.GeoUtils;
import com.platypii.baselinexr.util.tensor.Vector3;
import com.platypii.baselinexr.wind.WindSystem;

/**
 * 18D state Kalman filter for ENU position/velocity/acceleration, wingsuit parameters, and wind estimates.
 *
 * State vector (size 18):
 *   [0..2]  position  (x=east, y=up, z=north)
 *   [3..5]  velocity  (vx, vy, vz)
 *   [6..8]  accel     (ax, ay, az)
 *   [9]     kl
 *   [10]    kd
 *   [11]    roll (radians)
 *   [12..14] wind velocity (wvx=east, wvy=up, wvz=north)
 *   [15]    klwind (lift coefficient with wind)
 *   [16]    kdwind (drag coefficient with wind)
 *   [17]    rollwind (roll with wind consideration)
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

    // --- State (18) ---
    private final double[] x = new double[18];

    // Covariances and noise
    private double[][] P; // 18x18
    private double[][] Q; // 18x18
    private double[][] R; // 6x6

    // Last timing & reference
    private MLocation origin = null;
    private MLocation lastGps = null;
    // saved kalman gain
    private Vector3 kalmanStep = new Vector3();
    private Vector3 kalmanVelStep = new Vector3();
    private Vector3 kalmanAccelStep = new Vector3();
    // wingsuit parameter kalman steps
    private double kalmanKlStep = 0.0;
    private double kalmanKdStep = 0.0;
    private double kalmanRollStep = 0.0;
    // wind parameter kalman steps
    private Vector3 kalmanWindStep = new Vector3();
    private double kalmanKlWindStep = 0.0;
    private double kalmanKdWindStep = 0.0;
    private double kalmanRollWindStep = 0.0;

    // Wind estimation components
    private WindKalmanFilter windFilter;
    private PolarLibrary.WingsuitPolar polar = PolarLibrary.AURA_FIVE_POLAR; // Default polar

    // Constants
    private static final double MAX_STEP = 0.1; // seconds
    private static final double accelerationLimit = 9.81 * 3.0; // 3 g on each axis limit for stability

    private static final double groundAccelerationLimit = 9.81 ; // 1 g on each axis limit on ground
    private static final boolean groundModeEnabled = true;// set ground accel limits
    private static final boolean stepSmoothing = true;

    public KalmanFilter3D() {
        // Initial state
        for (int i = 0; i < x.length; i++) x[i] = 0.0;
        x[9]  = 0.01; // kl
        x[10] = 0.01; // kd
        x[11] = 0.0;  // roll
        // Wind velocity components (initially zero)
        x[12] = 0.0;  // wvx (east wind)
        x[13] = 0.0;  // wvy (up wind)
        x[14] = 0.0;  // wvz (north wind)
        // Wind-based wingsuit parameters
        x[15] = 0.01; // klwind
        x[16] = 0.01; // kdwind
        x[17] = 0.0;  // rollwind

        // Initial covariance
        P = LinearAlgebra.identity(18);
        for (int i = 0; i < 9; i++) P[i][i] = 1000.0;
        P[9][9]   = 0.1;   // kl
        P[10][10] = 0.1;   // kd
        P[11][11] = 0.005; // roll
        // Wind velocity components (higher uncertainty initially)
        P[12][12] = 100.0; // wvx
        P[13][13] = 100.0; // wvy
        P[14][14] = 100.0; // wvz
        // Wind-based wingsuit parameters
        P[15][15] = 0.1;   // klwind
        P[16][16] = 0.1;   // kdwind
        P[17][17] = 0.005; // rollwind

        // Process noise
        Q = LinearAlgebra.identity(18);
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
        // wind velocity (moderate)
        Q[12][12] = 0.1; Q[13][13] = 0.1; Q[14][14] = 0.1;
        // wind-based wingsuit params (slow)
        Q[15][15] = 0.01;
        Q[16][16] = 0.01;
        Q[17][17] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 1.2; R[1][1] = 1.2; R[2][2] = 1.2;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;

        //smooth20hz
        // Process noise
        Q = LinearAlgebra.identity(18);
        // pos
        Q[0][0] = 1.0; Q[1][1] =1.0; Q[2][2] = 1.0;
        // vel
        Q[3][3] = 0.4226; Q[4][4] = 0.4226; Q[5][5] = 0.4226;
        // accel (higher)
        Q[6][6] = 5; Q[7][7] = 5; Q[8][8] = 5;
        // wingsuit params (slow)
        Q[9][9]   = 0.01;
        Q[10][10] = 0.01;
        Q[11][11] = 0.001;
        // wind velocity (moderate)
        Q[12][12] = 0.1; Q[13][13] = 0.1; Q[14][14] = 0.1;
        // wind-based wingsuit params (slow)
        Q[15][15] = 0.01;
        Q[16][16] = 0.01;
        Q[17][17] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 1.21; R[1][1] = 1.21; R[2][2] = 1.21;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;
/*
// 5 hz settings
        // Process noise
        Q = LinearAlgebra.identity(18);
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
        // wind velocity (moderate)
        Q[12][12] = 0.1; Q[13][13] = 0.1; Q[14][14] = 0.1;
        // wind-based wingsuit params (slow)
        Q[15][15] = 0.01;
        Q[16][16] = 0.01;
        Q[17][17] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 8.7; R[1][1] = 8.7; R[2][2] = 8.7;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;

// other5 hz settings
        // Process noise
        Q = LinearAlgebra.identity(18);
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
        // wind velocity (moderate)
        Q[12][12] = 0.1; Q[13][13] = 0.1; Q[14][14] = 0.1;
        // wind-based wingsuit params (slow)
        Q[15][15] = 0.01;
        Q[16][16] = 0.01;
        Q[17][17] = 0.001;

        // Measurement noise (position+velocity)
        R = LinearAlgebra.identity(6);
        // Position (GPS)
        R[0][0] = 4.7; R[1][1] = 4.7; R[2][2] = 4.7;
        // Velocity (GPS)
        R[3][3] = 2.25;  R[4][4] = 2.25;  R[5][5] = 2.25;
*/

        // Initialize wind filter
        windFilter = new WindKalmanFilter();
        windFilter.setPolar(polar);
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
            // Initialize wind estimates to zero
            x[12] = 0.0; x[13] = 0.0; x[14] = 0.0;
            x[15] = 0.01; x[16] = 0.01; x[17] = 0.0;
            lastGps = gps;
            return;
        }

        // Measurement preparation
        final Vector3 pMeas = gpsToEnu(gps);
        final double vx = gps.vE, vy = gps.climb, vz = gps.vN;

        // time step
        final double dt = Math.max(0.0, 1e-3 * (tNow - lastGps.millis));

        // save wse
        final double Kl = x[9];       // wingsuit parameter steps
        final double Kd = x[10];
        final double Roll = x[11];


        // Predict to now
        if (dt > 0.0) predict(dt);


        // --- Measurement update (position + velocity) ---
        // H: 6x18 maps [p,v] to measurement
        final double[][] H = LinearAlgebra.zeros(6, 18);
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
        // Only update the states that are actually being measured (position, velocity, acceleration, wingsuit params)
        // Do NOT update wind states (12-17) from GPS measurements - they should only be updated by wind estimation
        for (int i = 0; i < 12; i++) x[i] += Ky[i];  // Update states 0-11 only

        // save kalman steps for smoothing
        kalmanStep.x = Ky[0];       // position step
        kalmanStep.y = Ky[1];
        kalmanStep.z = Ky[2];
        kalmanVelStep.x = Ky[3];    // velocity step
        kalmanVelStep.y = Ky[4];
        kalmanVelStep.z = Ky[5];
        kalmanAccelStep.x = Ky[6];  // acceleration step
        kalmanAccelStep.y = Ky[7];
        kalmanAccelStep.z = Ky[8];
        // Wind velocity steps should NOT come from GPS measurements - set to zero
        //kalmanWindStep.x = 0.0;  // GPS measurement shouldn't affect wind
        //kalmanWindStep.y = 0.0;
        //kalmanWindStep.z = 0.0;

        // For plotting: WSE accel from measured velocity
        //aWSE = calculateWingsuitAcceleration(new Vector3(vx, vy, vz), new WSEParams(x[9], x[10], x[11]));

        // P = (I - K H) P
        final double[][] KH   = LinearAlgebra.mul(K, H);
        final double[][] I_KH = LinearAlgebra.sub(LinearAlgebra.identity(18), KH);
        P = LinearAlgebra.mul(I_KH, P);

        // Save old wingsuit parameters before update
        final double oldKl = x[9];
        final double oldKd = x[10];
        final double oldRoll = x[11];
        // Save old wind parameters before update
        final double oldWvx = x[12];
        final double oldWvy = x[13];
        final double oldWvz = x[14];
        final double oldKlWind = x[15];
        final double oldKdWind = x[16];
        final double oldRollWind = x[17];

        // Update wingsuit parameters from current Kalman v,a (in ENU)
        updateWingsuitParameters();

        // Wind estimation is now handled in updateWingsuitParameters() method
        final double currentSpeed = Math.sqrt(x[3] * x[3] + x[4] * x[4] + x[5] * x[5]);
        if (Log.isLoggable(TAG, Log.DEBUG) || true) {
            Log.d(TAG, String.format("Before wind estimation: speed=%.1f m/s, wind=[%.3f,%.3f,%.3f]",
                    currentSpeed, x[12], x[13], x[14]));
        }

        // Calculate wingsuit parameter steps as difference between new and old values
        kalmanKlStep = x[9] - oldKl;       // wingsuit parameter steps
        kalmanKdStep = x[10] - oldKd;
        kalmanRollStep = x[11] - oldRoll;
        // Calculate wind velocity steps
        kalmanWindStep.x = x[12] - oldWvx;  // wind velocity step (east)
        kalmanWindStep.y = x[13] - oldWvy;  // wind velocity step (up)
        kalmanWindStep.z = x[14] - oldWvz;  // wind velocity step (north)

        // Debug: Log wind step calculation
        if (Log.isLoggable(TAG, Log.DEBUG) || true) {
            Log.d(TAG, String.format("Wind step: old=[%.3f,%.3f,%.3f] new=[%.3f,%.3f,%.3f] step=[%.3f,%.3f,%.3f]",
                    oldWvx, oldWvy, oldWvz, x[12], x[13], x[14], kalmanWindStep.x, kalmanWindStep.y, kalmanWindStep.z));
        }
        // Calculate wind parameter steps
        kalmanKlWindStep = x[15] - oldKlWind;     // wind-based wingsuit parameter steps
        kalmanKdWindStep = x[16] - oldKdWind;
        kalmanRollWindStep = x[17] - oldRollWind;

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
        final double[][] Q_scaled = new double[18][18];
        for (int i = 0; i < 18; i++) {
            for (int j = 0; j < 18; j++) {
                Q_scaled[i][j] = this.Q[i][j] * deltaTime;
            }
        }
        P = LinearAlgebra.add(FPFT, Q_scaled);
    }

    // Cached predicted state for 90Hz updates
    private KFState cachedPredictedState = null;
    private long cachedPredictionTime = 0;

    // Cached position delta for 90Hz updates
    private Vector3 cachedDelta = null;
    private long cachedDeltaTime = 0;
    // Implement MotionEstimator interface method
    @Override
    public Vector3 predictDelta(long currentTimeMillis) {
        return predictDelta(currentTimeMillis, false);
    }
    /** Predict the state delta from last update (without mutating this filter) at a given wallclock time in ms. */
    public Vector3 predictDelta(long currentTimeMillis, boolean shouldUpdateCache) {


        if (lastGps == null) {
            cachedPredictedState = toState(x);
            cachedPredictionTime = currentTimeMillis;
            cachedDelta = new Vector3(0, 0, 0);
            cachedDeltaTime = currentTimeMillis;
            return cachedDelta;
        }
        // If not updating cache, always return the last cached delta (may be stale)
        if (!shouldUpdateCache && cachedDelta != null) {
            return cachedDelta;
        }
        // Correct for phone/gps time skew
        final double adjustedCurrentTime = TimeOffset.phoneToGpsTime(currentTimeMillis);
        double dt = (adjustedCurrentTime - lastGps.millis) * 1e-3;

        // clamp if asked for the past
        if (dt <= 0) {
            cachedPredictedState = toState(x);
            cachedPredictionTime = currentTimeMillis;
            cachedDelta = new Vector3(0, 0, 0);
            cachedDeltaTime = currentTimeMillis;
            return cachedDelta;
        }

        // Clone state and step forward in small increments
        double[] s = x.clone();
        double remaining = dt;
        while (remaining > 0.0) {
            final double step = Math.min(remaining, MAX_STEP);
            s = integrateState(s, step);
            remaining -= step;
        }

        Vector3 delta;
        if(!stepSmoothing) {
            delta = new Vector3(s[0]-x[0], s[1]-x[1], s[2]-x[2]);
            cachedPredictedState = toState(s);
            cachedPredictionTime = currentTimeMillis;
            cachedDelta = delta;
            cachedDeltaTime = currentTimeMillis;
            return delta;
        }

        // Apply Kalman gain smoothing to position, velocity, and acceleration
        double alpha = 1-(dt * Services.location.refreshRate.refreshRate);
        if (alpha < 0) alpha = 0;
        if (alpha > 1) alpha = 1;

        // Smooth position, velocity, and acceleration with Kalman step
        Vector3 ps = kalmanStep.mul(alpha);
        Vector3 vs = kalmanVelStep.mul(alpha);
        Vector3 as = kalmanAccelStep.mul(alpha);

        // Apply smoothing to predicted state
        s[0] = s[0] - ps.x;  // position
        s[1] = s[1] - ps.y;
        s[2] = s[2] - ps.z;
        s[3] = s[3] - vs.x;  // velocity
        s[4] = s[4] - vs.y;
        s[5] = s[5] - vs.z;
        s[6] = s[6] - as.x;  // acceleration
        s[7] = s[7] - as.y;
        s[8] = s[8] - as.z;
        // Smooth wingsuit parameters
        s[9] = s[9] - (kalmanKlStep * alpha);    // kl
        s[10] = s[10] - (kalmanKdStep * alpha);  // kd
        s[11] = s[11] - (kalmanRollStep * alpha); // roll
        // Smooth wind velocity
        Vector3 ws = kalmanWindStep.mul(alpha);
        s[12] = s[12] - ws.x;  // wvx
        s[13] = s[13] - ws.y;  // wvy
        s[14] = s[14] - ws.z;  // wvz
        // Smooth wind-based wingsuit parameters
        s[15] = s[15] - (kalmanKlWindStep * alpha);    // klwind
        s[16] = s[16] - (kalmanKdWindStep * alpha);    // kdwind
        s[17] = s[17] - (kalmanRollWindStep * alpha);  // rollwind

        // Compute and cache
        delta = new Vector3(s[0]-x[0], s[1]-x[1], s[2]-x[2]);
        cachedPredictedState = toState(s);
        cachedPredictionTime = currentTimeMillis;
        cachedDelta = delta;
        cachedDeltaTime = currentTimeMillis;
        return delta;
    }

    /**
     * Get the predicted state for the given time, caching the result for efficiency.
     * If the requested time is different from the last cached prediction, recompute and cache it.
     * Always returns the up-to-date predicted state for the requested time.
     */
    /**
     * Always returns the last cached predicted state (may be stale).
     */
    public KFState getCachedPredictedState(long currentTimeMillis) {
        if(cachedPredictedState != null ) {//&& cachedPredictionTime == currentTimeMillis) {
            return cachedPredictedState;
        }
        //cachedPredictedState = predictDelta(currentTimeMillis)
        return getState();
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
        //  x[6] = ax; x[7] = ay; x[8] = az;
        // x[9..17] unchanged in prediction (wingsuit params, wind velocity, wind params)
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
        // out[6] = ax; out[7] = ay; out[8] = az;
        // wingsuit params, wind velocity, and wind params unchanged (indices 9-17)
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
        int fm = Services.flightComputer != null ? Services.flightComputer.flightMode : FlightMode.MODE_WINGSUIT;
        //Log.d(TAG, "flight mode: "+fm);
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

        // Update standard wingsuit parameters (no wind consideration)
        final WSEParams updated = calculateWingsuitParameters(vKal, aKal, new WSEParams(x[9], x[10], x[11]));
        x[9] = updated.kl();
        x[10] = updated.kd();
        x[11] = updated.roll();

        // Use wind velocity from WindSystem altitude-based wind data
        try {
            if (lastGps != null) {
                // Get current flight mode
                int flightMode = Services.flightComputer != null ? Services.flightComputer.flightMode : FlightMode.MODE_WINGSUIT;

                // Get wind estimate from WindSystem at current altitude
                final double currentAltitude = lastGps.altitude_gps;
                final WindSystem.WindEstimate windEstimate = WindSystem.Companion.getInstance().getWindAtAltitude(currentAltitude, flightMode);

                // Update wind state variables with the wind estimate
                final Vector3 windVector = windEstimate.getWindVector(); // Already in ENU format
                x[12] = windVector.x; // wvx (east wind)
                x[13] = windVector.y; // wvy (up wind)
                x[14] = windVector.z; // wvz (north wind)

                // Calculate airspeed (velocity relative to air mass)
                final Vector3 airspeedVector = new Vector3(
                        vKal.x + windVector.x, // East airspeed (subtract wind)
                        vKal.y + windVector.y, // Up airspeed (subtract wind)
                        vKal.z + windVector.z  // North airspeed (subtract wind)
                );

                // Calculate wind-adjusted wingsuit parameters using airspeed
                final double airspeed = Math.sqrt(airspeedVector.x * airspeedVector.x +
                        airspeedVector.y * airspeedVector.y +
                        airspeedVector.z * airspeedVector.z);

                if (airspeed > 1.0) {
                    final WSEParams windUpdated = calculateWingsuitParameters(airspeedVector, aKal,
                            new WSEParams(x[15], x[16], x[17]));
                    x[15] = windUpdated.kl(); // klwind
                    x[16] = windUpdated.kd(); // kdwind
                    x[17] = windUpdated.roll(); // rollwind
                }
//
//                // Test inverse WSE calculation to compare wind estimates using WindKalmanFilter
//                if (airspeed > 1.0) {
//                    // Set the current polar in the wind filter before optimization
//                    windFilter.setPolar(polar);
//
//                    // Use WSE horizontal calculation instead of WindKalmanFilter for performance
//                    final WSE.AirspeedResult windUpdated = WSE.calculateHorizontalAirspeedFromKLKD(
//                            vKal, // velocity
//                            aKal, // acceleration
//                            new WSEParams(x[15], x[16], x[17]) // WSE parameters (kl, kd, rollwind)
//                    );
//
//                    // Convert airspeed result to wind vector (wind = airspeed - ground_velocity)
//                    final Vector3 windFromWSE = new Vector3(
//                            windUpdated.airspeed.x - vKal.x, // wind_east = airspeed_east - ground_velocity_east
//                            windUpdated.airspeed.y - vKal.y, // wind_up = airspeed_up - ground_velocity_up
//                            windUpdated.airspeed.z - vKal.z  // wind_north = airspeed_north - ground_velocity_north
//                    );
//                    x[12] = windFromWSE.x; // wvx (east wind)
//                    x[13] = windFromWSE.y; // wvy (up wind)
//                    x[14] = windFromWSE.z; // wvz (north wind)
//                    x[15] = windUpdated.wseParams.kl(); // klwind
//                    x[16] = windUpdated.wseParams.kd(); // kdwind
//                    x[17] = windUpdated.wseParams.roll(); // rollwind
//                    final Vector3 calcAirspeedVec = windFromWSE; // The WSE method returns airspeed directly
//                    final double calcAirspeedMag = Math.sqrt(calcAirspeedVec.x * calcAirspeedVec.x +
//                            calcAirspeedVec.y * calcAirspeedVec.y +
//                            calcAirspeedVec.z * calcAirspeedVec.z);
//
//                    // Check if wind vectors differ significantly and log error
//                    final double windDiffX = Math.abs(windVector.x - windFromWSE.x);
//                    final double windDiffY = Math.abs(windVector.y - windFromWSE.y);
//                    final double windDiffZ = Math.abs(windVector.z - windFromWSE.z);
//                    final double windDiffMagnitude = Math.sqrt(windDiffX * windDiffX + windDiffY * windDiffY + windDiffZ * windDiffZ);
//
//                    if (windDiffMagnitude > 1.0) { // Threshold for significant difference (1 m/s)
//                        Log.e(TAG, "WSE calculateHorizontalAirspeedFromKLKD ERROR!");
//                        Log.e(TAG, String.format("Wind difference: actual=[%.1f,%.1f,%.1f], calc=[%.1f,%.1f,%.1f], diff_mag=%.1f m/s",
//                                windVector.x, windVector.y, windVector.z,
//                                windFromWSE.x, windFromWSE.y, windFromWSE.z, windDiffMagnitude));
//                    }
//
//                    Log.d(TAG, String.format("WSE Horizontal Test: actual_airspeed=%.1f, calc_airspeed=%.1f, " +
//                                    "actual_wind=[%.1f,%.1f,%.1f], calc_wind=[%.1f,%.1f,%.1f], diff=%.1f",
//                            airspeed, calcAirspeedMag,
//                            windVector.x, windVector.y, windVector.z,
//                            windFromWSE.x, windFromWSE.y, windFromWSE.z, windDiffMagnitude));
//                }
//
//                if (Log.isLoggable(TAG, Log.DEBUG) || true) {
//                    Log.d(TAG, String.format("WindSystem: alt=%.0fm, wind=[%.1f,%.1f,%.1f] m/s, airspeed=%.1f m/s, klwind=%.3f, kdwind=%.3f (%s)",
//                            currentAltitude, windVector.x, windVector.y, windVector.z, airspeed, x[15], x[16], windEstimate.getSource().getDisplayName()));
//                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to update wind parameters from WindSystem: " + e.getMessage());
            // Keep existing wind parameters if WindSystem fails
        }
    }

    /**
     * Update wind estimation using direct optimization results
     * Uses the optimal wind velocity directly from optimizeWindVelocity()
     */
    private void updateWindEstimation(double deltaTime) {
        // Get current state from main filter
        final Vector3 velocity = new Vector3(x[3], x[4], x[5]);
        final Vector3 acceleration = new Vector3(x[6], x[7], x[8]);
        final double currentRoll = x[11];

        // Debug: Log that wind estimation is being called
        if (Log.isLoggable(TAG, Log.DEBUG) || true) {
            final double velMag = Math.sqrt(velocity.x * velocity.x + velocity.y * velocity.y + velocity.z * velocity.z);
            Log.d(TAG, String.format("updateWindEstimation called: vel=[%.1f,%.1f,%.1f] (%.1f m/s), accel=[%.1f,%.1f,%.1f], roll=%.1f°",
                    velocity.x, velocity.y, velocity.z, velMag, acceleration.x, acceleration.y, acceleration.z, Math.toDegrees(currentRoll)));
        }

        // Find optimal wind velocity that minimizes acceleration residual
        final WindKalmanFilter.WindOptimizationResult optimalWind = windFilter.optimizeWindVelocity(velocity, acceleration, currentRoll);

        // Debug: Log wind values before updating state
        if (Log.isLoggable(TAG, Log.DEBUG) || true) {
            Log.d(TAG, String.format("Wind state update: old=[%.3f,%.3f,%.3f] -> optimal=[%.3f,%.3f,%.3f]",
                    x[12], x[13], x[14], optimalWind.windVelocity.x, optimalWind.windVelocity.y, optimalWind.windVelocity.z));
        }

        // Update the main filter's wind state directly with optimal wind values
        x[12] = optimalWind.windVelocity.x; // wvx (east wind)
        x[13] = optimalWind.windVelocity.y; // wvy (up wind)
        x[14] = optimalWind.windVelocity.z; // wvz (north wind)

        // Convert cl, cd coefficients to kl, kd for display compatibility
        // Using atmospheric density with 10°C offset (same as WindKalmanFilter)
        final double rho = AtmosphericModel.calculateDensity((float) lastGps.altitude_gps, 10f);
        final double k = 0.5 * rho * polar.s / polar.m;
        final double GRAVITY = 9.81;

        // Update wind-adjusted wingsuit parameters (x[15-17]) using optimal results
        x[15] = optimalWind.bestCoeff.cl * k / GRAVITY; // klwind
        x[16] = optimalWind.bestCoeff.cd * k / GRAVITY; // kdwind
        x[17] = optimalWind.bestRoll; // rollwind (use actual roll from wind optimization)

        if (Log.isLoggable(TAG, Log.DEBUG) || true) { // Force logging for debugging
            final double windSpeed = Math.sqrt(optimalWind.windVelocity.x * optimalWind.windVelocity.x + optimalWind.windVelocity.y * optimalWind.windVelocity.y + optimalWind.windVelocity.z * optimalWind.windVelocity.z);
            Log.d(TAG, String.format("WindOptimal: wind=[%.3f,%.3f,%.3f] m/s (%.3f m/s), AoA: %.1f°, klwind=%.4f, kdwind=%.4f, residual: %.3f",
                    optimalWind.windVelocity.x, optimalWind.windVelocity.y, optimalWind.windVelocity.z, windSpeed, optimalWind.bestAoA, x[15], x[16], optimalWind.minResidual));
        }
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
                new Vector3(s[0], s[1], s[2]),     // position
                new Vector3(s[3], s[4], s[5]),     // velocity
                new Vector3(s[6], s[7], s[8]),     // acceleration
                new Vector3(s[6], s[7], s[8]),     // aMeasured (same as acceleration)
                new Vector3(s[6], s[7], s[8]),     // aWSE (same as acceleration)
                s[9], s[10], s[11],                // kl, kd, roll
                new Vector3(s[12], s[13], s[14]),  // wind velocity
                s[15], s[16], s[17]                // klwind, kdwind, rollwind
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
            double roll,
            Vector3 windVelocity,    // Wind velocity components (ENU)
            double klwind,           // Lift coefficient with wind
            double kdwind,           // Drag coefficient with wind
            double rollwind          // Roll with wind consideration
    ) {}

    public double ld() {
        return getState().kl / getState().kd;
    }

    public MLocation getLastUpdate() {
        return lastGps;
    }

    // Wind estimation getter methods (based on TypeScript kalman.ts integration)

    /** Get the current wind estimate from the optimized wind filter */
    public Vector3 getWindEstimate() {
        return new Vector3(x[12], x[13], x[14]);
    }

    /** Get the wind-adjusted aerodynamic parameters (kl, kd) */
    public Vector3 getWindAdjustedParameters() {
        return new Vector3(x[15], x[16], x[17]); // klwind, kdwind, rollwind
    }

    // Wind filter control methods

    /** Set wind filter process noise (how quickly wind changes) */
    public void setWindProcessNoise(double noise) {
        windFilter.setProcessNoise(noise);
    }

    /** Set wind filter measurement noise (trust in wind estimates) */
    public void setWindMeasurementNoise(double noise) {
        windFilter.setMeasurementNoise(noise);
    }

    /** Get current wind filter process noise */
    public double getWindProcessNoise() {
        return windFilter.getProcessNoise();
    }

    /** Get current wind filter measurement noise */
    public double getWindMeasurementNoise() {
        return windFilter.getMeasurementNoise();
    }

    /** Set custom polar for wind estimation */
    public void setPolar(PolarLibrary.WingsuitPolar polar) {
        this.polar = polar;
        windFilter.setPolar(polar);
    }

    /** Reset wind estimation to initial state */
    public void resetWindEstimation() {
        windFilter.reset();
        // Reset wind state variables
        x[12] = 0.0; // wvx (east wind)
        x[13] = 0.0; // wvy (up wind) 
        x[14] = 0.0; // wvz (north wind)
        x[15] = 0.01; // klwind
        x[16] = 0.01; // kdwind
        x[17] = 0.0; // rollwind
    }

}
