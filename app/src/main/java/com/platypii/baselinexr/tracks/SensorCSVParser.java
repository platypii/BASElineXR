package com.platypii.baselinexr.tracks;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MTimeSync;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Parser for FlySight SENSOR.CSV files.
 * 
 * Format example:
 * $FLYS,1
 * $VAR,FIRMWARE_VER,v2024.06.09.starter_pistol
 * $VAR,DEVICE_ID,0017003b3253501720373657
 * $VAR,SESSION_ID,a1997456d69dd17124414fa9
 * $COL,BARO,time,pressure,temperature
 * $UNIT,BARO,s,Pa,deg C
 * $COL,IMU,time,wx,wy,wz,ax,ay,az,temperature
 * $UNIT,IMU,s,deg/s,deg/s,deg/s,g,g,g,deg C
 * $COL,MAG,time,x,y,z,temperature
 * $UNIT,MAG,s,gauss,gauss,gauss,deg C
 * $COL,TIME,time,tow,week
 * $UNIT,TIME,s,s,
 * $DATA
 * $IMU,152650.465,10.681,-17.700,-44.189,0.07470,0.24755,0.76123,30.64
 * $MAG,152650.427,-0.258,-0.702,-0.988,29.5
 * $BARO,152650.420,76997.26,31.04
 * $TIME,152654.461,227191.000,2377
 */
public class SensorCSVParser {
    private static final String TAG = "SensorCSVParser";

    /**
     * Parse a SENSOR.CSV file and return a SensorDataSet.
     * Data before the first $TIME entry is discarded since we can't sync it.
     */
    @NonNull
    public static SensorDataSet parse(@NonNull BufferedReader reader) throws IOException {
        List<MImuData> imuList = new ArrayList<>();
        List<MMagData> magList = new ArrayList<>();
        List<MBaroData> baroList = new ArrayList<>();
        List<MTimeSync> timeSyncList = new ArrayList<>();

        // Pending data before first TIME sync
        List<PendingImu> pendingImu = new ArrayList<>();
        List<PendingMag> pendingMag = new ArrayList<>();
        List<PendingBaro> pendingBaro = new ArrayList<>();

        MTimeSync currentSync = null;
        boolean inData = false;
        String sessionId = null;
        String deviceId = null;
        String firmwareVer = null;

        String line;
        int lineNum = 0;
        while ((line = reader.readLine()) != null) {
            lineNum++;
            line = line.trim();
            if (line.isEmpty()) continue;

            // Check for $DATA marker
            if (line.equals("$DATA")) {
                inData = true;
                continue;
            }

            // Parse header variables
            if (line.startsWith("$VAR,")) {
                String[] parts = line.split(",");
                if (parts.length >= 3) {
                    switch (parts[1]) {
                        case "SESSION_ID":
                            sessionId = parts[2];
                            break;
                        case "DEVICE_ID":
                            deviceId = parts[2];
                            break;
                        case "FIRMWARE_VER":
                            firmwareVer = parts[2];
                            break;
                    }
                }
                continue;
            }

            // Skip non-data lines
            if (!inData) continue;

            // Parse data lines
            try {
                if (line.startsWith("$TIME,")) {
                    MTimeSync sync = parseTime(line);
                    if (sync != null) {
                        timeSyncList.add(sync);
                        currentSync = sync;

                        // Process pending data now that we have sync
                        for (PendingImu p : pendingImu) {
                            imuList.add(MImuData.fromSensorTime(p.time, currentSync,
                                    p.gyroX, p.gyroY, p.gyroZ, p.accelX, p.accelY, p.accelZ, p.temp));
                        }
                        for (PendingMag p : pendingMag) {
                            magList.add(MMagData.fromSensorTime(p.time, currentSync,
                                    p.magX, p.magY, p.magZ, p.temp));
                        }
                        for (PendingBaro p : pendingBaro) {
                            baroList.add(MBaroData.fromSensorTime(p.time, currentSync,
                                    p.pressure, p.temp));
                        }
                        pendingImu.clear();
                        pendingMag.clear();
                        pendingBaro.clear();
                    }
                } else if (line.startsWith("$IMU,")) {
                    if (currentSync == null) {
                        // Buffer until we get time sync
                        PendingImu p = parsePendingImu(line);
                        if (p != null) pendingImu.add(p);
                    } else {
                        MImuData imu = parseImu(line, currentSync);
                        if (imu != null) imuList.add(imu);
                    }
                } else if (line.startsWith("$MAG,")) {
                    if (currentSync == null) {
                        PendingMag p = parsePendingMag(line);
                        if (p != null) pendingMag.add(p);
                    } else {
                        MMagData mag = parseMag(line, currentSync);
                        if (mag != null) magList.add(mag);
                    }
                } else if (line.startsWith("$BARO,")) {
                    if (currentSync == null) {
                        PendingBaro p = parsePendingBaro(line);
                        if (p != null) pendingBaro.add(p);
                    } else {
                        MBaroData baro = parseBaro(line, currentSync);
                        if (baro != null) baroList.add(baro);
                    }
                }
                // Ignore $HUM, $VBAT for now
            } catch (Exception e) {
                Log.w(TAG, "Error parsing line " + lineNum + ": " + line, e);
            }
        }

        // Log if we discarded data due to no time sync
        if (!pendingImu.isEmpty() || !pendingMag.isEmpty() || !pendingBaro.isEmpty()) {
            Log.w(TAG, "Discarded " + pendingImu.size() + " IMU, " + pendingMag.size() +
                    " MAG, " + pendingBaro.size() + " BARO samples (no TIME sync found)");
        }

        Log.i(TAG, "Parsed " + imuList.size() + " IMU, " + magList.size() + " MAG, " +
                baroList.size() + " BARO, " + timeSyncList.size() + " TIME entries");

        return new SensorDataSet(imuList, magList, baroList, timeSyncList,
                sessionId, deviceId, firmwareVer);
    }

    @Nullable
    private static MTimeSync parseTime(String line) {
        // $TIME,152654.461,227191.000,2377
        String[] parts = line.split(",");
        if (parts.length < 4) return null;
        double sensorTime = Double.parseDouble(parts[1]);
        double tow = Double.parseDouble(parts[2]);
        int week = Integer.parseInt(parts[3]);
        return new MTimeSync(sensorTime, tow, week);
    }

    @Nullable
    private static MImuData parseImu(String line, MTimeSync sync) {
        PendingImu p = parsePendingImu(line);
        if (p == null) return null;
        return MImuData.fromSensorTime(p.time, sync,
                p.gyroX, p.gyroY, p.gyroZ, p.accelX, p.accelY, p.accelZ, p.temp);
    }

    @Nullable
    private static PendingImu parsePendingImu(String line) {
        // $IMU,time,wx,wy,wz,ax,ay,az,temperature
        String[] parts = line.split(",");
        if (parts.length < 9) return null;
        return new PendingImu(
                Double.parseDouble(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5]),
                Float.parseFloat(parts[6]),
                Float.parseFloat(parts[7]),
                Float.parseFloat(parts[8])
        );
    }

    @Nullable
    private static MMagData parseMag(String line, MTimeSync sync) {
        PendingMag p = parsePendingMag(line);
        if (p == null) return null;
        return MMagData.fromSensorTime(p.time, sync, p.magX, p.magY, p.magZ, p.temp);
    }

    @Nullable
    private static PendingMag parsePendingMag(String line) {
        // $MAG,time,x,y,z,temperature
        String[] parts = line.split(",");
        if (parts.length < 6) return null;
        return new PendingMag(
                Double.parseDouble(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3]),
                Float.parseFloat(parts[4]),
                Float.parseFloat(parts[5])
        );
    }

    @Nullable
    private static MBaroData parseBaro(String line, MTimeSync sync) {
        PendingBaro p = parsePendingBaro(line);
        if (p == null) return null;
        return MBaroData.fromSensorTime(p.time, sync, p.pressure, p.temp);
    }

    @Nullable
    private static PendingBaro parsePendingBaro(String line) {
        // $BARO,time,pressure,temperature
        String[] parts = line.split(",");
        if (parts.length < 4) return null;
        return new PendingBaro(
                Double.parseDouble(parts[1]),
                Float.parseFloat(parts[2]),
                Float.parseFloat(parts[3])
        );
    }

    // Temporary holders for data before time sync
    private record PendingImu(double time, float gyroX, float gyroY, float gyroZ,
                              float accelX, float accelY, float accelZ, float temp) {}
    private record PendingMag(double time, float magX, float magY, float magZ, float temp) {}
    private record PendingBaro(double time, float pressure, float temp) {}
}
