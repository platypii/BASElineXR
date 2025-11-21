package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.util.Numbers.isReal;
import static java.lang.Math.signum;

import android.util.Log;
import com.platypii.baselinexr.util.tensor.Vector3;

/**
 * Analytical WSE solver using 4x4 system of equations
 * Solves for 3D airspeed and roll simultaneously without sampling
 */
public class WSEAnalytical {
    private static final double G = 9.81;

    /**
     * Solve for 3D airspeed and roll analytically using a 4x4 system of equations
     * System: 3 WSE equations + airspeed magnitude constraint = 4 equations for 4 unknowns (vN, vE, vD, roll)
     *
     * @param velocity Ground velocity vector (ENU coordinates)
     * @param accel Measured acceleration vector (ENU coordinates)
     * @param wseParams Target WSE parameters (KL, KD, and roll as initial guess)
     * @return Result containing airspeed vector and WSEParams with calculated roll
     */
    public static WSE.AirspeedResult calculateAnalytical4DAirspeedFromKLKD(
            Vector3 velocity,
            Vector3 accel,
            WSEParams wseParams
    ) {
        // Extract parameters
        double targetKl = wseParams.kl();
        double targetKd = wseParams.kd();
        double rollGuess = wseParams.roll();

        // Debug logging
        Log.d("WSEAnalytical", String.format("calculateAnalytical4DAirspeedFromKLKD: targetKl=%.4f, targetKd=%.4f, rollGuess=%.2f",
                targetKl, targetKd, rollGuess));

        // Quick validity checks
        if (Math.abs(targetKl) < 1e-12 || Math.abs(targetKd) < 1e-12) {
            Log.d("WSEAnalytical", "Invalid KL/KD values, returning zero airspeed");
            return new WSE.AirspeedResult(new Vector3(0, 0, 0), new WSEParams(targetKl, targetKd, rollGuess));
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

        // The 4x4 system of equations:
        // Let unknowns be: x1=vN_air, x2=vE_air, x3=vD_air, x4=roll
        //
        // Equation 1 (aN): G * (kl * v / groundSpeed * (x1 * x3 * cos(x4) - x2 * v * sin(x4)) - kd * x1 * v) = accelN
        // Equation 2 (aE): G * (kl * v / groundSpeed * (x2 * x3 * cos(x4) + x1 * v * sin(x4)) - kd * x2 * v) = accelE  
        // Equation 3 (aD): G * (1 - kl * v * groundSpeed * cos(x4) - kd * x3 * v) = accelDminusG + G
        // Equation 4 (magnitude): x1^2 + x2^2 + x3^2 = v^2
        //
        // Where: v = sqrt(x1^2 + x2^2 + x3^2), groundSpeed = sqrt(x1^2 + x2^2)

        // This is a highly nonlinear system due to the trigonometric terms and the coupling between
        // the variables in v and groundSpeed. We need to use Newton-Raphson or similar iterative method.

        // Initial guess - start with ground velocity and roll guess
        double[] x = new double[4];
        x[0] = vN;  // vN_air initial guess
        x[1] = vE;  // vE_air initial guess  
        x[2] = vD;  // vD_air initial guess
        x[3] = rollGuess;  // roll initial guess

        // Newton-Raphson iteration
        int maxIterations = 20;
        double tolerance = 1e-6;
        
        for (int iter = 0; iter < maxIterations; iter++) {
            // Calculate current values
            double vN_air = x[0];
            double vE_air = x[1];
            double vD_air = x[2];
            double roll = x[3];
            
            double v = Math.sqrt(vN_air * vN_air + vE_air * vE_air + vD_air * vD_air);
            double groundSpeed = Math.sqrt(vN_air * vN_air + vE_air * vE_air);
            double cosRoll = Math.cos(roll);
            double sinRoll = Math.sin(roll);
            
            if (v < 0.1 || groundSpeed < 0.1) {
                Log.d("WSEAnalytical", "Degenerate case in iteration, breaking");
                break;
            }

            // Calculate function values f(x)
            double f1 = gravity * (targetKl * v / groundSpeed * (vN_air * vD_air * cosRoll - vE_air * v * sinRoll) - targetKd * vN_air * v) - accelN;
            double f2 = gravity * (targetKl * v / groundSpeed * (vE_air * vD_air * cosRoll + vN_air * v * sinRoll) - targetKd * vE_air * v) - accelE;
            double f3 = gravity * (1 - targetKl * v * groundSpeed * cosRoll - targetKd * vD_air * v) - (accelDminusG + gravity);
            double f4 = vN_air * vN_air + vE_air * vE_air + vD_air * vD_air - v * v; // This should be 0 by definition, but helps with numerical stability

            // Check convergence
            double residual = Math.sqrt(f1*f1 + f2*f2 + f3*f3 + f4*f4);
            Log.d("WSEAnalytical", String.format("Iteration %d: residual=%.6f, v=%.2f, roll=%.1f°", 
                    iter, residual, v, Math.toDegrees(roll)));
            
            if (residual < tolerance) {
                Log.d("WSEAnalytical", String.format("Converged after %d iterations", iter));
                break;
            }

            // Calculate Jacobian matrix J (4x4)
            // This is complex due to the nonlinear coupling, so we'll use numerical differentiation
            double eps = 1e-8;
            double[][] J = new double[4][4];
            
            for (int j = 0; j < 4; j++) {
                // Perturb x[j] and calculate finite difference
                double[] x_plus = x.clone();
                x_plus[j] += eps;
                
                double vN_p = x_plus[0];
                double vE_p = x_plus[1];
                double vD_p = x_plus[2];
                double roll_p = x_plus[3];
                
                double v_p = Math.sqrt(vN_p * vN_p + vE_p * vE_p + vD_p * vD_p);
                double groundSpeed_p = Math.sqrt(vN_p * vN_p + vE_p * vE_p);
                double cosRoll_p = Math.cos(roll_p);
                double sinRoll_p = Math.sin(roll_p);
                
                if (v_p < 0.1 || groundSpeed_p < 0.1) continue;
                
                double f1_p = gravity * (targetKl * v_p / groundSpeed_p * (vN_p * vD_p * cosRoll_p - vE_p * v_p * sinRoll_p) - targetKd * vN_p * v_p) - accelN;
                double f2_p = gravity * (targetKl * v_p / groundSpeed_p * (vE_p * vD_p * cosRoll_p + vN_p * v_p * sinRoll_p) - targetKd * vE_p * v_p) - accelE;
                double f3_p = gravity * (1 - targetKl * v_p * groundSpeed_p * cosRoll_p - targetKd * vD_p * v_p) - (accelDminusG + gravity);
                double f4_p = vN_p * vN_p + vE_p * vE_p + vD_p * vD_p - v_p * v_p;
                
                J[0][j] = (f1_p - f1) / eps;
                J[1][j] = (f2_p - f2) / eps;
                J[2][j] = (f3_p - f3) / eps;
                J[3][j] = (f4_p - f4) / eps;
            }

            // Solve J * dx = -f for dx
            double[] f = {f1, f2, f3, f4};
            double[] dx = solveLinearSystem4x4(J, f);
            
            if (dx == null) {
                Log.d("WSEAnalytical", "Singular Jacobian, breaking iteration");
                break;
            }

            // Update solution with damping for stability
            double damping = 0.7; // Reduce step size for stability
            for (int j = 0; j < 4; j++) {
                x[j] -= damping * dx[j];
            }
            
            // Keep roll in reasonable bounds
            while (x[3] > Math.PI) x[3] -= 2 * Math.PI;
            while (x[3] < -Math.PI) x[3] += 2 * Math.PI;
        }

        // Extract final solution
        double finalVN = x[0];
        double finalVE = x[1];
        double finalVD = x[2];
        double finalRoll = x[3];
        
        // Convert back to ENU coordinates
        Vector3 airspeed = new Vector3(finalVE, -finalVD, finalVN);
        
        // Verify solution by computing predicted acceleration
        WSEParams finalParams = new WSEParams(targetKl, targetKd, finalRoll);
        Vector3 predictedAccel = WSE.calculateWingsuitAcceleration(airspeed, finalParams);
        
        double accelError = Math.sqrt(
                Math.pow(predictedAccel.x - accel.x, 2) +
                        Math.pow(predictedAccel.y - accel.y, 2) +
                        Math.pow(predictedAccel.z - accel.z, 2)
        );
        
        // Calculate wind vector
        Vector3 windVector = new Vector3(
                airspeed.x - velocity.x,
                airspeed.y - velocity.y,
                airspeed.z - velocity.z
        );
        double windMagnitude = windVector.magnitude();

        Log.d("WSEAnalytical", String.format("Analytical 4D result: airspeed=[%.2f,%.2f,%.2f], roll=%.1f°, wind=%.2f m/s, accelErr=%.3f",
                airspeed.x, airspeed.y, airspeed.z, Math.toDegrees(finalRoll), windMagnitude, accelError));

        return new WSE.AirspeedResult(airspeed, new WSEParams(targetKl, targetKd, finalRoll));
    }

    /**
     * Solve 4x4 linear system Ax = b using Gaussian elimination with partial pivoting
     */
    private static double[] solveLinearSystem4x4(double[][] A, double[] b) {
        int n = 4;
        double[][] augmented = new double[n][n + 1];
        
        // Create augmented matrix
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                augmented[i][j] = A[i][j];
            }
            augmented[i][n] = -b[i]; // Note: we want to solve for -f
        }
        
        // Gaussian elimination with partial pivoting
        for (int i = 0; i < n; i++) {
            // Find pivot
            int maxRow = i;
            for (int k = i + 1; k < n; k++) {
                if (Math.abs(augmented[k][i]) > Math.abs(augmented[maxRow][i])) {
                    maxRow = k;
                }
            }
            
            // Swap rows
            double[] temp = augmented[i];
            augmented[i] = augmented[maxRow];
            augmented[maxRow] = temp;
            
            // Check for singular matrix
            if (Math.abs(augmented[i][i]) < 1e-12) {
                return null;
            }
            
            // Eliminate column
            for (int k = i + 1; k < n; k++) {
                double factor = augmented[k][i] / augmented[i][i];
                for (int j = i; j < n + 1; j++) {
                    augmented[k][j] -= factor * augmented[i][j];
                }
            }
        }
        
        // Back substitution
        double[] x = new double[n];
        for (int i = n - 1; i >= 0; i--) {
            x[i] = augmented[i][n];
            for (int j = i + 1; j < n; j++) {
                x[i] -= augmented[i][j] * x[j];
            }
            x[i] /= augmented[i][i];
        }
        
        return x;
    }

    /**
     * Alternative approach: Solve for airspeed magnitude first, then direction
     * This can be more stable than solving the full 4x4 system
     */
    public static WSE.AirspeedResult calculateMagnitudeFirstApproach(
            Vector3 velocity,
            Vector3 accel,
            WSEParams wseParams
    ) {
        // Extract parameters
        double targetKl = wseParams.kl();
        double targetKd = wseParams.kd();
        double rollGuess = wseParams.roll();

        Log.d("WSEAnalytical", String.format("calculateMagnitudeFirstApproach: targetKl=%.4f, targetKd=%.4f", targetKl, targetKd));

        // Quick validity checks
        if (Math.abs(targetKl) < 1e-12 || Math.abs(targetKd) < 1e-12) {
            return new WSE.AirspeedResult(new Vector3(0, 0, 0), new WSEParams(targetKl, targetKd, rollGuess));
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

        // Step 1: Estimate airspeed magnitude from acceleration magnitude
        Vector3 accelMinusG = new Vector3(accelE, accelDminusG, accelN);
        double totalAccelMag = accelMinusG.magnitude();
        
        if (totalAccelMag < 0.1) {
            return new WSE.AirspeedResult(velocity, new WSEParams(targetKl, targetKd, rollGuess));
        }

        double klkdMag = Math.sqrt(targetKl * targetKl + targetKd * targetKd);
        double airspeedMagnitude = Math.sqrt(totalAccelMag / (gravity * klkdMag));

        Log.d("WSEAnalytical", String.format("Estimated airspeed magnitude: %.2f m/s", airspeedMagnitude));

        // Step 2: Solve for direction using constrained optimization
        // We know |airspeed| = airspeedMagnitude, so we can parameterize as:
        // vN_air = airspeedMagnitude * sin(theta) * cos(phi)
        // vE_air = airspeedMagnitude * sin(theta) * sin(phi)  
        // vD_air = airspeedMagnitude * cos(theta)
        // Where theta is polar angle (0 to PI), phi is azimuthal angle (0 to 2*PI)

        // Use optimization to find theta, phi, and roll that minimize WSE residual
        double bestTheta = Math.acos(-vD / airspeedMagnitude); // Initial guess from ground velocity
        double bestPhi = Math.atan2(vE, vN); // Initial guess from ground velocity
        double bestRoll = rollGuess;
        double minResidual = Double.MAX_VALUE;

        // Grid search over theta, phi, roll
        int thetaSteps = 15;
        int phiSteps = 20;
        int rollSteps = 15;

        for (int t = 0; t < thetaSteps; t++) {
            double theta = Math.PI * t / (thetaSteps - 1);
            
            for (int p = 0; p < phiSteps; p++) {
                double phi = 2 * Math.PI * p / phiSteps;
                
                for (int r = 0; r < rollSteps; r++) {
                    double roll = -Math.PI + 2 * Math.PI * r / (rollSteps - 1);
                    
                    // Calculate airspeed components
                    double vN_air = airspeedMagnitude * Math.sin(theta) * Math.cos(phi);
                    double vE_air = airspeedMagnitude * Math.sin(theta) * Math.sin(phi);
                    double vD_air = airspeedMagnitude * Math.cos(theta);
                    
                    Vector3 testAirspeed = new Vector3(vE_air, -vD_air, vN_air); // Convert to ENU
                    WSEParams testParams = new WSEParams(targetKl, targetKd, roll);
                    
                    // Calculate predicted acceleration
                    Vector3 predictedAccel = WSE.calculateWingsuitAcceleration(testAirspeed, testParams);
                    
                    // Calculate residual
                    double residual = Math.sqrt(
                            Math.pow(predictedAccel.x - accel.x, 2) +
                                    Math.pow(predictedAccel.y - accel.y, 2) +
                                    Math.pow(predictedAccel.z - accel.z, 2)
                    );
                    
                    if (residual < minResidual) {
                        minResidual = residual;
                        bestTheta = theta;
                        bestPhi = phi;
                        bestRoll = roll;
                    }
                }
            }
        }

        // Construct final solution
        double finalVN = airspeedMagnitude * Math.sin(bestTheta) * Math.cos(bestPhi);
        double finalVE = airspeedMagnitude * Math.sin(bestTheta) * Math.sin(bestPhi);
        double finalVD = airspeedMagnitude * Math.cos(bestTheta);
        
        Vector3 finalAirspeed = new Vector3(finalVE, -finalVD, finalVN);
        Vector3 windVector = new Vector3(
                finalAirspeed.x - velocity.x,
                finalAirspeed.y - velocity.y,
                finalAirspeed.z - velocity.z
        );

        Log.d("WSEAnalytical", String.format("MagnitudeFirst result: airspeed=[%.2f,%.2f,%.2f], roll=%.1f°, wind=%.2f m/s, residual=%.3f",
                finalAirspeed.x, finalAirspeed.y, finalAirspeed.z, Math.toDegrees(bestRoll), windVector.magnitude(), minResidual));

        return new WSE.AirspeedResult(finalAirspeed, new WSEParams(targetKl, targetKd, bestRoll));
    }
}