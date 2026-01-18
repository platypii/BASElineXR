package com.platypii.baselinexr.location;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.platypii.baselinexr.bluetooth.BluetoothService;
import com.platypii.baselinexr.measurements.MBaroData;
import com.platypii.baselinexr.measurements.MHumidityData;
import com.platypii.baselinexr.measurements.MImuData;
import com.platypii.baselinexr.measurements.MMagData;
import com.platypii.baselinexr.measurements.MSensorData;
import com.platypii.baselinexr.measurements.MTimeSync;
import com.platypii.baselinexr.util.PubSub;

/**
 * Provides live sensor data from FlySight 2 via Bluetooth LE.
 * Subscribes to BLE sensor PubSub channels and maintains the latest readings.
 * 
 * This is the live counterpart to MockSensorProvider.
 * 
 * Usage:
 *   LiveSensorProvider provider = new LiveSensorProvider();
 *   provider.start(bluetoothService);
 *   
 *   // Query latest sensor data
 *   MImuData imu = provider.getLatestImu();
 *   MMagData mag = provider.getLatestMag();
 *   
 *   provider.stop();
 */
public class LiveSensorProvider implements SensorProvider {
    private static final String TAG = "LiveSensorProvider";
    
    // Latest sensor readings (thread-safe via volatile)
    @Nullable private volatile MImuData latestImu;
    @Nullable private volatile MMagData latestMag;
    @Nullable private volatile MBaroData latestBaro;
    @Nullable private volatile MHumidityData latestHumidity;
    @Nullable private volatile MTimeSync latestTimeSync;
    
    // BLE subscribers (kept for unsubscribe)
    @Nullable private PubSub.Subscriber<MImuData> imuSubscriber;
    @Nullable private PubSub.Subscriber<MMagData> magSubscriber;
    @Nullable private PubSub.Subscriber<MBaroData> baroSubscriber;
    @Nullable private PubSub.Subscriber<MHumidityData> humiditySubscriber;
    @Nullable private PubSub.Subscriber<MTimeSync> timeSyncSubscriber;
    
    // Reference to BluetoothService for unsubscribe
    @Nullable private BluetoothService bluetoothService;
    
    // State
    private boolean started = false;
    
    // Default max age for sensor lookups (ms)
    private static final long DEFAULT_MAX_AGE_MS = 1000; // 1 second for live data
    
    /**
     * Start listening for live BLE sensor data
     * 
     * @param bluetoothService BluetoothService with active FlySight connection
     */
    public void start(@NonNull BluetoothService bluetoothService) {
        if (started) {
            Log.w(TAG, "Already started");
            return;
        }
        
        Log.i(TAG, "Starting live sensor provider");
        this.bluetoothService = bluetoothService;
        
        // Create subscribers for each sensor channel
        imuSubscriber = imu -> latestImu = imu;
        magSubscriber = mag -> latestMag = mag;
        baroSubscriber = baro -> latestBaro = baro;
        humiditySubscriber = hum -> latestHumidity = hum;
        timeSyncSubscriber = timeSync -> latestTimeSync = timeSync;
        
        // Subscribe to each sensor PubSub channel
        bluetoothService.imuUpdates.subscribe(imuSubscriber);
        bluetoothService.magUpdates.subscribe(magSubscriber);
        bluetoothService.baroUpdates.subscribe(baroSubscriber);
        bluetoothService.humidityUpdates.subscribe(humiditySubscriber);
        bluetoothService.timeSyncUpdates.subscribe(timeSyncSubscriber);
        
        started = true;
        Log.i(TAG, "Live sensor provider started");
    }
    
    /**
     * Stop listening for BLE sensor data
     */
    public void stop() {
        Log.i(TAG, "Stopping live sensor provider");
        
        // Unsubscribe from all channels
        if (bluetoothService != null) {
            if (imuSubscriber != null) {
                bluetoothService.imuUpdates.unsubscribe(imuSubscriber);
                imuSubscriber = null;
            }
            if (magSubscriber != null) {
                bluetoothService.magUpdates.unsubscribe(magSubscriber);
                magSubscriber = null;
            }
            if (baroSubscriber != null) {
                bluetoothService.baroUpdates.unsubscribe(baroSubscriber);
                baroSubscriber = null;
            }
            if (humiditySubscriber != null) {
                bluetoothService.humidityUpdates.unsubscribe(humiditySubscriber);
                humiditySubscriber = null;
            }
            if (timeSyncSubscriber != null) {
                bluetoothService.timeSyncUpdates.unsubscribe(timeSyncSubscriber);
                timeSyncSubscriber = null;
            }
            bluetoothService = null;
        }
        
        // Clear cached data
        latestImu = null;
        latestMag = null;
        latestBaro = null;
        latestHumidity = null;
        latestTimeSync = null;
        
        started = false;
        Log.i(TAG, "Live sensor provider stopped");
    }
    
    // ========== Latest sensor accessors (for live data) ==========
    
    /**
     * Get the most recent IMU reading
     * 
     * @return Latest IMU data, or null if not yet received
     */
    @Nullable
    public MImuData getLatestImu() {
        return latestImu;
    }
    
    /**
     * Get the most recent magnetometer reading
     * 
     * @return Latest magnetometer data, or null if not yet received
     */
    @Nullable
    public MMagData getLatestMag() {
        return latestMag;
    }
    
    /**
     * Get the most recent barometer reading
     * 
     * @return Latest barometer data, or null if not yet received
     */
    @Nullable
    public MBaroData getLatestBaro() {
        return latestBaro;
    }
    
    /**
     * Get the most recent humidity reading
     * 
     * @return Latest humidity data, or null if not yet received
     */
    @Nullable
    public MHumidityData getLatestHumidity() {
        return latestHumidity;
    }
    
    /**
     * Get the most recent time sync packet
     * 
     * @return Latest time sync, or null if not yet received
     */
    @Nullable
    public MTimeSync getLatestTimeSync() {
        return latestTimeSync;
    }
    
    // ========== Time-based accessors (parallel to MockSensorProvider API) ==========
    
    /**
     * Get IMU data if recent enough
     * 
     * @param currentGpsMillis Current GPS time (used for staleness check)
     * @return IMU data if within max age, null otherwise
     */
    @Override
    @Nullable
    public MImuData getImuAtTime(long currentGpsMillis) {
        MImuData imu = latestImu;
        if (imu != null && (currentGpsMillis - imu.millis) <= DEFAULT_MAX_AGE_MS) {
            return imu;
        }
        return null;
    }
    
    /**
     * Get magnetometer data if recent enough
     * 
     * @param currentGpsMillis Current GPS time (used for staleness check)
     * @return Magnetometer data if within max age, null otherwise
     */
    @Override
    @Nullable
    public MMagData getMagAtTime(long currentGpsMillis) {
        MMagData mag = latestMag;
        if (mag != null && (currentGpsMillis - mag.millis) <= DEFAULT_MAX_AGE_MS) {
            return mag;
        }
        return null;
    }
    
    /**
     * Get barometer data if recent enough
     * 
     * @param currentGpsMillis Current GPS time (used for staleness check)
     * @return Barometer data if within max age, null otherwise
     */
    @Override
    @Nullable
    public MBaroData getBaroAtTime(long currentGpsMillis) {
        MBaroData baro = latestBaro;
        if (baro != null && (currentGpsMillis - baro.millis) <= DEFAULT_MAX_AGE_MS) {
            return baro;
        }
        return null;
    }
    
    /**
     * Get humidity data if recent enough
     * 
     * @param currentGpsMillis Current GPS time (used for staleness check)
     * @return Humidity data if within max age, null otherwise
     */
    @Override
    @Nullable
    public MHumidityData getHumidityAtTime(long currentGpsMillis) {
        MHumidityData hum = latestHumidity;
        if (hum != null && (currentGpsMillis - hum.millis) <= DEFAULT_MAX_AGE_MS) {
            return hum;
        }
        return null;
    }
    
    // ========== Legacy combined accessor (for backwards compatibility) ==========
    
    /**
     * Get combined sensor data - builds MSensorData from latest readings
     * This is the legacy API for backwards compatibility with existing code.
     * 
     * @param gpsMillis Current GPS time (used for staleness check)
     * @return Combined sensor data, or null if no data available
     * @deprecated Use type-specific methods like {@link #getImuAtTime(long)}
     */
    @Deprecated
    @Override
    @Nullable
    public MSensorData getSensorAtTime(long gpsMillis) {
        // Get latest readings if recent enough
        MImuData imu = getImuAtTime(gpsMillis);
        MMagData mag = getMagAtTime(gpsMillis);
        MBaroData baro = getBaroAtTime(gpsMillis);
        MHumidityData hum = getHumidityAtTime(gpsMillis);
        
        // Need at least one sensor reading
        if (imu == null && mag == null && baro == null && hum == null) {
            return null;
        }
        
        // Build combined MSensorData from available readings using constructor
        return new MSensorData(
                gpsMillis,                                  // millis
                0L,                                         // nano
                // Magnetometer
                mag != null ? mag.magX : Float.NaN,
                mag != null ? mag.magY : Float.NaN,
                mag != null ? mag.magZ : Float.NaN,
                mag != null ? mag.temperature : Float.NaN,  // magTemp
                // Gyroscope
                imu != null ? imu.gyroX : Float.NaN,
                imu != null ? imu.gyroY : Float.NaN,
                imu != null ? imu.gyroZ : Float.NaN,
                // Accelerometer
                imu != null ? imu.accelX : Float.NaN,
                imu != null ? imu.accelY : Float.NaN,
                imu != null ? imu.accelZ : Float.NaN,
                imu != null ? imu.temperature : Float.NaN,  // imuTemp
                // Barometer
                baro != null ? baro.pressure : Float.NaN,
                baro != null ? baro.temperature : Float.NaN, // baroTemp
                // Humidity
                hum != null ? hum.humidity : Float.NaN,
                hum != null ? hum.temperature : Float.NaN,  // humidityTemp
                // Battery (not available from BLE yet)
                Float.NaN                                    // vbat
        );
    }
    
    // ========== Status methods ==========
    
    @Override
    public boolean hasSensorData() {
        return latestImu != null || latestMag != null || 
               latestBaro != null || latestHumidity != null;
    }
    
    @Override
    public boolean hasImuData() {
        return latestImu != null;
    }
    
    @Override
    public boolean hasMagData() {
        return latestMag != null;
    }
    
    @Override
    public boolean hasBaroData() {
        return latestBaro != null;
    }
    
    @Override
    public boolean hasHumidityData() {
        return latestHumidity != null;
    }
    
    /**
     * Check if the time synchronization has been established
     * 
     * @return true if at least one TIME sync packet has been received
     */
    public boolean isTimeSyncEstablished() {
        return latestTimeSync != null;
    }
    
    /**
     * Check if the provider is currently receiving data
     * 
     * @return true if started and at least one sensor reading received
     */
    public boolean isReceivingData() {
        return started && (latestImu != null || latestMag != null || 
                          latestBaro != null || latestHumidity != null);
    }
    
    /**
     * Get a summary of the current sensor state for debugging
     * 
     * @return String describing current sensor data availability
     */
    @NonNull
    @Override
    public String getStatusSummary() {
        return String.format("LiveSensor[started=%b, imu=%s, mag=%s, baro=%s, hum=%s, timeSync=%s]",
                started,
                latestImu != null ? "OK" : "null",
                latestMag != null ? "OK" : "null",
                latestBaro != null ? "OK" : "null",
                latestHumidity != null ? "OK" : "null",
                latestTimeSync != null ? "OK" : "null");
    }
}
