package com.platypii.baselinexr;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;

import androidx.annotation.NonNull;

import com.platypii.baselinexr.bluetooth.BluetoothService;
import com.platypii.baselinexr.cloud.AuthState;
import com.platypii.baselinexr.cloud.tasks.Tasks;
import com.platypii.baselinexr.jarvis.FlightComputer;
import com.platypii.baselinexr.location.LocationService;
import com.platypii.baselinexr.places.Places;
import com.platypii.baselinexr.tracks.TrackLogger;
import com.platypii.baselinexr.util.Convert;

/**
 * Start and stop essential services.
 * This class provides essential services intended to persist between activities.
 * This class will also keep services running if logging or audible is enabled.
 */
public class Services {
    private static final String TAG = "Services";

    // Count the number of times an activity has started.
    // This allows us to only stop services once the app is really done.
    private static int startCount = 0;
    private static boolean initialized = false;

    /**
     * A handler to shut down services after activity has stopped
     */
    private static final Handler handler = new Handler();
    private static final int shutdownDelay = 10000;
    private static final Runnable stopRunnable = Services::stopIfIdle;

    // Services
    public static final BluetoothService bluetooth = new BluetoothService();
    public static final LocationService location = new LocationService(bluetooth);
    public static final FlightComputer flightComputer = new FlightComputer();
    public static final Tasks tasks = new Tasks();
    public static final Places places = new Places();
    public static final TrackLogger trackLogger = new TrackLogger();

    /**
     * Timestamp when location service start was requested.
     * Used by Video360Controller to calculate how much of the GPS startup delay has elapsed.
     */
    public static long locationStartRequestedMs = 0;

    /**
     * We want preferences to be available as early as possible.
     * Call this in onCreate
     */
    public static void create(@NonNull Activity activity) {
        if (!created) {
            Log.i(TAG, "Loading app preferences");
            loadPreferences(activity);
            created = true;
        }
    }

    private static boolean created = false;

    public static void start(@NonNull Activity activity) {
        final boolean shouldStart = inc();
        if (shouldStart && initialized) {
            // This happens when services are started again before the shutdown delay
            Log.i(TAG, "Services still alive");
            // Even without this line, stopRunnable would notice that startCount > 0.
            // But why waste the cycles? Might as well remove the stop runnable.
            handler.removeCallbacks(stopRunnable);
        } else if (shouldStart) {
            initialized = true;
            final long startTime = System.currentTimeMillis();
            Log.i(TAG, "Starting services");
            final Context appContext = activity.getApplicationContext();

            // Start the various services

            Log.i(TAG, "Starting bluetooth service");
            if (bluetooth.preferences.preferenceEnabled) {
                bluetooth.start(activity);
            }

            Log.i(TAG, "Starting location service");
            locationStartRequestedMs = System.currentTimeMillis();
            location.start(appContext);

            Log.i(TAG, "Starting flight services");
            flightComputer.start();

            Log.i(TAG, "Starting task manager");
            tasks.start(appContext);

            Log.i(TAG, "Starting place database");
            places.start(appContext);

            Log.i(TAG, "Starting track logger");
            trackLogger.start(appContext);

            Log.i(TAG, "Services started in " + (System.currentTimeMillis() - startTime) + " ms");
        } else if (initialized) {
            // Every time an activity starts...
            tasks.tendQueue();
        }
    }

    /**
     * Increment startCount, and return true if 0 -> 1, meaning start services
     */
    private static synchronized boolean inc() {
        return startCount++ == 0;
    }

    /**
     * Decrement startCount, and return true if 1 -> 0, meaning stop services
     */
    private static synchronized boolean dec() {
        return --startCount == 0;
    }

    public static void stop() {
        if (dec()) {
            Log.i(TAG, String.format("All activities have stopped. Base services will stop in %d seconds", shutdownDelay / 1000));
            handler.postDelayed(stopRunnable, shutdownDelay);
        } else {
            Log.w(TAG, "Not stopping, start count " + startCount);
        }
    }

    /**
     * Stop services IF nothing is using them
     */
    private static synchronized void stopIfIdle() {
        if (initialized && startCount == 0) {
            Log.i(TAG, "All activities have stopped. Stopping services.");
            // Stop services
            trackLogger.stop();
            places.stop();
            tasks.stop();
            flightComputer.stop();
            location.stop();
            bluetooth.stop();
            initialized = false;
        }
    }

    private static void loadPreferences(@NonNull Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        // Metric
        Convert.metric = prefs.getBoolean("metric_enabled", Convert.metric);

        // Sign in state
        AuthState.loadFromPreferences(prefs);

        // Bluetooth
        bluetooth.preferences.load(prefs);
    }

}
