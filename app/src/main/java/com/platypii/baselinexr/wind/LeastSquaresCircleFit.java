package com.platypii.baselinexr.wind;

import java.util.ArrayList;
import java.util.List;

/**
 * Least squares circle fitting algorithm for wind estimation.
 * Fits a circle to GPS velocity data points where the circle center represents wind velocity.
 */
public class LeastSquaresCircleFit {
    
    public static class CircleFitResult {
        public final double centerX;        // Circle center X coordinate (East)
        public final double centerY;        // Circle center Y coordinate (North) 
        public final double radius;         // Circle radius (airspeed)
        public final double aircraftSpeed;  // Aircraft speed (same as radius)
        public final double rSquared;       // R-squared goodness of fit (0-1)
        public final int pointCount;        // Number of points used
        
        public CircleFitResult(double centerX, double centerY, double radius, double aircraftSpeed, double rSquared, int pointCount) {
            this.centerX = centerX;
            this.centerY = centerY;
            this.radius = radius;
            this.aircraftSpeed = aircraftSpeed;
            this.rSquared = rSquared;
            this.pointCount = pointCount;
        }
        
        public double getWindMagnitude() {
            // Wind components are negative of circle center
            double windE = -centerX;
            double windN = -centerY;
            return Math.sqrt(windE * windE + windN * windN);
        }
        
        public double getWindDirection() {
            // Wind components are negative of circle center
            double windE = -centerX;
            double windN = -centerY;
            return Math.atan2(windE, windN) * 180.0 / Math.PI;
        }
        
        public double getWindE() {
            return -centerX;  // East wind component
        }
        
        public double getWindN() {
            return -centerY;  // North wind component
        }
    }
    
    /**
     * Fit a circle to GPS velocity data using Taubin's method (algebraic circle fit)
     * followed by geometric refinement for improved accuracy.
     */
    public static CircleFitResult fitCircleToGPSVelocities(List<WindDataPoint> dataPoints) {
        if (dataPoints.size() < 3) {
            return new CircleFitResult(0, 0, 0, 0, 0, 0);
        }
        
        int n = dataPoints.size();
        double[] x = new double[n];
        double[] y = new double[n];
        
        // Extract velocity components
        for (int i = 0; i < n; i++) {
            x[i] = dataPoints.get(i).vE;  // East velocity
            y[i] = dataPoints.get(i).vN;  // North velocity
        }
        
        return fitCircle(x, y);
    }
    
    /**
     * Fit a circle to sustained velocity data 
     */
    public static CircleFitResult fitCircleToSustainedVelocities(List<WindDataPoint> dataPoints) {
        if (dataPoints.size() < 3) {
            return new CircleFitResult(0, 0, 0, 0, 0, 0);
        }
        
        // Filter out zero sustained velocities (invalid data)
        List<WindDataPoint> validPoints = new ArrayList<>();
        for (WindDataPoint point : dataPoints) {
            double sustainedSpeed = Math.sqrt(point.sustainedVE * point.sustainedVE + point.sustainedVN * point.sustainedVN);
            if (sustainedSpeed > 0.1) { // Only include points with meaningful sustained velocity
                validPoints.add(point);
            }
        }
        
        if (validPoints.size() < 3) {
            return new CircleFitResult(0, 0, 0, 0, 0, validPoints.size());
        }
        
        int n = validPoints.size();
        double[] x = new double[n];
        double[] y = new double[n];
        
        // Extract sustained velocity components from valid points only
        for (int i = 0; i < n; i++) {
            x[i] = validPoints.get(i).sustainedVE;  // Sustained East velocity
            y[i] = validPoints.get(i).sustainedVN;  // Sustained North velocity
        }
        
        return fitCircle(x, y);
    }
    
    /**
     * Weighted least-squares circle fitting method based on 
     * http://www.dtcenter.org/met/users/docs/write_ups/circle_fit.pdf
     */
    private static CircleFitResult fitCircle(double[] x, double[] y) {
        int n = x.length;
        if (n < 3) {
            return new CircleFitResult(0, 0, 0, 0, 0, 0);
        }
        
        // Calculate weighted means (using uniform weights wi = 1.0)
        double xbar = 0, ybar = 0, N = 0;
        for (int i = 0; i < n; i++) {
            double wi = 1.0; // Uniform weights
            double xi = x[i];
            double yi = y[i];
            
            xbar += wi * xi;
            ybar += wi * yi;
            N += wi;
        }
        xbar /= N;
        ybar /= N;
        
        // Calculate moments
        double suu = 0, suv = 0, svv = 0;
        double suuu = 0, suvv = 0, svuu = 0, svvv = 0;
        
        for (int i = 0; i < n; i++) {
            double wi = 1.0; // Uniform weights
            double xi = x[i];
            double yi = y[i];
            
            double ui = xi - xbar;
            double vi = yi - ybar;
            
            suu += wi * ui * ui;
            suv += wi * ui * vi;
            svv += wi * vi * vi;
            
            suuu += wi * ui * ui * ui;
            suvv += wi * ui * vi * vi;
            svuu += wi * vi * ui * ui;
            svvv += wi * vi * vi * vi;
        }
        
        // Check for degenerate case
        double det = suu * svv - suv * suv;
        if (Math.abs(det) < 1e-10) {
            return new CircleFitResult(0, 0, 0, 0, 0, n);
        }
        
        // Calculate circle center in centered coordinates
        double uc = (1.0 / det) * (0.5 * svv * (suuu + suvv) - 0.5 * suv * (svvv + svuu));
        double vc = (1.0 / det) * (0.5 * suu * (svvv + svuu) - 0.5 * suv * (suuu + suvv));
        
        // Transform back to original coordinates
        double xc = uc + xbar;
        double yc = vc + ybar;
        
        // Calculate radius (aircraft speed)
        double alpha = uc * uc + vc * vc + (suu + svv) / N;
        double R = Math.sqrt(alpha);
        
        // Circle center coordinates (wind calculation done in getter methods)
        double windE = xc;
        double windN = yc;
        double aircraftSpeed = R;
        
        // Compute R-squared goodness of fit
        double rSquared = computeRSquared(x, y, xc, yc, aircraftSpeed);
        
        return new CircleFitResult(windE, windN, R, aircraftSpeed, rSquared, n);
    }
    
    /**
     * Compute R-squared goodness of fit for circle
     * @param aircraftSpeed The true aircraft speed (radius) to compare against
     */
    private static double computeRSquared(double[] x, double[] y, double centerX, double centerY, double aircraftSpeed) {
        int n = x.length;
        
        // Compute R-squared as goodness of fit for circle
        // R² = 1 - (RSS/TSS) where RSS is residual sum of squares and TSS is total sum of squares
        double[] radii = new double[n];
        double meanRadius = 0;
        
        // First pass: calculate actual radii and mean
        for (int i = 0; i < n; i++) {
            double dx = x[i] - centerX;
            double dy = y[i] - centerY;
            radii[i] = Math.sqrt(dx * dx + dy * dy);
            meanRadius += radii[i];
        }
        meanRadius /= n;
        
        // Second pass: calculate sums of squares
        double residualSumSquares = 0;  // How far points are from true aircraft speed circle
        double totalSumSquares = 0;     // Total variance in the data (baseline: points at origin)
        
        for (int i = 0; i < n; i++) {
            // Residual: difference between actual radius and aircraft airspeed
            double residual = radii[i] - aircraftSpeed;
            residualSumSquares += residual * residual;
            
            // Total: distance from origin (null model - no wind, aircraft at origin)
            double distanceFromOrigin = Math.sqrt(x[i] * x[i] + y[i] * y[i]);
            totalSumSquares += distanceFromOrigin * distanceFromOrigin;
        }
        
        if (totalSumSquares < 1e-10) {
            return 1.0; // Perfect fit if all points are at origin
        }
        
        // R² = 1 - (RSS/TSS), clamped to [0,1]
        double rSquared = Math.max(0, 1.0 - residualSumSquares / totalSumSquares);
        
        // Debug logging for R-squared calculation
        android.util.Log.d("CircleFit", String.format("R² calc: n=%d, meanRadius=%.3f, aircraftSpeed=%.3f, TSS=%.6f, RSS=%.6f, R²=%.3f", 
            n, meanRadius, aircraftSpeed, totalSumSquares, residualSumSquares, rSquared));
        
        return rSquared;
    }
}