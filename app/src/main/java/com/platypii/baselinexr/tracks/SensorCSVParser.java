package com.platypii.baselinexr.tracks;

import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MSensorData;
import com.platypii.baselinexr.measurements.MTimeSync;
import com.platypii.baselinexr.measurements.SensorDataSet;

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
 * 
 * Each sensor type is parsed into separate lists with independent timestamps,
 * matching the BLE characteristic structure where each sensor streams independently.
 */
public class SensorCSVParser {
    private static final String TAG = "SensorCSVParser";

    /**
     * Parse SENSOR.CSV file and return SensorDataSet with separate lists for each sensor type.
     * Each sensor type maintains its own timestamps, matching the BLE streaming model.
     */
    @NonNull
    public static SensorDataSet parseToDataSet(@NonNull BufferedReader br) throws IOException {
        final List<MImuData> imuData = new ArrayList<>();
        final List<MMagData> magData = new ArrayList<>();
        final List<MBaroData> baroData = new ArrayList<>();
        final List<MHumidityData> humidityData = new ArrayList<>();
        final List<MTimeSync> timeSyncData = new ArrayList<>();
        final List<MSensorData> combinedData = new ArrayList<>();

        // Time synchronization state
        MTimeSync currentSync = null;

        // Latest sensor values (for legacy combined data)
        Float lastMagX = null, lastMagY = null, lastMagZ = null, lastMagTemp = null;
        Float lastGyroX = null, lastGyroY = null, lastGyroZ = null;
        Float lastAccelX = null, lastAccelY = null, lastAccelZ = null, lastImuTemp = null;
        Float lastPressure = null, lastBaroTemp = null;
        Float lastHumidity = null, lastHumidityTemp = null;
        Float lastVbat = null;

        // Last known good humidity values (sensor sometimes outputs bad data during startup)
        Float lastGoodHumidity = null;
        Float lastGoodHumidityTemp = null;
        int badHumidityCount = 0;

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
                                double localTimeSec = parseDouble(parts[1]);
                                double towSec = parseDouble(parts[2]);
                                int week = parseInt(parts[3]);
                                currentSync = MTimeSync.fromCsvSeconds(localTimeSec, towSec, week);
                                timeSyncData.add(currentSync);
                                Log.d(TAG, String.format("GPS time sync: local=%.3f, tow=%.0f, week=%d",
                                        localTimeSec, towSec, week));
                            }
                            break;

                        case "IMU":
                            // $IMU,time,wx,wy,wz,ax,ay,az,temperature
                            if (parts.length >= 9 && currentSync != null) {
                                double sensorTimeSec = parseDouble(parts[1]);
                                long deviceTimeMs = (long) (sensorTimeSec * 1000);
                                long gpsMillis = currentSync.toUnixMillis(sensorTimeSec);
                                
                                float gyroX = parseFloat(parts[2]);
                                float gyroY = parseFloat(parts[3]);
                                float gyroZ = parseFloat(parts[4]);
                                float accelX = parseFloat(parts[5]);
                                float accelY = parseFloat(parts[6]);
                                float accelZ = parseFloat(parts[7]);
                                float imuTemp = parseFloat(parts[8]);
                                
                                MImuData imu = new MImuData(gpsMillis, deviceTimeMs,
                                        gyroX, gyroY, gyroZ, accelX, accelY, accelZ, imuTemp);
                                imuData.add(imu);
                                
                                // Cache for legacy combined data
                                lastGyroX = gyroX;
                                lastGyroY = gyroY;
                                lastGyroZ = gyroZ;
                                lastAccelX = accelX;
                                lastAccelY = accelY;
                                lastAccelZ = accelZ;
                                lastImuTemp = imuTemp;
                            }
                            break;

                        case "MAG":
                            // $MAG,time,x,y,z,temperature
                            if (parts.length >= 6 && currentSync != null) {
                                double sensorTimeSec = parseDouble(parts[1]);
                                long deviceTimeMs = (long) (sensorTimeSec * 1000);
                                long gpsMillis = currentSync.toUnixMillis(sensorTimeSec);
                                
                                float magX = parseFloat(parts[2]);
                                float magY = parseFloat(parts[3]);
                                float magZ = parseFloat(parts[4]);
                                float magTemp = parseFloat(parts[5]);
                                
                                MMagData mag = new MMagData(gpsMillis, deviceTimeMs,
                                        magX, magY, magZ, magTemp);
                                magData.add(mag);
                                
                                // Cache for legacy combined data
                                lastMagX = magX;
                                lastMagY = magY;
                                lastMagZ = magZ;
                                lastMagTemp = magTemp;
                                
                                // Create legacy combined entry on MAG (preserves old behavior)
                                createCombinedEntry(combinedData, gpsMillis,
                                        lastMagX, lastMagY, lastMagZ, lastMagTemp,
                                        lastGyroX, lastGyroY, lastGyroZ, lastAccelX, lastAccelY, lastAccelZ, lastImuTemp,
                                        lastPressure, lastBaroTemp, lastHumidity, lastHumidityTemp, lastVbat);
                            }
                            break;

                        case "BARO":
                            // $BARO,time,pressure,temperature
                            if (parts.length >= 4 && currentSync != null) {
                                double sensorTimeSec = parseDouble(parts[1]);
                                long deviceTimeMs = (long) (sensorTimeSec * 1000);
                                long gpsMillis = currentSync.toUnixMillis(sensorTimeSec);
                                
                                float pressure = parseFloat(parts[2]);
                                float baroTemp = parseFloat(parts[3]);
                                
                                MBaroData baro = new MBaroData(gpsMillis, deviceTimeMs, pressure, baroTemp);
                                baroData.add(baro);
                                
                                // Cache for legacy combined data
                                lastPressure = pressure;
                                lastBaroTemp = baroTemp;
                            }
                            break;

                        case "HUM":
                            // $HUM,time,humidity,temperature
                            if (parts.length >= 4 && currentSync != null) {
                                double sensorTimeSec = parseDouble(parts[1]);
                                long deviceTimeMs = (long) (sensorTimeSec * 1000);
                                long gpsMillis = currentSync.toUnixMillis(sensorTimeSec);
                                
                                float rawHumidity = parseFloat(parts[2]);
                                float rawHumidityTemp = parseFloat(parts[3]);
                                
                                // Validate humidity: must be 0-100%
                                if (isValidHumidity(rawHumidity)) {
                                    MHumidityData hum = new MHumidityData(gpsMillis, deviceTimeMs,
                                            rawHumidity, rawHumidityTemp);
                                    humidityData.add(hum);
                                    
                                    // Cache for legacy combined data
                                    lastHumidity = rawHumidity;
                                    lastHumidityTemp = rawHumidityTemp;
                                    lastGoodHumidity = rawHumidity;
                                    lastGoodHumidityTemp = rawHumidityTemp;
                                } else {
                                    badHumidityCount++;
                                    if (badHumidityCount <= 5) {
                                        Log.w(TAG, String.format("Bad humidity value %.1f%% at line %d, skipping", 
                                                rawHumidity, lineNumber));
                                    }
                                    // For legacy data, use last good value if available
                                    if (lastGoodHumidity != null) {
                                        lastHumidity = lastGoodHumidity;
                                        lastHumidityTemp = lastGoodHumidityTemp;
                                    }
                                }
                            }
                            break;

                        case "VBAT":
                            // $VBAT,time,voltage
                            if (parts.length >= 3) {
                                lastVbat = parseFloat(parts[2]);
                            }
                            break;
                    }

                } catch (Exception e) {
                    Log.w(TAG, "Error parsing line " + lineNumber + ": " + line, e);
                }
            }
        }

        if (badHumidityCount > 0) {
            Log.w(TAG, String.format("Filtered %d bad humidity readings from SENSOR.CSV", badHumidityCount));
        }
        Log.i(TAG, String.format("Parsed SENSOR.CSV: imu=%d, mag=%d, baro=%d, hum=%d, timeSync=%d, combined=%d",
                imuData.size(), magData.size(), baroData.size(), humidityData.size(), 
                timeSyncData.size(), combinedData.size()));
        
        return new SensorDataSet(imuData, magData, baroData, humidityData, timeSyncData, combinedData);
    }
    
    /**
     * Parse SENSOR.CSV file and return legacy list of combined sensor measurements.
     * This is for backwards compatibility with existing code.
     * 
     * @deprecated Use {@link #parseToDataSet(BufferedReader)} for new code
     */
    @Deprecated
    @NonNull
    public static List<MSensorData> parse(@NonNull BufferedReader br) throws IOException {
        SensorDataSet dataSet = parseToDataSet(br);
        return dataSet.combinedData;
    }

    /**
     * Create a legacy combined sensor data entry (preserves old behavior for backwards compatibility)
     */
    private static void createCombinedEntry(List<MSensorData> data, long gpsMillis,
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

    /**
     * Validate humidity reading. Sensor sometimes outputs bad values (negative, >100%, or extreme values like 6000%)
     * Valid range is 0-100% relative humidity.
     */
    private static boolean isValidHumidity(float humidity) {
        return !Float.isNaN(humidity) && humidity >= 0f && humidity <= 100f;
    }
}
