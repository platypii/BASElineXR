package com.platypii.baselinexr.polars;

import com.platypii.baselinexr.location.PolarLibrary;
import com.platypii.baselinexr.location.PolarLibrary.WingsuitPolar;
import com.platypii.baselinexr.location.PolarLibrary.PolarPoint;

import java.util.Arrays;

/**
 * Centralized polar management and efficient sustained speed sampling/caching for all flight modes.
 */
public class Polars {
    // Equipment setup (can be changed at runtime)
    public static WingsuitPolar wingsuitPolar = PolarLibrary.A6_POLAR;
    public static WingsuitPolar canopyPolar = PolarLibrary.IBEX_UL_POLAR;
    public static WingsuitPolar airplanePolar = PolarLibrary.CARAVAN_POLAR;

    public static class PolarCache {
        public PolarPoint[] points;
        public double[] aoas;
        public int[] colors;
        public double[] originalSpeeds;
        public double originalDensity;
        public double densityRatio;
        public int nPoints;
        public WingsuitPolar polar;
    }

    private static PolarCache wingsuitCache, canopyCache, airplaneCache;
    private static int lastFlightMode = -1;

    /**
     * Initialize caches for all polars at the given altitude/density.
     */
    public static void initialize(double density) {
        wingsuitCache = sampleAndCache(wingsuitPolar, 25, density);
        canopyCache = sampleAndCache(canopyPolar, 15, density);
        airplaneCache = sampleAndCache(airplanePolar, 15, density);
        lastFlightMode = -1;
    }

    /**
     * Sample the polar curve at nPoints, evenly spaced by arc length (distance along the curve).
     * Returns a PolarCache with sampled points, aoas, and original sustained speeds.
     */
    private static PolarCache sampleAndCache(WingsuitPolar polar, int nPoints, double density) {
        double[] aoas = polar.aoas;
        PolarPoint[] polarPoints = polar.stallPoint.toArray(new PolarPoint[0]);
        int nPolar = Math.min(aoas.length, polarPoints.length);

        // Compute arc length along the polar curve in sustained speed space
        double[] arcLengths = new double[nPolar];
        arcLengths[0] = 0.0;
        for (int i = 1; i < nPolar; i++) {
            com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss0 =
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(
                            polarPoints[i - 1].cl, polarPoints[i - 1].cd, polar.s, polar.m, density);
            com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss1 =
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(
                            polarPoints[i].cl, polarPoints[i].cd, polar.s, polar.m, density);
            double dx = ss1.vxs - ss0.vxs;
            double dy = ss1.vys - ss0.vys;
            arcLengths[i] = arcLengths[i - 1] + Math.sqrt(dx * dx + dy * dy);
        }
        double totalLength = arcLengths[nPolar - 1];

        // Evenly spaced arc length targets
        double[] targetLengths = new double[nPoints];
        for (int i = 0; i < nPoints; i++) {
            targetLengths[i] = totalLength * i / (nPoints - 1);
        }

        // Interpolate to find sampled points and aoas
        PolarPoint[] sampledPoints = new PolarPoint[nPoints];
        double[] sampledAoas = new double[nPoints];
        double[] originalSpeeds = new double[nPoints];
        int[] colors = new int[nPoints];
        for (int i = 0; i < nPoints; i++) {
            double tLen = targetLengths[i];
            // Find segment
            int seg = 0;
            while (seg < nPolar - 2 && arcLengths[seg + 1] < tLen) seg++;
            // Clamp seg to valid range
            if (seg >= nPolar - 1) seg = nPolar - 2;
            double segLen = arcLengths[seg + 1] - arcLengths[seg];
            double frac = segLen > 0 ? (tLen - arcLengths[seg]) / segLen : 0.0;
            // Interpolate cl/cd/aoa
            double cl = lerp(polarPoints[seg].cl, polarPoints[seg + 1].cl, frac);
            double cd = lerp(polarPoints[seg].cd, polarPoints[seg + 1].cd, frac);
            double aoa = lerp(aoas[seg], aoas[seg + 1], frac);
            sampledPoints[i] = new PolarPoint(cl, cd);
            sampledAoas[i] = aoa;
            // Calculate sustained speed for this point using SpeedChartLive.coefftoss
            com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss =
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(cl, cd, polar.s, polar.m, density);
            originalSpeeds[i] = Math.sqrt(ss.vxs * ss.vxs + ss.vys * ss.vys);
            // Assign color (optional: gradient or fixed)
            colors[i] = colorForAoA(aoa, aoas[0], aoas[nPolar - 1]);
        }

        PolarCache cache = new PolarCache();
        cache.points = sampledPoints;
        cache.aoas = sampledAoas;
        cache.colors = colors;
        cache.originalSpeeds = Arrays.copyOf(originalSpeeds, nPoints);
        cache.originalDensity = density;
        cache.densityRatio = 1.0;
        cache.nPoints = nPoints;
        cache.polar = polar;
        return cache;
    }

    /**
     * Get the cached polar for the given density and flight mode, updating speeds if needed.
     * Returns the PolarCache (with updated speeds/colors if density changed).
     */
    public static PolarCache getCachedPolar(double density, int flightMode) {
        // Self-initialize caches if needed (first call or after reset)
        if (wingsuitCache == null || canopyCache == null || airplaneCache == null) {
            // Use the provided density for all caches
            initialize(density);
        }

        PolarCache cache;
        // Map flight modes to polars: airplane, canopy, wingsuit (default for wingsuit, freefall, unknown, ground)
        switch (flightMode) {
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_PLANE:
                cache = airplaneCache;
                break;
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_CANOPY:
                cache = canopyCache;
                break;
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_WINGSUIT:
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_FREEFALL:
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_UNKNOWN:
            case com.platypii.baselinexr.jarvis.FlightMode.MODE_GROUND:
            default:
                cache = wingsuitCache;
        }
        if (cache == null) return null;
        // If density changed, update sustained speeds using density ratio
        if (Math.abs(density - cache.originalDensity) > 0.01) {
            double scale = Math.sqrt(cache.originalDensity / density);
            for (int i = 0; i < cache.nPoints; i++) {
                cache.points[i] = new PolarPoint(cache.points[i].cl, cache.points[i].cd); // cl/cd unchanged
                cache.originalSpeeds[i] = cache.originalSpeeds[i] * scale;
            }
            cache.densityRatio = scale;
        }
        return cache;
    }

    /**
     * Interpolate between a and b by t (0..1)
     */
    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    /**
     * Calculate sustained speed for a given cl/cd at a given density (simplified for wingsuit/canopy/airplane)
     * v = sqrt((2 * W) / (rho * S * CL))
     * For now, use polar.s (area), polar.m (mass), and g = 9.81
     */
    private static double sustainedSpeed(double cl, double cd, double density, WingsuitPolar polar) {
        // Deprecated: use SpeedChartLive.coefftoss instead for consistency
        com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss =
                com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(cl, cd, polar.s, polar.m, density);
        return Math.sqrt(ss.vxs * ss.vxs + ss.vys * ss.vys);
    }

    /**
     * Assign a color for a given AoA (simple blue-red gradient)
     */
    private static int colorForAoA(double aoa, double aoaMax, double aoaMin) {
        // Map aoa (aoaMax..aoaMin) to 0..1
        double t = (aoa - aoaMin) / (aoaMax - aoaMin);
        int r = (int) (255 * (1 - t));
        int b = (int) (255 * t);
        return 0xFF000000 | (r << 16) | (b);
    }
}
