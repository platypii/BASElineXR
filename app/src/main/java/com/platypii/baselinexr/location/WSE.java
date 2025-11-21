package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.util.Numbers.isReal;
import static java.lang.Math.signum;

import android.util.Log;
import com.platypii.baselinexr.util.tensor.Vector3;

public class WSE {
    private static final double G = 9.81;

    /**
     * Calculate wingsuit acceleration based on velocity and wingsuit parameters
     * Input: ENU coordinates (x=East, y=Up, z=North)
     * Output: ENU acceleration
     *
     * @return Vector3 with (x=East, y=Up, z=North) accelerations in ENU coordinates
     */
    public static Vector3 calculateWingsuitAcceleration(
            Vector3 velocity,
            WSEParams wseParams
    ) {
        double vN = velocity.z;
        double vE = velocity.x;
        double vD = -velocity.y;
        double kl = wseParams.kl();
        double kd = wseParams.kd();
        double roll = wseParams.roll();

        double v = velocity.magnitude();
        if (v < 0.1) {
            return new Vector3(0, 0, 0); // Handle near-zero velocity on ground
        }

        double groundSpeed = Math.sqrt(vN * vN + vE * vE);
        if (groundSpeed < 0.1) {
            return new Vector3(0, 0, 0); // Handle near-zero groundspeed
        }

        double cosRoll = Math.cos(roll);
        double sinRoll = Math.sin(roll);

        // Calculate acceleration in NDE coordinates
        double aN = G * (kl * v / groundSpeed * (vN * vD * cosRoll - vE * v * sinRoll) - kd * vN * v);
        double aD = G * (1 - kl * v * groundSpeed * cosRoll - kd * vD * v);
        double aE = G * (kl * v / groundSpeed * (vE * vD * cosRoll + vN * v * sinRoll) - kd * vE * v);

        // Convert back to ENU coordinates
        return new Vector3(aE, -aD, aN);
    }

    /**
     * Calculate wingsuit parameters from measured acceleration
     * Input: NDE coordinates, Output: [kl, kd, roll]
     */
    public static WSEParams calculateWingsuitParameters(
            Vector3 velocity,
            Vector3 accel,
            WSEParams wseParams
    ) {
        double vN = velocity.z;
        double vE = velocity.x;
        double vD = -velocity.y;
        double accelN = accel.z;
        double accelE = accel.x;
        double accelD = -accel.y;
        double gravity = 9.81;
        double accelDminusG = accelD - gravity;

        // Calculate acceleration due to drag (projection onto velocity)
        double vel = Math.sqrt(vN * vN + vE * vE + vD * vD);
        if (vel < 1.0) {
            return wseParams; // Return current values at low speeds
        }

        double proj = (accelN * vN + accelE * vE + accelDminusG * vD) / vel;

        double dragN = proj * vN / vel;
        double dragE = proj * vE / vel;
        double dragD = proj * vD / vel;
        // Calculate correct sign for drag
        double dragSign = -signum(dragN * vN + dragE * vE + dragD * vD);

        double accelDrag = dragSign * Math.sqrt(dragN * dragN + dragE * dragE + dragD * dragD);

        // Calculate acceleration due to lift (rejection from velocity)
        double liftN = accelN - dragN;
        double liftE = accelE - dragE;
        double liftD = accelDminusG - dragD;
        double accelLift = Math.sqrt(liftN * liftN + liftE * liftE + liftD * liftD);

        // Calculate wingsuit coefficients
        double kl = accelLift / gravity / vel / vel;
        double kd = accelDrag / gravity / vel / vel;
        double roll = wseParams.roll();

        // Calculate roll angle
        double smoothGroundspeed = Math.sqrt(vN * vN + vE * vE);

        if (smoothGroundspeed > 1.0) {
            double rollArg = (1 - accelD / gravity - kd * vel * vD) / (kl * smoothGroundspeed * vel);
            //if (Math.abs(rollArg) <= 1.0) {
            double rollMagnitude = Math.acos(rollArg);
            double rollSign = signum(liftN * -vE + liftE * vN);
            //use roll if it is a number and between-PI to PI
            if(isReal(rollSign * rollMagnitude)) // This is line 104
                roll = rollSign * rollMagnitude;
            //}
        }
        // check KL and KD are finite
        if(Double.isInfinite(kl) || Double.isInfinite(kd) || Double.isNaN(kl) || Double.isNaN(kd)){
            return new WSEParams(wseParams.kl(), wseParams.kd(), roll);
        }
        return new WSEParams(kl, kd, roll);
    }

    /**
     * Find airspeed vector that produces given KL/KD when acceleration is projected onto it
     * Uses the inverse of the projection technique from calculateWingsuitParameters()
     *
     * @param velocity Ground velocity vector (ENU coordinates)
     * @param accel Measured acceleration vector (ENU coordinates)
     * @param wseParams Target WSE parameters (KL, KD, and roll as fallback)
     * @return Result containing airspeed vector and WSEParams with calculated roll
     */
    public static AirspeedResult calculateHorizontalAirspeedFromKLKD(
            Vector3 velocity,
            Vector3 accel,
            WSEParams wseParams
    ) {
        // Extract parameters
        double targetKl = wseParams.kl();
        double targetKd = wseParams.kd();
        double fallbackRoll = wseParams.roll();

        // Debug logging
        Log.d("WSE", String.format("calculateAirspeedFromKLKD: targetKl=%.8f, targetKd=%.8f, roll=%.2f",
                targetKl, targetKd, fallbackRoll));
        Log.d("WSE", String.format("Input velocity: vx=%.2f, vy=%.2f, vz=%.2f",
                velocity.x, velocity.y, velocity.z));
        Log.d("WSE", String.format("Input accel: ax=%.3f, ay=%.3f, az=%.3f",
                accel.x, accel.y, accel.z));

        // Quick validity checks
        if (Math.abs(targetKl) < 1e-12 || Math.abs(targetKd) < 1e-12) {
            // Invalid KL/KD values, use fallback roll
            Log.d("WSE", "Invalid KL/KD values, returning zero airspeed");
            return new AirspeedResult(new Vector3(0, 0, 0), new WSEParams(targetKl, targetKd, fallbackRoll));
        }

        // Convert to NDE coordinates
        double vN = velocity.z;
        double vE = velocity.x;
        double vD = -velocity.y;
        double accelN = accel.z;
        double accelE = accel.x;
        double accelD = -accel.y;
        double gravity = 9.81;
        double accelDminusG = accelD - gravity;

        // We need to find airspeed vector (avN, avE, avD) such that:
        // - Drag projection: targetKd * gravity * airspeed^2 = |proj_drag|
        // - Lift magnitude: targetKl * gravity * airspeed^2 = |rejection_lift|

        // The key insight: if we know the total airspeed magnitude, we can solve for direction
        // Let's work backwards from the constraint equations

        // Total acceleration magnitude (minus gravity in vertical)
        Vector3 accelMinusG = new Vector3(accelE, accelDminusG, accelN);
        double totalAccelMag = accelMinusG.magnitude();

        Log.d("WSE", String.format("Total accel magnitude: %.3f", totalAccelMag));

        if (totalAccelMag < 0.1) {
            // Minimal acceleration - return ground velocity as airspeed, use fallback roll
            Log.d("WSE", "Minimal acceleration detected, returning ground velocity as airspeed");
            return new AirspeedResult(velocity, new WSEParams(targetKl, targetKd, fallbackRoll));
        }

        // From the WSE constraint: |drag| = kd * g * v^2, |lift| = kl * g * v^2
        // And: |drag|^2 + |lift|^2 = |total_accel|^2 (approximately, for small angles)
        // So: (kd * g * v^2)^2 + (kl * g * v^2)^2 = |total_accel|^2
        // Therefore: v^2 = |total_accel| / (g * sqrt(kd^2 + kl^2))

        double klkdMag = Math.sqrt(targetKl * targetKl + targetKd * targetKd);
        if (klkdMag < 0.000001) {
            // KL/KD magnitude too small - return ground velocity as airspeed, use fallback roll
            Log.d("WSE", "KL/KD magnitude too small, returning ground velocity as airspeed");
            return new AirspeedResult(velocity, new WSEParams(targetKl, targetKd, fallbackRoll));
        }

        double airspeedMagnitude = Math.sqrt(totalAccelMag / (gravity * klkdMag));

        Log.d("WSE", String.format("klkdMag: %.6f, calculated airspeedMagnitude: %.2f", klkdMag, airspeedMagnitude));

        // New systematic approach: Assume zero vertical wind and solve 2x2 system
        // Step 1: Calculate horizontal airspeed assuming zero vertical wind
        double horizontalAirspeed = Math.sqrt(airspeedMagnitude * airspeedMagnitude - vD * vD);

        Log.d("WSE", String.format("Horizontal airspeed: %.2f, vD: %.2f", horizontalAirspeed, vD));

        if (horizontalAirspeed < 1.0) {
            // Very low horizontal airspeed - return ground velocity as approximation
            Log.d("WSE", "Very low horizontal airspeed, using ground velocity");
            return new AirspeedResult(velocity, new WSEParams(targetKl, targetKd, fallbackRoll));
        }

        // Step 2: Calculate roll angle magnitude (we'll determine sign later)
        double rollArg = (1 - accelD / gravity - targetKd * airspeedMagnitude * vD) / (targetKl * horizontalAirspeed * airspeedMagnitude);

        //if (Math.abs(rollArg) > 1.0) {

        //}

        double rollMagnitude = Math.acos(Math.abs(rollArg));

        // Step 3: Try both roll signs and solve the 2x2 system for airspeedN and airspeedE
        Vector3 airspeed = null;
        double bestRoll = 0.0;
        double fallbackRollSign = Math.signum(fallbackRoll);
        Vector3 matchingSignSolution = null;
        double matchingSignRoll = 0.0;
        Vector3 fallbackSolution = null;
        double fallbackSolutionRoll = 0.0;
        double minWindMagnitude = Double.MAX_VALUE;

        Log.d("WSE", String.format("Fallback roll: %.3f°, sign: %.0f", Math.toDegrees(fallbackRoll), fallbackRollSign));

        for (int rollSign = -1; rollSign <= 1; rollSign += 2) {
            double testRoll = rollSign * rollMagnitude;
            double cosRoll = Math.cos(testRoll);
            double sinRoll = Math.sin(testRoll);

            // Solve the system using the WSE acceleration equations:
            // aN = G * (kl * v / horizontalAirspeed * (vN * vD * cosRoll - vE * v * sinRoll) - kd * vN * v)
            // aE = G * (kl * v / horizontalAirspeed * (vE * vD * cosRoll + vN * v * sinRoll) - kd * vE * v)

            double klTerm = targetKl * airspeedMagnitude / horizontalAirspeed;
            double kdTerm = targetKd * airspeedMagnitude;

            // Coefficients for the 2x2 system: A * [vN; vE] = B
            double a11 = klTerm * vD * cosRoll - kdTerm;  // coefficient of vN in first equation
            double a12 = -klTerm * airspeedMagnitude * sinRoll;  // coefficient of vE in first equation
            double a21 = klTerm * airspeedMagnitude * sinRoll;   // coefficient of vN in second equation
            double a22 = klTerm * vD * cosRoll - kdTerm;  // coefficient of vE in second equation

            double b1 = accelN / gravity;  // RHS of first equation
            double b2 = accelE / gravity;  // RHS of second equation

            // Debug the 2x2 system coefficients
            Log.d("WSE", String.format("Roll sign %d: roll=%.3f, cos=%.3f, sin=%.3f",
                    rollSign, testRoll*180/Math.PI, cosRoll, sinRoll));
            Log.d("WSE", String.format("Matrix A: [[%.3f, %.3f], [%.3f, %.3f]], B: [%.3f, %.3f]",
                    a11, a12, a21, a22, b1, b2));

            // Solve 2x2 system using Cramer's rule
            double det = a11 * a22 - a12 * a21;

            Log.d("WSE", String.format("Determinant: %.6f", det));

            if (Math.abs(det) < 1e-12) {
                Log.d("WSE", "Singular matrix, skipping this roll sign");
                continue; // Singular matrix, try other roll sign
            }

            double testVN = (b1 * a22 - b2 * a12) / det;
            double testVE = (a11 * b2 - a21 * b1) / det;

            // Check if this solution makes sense by testing against the actual WSE equations
            double testHorizontalSpeed = Math.sqrt(testVN * testVN + testVE * testVE);
            double horizontalError = Math.abs(testHorizontalSpeed - horizontalAirspeed);

            // Convert to ENU for testing
            Vector3 testAirspeed = new Vector3(testVE, -vD, testVN);  // ENU: (East, Up, North)
            WSEParams testParams = new WSEParams(targetKl, targetKd, testRoll);

            // Calculate what acceleration this airspeed would produce
            Vector3 predictedAccel = calculateWingsuitAcceleration(testAirspeed, testParams);

            // Compare with actual measured acceleration
            double accelError = Math.sqrt(
                    Math.pow(predictedAccel.x - accel.x, 2) +
                            Math.pow(predictedAccel.y - accel.y, 2) +
                            Math.pow(predictedAccel.z - accel.z, 2)
            );

            // Calculate wind vector: wind = airspeed - velocity
            Vector3 testWindVector = new Vector3(
                    testAirspeed.x - velocity.x,  // East wind
                    testAirspeed.y - velocity.y,  // Up wind
                    testAirspeed.z - velocity.z   // North wind
            );
            double testWindMagnitude = testWindVector.magnitude();

            Log.d("WSE", String.format("Roll sign %d: vN=%.2f, vE=%.2f, windMag=%.2f",
                    rollSign, testVN, testVE, testWindMagnitude));
            Log.d("WSE", String.format("  Wind vector: [%.2f, %.2f, %.2f]",
                    testWindVector.x, testWindVector.y, testWindVector.z));

            // Check if this roll sign matches the fallback roll sign
            if (Math.signum(testRoll) == fallbackRollSign && matchingSignSolution == null) {
                matchingSignSolution = testAirspeed;
                matchingSignRoll = testRoll;
                Log.d("WSE", String.format("Found solution matching fallback roll sign: %.2f", testWindMagnitude));
            }

            // Keep track of solution with smallest wind magnitude as backup
            if (testWindMagnitude < minWindMagnitude) {
                minWindMagnitude = testWindMagnitude;
                fallbackSolution = testAirspeed;
                fallbackSolutionRoll = testRoll;
            }
        }

        // Choose solution: prefer matching roll sign, fall back to minimum wind magnitude
        if (matchingSignSolution != null) {
            airspeed = matchingSignSolution;
            bestRoll = matchingSignRoll;
            Log.d("WSE", String.format("Using solution with matching roll sign: %.3f°", Math.toDegrees(bestRoll)));
        } else if (fallbackSolution != null) {
            airspeed = fallbackSolution;
            bestRoll = fallbackSolutionRoll;
            Log.d("WSE", String.format("No matching roll sign, using minimum wind solution: %.3f°", Math.toDegrees(bestRoll)));
        }

        if (airspeed == null) {
            // No valid solution found - use ground velocity approximation
            Log.d("WSE", String.format("No valid solution found (minWind=%.3f), using ground velocity approximation", minWindMagnitude));
            Vector3 groundDir = velocity.normalize();
            airspeed = new Vector3(
                    groundDir.x * airspeedMagnitude,
                    groundDir.y * airspeedMagnitude,
                    groundDir.z * airspeedMagnitude
            );
            bestRoll = fallbackRoll;
        } else {
            Log.d("WSE", String.format("Found valid solution with wind magnitude: %.3f", minWindMagnitude));
        }


        Log.d("WSE", String.format("Final airspeed result: x=%.2f, y=%.2f, z=%.2f, roll=%.2f",
                airspeed.x, airspeed.y, airspeed.z, bestRoll));

        return new AirspeedResult(airspeed, new WSEParams(targetKl, targetKd, bestRoll));
    }

    /**
     * Result container for airspeed calculation
     */
    public static class AirspeedResult {
        public final Vector3 airspeed;  // Airspeed vector in ENU coordinates
        public final WSEParams wseParams;  // WSE parameters with calculated roll

        public AirspeedResult(Vector3 airspeed, WSEParams wseParams) {
            this.airspeed = airspeed;
            this.wseParams = wseParams;
        }
    }

    /**
     * Convert kl/kd coefficients to angle of attack
     * @param kl Lift coefficient
     * @param kd Drag coefficient  
     * @param polar Wingsuit polar data
     * @param rho Air density (kg/m³)
     * @return Angle of attack in radians
     */
    public static double klkdToAoa(double kl, double kd, PolarLibrary.WingsuitPolar polar, double rho) {
        return Math.atan2(kl, kd);
    }
}