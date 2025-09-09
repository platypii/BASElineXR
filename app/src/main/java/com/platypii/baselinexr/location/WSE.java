package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.util.Numbers.isReal;
import static java.lang.Math.signum;

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
            if (Math.abs(rollArg) <= 1.0) {
                double rollMagnitude = Math.acos(rollArg);
                double rollSign = signum(liftN * -vE + liftE * vN);
                //use roll if it is a number and between-PI to PI
                if(isReal(rollSign * rollMagnitude)) // This is line 104
                    roll = rollSign * rollMagnitude;
            }
        }
        // check KL and KD are finite
        if(Double.isInfinite(kl) || Double.isInfinite(kd) || Double.isNaN(kl) || Double.isNaN(kd)){
            return new WSEParams(wseParams.kl(), wseParams.kd(), roll);
        }
        return new WSEParams(kl, kd, roll);
    }
}