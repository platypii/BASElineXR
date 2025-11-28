package com.platypii.baselinexr.polars;

import com.platypii.baselinexr.location.PolarLibrary;
import com.platypii.baselinexr.location.PolarLibrary.WingsuitPolar;
import com.platypii.baselinexr.location.PolarLibrary.PolarPoint;

import java.util.Arrays;

/**
 * Centralized polar management and efficient sustained speed sampling/caching for all flight modes.
 */
public class Polars {
    // Singleton instance
    public static final Polars instance = new Polars();

    // Equipment setup (can be changed at runtime)
    public WingsuitPolar wingsuitPolar = PolarLibrary.A6_POLAR;
    public WingsuitPolar canopyPolar = PolarLibrary.IBEX_UL_POLAR;
    public WingsuitPolar airplanePolar = PolarLibrary.CARAVAN_POLAR;

    // Custom surface areas and masses (null = use polar defaults)
    public Double wingsuitArea = null;
    public Double wingsuitMass = null;
    public Double canopyArea = null;
    public Double canopyMass = null;
    public Double airplaneArea = null;
    public Double airplaneMass = null;

    public static class PolarCache {
        public PolarPoint[] points;
        public double[] aoas;
        public int[] colors;
        public double[] originalSpeeds;
        public double originalDensity;
        public double densityRatio;
        public int nPoints;
        public WingsuitPolar polar;
        public double effectiveArea; // Effective surface area (custom or default)
        public double effectiveMass; // Effective mass (custom or default)
    }

    private PolarCache wingsuitCache, canopyCache, airplaneCache;
    private int lastFlightMode = -1;

    /**
     * Initialize caches for all polars at the given altitude/density.
     */
    public void initialize(double density) {
        wingsuitCache = sampleAndCache(wingsuitPolar, 30, density, wingsuitArea, wingsuitMass);
        canopyCache = sampleAndCache(canopyPolar, 15, density, canopyArea, canopyMass);
        airplaneCache = sampleAndCache(airplanePolar, 30, density, airplaneArea, airplaneMass);
        lastFlightMode = -1;
    }

    /**
     * Sample the polar curve at nPoints, evenly spaced by arc length (distance along the curve).
     * Returns a PolarCache with sampled points, aoas, and original sustained speeds.
     */
    private PolarCache sampleAndCache(WingsuitPolar polar, int nPoints, double density, Double customArea, Double customMass) {
        double s = getEffectiveArea(polar, customArea);
        double m = getEffectiveMass(polar, customMass);
        double[] aoas = polar.aoas;
        PolarPoint[] polarPoints = polar.stallPoint.toArray(new PolarPoint[0]);
        int nPolar = Math.min(aoas.length, polarPoints.length);

        // Compute arc length along the polar curve in sustained speed space
        double[] arcLengths = new double[nPolar];
        arcLengths[0] = 0.0;
        for (int i = 1; i < nPolar; i++) {
            com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss0 =
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(
                            polarPoints[i - 1].cl, polarPoints[i - 1].cd, s, m, density);
            com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss1 =
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(
                            polarPoints[i].cl, polarPoints[i].cd, s, m, density);
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
                    com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(cl, cd, s, m, density);
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
        cache.effectiveArea = s;
        cache.effectiveMass = m;
        return cache;
    }

    /**
     * Sample a custom polar curve using a parabolic drag formula: cd = polarSlope*(cl-cl0)^2 + polarMinDrag
     * Samples nPoints evenly spaced by sustained speed magnitude.
     * Uses binary search to find CL values that produce target sustained speeds.
     */
    private PolarCache customSampleAndCache(int nPoints, double density, double s, double m,
                                            double polarMinDrag, double polarCl0, double polarSlope,
                                            double polarMinCl, double polarMaxCl) {
        // Calculate sustained speeds at the endpoints (polarMaxCl and polarMinCl)
        double cdMax = polarSlope * Math.pow(polarMaxCl - polarCl0, 2) + polarMinDrag;
        double cdMin = polarSlope * Math.pow(polarMinCl - polarCl0, 2) + polarMinDrag;

        com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ssMax =
                com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(polarMaxCl, cdMax, s, m, density);
        com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ssMin =
                com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(polarMinCl, cdMin, s, m, density);

        double speedMax = Math.sqrt(ssMax.vxs * ssMax.vxs + ssMax.vys * ssMax.vys);
        double speedMin = Math.sqrt(ssMin.vxs * ssMin.vxs + ssMin.vys * ssMin.vys);

        // Sample points
        PolarPoint[] sampledPoints = new PolarPoint[nPoints];
        double[] sampledAoas = new double[nPoints];
        double[] originalSpeeds = new double[nPoints];
        int[] colors = new int[nPoints];

        for (int i = 0; i < nPoints; i++) {
            // Calculate target sustained speed for this point
            double t = (double) i / (nPoints - 1);
            double targetSpeed = speedMax + t * (speedMin - speedMax);

            // Binary search for CL that produces targetSpeed
            // Search bounds: low CL (polarMinCl) to high CL (polarMaxCl)
            double clMin = polarMinCl;
            double clMax = polarMaxCl;
            double cl = 0;
            double cd = 0;

            // Binary search iterations
            for (int iter = 0; iter < 50; iter++) {
                cl = (clMin + clMax) / 2.0;
                cd = polarSlope * Math.pow(cl - polarCl0, 2) + polarMinDrag;

                com.platypii.baselinexr.charts.SpeedChartLive.SustainedSpeeds ss =
                        com.platypii.baselinexr.charts.SpeedChartLive.coefftoss(cl, cd, s, m, density);
                double speed = Math.sqrt(ss.vxs * ss.vxs + ss.vys * ss.vys);

                if (Math.abs(speed - targetSpeed) < 0.001) {
                    break; // Close enough
                }

                // Higher CL generally means slower speed (more drag)
                if (speed > targetSpeed) {
                    clMin = cl; // Speed too high, need more CL (more drag)
                } else {
                    clMax = cl; // Speed too low, need less CL (less drag)
                }
            }

            sampledPoints[i] = new PolarPoint(cl, cd);

            // Estimate AoA from CL (linear mapping)
            double aoaMin = 0.0;
            double aoaMax = 20.0;
            double aoaT = (cl - polarMinCl) / (polarMaxCl - polarMinCl);
            double aoa = aoaMin + aoaT * (aoaMax - aoaMin);
            sampledAoas[i] = aoa;

            // Store the sustained speed
            originalSpeeds[i] = targetSpeed;

            // Assign color based on AoA
            colors[i] = colorForAoA(aoa, aoaMin, aoaMax);
        }

        // Create a minimal WingsuitPolar for the cache
        WingsuitPolar customPolar = new WingsuitPolar(
                "custom", "", "Custom", "Custom", false,
                polarSlope, polarCl0, polarMinDrag, polarMinCl, polarMaxCl,
                new double[0], new double[0], new double[0],
                java.util.Collections.emptyList(), s, m
        );

        PolarCache cache = new PolarCache();
        cache.points = sampledPoints;
        cache.aoas = sampledAoas;
        cache.colors = colors;
        cache.originalSpeeds = Arrays.copyOf(originalSpeeds, nPoints);
        cache.originalDensity = density;
        cache.densityRatio = 1.0;
        cache.nPoints = nPoints;
        cache.polar = customPolar;
        cache.effectiveArea = s;
        cache.effectiveMass = m;
        return cache;
    }

    /**
     * Get the cached polar for the given density and flight mode, updating speeds if needed.
     * Returns the PolarCache (with updated speeds/colors if density changed).
     */
    public PolarCache getCachedPolar(double density, int flightMode) {
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

    /**
     * Set the wingsuit polar and invalidate cache
     */
    public void setWingsuitPolar(WingsuitPolar polar) {
        wingsuitPolar = polar;
        wingsuitCache = null;
    }

    /**
     * Set the canopy polar and invalidate cache
     */
    public void setCanopyPolar(WingsuitPolar polar) {
        canopyPolar = polar;
        canopyCache = null;
    }

    /**
     * Set the airplane polar and invalidate cache
     */
    public void setAirplanePolar(WingsuitPolar polar) {
        airplanePolar = polar;
        airplaneCache = null;
    }

    /**
     * Set custom wingsuit surface area and mass, then invalidate cache
     */
    public void setWingsuitParameters(Double area, Double mass) {
        wingsuitArea = area;
        wingsuitMass = mass;
        wingsuitCache = null;
    }

    /**
     * Set custom canopy surface area and mass, then invalidate cache
     */
    public void setCanopyParameters(Double area, Double mass) {
        canopyArea = area;
        canopyMass = mass;
        canopyCache = null;
    }

    /**
     * Set custom airplane surface area and mass, then invalidate cache
     */
    public void setAirplaneParameters(Double area, Double mass) {
        airplaneArea = area;
        airplaneMass = mass;
        airplaneCache = null;
    }

    /**
     * Set custom airplane polar from coefficients and regenerate cache using formula
     */
    public void setCustomAirplanePolar(double mass, double area, double polarSlope,
                                       double polarCl0, double polarMinDrag,
                                       double polarMinCl, double polarMaxCl) {
        airplaneMass = mass;
        airplaneArea = area;
        // Create cache directly using custom formula
        airplaneCache = customSampleAndCache(30, 1.225, area, mass,
                polarMinDrag, polarCl0, polarSlope,
                polarMinCl, polarMaxCl);
    }

    /**
     * Set custom wingsuit polar from coefficients and regenerate cache using formula
     */
    public void setCustomWingsuitPolar(double mass, double area, double polarSlope,
                                       double polarCl0, double polarMinDrag,
                                       double polarMinCl, double polarMaxCl) {
        wingsuitMass = mass;
        wingsuitArea = area;
        // Create cache directly using custom formula
        wingsuitCache = customSampleAndCache(30, 1.225, area, mass,
                polarMinDrag, polarCl0, polarSlope,
                polarMinCl, polarMaxCl);
    }

    /**
     * Set custom canopy polar from coefficients and regenerate cache using formula
     */
    public void setCustomCanopyPolar(double mass, double area, double polarSlope,
                                     double polarCl0, double polarMinDrag,
                                     double polarMinCl, double polarMaxCl) {
        canopyMass = mass;
        canopyArea = area;
        // Create cache directly using custom formula
        canopyCache = customSampleAndCache(15, 1.225, area, mass,
                polarMinDrag, polarCl0, polarSlope,
                polarMinCl, polarMaxCl);
    }

    /**
     * Get effective surface area for a polar (custom or default)
     */
    private double getEffectiveArea(WingsuitPolar polar, Double customArea) {
        return customArea != null ? customArea : polar.s;
    }

    /**
     * Get effective mass for a polar (custom or default)
     */
    private double getEffectiveMass(WingsuitPolar polar, Double customMass) {
        return customMass != null ? customMass : polar.m;
    }
}
