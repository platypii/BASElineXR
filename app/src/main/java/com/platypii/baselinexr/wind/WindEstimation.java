package com.platypii.baselinexr.wind;

import java.util.ArrayList;
import java.util.List;

/**
 * Wind estimation result from least squares circle fit
 */
public class WindEstimation {
    public final double windSpeedE;      // Wind speed East component (m/s)
    public final double windSpeedN;      // Wind speed North component (m/s)
    public final double windMagnitude;   // Wind speed magnitude (m/s)
    public final double windDirection;   // Wind direction (degrees, 0=North, 90=East)
    public final double confidence;      // Estimation confidence (0-1)
    public final int dataPointCount;     // Number of data points used
    
    public WindEstimation(double windSpeedE, double windSpeedN, double confidence, int dataPointCount) {
        this.windSpeedE = windSpeedE;
        this.windSpeedN = windSpeedN;
        this.windMagnitude = Math.sqrt(windSpeedE * windSpeedE + windSpeedN * windSpeedN);
        this.windDirection = Math.atan2(windSpeedE, windSpeedN) * 180.0 / Math.PI;
        this.confidence = confidence;
        this.dataPointCount = dataPointCount;
    }
    
    @Override
    public String toString() {
        return String.format("Wind: %.1f m/s @ %.0f° (%.1f%% confidence, %d points)", 
                           windMagnitude, windDirection, confidence * 100, dataPointCount);
    }
}