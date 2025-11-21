package com.platypii.baselinexr.location;

import static com.platypii.baselinexr.location.WSE.calculateWingsuitAcceleration;
import static com.platypii.baselinexr.location.WSE.calculateWingsuitParameters;

import android.util.Log;

import com.platypii.baselinexr.Services;
import com.platypii.baselinexr.location.AtmosphericModel;
import com.platypii.baselinexr.util.tensor.Vector3;

import java.util.List;

/**
 * Wind velocity Kalman filter for estimating 3D wind using wingsuit aerodynamic models.
 *
 * This filter estimates wind velocity by optimizing the match between measured acceleration
 * and expected acceleration from wingsuit aerodynamic models.
 */
public class WindKalmanFilter {
    private static final String TAG = "WindKalmanFilter";
    private static final double GRAVITY = 9.81;

    // State vector: wind velocity [wx, wy, wz] in ENU coordinates
    private final double[] state = new double[3];

    // Covariance matrices
    private double[][] covariance;
    private double[][] processNoise;
    private double[][] measurementNoise;

    // Default polar for wind estimation
    private PolarLibrary.WingsuitPolar polar = PolarLibrary.AURA_FIVE_POLAR;

    // Optimization parameters
    private static final double LEARNING_RATE_INITIAL = 0.05;
    private static final double LEARNING_RATE_MAX = 0.2;
    private static final int MAX_ITERATIONS = 50;
    private static final double CONVERGENCE_THRESHOLD = 1e-6;
    private static final double EPSILON = 1e-4; // For numerical gradient

    // Cost function weights
    private static final double RESIDUAL_WEIGHT = 100.0;
    private static final double WIND_SPEED_WEIGHT = 1.0;

    public WindKalmanFilter() {
        // Initialize state to zero wind
        state[0] = 0.0; // wx (east)
        state[1] = 0.0; // wy (up)  
        state[2] = 0.0; // wz (north)

        // Initialize covariance matrices
        covariance = LinearAlgebra.identity(3);
        processNoise = LinearAlgebra.identity(3);
        measurementNoise = LinearAlgebra.identity(3);

        // Default noise values
        for (int i = 0; i < 3; i++) {
            processNoise[i][i] = 0.1;      // Wind process noise
            measurementNoise[i][i] = 1.0;  // Wind measurement noise
        }
    }

    /**
     * Predict step: advance wind state and add process noise
     */
    public void predict() {
        // Wind velocity evolves slowly - just add process noise to covariance
        covariance = LinearAlgebra.add(covariance, processNoise);
    }

    /**
     * Update step using measured wind velocity
     */
    public void update(Vector3 measuredWindVelocity) {
        // Create measurement vector
        final double[] measurement = {
                measuredWindVelocity.x,
                measuredWindVelocity.y,
                measuredWindVelocity.z
        };

        // H matrix is identity for direct observation
        final double[][] H = LinearAlgebra.identity(3);

        // Innovation: z - H*x
        final double[] innovation = new double[3];
        for (int i = 0; i < 3; i++) {
            innovation[i] = measurement[i] - state[i];
        }

        // Innovation covariance: S = H*P*H^T + R
        final double[][] HP = LinearAlgebra.mul(H, covariance);
        final double[][] HPHT = LinearAlgebra.mul(HP, LinearAlgebra.transpose(H));
        final double[][] S = LinearAlgebra.add(HPHT, measurementNoise);

        // Kalman gain: K = P*H^T*S^(-1)
        final double[][] PHT = LinearAlgebra.mul(covariance, LinearAlgebra.transpose(H));
        final double[][] S_inv = LinearAlgebra.inverse(S);
        final double[][] K = LinearAlgebra.mul(PHT, S_inv);

        // Update state: x = x + K*innovation
        final double[] Ky = LinearAlgebra.mul(K, innovation);
        for (int i = 0; i < 3; i++) {
            state[i] += Ky[i];
        }

        // Update covariance: P = (I - K*H)*P
        final double[][] KH = LinearAlgebra.mul(K, H);
        final double[][] I_KH = LinearAlgebra.sub(LinearAlgebra.identity(3), KH);
        covariance = LinearAlgebra.mul(I_KH, covariance);
    }

    /**
     * Optimize wind velocity using gradient descent to minimize composite objective function
     */
    public WindOptimizationResult optimizeWindVelocity(Vector3 velocity, Vector3 measuredAccel, double currentRoll) {
        final long startTime = System.nanoTime();

        // Generate candidate coefficients from polar
        final CoefficientCandidate[] candidates = generateCandidatesFromPolar();

        final double rho = calculateDensity(); // Air density
        final double s = polar.s;
        final double m = polar.m;

        // Current wind estimate as starting point
        Vector3 currentWind = new Vector3(state[0], state[1], state[2]);

        double learningRate = LEARNING_RATE_INITIAL;
        ObjectiveResult bestResult = evaluateObjectiveFunction(currentWind, velocity, measuredAccel, currentRoll, candidates, rho, s, m);
        Vector3 bestWind = new Vector3(currentWind.x, currentWind.y, currentWind.z);

        if (Log.isLoggable(TAG, Log.DEBUG) || true) { // Force logging for debugging
            Log.d(TAG, String.format("Wind optimization starting - Initial cost: %.6f, residual: %.6f",
                    bestResult.cost, bestResult.residual));
        }

        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            // Calculate numerical gradient
            final Vector3 gradient = calculateNumericalGradient(currentWind, velocity, measuredAccel, currentRoll, candidates, rho, s, m);

            // Update wind estimate using gradient descent
            final Vector3 newWind = new Vector3(
                    currentWind.x - learningRate * gradient.x,
                    currentWind.y - learningRate * gradient.y,
                    currentWind.z - learningRate * gradient.z
            );

            // Evaluate new position
            final ObjectiveResult newResult = evaluateObjectiveFunction(newWind, velocity, measuredAccel, currentRoll, candidates, rho, s, m);

            // Check if we improved
            if (newResult.cost < bestResult.cost) {
                bestResult = newResult;
                bestWind = new Vector3(newWind.x, newWind.y, newWind.z);
                currentWind = newWind;

                // Adaptive learning rate: increase if improving
                learningRate = Math.min(learningRate * 1.1, LEARNING_RATE_MAX);

                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    Log.v(TAG, String.format("Iteration %d: improved cost=%.6f, residual=%.6f",
                            iteration, newResult.cost, newResult.residual));
                }
            } else {
                // Decrease learning rate if not improving
                learningRate *= 0.5;

                // Reset to best known position
                currentWind = new Vector3(bestWind.x, bestWind.y, bestWind.z);

                if (learningRate < 1e-6) {
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, String.format("Wind optimization converged (learning rate): iteration %d", iteration));
                    }
                    break;
                }
            }

            // Check for convergence
            final double gradientMagnitude = Math.sqrt(gradient.x * gradient.x + gradient.y * gradient.y + gradient.z * gradient.z);
            if (gradientMagnitude < CONVERGENCE_THRESHOLD) {
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, String.format("Wind optimization converged (gradient): iteration %d", iteration));
                }
                break;
            }
        }

        final long endTime = System.nanoTime();
        final double executionTimeMs = (endTime - startTime) / 1_000_000.0;

        if (Log.isLoggable(TAG, Log.DEBUG) || true) { // Force logging for debugging
            final double finalWindSpeed = Math.sqrt(bestWind.x * bestWind.x + bestWind.y * bestWind.y + bestWind.z * bestWind.z);
            Log.d(TAG, String.format("Wind optimization complete - Final cost: %.6f, residual: %.6f, wind speed: %.3f m/s, execution time: %.3f ms",
                    bestResult.cost, bestResult.residual, finalWindSpeed, executionTimeMs));
        }

        return new WindOptimizationResult(bestWind, bestResult.bestCoeff, bestResult.residual, bestResult.aoa, bestResult.roll);
    }

    /**
     * Enhanced aerodynamic model matching with ternary search and interpolation
     * Uses binary search + linear interpolation to find optimal AoA that minimizes acceleration residual
     */
    public AerodynamicModelResult matchAerodynamicModel(Vector3 measuredAccel, Vector3 velocity, double roll,
                                                        CoefficientCandidate[] candidates, double rho, double s, double m) {
        final double k = 0.5 * rho * s / m;

        // Objective function to minimize - returns residual for given AoA
        final AoAObjectiveFunction objectiveFunction = (aoa) -> {
            final CoefficientCandidate coeffs = interpolatePolar(aoa);
            final double kl = coeffs.cl * k / GRAVITY;
            final double kd = coeffs.cd * k / GRAVITY;
            final double newroll = calculateWingsuitParameters(velocity, measuredAccel, new WSEParams(kl, kd, roll)).roll();
            final Vector3 predictedAccel = calculateWingsuitAcceleration(velocity, new WSEParams(kl, kd, newroll));
            final Vector3 diff = measuredAccel.minus(predictedAccel);
            return Math.sqrt(diff.x * diff.x + diff.y * diff.y + diff.z * diff.z);
        };

        // Phase 1: Coarse search through discrete polar points to find approximate region
        double minResidual = Double.POSITIVE_INFINITY;
        double bestDiscreteAoa = polar.aoas[0];

        for (int i = 0; i < polar.aoas.length; i++) {
            final double aoa = polar.aoas[i];
            final double residual = objectiveFunction.evaluate(aoa);

            if (residual < minResidual) {
                minResidual = residual;
                bestDiscreteAoa = aoa;
            }
        }

        // Phase 2: Fine search using ternary search around the best discrete point
        final double searchRange = 2.0; // Search ±2 degrees around best discrete point
        // Note: AoAs are in decreasing order (90° to 0°), so min/max logic is reversed
        double left = Math.max(bestDiscreteAoa - searchRange, polar.aoas[polar.aoas.length - 1]); // min AoA (0°)
        double right = Math.min(bestDiscreteAoa + searchRange, polar.aoas[0]); // max AoA (90°)
        final double tolerance = 0.01; // 0.01 degree precision

        // Ternary search for optimal AoA
        while (right - left > tolerance) {
            final double mid1 = left + (right - left) / 3.0;
            final double mid2 = right - (right - left) / 3.0;

            if (objectiveFunction.evaluate(mid1) > objectiveFunction.evaluate(mid2)) {
                left = mid1;
            } else {
                right = mid2;
            }
        }

        // Get the optimal solution
        final double bestAoa = (left + right) / 2.0;
        final CoefficientCandidate bestCoeff = interpolatePolar(bestAoa);
        final double kl = bestCoeff.cl * k / GRAVITY;
        final double kd = bestCoeff.cd * k / GRAVITY;

        // Calculate the expected acceleration at optimal point
        // Calculate roll using WSE parameters
        final double bestRoll = calculateWingsuitParameters(velocity, measuredAccel, new WSEParams(kl, kd, roll)).roll();
        final Vector3 bestAccel = calculateWingsuitAcceleration(velocity, new WSEParams(kl, kd, bestRoll));

        return new AerodynamicModelResult(bestCoeff, bestAccel, bestAoa, bestRoll);
    }

    /**
     * Binary search + linear interpolation for polar data
     * Interpolates coefficients for any AoA within the polar range
     * NOTE: AoAs are stored in DECREASING order (90° down to 0°)
     */
    private CoefficientCandidate interpolatePolar(double targetAoa) {
        final double[] aoas = polar.aoas;
        final List<PolarLibrary.PolarPoint> stallpoints = polar.stallPoint;

        // Handle edge cases - since AoAs are in decreasing order:
        // aoas[0] = highest AoA (90°), aoas[length-1] = lowest AoA (0°)
        if (targetAoa >= aoas[0]) {
            return new CoefficientCandidate(stallpoints.get(0).cl, stallpoints.get(0).cd, aoas[0]);
        }
        if (targetAoa <= aoas[aoas.length - 1]) {
            final PolarLibrary.PolarPoint last = stallpoints.get(stallpoints.size() - 1);
            return new CoefficientCandidate(last.cl, last.cd, aoas[aoas.length - 1]);
        }

        // Binary search to find bracketing indices for DECREASING order array
        int left = 0;
        int right = aoas.length - 1;

        while (right - left > 1) {
            final int mid = (left + right) / 2;
            if (aoas[mid] >= targetAoa) {  // Note: >= for decreasing order
                left = mid;
            } else {
                right = mid;
            }
        }

        // Linear interpolation between left and right points
        // For decreasing order: left has higher AoA, right has lower AoA
        final double t = (aoas[left] - targetAoa) / (double)(aoas[left] - aoas[right]);
        final PolarLibrary.PolarPoint leftPoint = stallpoints.get(left);
        final PolarLibrary.PolarPoint rightPoint = stallpoints.get(right);

        final double cl = leftPoint.cl + t * (rightPoint.cl - leftPoint.cl);
        final double cd = leftPoint.cd + t * (rightPoint.cd - leftPoint.cd);

        return new CoefficientCandidate(cl, cd, targetAoa);
    }

    /**
     * Functional interface for AoA objective function evaluation
     */
    @FunctionalInterface
    private interface AoAObjectiveFunction {
        double evaluate(double aoa);
    }

    // Private helper methods

    private CoefficientCandidate[] generateCandidatesFromPolar() {
        final List<PolarLibrary.PolarPoint> stallPoints = polar.stallPoint;
        final double[] aoas = polar.aoas;

        final CoefficientCandidate[] candidates = new CoefficientCandidate[stallPoints.size()];
        for (int i = 0; i < stallPoints.size(); i++) {
            final PolarLibrary.PolarPoint point = stallPoints.get(i);
            final double aoa = (i < aoas.length) ? aoas[i] : 0.0;
            candidates[i] = new CoefficientCandidate(point.cl, point.cd, aoa);
        }

        return candidates;
    }

    private ObjectiveResult evaluateObjectiveFunction(Vector3 wind, Vector3 velocity, Vector3 measuredAccel,
                                                      double currentRoll, CoefficientCandidate[] candidates,
                                                      double rho, double s, double m) {
        final Vector3 airspeedVelocity = velocity.plus(wind);

        final AerodynamicModelResult bestFit = matchAerodynamicModel(measuredAccel, airspeedVelocity, currentRoll, candidates, rho, s, m);

        final Vector3 residualVec = measuredAccel.minus(bestFit.expectedAccel);
        final double residual = Math.sqrt(residualVec.x * residualVec.x + residualVec.y * residualVec.y + residualVec.z * residualVec.z);

        final double windSpeed = Math.sqrt(wind.x * wind.x + wind.y * wind.y + wind.z * wind.z);

        // Composite cost function
        final double cost = RESIDUAL_WEIGHT * residual * residual + WIND_SPEED_WEIGHT * windSpeed * windSpeed;

        return new ObjectiveResult(cost, bestFit.bestCoeff, residual, bestFit.aoa, bestFit.roll);
    }

    private Vector3 calculateNumericalGradient(Vector3 currentWind, Vector3 velocity, Vector3 measuredAccel,
                                               double currentRoll, CoefficientCandidate[] candidates,
                                               double rho, double s, double m) {
        final Vector3 gradient = new Vector3();

        // Partial derivative with respect to wind.x
        final Vector3 windXPlus = new Vector3(currentWind.x + EPSILON, currentWind.y, currentWind.z);
        final Vector3 windXMinus = new Vector3(currentWind.x - EPSILON, currentWind.y, currentWind.z);
        final double costXPlus = evaluateObjectiveFunction(windXPlus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        final double costXMinus = evaluateObjectiveFunction(windXMinus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        gradient.x = (costXPlus - costXMinus) / (2 * EPSILON);

        // Partial derivative with respect to wind.y
        final Vector3 windYPlus = new Vector3(currentWind.x, currentWind.y + EPSILON, currentWind.z);
        final Vector3 windYMinus = new Vector3(currentWind.x, currentWind.y - EPSILON, currentWind.z);
        final double costYPlus = evaluateObjectiveFunction(windYPlus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        final double costYMinus = evaluateObjectiveFunction(windYMinus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        gradient.y = (costYPlus - costYMinus) / (2 * EPSILON);

        // Partial derivative with respect to wind.z
        final Vector3 windZPlus = new Vector3(currentWind.x, currentWind.y, currentWind.z + EPSILON);
        final Vector3 windZMinus = new Vector3(currentWind.x, currentWind.y, currentWind.z - EPSILON);
        final double costZPlus = evaluateObjectiveFunction(windZPlus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        final double costZMinus = evaluateObjectiveFunction(windZMinus, velocity, measuredAccel, currentRoll, candidates, rho, s, m).cost;
        gradient.z = (costZPlus - costZMinus) / (2 * EPSILON);

        return gradient;
    }

    // Public getter/setter methods

    public Vector3 getWindVelocity() {
        return new Vector3(state[0], state[1], state[2]);
    }

    public void setProcessNoise(double noise) {
        for (int i = 0; i < 3; i++) {
            processNoise[i][i] = noise;
        }
    }

    public void setMeasurementNoise(double noise) {
        for (int i = 0; i < 3; i++) {
            measurementNoise[i][i] = noise;
        }
    }

    public double getProcessNoise() {
        return processNoise[0][0];
    }

    public double getMeasurementNoise() {
        return measurementNoise[0][0];
    }

    public void setPolar(PolarLibrary.WingsuitPolar polar) {
        this.polar = polar;
    }

    public void reset() {
        state[0] = 0.0;
        state[1] = 0.0;
        state[2] = 0.0;
        covariance = LinearAlgebra.identity(3);
    }

    /**
     * Complete wind estimation update cycle
     * This is the main integration method to be called from KalmanFilter3D
     *
     * @param velocity Current velocity estimate from main filter (ENU)
     * @param acceleration Current acceleration estimate from main filter (ENU)
     * @param currentRoll Current roll estimate from main filter
     * @return Wind estimation results including optimized wind velocity and aerodynamic parameters
     */
    public WindEstimationResult updateWindEstimation(Vector3 velocity, Vector3 acceleration, double currentRoll) {
        // Step 1: Predict wind filter state forward in time
        predict();

        // Step 2: Find optimal wind velocity that minimizes acceleration residual
        final WindOptimizationResult optimalWind = optimizeWindVelocity(velocity, acceleration, currentRoll);

        // Step 3: Use optimal wind as "measurement" for wind filter update
        update(optimalWind.windVelocity);

        // Step 4: Get updated wind estimate from filter
        final Vector3 estimatedWind = getWindVelocity();

        if (Log.isLoggable(TAG, Log.DEBUG) || true) { // Force logging for debugging
            Log.d(TAG, String.format("Wind estimation: [%.3f,%.3f,%.3f] m/s, AoA: %.1f°, roll: %.1f°, residual: %.3f",
                    estimatedWind.x, estimatedWind.y, estimatedWind.z, optimalWind.bestAoA, Math.toDegrees(optimalWind.bestRoll), optimalWind.minResidual));
        }

        return new WindEstimationResult(estimatedWind, optimalWind.bestAoA, optimalWind.bestCoeff, optimalWind.minResidual, optimalWind.bestRoll);
    }

    /**
     * Result of complete wind estimation process
     */
    public static class WindEstimationResult {
        public final Vector3 windVelocity;
        public final double angleOfAttack;
        public final CoefficientCandidate coefficients;
        public final double residual;
        public final double roll;

        public WindEstimationResult(Vector3 windVelocity, double angleOfAttack, CoefficientCandidate coefficients, double residual, double roll) {
            this.windVelocity = windVelocity;
            this.angleOfAttack = angleOfAttack;
            this.coefficients = coefficients;
            this.residual = residual;
            this.roll = roll;
        }
    }

    /**
     * Calculate air density using atmospheric model with 10°C temperature offset.
     * @return Air density in kg/m³
     */
    private double calculateDensity() {
        // Get current altitude from GPS
        float altitude = (float) Services.location.lastLoc.altitude_gps;

        // Use atmospheric model with 10°C temperature offset as requested
        return AtmosphericModel.calculateDensity(altitude, 10f);
    }

    // Inner classes for results

    public static class WindOptimizationResult {
        public final Vector3 windVelocity;
        public final CoefficientCandidate bestCoeff;
        public final double minResidual;
        public final double bestAoA;
        public final double bestRoll;

        public WindOptimizationResult(Vector3 windVelocity, CoefficientCandidate bestCoeff, double minResidual, double bestAoA, double bestRoll) {
            this.windVelocity = windVelocity;
            this.bestCoeff = bestCoeff;
            this.minResidual = minResidual;
            this.bestAoA = bestAoA;
            this.bestRoll = bestRoll;
        }
    }

    public static class AerodynamicModelResult {
        public final CoefficientCandidate bestCoeff;
        public final Vector3 expectedAccel;
        public final double aoa;
        public final double roll;

        public AerodynamicModelResult(CoefficientCandidate bestCoeff, Vector3 expectedAccel, double aoa, double roll) {
            this.bestCoeff = bestCoeff;
            this.expectedAccel = expectedAccel;
            this.aoa = aoa;
            this.roll = roll;
        }
    }

    public static class CoefficientCandidate {
        public final double cl;
        public final double cd;
        public final double aoa;

        public CoefficientCandidate(double cl, double cd, double aoa) {
            this.cl = cl;
            this.cd = cd;
            this.aoa = aoa;
        }
    }

    private static class ObjectiveResult {
        public final double cost;
        public final CoefficientCandidate bestCoeff;
        public final double residual;
        public final double aoa;
        public final double roll;

        public ObjectiveResult(double cost, CoefficientCandidate bestCoeff, double residual, double aoa, double roll) {
            this.cost = cost;
            this.bestCoeff = bestCoeff;
            this.residual = residual;
            this.aoa = aoa;
            this.roll = roll;
        }
    }
}