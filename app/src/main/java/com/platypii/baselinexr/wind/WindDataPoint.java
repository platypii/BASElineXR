package com.platypii.baselinexr.wind;

/**
 * Single data point for wind estimation containing GPS velocity and sustained velocity data
 */
public class WindDataPoint {
    public final long millis;
    public final double altitude;
    public final double vE;          // GPS East velocity
    public final double vN;          // GPS North velocity
    public final double vD;          // GPS Down velocity
    public final double sustainedVE; // Sustained East velocity from KL/KD
    public final double sustainedVN; // Sustained North velocity from KL/KD
    public final double sustainedVD; // Sustained Down velocity from KL/KD
    
    public WindDataPoint(long millis, double altitude, double vE, double vN, double vD, 
                        double sustainedVE, double sustainedVN, double sustainedVD) {
        this.millis = millis;
        this.altitude = altitude;
        this.vE = vE;
        this.vN = vN;
        this.vD = vD;
        this.sustainedVE = sustainedVE;
        this.sustainedVN = sustainedVN;
        this.sustainedVD = sustainedVD;
    }
    
    public double getGroundSpeed() {
        return Math.sqrt(vE * vE + vN * vN);
    }
    
    public double getSustainedGroundSpeed() {
        return Math.sqrt(sustainedVE * sustainedVE + sustainedVN * sustainedVN);
    }
    
    public double getHeading() {
        return Math.atan2(vE, vN) * 180.0 / Math.PI;
    }
    
    public double getSustainedHeading() {
        return Math.atan2(sustainedVE, sustainedVN) * 180.0 / Math.PI;
    }
}