package com.platypii.baselinexr.location;

import android.content.Context;
import android.location.GnssStatus;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.measurements.MLocation;
import com.platypii.baselinexr.util.Exceptions;
import com.platypii.baselinexr.util.Numbers;

// TODO: Switch to GnssStatus when minsdk >= 24
class LocationProviderAndroid extends LocationProvider implements LocationListener, GpsStatus.Listener {
    private static final String TAG = "LocationProviderAndroid";

    // Android Location manager
    @Nullable
    private LocationManager manager;

    // Satellite data comes from GnssStatus
    private int satellitesInView = -1;
    private int satellitesUsed = -1;

    @NonNull
    @Override
    protected String providerName() {
        return TAG;
    }

    @NonNull
    @Override
    protected String dataSource() {
        return Build.MANUFACTURER + " " + Build.MODEL;
    }

    /**
     * Start location updates
     *
     * @param context The Application context
     */
    @Override
    public void start(@NonNull Context context) throws SecurityException {
        Log.i(TAG, "Starting android location service");
        manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager != null) {
            try {
                if (manager.getProvider(LocationManager.GPS_PROVIDER) != null) {
                    // TODO: Specify looper thread?
                    manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
                    requestStatusUpdates(manager);
                } else {
                    Log.e(TAG, "Failed to get android location provider");
                }
            } catch (SecurityException e) {
                Log.e(TAG, "Failed to start android location service, permission denied");
            } catch (Exception e) {
                Exceptions.report(e);
            }
        } else {
            Log.e(TAG, "Failed to get android location manager");
        }
    }

    /**
     * Android location listener
     */
    @Override
    public void onLocationChanged(@NonNull Location loc) {
        // TODO: minsdk26: loc.getVerticalAccuracyMeters();
        // TODO: minsdk26: loc.getSpeedAccuracyMetersPerSecond()
        if (Numbers.isReal(loc.getLatitude()) && Numbers.isReal(loc.getLongitude())) {
            final float hAcc;
            if (loc.hasAccuracy())
                hAcc = loc.getAccuracy();
            else
                hAcc = Float.NaN;

            final long lastFixMillis = loc.getTime();
            final double latitude = loc.getLatitude();
            final double longitude = loc.getLongitude();
            final double altitude_gps;
            if (loc.hasAltitude()) {
                altitude_gps = loc.getAltitude();
            } else {
                altitude_gps = Double.NaN;
            }
            final double climb = Double.NaN;
            final double groundSpeed;
            if (loc.hasSpeed()) {
                groundSpeed = loc.getSpeed();
            } else {
                groundSpeed = Double.NaN;
            }
            final double bearing;
            if (loc.hasBearing()) {
                bearing = loc.getBearing();
            } else {
                bearing = Double.NaN;
            }

            final double vN;
            final double vE;
            if (Numbers.isReal(groundSpeed) && Numbers.isReal(bearing)) {
                vN = groundSpeed * Math.cos(Math.toRadians(bearing));
                vE = groundSpeed * Math.sin(Math.toRadians(bearing));
            } else {
                vN = vE = Double.NaN;
            }

            final float pdop, hdop, vdop;
            pdop = hdop = vdop = Float.NaN;

            // Update official location
            updateLocation(new MLocation(
                    lastFixMillis, latitude, longitude, altitude_gps, climb, vN, vE,
                    hAcc, pdop, hdop, vdop, satellitesUsed, satellitesInView));
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    private GnssStatus.Callback onGnssStatusChanged;

    private void requestStatusUpdates(@NonNull LocationManager manager) throws SecurityException {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) { // minsdk24
            // Use GnssStatus on newer android
            onGnssStatusChanged = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    try {
                        final int count = status.getSatelliteCount();
                        int used = 0;
                        for (int i = 0; i < count; i++) {
                            if (status.usedInFix(i)) {
                                used++;
                            }
                        }
                        satellitesInView = count;
                        satellitesUsed = used;
                    } catch (SecurityException e) {
                        Exceptions.report(e);
                    }
                }
            };
            manager.registerGnssStatusCallback(onGnssStatusChanged);
        } else {
            manager.addGpsStatusListener(this);
        }
    }

    private GpsStatus gpsStatus;

    @Override
    public void onGpsStatusChanged(int event) {
        if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS && manager != null) {
            try {
                gpsStatus = manager.getGpsStatus(gpsStatus);
                final Iterable<GpsSatellite> satellites = gpsStatus.getSatellites();
                int count = 0;
                int used = 0;
                for (GpsSatellite sat : satellites) {
                    count++;
                    if (sat.usedInFix()) {
                        used++;
                    }
                }
                satellitesInView = count;
                satellitesUsed = used;
            } catch (SecurityException e) {
                Exceptions.report(e);
            }
        }
    }

    @Override
    public void stop() {
        Log.i(TAG, "Stopping android location service");
        super.stop();
        if (manager != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                manager.unregisterGnssStatusCallback(onGnssStatusChanged);
                onGnssStatusChanged = null;
            } else {
                manager.removeGpsStatusListener(this);
            }
            try {
                manager.removeUpdates(this);
            } catch (SecurityException e) {
                Log.w(TAG, "Exception while stopping android location updates", e);
            }
            manager = null;
        }
    }
}
