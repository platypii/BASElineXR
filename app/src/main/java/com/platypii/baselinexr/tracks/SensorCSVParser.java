package com.platypii.baselinexr.tracks;

import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.measurements.MSensorData;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for FlySight SENSOR.CSV files.
 *
 * Handles GPS time synchronization using TIME entries that provide the mapping between
 * local sensor time and GPS time (TOW = Time Of Week in seconds).
 *
 * Format:
 * $TIME,localTime,tow,week
 * $MAG,time,x,y,z,temperature
 * $IMU,time,wx,wy,wz,ax,ay,az,temperature
 * $BARO,time,pressure,temperature
 * $HUM,time,humidity,temperature
 * $VBAT,time,voltage
 */
public class SensorCSVParser {
    private static final String TAG = "SensorCSVParser";

    // GPS time synchronization state
    private static class TimeSync {
        double localTime;   // Local sensor time (seconds)
        double tow;         // GPS Time Of Week (seconds)
        int week;           // GPS week number

        /**
         * Convert local sensor time to GPS milliseconds since epoch
         */
        long toGpsMillis(double sensorTime) {
            // GPS epoch is January 6, 1980, 00:00:00 UTC
            final long GPS_EPOCH_MILLIS = 315964800000L; // Unix time of GPS epoch

            // Calculate time delta from sync point
            double deltaSeconds = sensorTime - localTime;

            // Apply delta to GPS TOW
            double gpsSeconds = tow + deltaSeconds;

            // Convert GPS week + TOW to milliseconds since GPS epoch
            long gpsMillis = (long) (week * 7 * 24 * 3600 * 1000L + gpsSeconds * 1000);

            // Convert to Unix time
            return GPS_EPOCH_MILLIS + gpsMillis;
        }
    }

    /**
     * Parse SENSOR.CSV file and return list of sensor measurements with GPS-synchronized timestamps
     */
    @NonNull
    public static List<MSensorData> parse(@NonNull BufferedReader br) throws IOException {
        final List<MSensorData> data = new ArrayList<>();

        // Time synchronization state
        TimeSync currentSync = null;

        // Latest sensor values (used to combine data from different sensor types)
        Float magX = null, magY = null, magZ = null, magTemp = null;
        Float gyroX = null, gyroY = null, gyroZ = null;
        Float accelX = null, accelY = null, accelZ = null, imuTemp = null;
        Float pressure = null, baroTemp = null;
        Float humidity = null, humidityTemp = null;
        Float vbat = null;

        String line;
        int lineNumber = 0;

        while ((line = br.readLine()) != null) {
            lineNumber++;

            // Skip empty lines and headers
            if (line.isEmpty() || line.startsWith("$FLYS") || line.startsWith("$VAR") ||
                    line.startsWith("$COL") || line.startsWith("$UNIT") || line.equals("$DATA")) {
                continue;
            }

            // Parse data lines
            if (line.startsWith("$")) {
                try {
                    final String[] parts = line.substring(1).split(",");
                    if (parts.length < 2) continue;

                    final String type = parts[0];

                    switch (type) {
                        case "TIME":
                            // $TIME,localTime,tow,week
                            if (parts.length >= 4) {
                                currentSync = new TimeSync();
                                currentSync.localTime = parseDouble(parts[1]);
                                currentSync.tow = parseDouble(parts[2]);
                                currentSync.week = parseInt(parts[3]);
                                Log.d(TAG, String.format("GPS time sync: local=%.3f, tow=%.0f, week=%d",
                                        currentSync.localTime, currentSync.tow, currentSync.week));
                            }
                            break;

                        case "MAG":
                            // $MAG,time,x,y,z,temperature
                            if (parts.length >= 6 && currentSync != null) {
                                double sensorTime = parseDouble(parts[1]);
                                magX = parseFloat(parts[2]);
                                magY = parseFloat(parts[3]);
                                magZ = parseFloat(parts[4]);
                                magTemp = parseFloat(parts[5]);

                                // Create sensor data entry if we have complete data
                                long gpsMillis = currentSync.toGpsMillis(sensorTime);
                                createSensorDataEntry(data, gpsMillis,
                                        magX, magY, magZ, magTemp,
                                        gyroX, gyroY, gyroZ, accelX, accelY, accelZ, imuTemp,
                                        pressure, baroTemp, humidity, humidityTemp, vbat);
                            }
                            break;

                        case "IMU":
                            // $IMU,time,wx,wy,wz,ax,ay,az,temperature
                            if (parts.length >= 9) {
                                double sensorTime = parseDouble(parts[1]);
                                gyroX = parseFloat(parts[2]);
                                gyroY = parseFloat(parts[3]);
                                gyroZ = parseFloat(parts[4]);
                                accelX = parseFloat(parts[5]);
                                accelY = parseFloat(parts[6]);
                                accelZ = parseFloat(parts[7]);
                                imuTemp = parseFloat(parts[8]);
                            }
                            break;

                        case "BARO":
                            // $BARO,time,pressure,temperature
                            if (parts.length >= 4) {
                                double sensorTime = parseDouble(parts[1]);
                                pressure = parseFloat(parts[2]);
                                baroTemp = parseFloat(parts[3]);
                            }
                            break;

                        case "HUM":
                            // $HUM,time,humidity,temperature
                            if (parts.length >= 4) {
                                double sensorTime = parseDouble(parts[1]);
                                humidity = parseFloat(parts[2]);
                                humidityTemp = parseFloat(parts[3]);
                            }
                            break;

                        case "VBAT":
                            // $VBAT,time,voltage
                            if (parts.length >= 3) {
                                double sensorTime = parseDouble(parts[1]);
                                vbat = parseFloat(parts[2]);
                            }
                            break;
                    }

                } catch (Exception e) {
                    Log.w(TAG, "Error parsing line " + lineNumber + ": " + line, e);
                }
            }
        }

        Log.i(TAG, String.format("Parsed %d sensor measurements from SENSOR.CSV", data.size()));
        return data;
    }

    /**
     * Create a sensor data entry if we have magnetometer data (minimum requirement)
     */
    private static void createSensorDataEntry(List<MSensorData> data, long gpsMillis,
                                              Float magX, Float magY, Float magZ, Float magTemp,
                                              Float gyroX, Float gyroY, Float gyroZ,
                                              Float accelX, Float accelY, Float accelZ, Float imuTemp,
                                              Float pressure, Float baroTemp,
                                              Float humidity, Float humidityTemp,
                                              Float vbat) {
        // Only create entry if we have magnetometer data
        if (magX != null && magY != null && magZ != null) {
            MSensorData entry = new MSensorData(
                    gpsMillis, System.nanoTime(),
                    magX, magY, magZ, magTemp != null ? magTemp : Float.NaN,
                    gyroX != null ? gyroX : Float.NaN,
                    gyroY != null ? gyroY : Float.NaN,
                    gyroZ != null ? gyroZ : Float.NaN,
                    accelX != null ? accelX : Float.NaN,
                    accelY != null ? accelY : Float.NaN,
                    accelZ != null ? accelZ : Float.NaN,
                    imuTemp != null ? imuTemp : Float.NaN,
                    pressure != null ? pressure : Float.NaN,
                    baroTemp != null ? baroTemp : Float.NaN,
                    humidity != null ? humidity : Float.NaN,
                    humidityTemp != null ? humidityTemp : Float.NaN,
                    vbat != null ? vbat : Float.NaN
            );
            data.add(entry);

            // Log first few entries to verify timestamps
            if (data.size() <= 3) {
                Log.d(TAG, String.format("Created sensor entry #%d: gpsMillis=%d, mag=[%.3f,%.3f,%.3f]",
                        data.size(), gpsMillis, magX, magY, magZ));
            }
        }
    }

    private static double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static float parseFloat(String s) {
        try {
            return Float.parseFloat(s);
        } catch (NumberFormatException e) {
            return Float.NaN;
        }
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
