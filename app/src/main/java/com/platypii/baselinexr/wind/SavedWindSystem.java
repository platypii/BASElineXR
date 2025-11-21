package com.platypii.baselinexr.wind;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads wind keyframes from a CSV file and provides interpolated wind data by altitude.
 */
public class SavedWindSystem {
    private static final String TAG = "SavedWindSystem";
    private static final String CSV_FILE = "savedwind.csv";

    public static class WindKeyframe {
        public final double altitude;
        public final double windspeed;
        public final double direction;
        public final double inclination;

        public WindKeyframe(double altitude, double windspeed, double direction, double inclination) {
            this.altitude = altitude;
            this.windspeed = windspeed;
            this.direction = direction;
            this.inclination = inclination;
        }
    }

    private final List<WindKeyframe> keyframes = new ArrayList<>();

    public SavedWindSystem(Context context) {
        loadKeyframes(context);
    }

    private void loadKeyframes(Context context) {
        AssetManager assetManager = context.getAssets();
        try (InputStream is = assetManager.open(CSV_FILE);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) continue;
                String[] tokens = line.split(",");
                if (tokens.length < 4) continue;
                try {
                    double altitude = Double.parseDouble(tokens[0]);
                    double windspeed = Double.parseDouble(tokens[1]);
                    double direction = Double.parseDouble(tokens[2]);
                    double inclination = Double.parseDouble(tokens[3]);
                    keyframes.add(new WindKeyframe(altitude, windspeed, direction, inclination));
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Invalid wind keyframe: " + line);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to load saved wind CSV", e);
        }
    }

    /**
     * Returns an interpolated WindKeyframe for the given altitude.
     */
    public WindKeyframe getWindAtAltitude(double altitude) {
        if (keyframes.isEmpty()) return null;
        if (altitude <= keyframes.get(0).altitude) return keyframes.get(0);
        if (altitude >= keyframes.get(keyframes.size() - 1).altitude) return keyframes.get(keyframes.size() - 1);
        for (int i = 0; i < keyframes.size() - 1; i++) {
            WindKeyframe kf1 = keyframes.get(i);
            WindKeyframe kf2 = keyframes.get(i + 1);
            if (altitude >= kf1.altitude && altitude <= kf2.altitude) {
                double t = (altitude - kf1.altitude) / (kf2.altitude - kf1.altitude);
                double windspeed = lerp(kf1.windspeed, kf2.windspeed, t);
                double direction = lerpAngle(kf1.direction, kf2.direction, t);
                double inclination = lerp(kf1.inclination, kf2.inclination, t);
                return new WindKeyframe(altitude, windspeed, direction, inclination);
            }
        }
        return null;
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    // Interpolates angles in degrees, handling wraparound
    private double lerpAngle(double a, double b, double t) {
        double diff = ((b - a + 540) % 360) - 180;
        return (a + diff * t + 360) % 360;
    }
}
