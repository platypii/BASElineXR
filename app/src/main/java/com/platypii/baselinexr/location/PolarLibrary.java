package com.platypii.baselinexr.location;

import java.util.Arrays;
import java.util.List;

public class PolarLibrary {

    public static class PolarPoint {
        public final double cl;
        public final double cd;
        
        public PolarPoint(double cl, double cd) {
            this.cl = cl;
            this.cd = cd;
        }
    }

    public static class WingsuitPolar {
        public final String polarId;
        public final String userId;
        public final String type;
        public final String name;
        public final boolean isPublic;
        public final double polarSlope;
        public final double polarClo;
        public final double polarMinDrag;
        public final double rangeMinCl;
        public final double rangeMaxCl;
        public final double[] aoaIndexes;
        public final int[] aoas;
        public final double[] cp;
        public final List<PolarPoint> stallPoint;
        public final double s; // wing area (m²)
        public final double m; // mass (kg)
        
        public WingsuitPolar(String polarId, String userId, String type, String name, boolean isPublic,
                           double polarSlope, double polarClo, double polarMinDrag, double rangeMinCl, double rangeMaxCl,
                           double[] aoaIndexes, int[] aoas, double[] cp, List<PolarPoint> stallPoint, double s, double m) {
            this.polarId = polarId;
            this.userId = userId;
            this.type = type;
            this.name = name;
            this.isPublic = isPublic;
            this.polarSlope = polarSlope;
            this.polarClo = polarClo;
            this.polarMinDrag = polarMinDrag;
            this.rangeMinCl = rangeMinCl;
            this.rangeMaxCl = rangeMaxCl;
            this.aoaIndexes = aoaIndexes;
            this.aoas = aoas;
            this.cp = cp;
            this.stallPoint = stallPoint;
            this.s = s;
            this.m = m;
        }
    }

    public static final List<PolarPoint> AURA_FIVE_STALL_POINT = Arrays.asList(
        new PolarPoint(0.108983764628346, 1.08733),
        new PolarPoint(0.185891612981445, 1.07550125),
        new PolarPoint(0.210159022016888, 1.04624),
        new PolarPoint(0.236338092608918, 1.00421875),
        new PolarPoint(0.276174302741996, 0.95411),
        new PolarPoint(0.321902830713497, 0.90058625),
        new PolarPoint(0.363958657340666, 0.848320000000001),
        new PolarPoint(0.400686482484531, 0.80198375),
        new PolarPoint(0.443431551630603, 0.77),
        new PolarPoint(0.501784571242392, 0.741),
        new PolarPoint(0.516614859906805, 0.735),
        new PolarPoint(0.533624176122725, 0.73),
        new PolarPoint(0.548480181747974, 0.72),
        new PolarPoint(0.564801482459875, 0.71),
        new PolarPoint(0.582677701039275, 0.7),
        new PolarPoint(0.602181355660182, 0.69),
        new PolarPoint(0.623365404678184, 0.68),
        new PolarPoint(0.646260864379027, 0.67),
        new PolarPoint(0.670874519347125, 0.66),
        new PolarPoint(0.70576750505298, 0.658),
        new PolarPoint(0.759140843806183, 0.67),
        new PolarPoint(0.810986085509315, 0.677),
        new PolarPoint(0.88072260117917, 0.695),
        new PolarPoint(0.965501597206807, 0.72),
        new PolarPoint(1.05011755421627, 0.74),
        new PolarPoint(1.1371, 0.747538425047438),
        new PolarPoint(1.15574082635108, 0.715249918087947),
        new PolarPoint(1.15539526148586, 0.674507873388006),
        new PolarPoint(1.14683928171753, 0.630877095494163),
        new PolarPoint(1.11365205723984, 0.578580118018614),
        new PolarPoint(1.08461323582186, 0.532820262727508),
        new PolarPoint(1.03921292555216, 0.485724926151704),
        new PolarPoint(0.973302723371025, 0.430756986106757),
        new PolarPoint(0.907945945116896, 0.377696088274903),
        new PolarPoint(0.855618188821683, 0.343913198706244),
        new PolarPoint(0.805163095460971, 0.313424543901754),
        new PolarPoint(0.761810592507725, 0.288863037567478),
        new PolarPoint(0.719519027870984, 0.266359012152287),
        new PolarPoint(0.677227463234242, 0.245293347914775),
        new PolarPoint(0.642590319337377, 0.229111816346851),
        new PolarPoint(0.601309850277438, 0.211086840529111),
        new PolarPoint(0.568285475029486, 0.197653553200295),
        new PolarPoint(0.510492818345571, 0.176255727765257),
        new PolarPoint(0.483132714445128, 0.167062404747948),
        new PolarPoint(0.461436881892194, 0.160200300064325),
        new PolarPoint(0.418045216786326, 0.147611714122478),
        new PolarPoint(0.385501467956925, 0.139163945163318),
        new PolarPoint(0.342109802851057, 0.129225147214071),
        new PolarPoint(0.298718137745189, 0.120800513832024),
        new PolarPoint(0.249388066223454, 0.11306209742444),
        new PolarPoint(0.207936707770252, 0.108072711176012),
        new PolarPoint(0.15266822983265, 0.103569627182967),
        new PolarPoint(0.0835826324106472, 0.101395214878043),
        new PolarPoint(0.0144970349886442, 0.103059072224655)
    );

    public static final int[] AURA_FIVE_AOAS = {
        90, 85, 80, 75, 70, 65, 60, 55, 50, 45, 44, 43, 42, 41, 40, 39, 38, 37, 36, 35,
        34, 33, 32, 31, 30, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16, 15, 14,
        13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
    };

    public static final double[] AURA_FIVE_AOA_INDEXES = {
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.0973293002538006,
        0.111603420554605, 0.126644343855929, 0.142952948209109, 0.157392756917588,
        0.176072473739675, 0.192339895747025, 0.224200774751407, 0.241070606343121,
        0.255406594180041, 0.28703753161202, 0.313823164024654, 0.354610522808676,
        0.40272129228758, 0.469227952116029, 0.538110242647231, 0.654985426264267,
        0.846649581714156, 1
    };

    public static final double[] AURA_FIVE_CP = {
        0.901367256532332, 0.819922671799767, 0.81865591174113, 0.817549210804472,
        0.807584803437846, 0.7877449240893, 0.759011807206887, 0.722367687238658,
        0.678794798632662, 0.629275375836952, 0.618744343806472, 0.608022597661911,
        0.597117995278852, 0.586038394532879, 0.574791653299577, 0.563385629454531,
        0.551828180873325, 0.540127165431543, 0.52829044100477, 0.51632586546859,
        0.504241296698587, 0.492044592570347, 0.481452630223225, 0.475420516158898,
        0.469421112816359, 0.457459576274893, 0.451502244326367, 0.445584032972467,
        0.439735391686759, 0.433995116116988, 0.428408751771098, 0.423026997703251,
        0.417904110199851, 0.413096306465556, 0.408660168309301, 0.40465104583032,
        0.40112146110416, 0.398119511868704, 0.395687275210186, 0.393859211249218,
        0.392660566826802, 0.39210577919035, 0.392196879679709, 0.392921897413175,
        0.394253262973511, 0.396146212093973, 0.398537189344323, 0.401342251816851,
        0.404455472812395, 0.407747345526357, 0.411063186734725, 0.414221540480093,
        0.417012581757679, 0.419196520201342
    };

    public static final WingsuitPolar AURA_FIVE_POLAR = new WingsuitPolar(
        "21",           // polarId
        "",             // userId
        "Wingsuit",     // type
        "Aura 5",       // name
        true,           // isPublic
        0.402096647,    // polarSlope
        0.078987854,    // polarClo
        0.101386726,    // polarMinDrag
        0.000679916,    // rangeMinCl
        0.950065434,    // rangeMaxCl
        AURA_FIVE_AOA_INDEXES,  // aoaIndexes
        AURA_FIVE_AOAS,         // aoas
        AURA_FIVE_CP,           // cp
        AURA_FIVE_STALL_POINT,  // stallPoint
        2.0,            // s (wing area in m²)
        77.5            // m (mass in kg)
    );

    /**
     * Convert measured KL/KD coefficients to Angle of Attack (AOA) in degrees
     * Uses atmospheric model for density calculation and specified polar parameters
     *
     * @param kl Measured lift coefficient (from Kalman filter)
     * @param kd Measured drag coefficient (from Kalman filter)
     * @param altitudeGps GPS altitude in meters
     * @param polar Wingsuit polar data containing s (wing area) and m (mass)
     * @return AOA in degrees, clamped between 0 and 30 degrees
     */
    public static double convertKlKdToAOA(double kl, double kd, double altitudeGps, WingsuitPolar polar) {
        // Constants
        final double GRAVITY = 9.80665; // m/s²
        final double TEMP_OFFSET = 10.0f; // Temperature offset in degrees C

        // Calculate atmospheric density using altitude + 10°C temperature offset
        final double rho = AtmosphericModel.calculateDensity((float)altitudeGps, 10);

        // Calculate k factor: k = 0.5 * rho * s / m
        final double k = 0.5 * rho * polar.s / polar.m;

        // Convert KL/KD to CL/CD: cl = kl / k * gravity, cd = kd / k * gravity
        final double cl = kl / k * GRAVITY;
        final double cd = kd / k * GRAVITY;

        // Calculate AOA using formula: aoadeg = 20.874*sqrt(cl^2+cd^2) - 1.1733
        final double clcdMagnitude = Math.sqrt(cl * cl + cd * cd);
        final double aoaDeg = 20.874 * clcdMagnitude - 1.1733;

        // Clamp between 0 and 30 degrees
        return Math.max(0.0, Math.min(35.0, aoaDeg));
    }
}