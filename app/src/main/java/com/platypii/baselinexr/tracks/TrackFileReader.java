package com.platypii.baselinexr.tracks;

import static com.platypii.baselinexr.util.CSVParse.getColumnDate;
import static com.platypii.baselinexr.util.CSVParse.getColumnDouble;
import static com.platypii.baselinexr.util.CSVParse.getColumnLong;

import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.CSVHeader;
import com.platypii.baselinexr.util.filters.Filter;
import com.platypii.baselinexr.util.filters.FilterKalman;

import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Parse location data from track file
 */
public class TrackFileReader {
    private static final String TAG = "TrackFileReader";

    @NonNull
    private final File trackFile;

    TrackFileReader(@NonNull File trackFile) {
        this.trackFile = trackFile;
    }

    /**
     * Load track data from file into location data
     */
    @NonNull
    List<MLocation> read() {
        // Read file line by line
        if (trackFile.getName().endsWith(".gz")) {
            // GZipped track file
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new GZIPInputStream(new FileInputStream(trackFile)), StandardCharsets.UTF_8))) {
                return parse(br);
            } catch (EOFException e) {
                // Still error but less verbose
                Log.e(TAG, "Premature end of gzip track file " + trackFile + "\n" + e);
            } catch (IOException e) {
                Log.e(TAG, "Error reading track data from " + trackFile, e);
            }
        } else {
            // Uncompressed CSV file
            try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(trackFile), StandardCharsets.UTF_8))) {
                return parse(br);
            } catch (IOException e) {
                Log.e(TAG, "Error reading track data from " + trackFile, e);
            }
        }
        return new ArrayList<>();
    }

    @NonNull
    public static List<MLocation> parse(@NonNull BufferedReader br) throws IOException {
        // Reset initial state
        // State used while scanning track file
        final Filter gpsAltitudeFilter = new FilterKalman();
        long gpsLastMillis = -1L;

        final List<MLocation> data = new ArrayList<>();

        // Parse header column
        final CSVHeader columns = new CSVHeader(br);
        // Add column aliases
        columns.addMapping("timeMillis", "millis");
        // Handle old files that were not FlySight compatible
        columns.addMapping("latitude", "lat");
        columns.addMapping("longitude", "lon");
        columns.addMapping("altitude_gps", "hMSL");

        // Parse data rows
        String line;
        while ((line = br.readLine()) != null) {
            final String[] row = line.split(",");
            final Integer sensorIndex = columns.get("sensor");
            if (sensorIndex == null || sensorIndex >= row.length) {
                // FlySight
                final long millis = getColumnDate(row, columns, "time");
                if (millis > 0) {
                    final double lat = getColumnDouble(row, columns, "lat");
                    final double lon = getColumnDouble(row, columns, "lon");
                    final double alt_gps = getColumnDouble(row, columns, "hMSL");
                    final double climb = -getColumnDouble(row, columns, "velD");
                    final double vN = getColumnDouble(row, columns, "velN");
                    final double vE = getColumnDouble(row, columns, "velE");
                    if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                        final MLocation loc = new MLocation(millis, lat, lon, alt_gps, climb, vN, vE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0);
                        data.add(loc);
                    }
                }
            } else if (row[sensorIndex].equals("gps")) {
                // BASEline GPS measurement
                final long millis = getColumnLong(row, columns, "millis");
                final double lat = getColumnDouble(row, columns, "lat");
                final double lon = getColumnDouble(row, columns, "lon");
                final double alt_gps = getColumnDouble(row, columns, "hMSL");
                final double vN = getColumnDouble(row, columns, "velN");
                final double vE = getColumnDouble(row, columns, "velE");
                // Update gps altitude filter
                if (gpsLastMillis < 0) {
                    gpsAltitudeFilter.update(alt_gps, 0);
                } else {
                    final double dt = (millis - gpsLastMillis) * 0.001;
                    gpsAltitudeFilter.update(alt_gps, dt);
                }
                // Climb rate from baro or gps
                double climb = gpsAltitudeFilter.v();
                if (!Double.isNaN(lat) && !Double.isNaN(lon)) {
                    final MLocation loc = new MLocation(millis, lat, lon, alt_gps, climb, vN, vE, Float.NaN, Float.NaN, Float.NaN, Float.NaN, 0, 0);
                    data.add(loc);
                }
                gpsLastMillis = millis;
            }
        }

        return data;
    }

}
