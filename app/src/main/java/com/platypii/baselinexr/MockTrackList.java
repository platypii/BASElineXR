package com.platypii.baselinexr;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.util.Arrays;

public class MockTrackList {
    private static final String TAG = "MockTrackList";

    /**
     * Data availability flags for a track/folder
     */
    public static class DataAvailability {
        public final boolean hasGps;
        public final boolean hasImu;
        public final boolean hasMag;
        public final boolean hasBaro;

        public DataAvailability(boolean hasGps, boolean hasImu, boolean hasMag, boolean hasBaro) {
            this.hasGps = hasGps;
            this.hasImu = hasImu;
            this.hasMag = hasMag;
            this.hasBaro = hasBaro;
        }

        /**
         * Get icon string showing available data types
         */
        @NonNull
        public String getIcons() {
            StringBuilder sb = new StringBuilder();
            if (hasGps) sb.append("📍");
            if (hasImu) sb.append("🧭");
            if (hasMag) sb.append("🧲");
            // Skip baro icon to keep it short
            return sb.toString();
        }

        @NonNull
        @Override
        public String toString() {
            return "DataAvailability[gps=" + hasGps + ", imu=" + hasImu +
                    ", mag=" + hasMag + ", baro=" + hasBaro + "]";
        }
    }

    public static class TrackInfo {
        public final String path;           // Filename for .csv, folder name for folders
        public final String displayName;
        public final boolean isFolder;      // True if this is a folder with TRACK.CSV and SENSOR.CSV

        // Cached data availability (populated on first check)
        @Nullable
        private DataAvailability dataAvailability;

        public TrackInfo(String path, String displayName) {
            this.path = path;
            this.displayName = displayName;
            this.isFolder = !path.endsWith(".csv");
        }

        public TrackInfo(String path, String displayName, boolean isFolder) {
            this.path = path;
            this.displayName = displayName;
            this.isFolder = isFolder;
        }

        /**
         * Get the path to TRACK.CSV (for GPS data)
         */
        @NonNull
        public String getTrackPath() {
            if (isFolder) {
                return path + "/TRACK.CSV";
            }
            return path;
        }

        /**
         * Get the path to SENSOR.CSV (for sensor data), or null if not a folder
         */
        @Nullable
        public String getSensorPath() {
            if (isFolder) {
                return path + "/SENSOR.CSV";
            }
            return null;
        }

        /**
         * Check data availability for this track (cached after first call)
         */
        @NonNull
        public DataAvailability getDataAvailability(@NonNull Context context) {
            if (dataAvailability != null) {
                return dataAvailability;
            }
            dataAvailability = detectDataAvailability(context);
            return dataAvailability;
        }

        private DataAvailability detectDataAvailability(@NonNull Context context) {
            boolean hasGps = false;
            boolean hasImu = false;
            boolean hasMag = false;
            boolean hasBaro = false;

            try {
                // Check for GPS data
                String trackPath = getTrackPath();
                String[] assets = context.getAssets().list("");
                if (isFolder) {
                    // Check folder contents
                    String[] folderContents = context.getAssets().list(path);
                    if (folderContents != null) {
                        hasGps = Arrays.asList(folderContents).contains("TRACK.CSV");
                        boolean hasSensor = Arrays.asList(folderContents).contains("SENSOR.CSV");
                        if (hasSensor) {
                            // For now, assume sensor file has all sensor types
                            // Could parse header to check, but that's expensive
                            hasImu = true;
                            hasMag = true;
                            hasBaro = true;
                        }
                    }
                } else {
                    // Single CSV file = GPS only
                    hasGps = true;
                }
            } catch (IOException e) {
                Log.w(TAG, "Error checking data availability for " + path, e);
            }

            return new DataAvailability(hasGps, hasImu, hasMag, hasBaro);
        }
    }

    // All available mock tracks (files and folders)
    public static final TrackInfo[] ALL_TRACKS = {
            new TrackInfo("eiger.csv", "Eiger"),
            new TrackInfo("kpow-prison.csv", "Prison"),
            new TrackInfo("seb_ff.csv", "Seb FF"),
            new TrackInfo("kpow-impact.csv", "Impact"),
            new TrackInfo("portal-run.csv", "Portal"),
            new TrackInfo("squaw7-29-25", "Squaw"),  // Folder with TRACK.CSV and SENSOR.CSV
    };

    /**
     * Get display name for a track path (with optional data availability icons)
     * @param path the track path (file or folder), or null for Live GPS
     * @param context optional context for data availability detection
     */
    public static String getDisplayName(@Nullable String path, @Nullable Context context) {
        if (path == null) {
            return "Live GPS 📡";
        }
        for (TrackInfo track : ALL_TRACKS) {
            if (track.path.equals(path)) {
                if (context != null) {
                    DataAvailability avail = track.getDataAvailability(context);
                    return track.displayName + " " + avail.getIcons();
                }
                return track.displayName;
            }
        }
        return path; // fallback to path if not found
    }

    /**
     * Get display name for a track path (without icons)
     * @param path the track path, or null for Live GPS
     */
    public static String getDisplayName(@Nullable String path) {
        return getDisplayName(path, null);
    }

    /**
     * Get TrackInfo by path
     */
    @Nullable
    public static TrackInfo getTrackInfo(@Nullable String path) {
        if (path == null) return null;
        for (TrackInfo track : ALL_TRACKS) {
            if (track.path.equals(path)) {
                return track;
            }
        }
        return null;
    }

    /**
     * Cycle to next track: Live GPS -> eiger -> prison -> ... -> squaw -> Live GPS...
     * @param currentPath current track path, or null for Live GPS
     * @return next track path, or null for Live GPS
     */
    @Nullable
    public static String getNextTrack(@Nullable String currentPath) {
        if (currentPath == null) {
            // Currently Live GPS, go to first track
            return ALL_TRACKS[0].path;
        }
        for (int i = 0; i < ALL_TRACKS.length; i++) {
            if (ALL_TRACKS[i].path.equals(currentPath)) {
                if (i == ALL_TRACKS.length - 1) {
                    // Last track, cycle back to Live GPS
                    return null;
                }
                return ALL_TRACKS[i + 1].path;
            }
        }
        // Not found, default to Live GPS
        return null;
    }
}
