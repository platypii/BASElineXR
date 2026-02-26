package com.platypii.baselinexr.bluetooth;

import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.welie.blessed.BluetoothPeripheral;
import com.welie.blessed.WriteType;
import java.util.UUID;

/**
 * Manages FlySight 2 SD_Control_Point and DS_Control_Point commands.
 * Extracted from connection flow to allow on-demand control point operations.
 * 
 * SD_Control_Point (Sensor Data) commands:
 * - 0x01: SET_GNSS_BLE_MASK
 * - 0x02: GET_GNSS_BLE_MASK  
 * - 0x10: SET_BLE_DIVIDER
 * - 0x11: GET_BLE_DIVIDER
 * - 0x20: SET_FUSION_MAG_HARD
 * - 0x21: SET_FUSION_MAG_SOFT
 * 
 * DS_Control_Point (Device State) commands:
 * - 0x01: GET_FW_VERSION
 * - 0x02: REBOOT_DEVICE
 * - 0x03: GET_DEVICE_ID
 * - 0x04: SET_MODE (SLEEP=0, ACTIVE=1)
 */
public class Flysight2ControlPoint {
    private static final String TAG = "FlysightProtocol";  // Use same tag for easier filtering

    // Sensor Data service and control point
    private static final UUID sensorDataService = UUID.fromString("00000001-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID sdControlPoint = UUID.fromString("00000006-8e22-4541-9d4c-21edae82ed19");
    
    // Device State service and control point
    private static final UUID deviceStateService = UUID.fromString("00000003-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID dsControlPoint = UUID.fromString("00000007-8e22-4541-9d4c-21edae82ed19");

    // SD_Control_Point opcodes
    public static final byte SD_CMD_SET_GNSS_BLE_MASK = 0x01;
    public static final byte SD_CMD_GET_GNSS_BLE_MASK = 0x02;
    public static final byte SD_CMD_SET_BLE_DIVIDER = 0x10;
    public static final byte SD_CMD_GET_BLE_DIVIDER = 0x11;
    public static final byte SD_CMD_SET_FUSION_MAG_HARD = 0x20;
    public static final byte SD_CMD_SET_FUSION_MAG_SOFT = 0x21;

    // DS_Control_Point opcodes
    public static final byte DS_CMD_GET_FW_VERSION = 0x01;
    public static final byte DS_CMD_REBOOT_DEVICE = 0x02;
    public static final byte DS_CMD_GET_DEVICE_ID = 0x03;
    public static final byte DS_CMD_SET_MODE = 0x04;

    // Sensor IDs for SET_BLE_DIVIDER
    public static final int SENSOR_BARO = 0;
    public static final int SENSOR_HUM = 1;
    public static final int SENSOR_ACCEL = 2;
    public static final int SENSOR_GYRO = 3;
    public static final int SENSOR_MAG = 4;
    public static final int SENSOR_COUNT = 5;

    // Sensor names for display
    public static final String[] SENSOR_NAMES = {"Baro", "Hum", "Accel", "Gyro", "Mag"};

    // ODR values in Hz for each sensor (index = ODR setting from config.txt)
    // Baro (LPS22HH): ODR 0-7
    public static final double[] BARO_ODR_HZ = {0, 1, 10, 25, 50, 75, 100, 200};
    // Humidity (HTS221): ODR 0-3
    public static final double[] HUM_ODR_HZ = {0, 1, 7, 12.5};
    // Accel (LSM6DSO): ODR 0-11
    public static final double[] ACCEL_ODR_HZ = {0, 12.5, 26, 52, 104, 208, 416, 833, 1666, 3333, 6666, 1.6};
    // Gyro (LSM6DSO): ODR 0-10
    public static final double[] GYRO_ODR_HZ = {0, 12.5, 26, 52, 104, 208, 416, 833, 1666, 3333, 6666};
    // Mag (LIS2MDL): ODR 0-3
    public static final double[] MAG_ODR_HZ = {10, 20, 50, 100};

    // Default ODR index for each sensor (from FlySight firmware defaults)
    // {Baro=2, Hum=1, Accel=1, Gyro=1, Mag=0}
    public static final int[] DEFAULT_ODR_INDEX = {2, 1, 1, 1, 0};

    // Common divider values for dropdown selection
    public static final int[] DIVIDER_VALUES = {0, 1, 2, 4, 8, 16, 32, 64};
    public static final String[] DIVIDER_LABELS = {"Auto", "1", "2", "4", "8", "16", "32", "64"};

    // Current divider values (updated from device responses)
    private final int[] currentDividers = new int[SENSOR_COUNT];

    // Response status codes
    public static final int CP_STATUS_SUCCESS = 0x01;
    public static final int CP_STATUS_NOT_SUPPORTED = 0x02;
    public static final int CP_STATUS_INVALID_PARAM = 0x03;
    public static final int CP_STATUS_FAILED = 0x04;
    public static final int CP_STATUS_NOT_PERMITTED = 0x05;
    public static final int CP_STATUS_BUSY = 0x06;

    // Current peripheral reference
    @Nullable
    private BluetoothPeripheral peripheral;

    // Listener for control point responses
    @Nullable
    private ControlPointListener listener;

    /**
     * Callback interface for control point responses
     */
    public interface ControlPointListener {
        void onControlPointResponse(int opcode, int status, @Nullable byte[] data);
    }

    public void setPeripheral(@Nullable BluetoothPeripheral peripheral) {
        this.peripheral = peripheral;
    }

    public void setListener(@Nullable ControlPointListener listener) {
        this.listener = listener;
    }

    /**
     * Get the default ODR in Hz for a specific sensor
     */
    public static double getDefaultOdrHz(int sensorId) {
        if (sensorId < 0 || sensorId >= SENSOR_COUNT) return 0;
        int odrIndex = DEFAULT_ODR_INDEX[sensorId];
        double[] odrArray = getOdrValues(sensorId);
        if (odrIndex < odrArray.length) {
            return odrArray[odrIndex];
        }
        return 0;
    }

    /**
     * Set BLE transmission divider for a sensor
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     * @param divider Decimation factor (1-65535). 1=every sample, 20=every 20th sample
     * @return true if write was queued successfully
     */
    public boolean setBleDivider(int sensorId, int divider) {
        if (peripheral == null) {
            Log.w(TAG, "setBleDivider: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
            SD_CMD_SET_BLE_DIVIDER,
            (byte) sensorId,
            (byte) (divider & 0xFF),        // divider low byte
            (byte) ((divider >> 8) & 0xFF)  // divider high byte
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setBleDivider sensor=" + sensorId + " divider=" + divider + " ok=" + ok);
        return ok;
    }

    /**
     * Get current BLE divider for a sensor
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     */
    public boolean getBleDivider(int sensorId) {
        if (peripheral == null) {
            Log.w(TAG, "getBleDivider: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
            SD_CMD_GET_BLE_DIVIDER,
            (byte) sensorId
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getBleDivider sensor=" + sensorId + " ok=" + ok);
        return ok;
    }

    /**
     * Set GNSS BLE mask (which fields to include in GNSS packets)
     * @param mask Bitmask: 0x80=TOW, 0x40=Week, 0x20=Position, 0x10=Velocity, 0x08=Accuracy, 0x04=NumSV
     */
    public boolean setGnssMask(int mask) {
        if (peripheral == null) {
            Log.w(TAG, "setGnssMask: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
            SD_CMD_SET_GNSS_BLE_MASK,
            (byte) mask
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setGnssMask mask=0x" + Integer.toHexString(mask) + " ok=" + ok);
        return ok;
    }

    /**
     * Get current GNSS BLE mask
     */
    public boolean getGnssMask() {
        if (peripheral == null) {
            Log.w(TAG, "getGnssMask: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
            SD_CMD_GET_GNSS_BLE_MASK
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getGnssMask ok=" + ok);
        return ok;
    }

    /**
     * Configure all sensor dividers at once (convenience method)
     * @param divider Divider value for all sensors
     */
    public void configureAllDividers(int divider) {
        Log.i(TAG, "Configuring all sensor dividers to " + divider);
        setBleDivider(SENSOR_BARO, divider);
        setBleDivider(SENSOR_HUM, divider);
        setBleDivider(SENSOR_ACCEL, divider);
        setBleDivider(SENSOR_GYRO, divider);
        setBleDivider(SENSOR_MAG, divider);
    }

    /**
     * Request firmware version via DS_Control_Point
     */
    public boolean getFirmwareVersion() {
        if (peripheral == null) {
            Log.w(TAG, "getFirmwareVersion: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_GET_FW_VERSION };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getFirmwareVersion ok=" + ok);
        return ok;
    }

    /**
     * Request device ID via DS_Control_Point
     */
    public boolean getDeviceId() {
        if (peripheral == null) {
            Log.w(TAG, "getDeviceId: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_GET_DEVICE_ID };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getDeviceId ok=" + ok);
        return ok;
    }

    /**
     * Set device mode (SLEEP or ACTIVE)
     * @param mode 0=SLEEP, 1=ACTIVE
     */
    public boolean setMode(int mode) {
        if (peripheral == null) {
            Log.w(TAG, "setMode: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
            DS_CMD_SET_MODE,
            (byte) mode
        };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setMode mode=" + mode + " ok=" + ok);
        return ok;
    }

    /**
     * Reboot the device
     */
    public boolean rebootDevice() {
        if (peripheral == null) {
            Log.w(TAG, "rebootDevice: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REBOOT_DEVICE };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "rebootDevice ok=" + ok);
        return ok;
    }

    /**
     * Process incoming control point response
     * Format: [0xF0] [Request Opcode] [Status] [Optional Data...]
     */
    public void processResponse(@NonNull byte[] value) {
        // Log raw hex for debugging
        StringBuilder hex = new StringBuilder();
        for (byte b : value) {
            hex.append(String.format("%02X ", b));
        }
        Log.i(TAG, "CP Response raw (" + value.length + " bytes): " + hex);
        
        if (value.length < 3) {
            Log.w(TAG, "Control point response too short: " + value.length);
            return;
        }
        int responseId = value[0] & 0xFF;
        int opcode = value[1] & 0xFF;
        int statusCode = value[2] & 0xFF;
        
        String statusStr = getStatusString(statusCode);
        Log.i(TAG, "CP Response: responseId=0x" + Integer.toHexString(responseId) 
            + " opcode=0x" + Integer.toHexString(opcode) 
            + " status=" + statusStr);

        // Extract optional response data
        byte[] data = null;
        if (value.length > 3) {
            data = new byte[value.length - 3];
            System.arraycopy(value, 3, data, 0, data.length);
            
            // Log specific response data for known opcodes
            if (opcode == SD_CMD_GET_BLE_DIVIDER && data.length >= 3) {
                int sensorId = data[0] & 0xFF;
                int divider = (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);
                Log.i(TAG, "  -> sensor=" + sensorId + " divider=" + divider);
                // Cache the divider value
                if (statusCode == CP_STATUS_SUCCESS) {
                    updateCurrentDivider(sensorId, divider);
                }
            } else if (opcode == SD_CMD_GET_GNSS_BLE_MASK && data.length >= 1) {
                int mask = data[0] & 0xFF;
                Log.i(TAG, "  -> mask=0x" + Integer.toHexString(mask));
            } else if (opcode == DS_CMD_GET_FW_VERSION) {
                String version = new String(data);
                Log.i(TAG, "  -> firmware=" + version);
            } else if (opcode == DS_CMD_GET_DEVICE_ID) {
                StringBuilder sb = new StringBuilder();
                for (byte b : data) {
                    sb.append(String.format("%02X", b));
                }
                Log.i(TAG, "  -> deviceId=" + sb);
            }
        }

        // Notify listener
        if (listener != null) {
            listener.onControlPointResponse(opcode, statusCode, data);
        }
    }

    private static String getStatusString(int statusCode) {
        switch (statusCode) {
            case CP_STATUS_SUCCESS: return "SUCCESS";
            case CP_STATUS_NOT_SUPPORTED: return "NOT_SUPPORTED";
            case CP_STATUS_INVALID_PARAM: return "INVALID_PARAM";
            case CP_STATUS_FAILED: return "FAILED";
            case CP_STATUS_NOT_PERMITTED: return "NOT_PERMITTED";
            case CP_STATUS_BUSY: return "BUSY";
            default: return "UNKNOWN(" + statusCode + ")";
        }
    }

    /**
     * Get ODR values array for a sensor
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     * @return Array of Hz values for each ODR setting
     */
    public static double[] getOdrValues(int sensorId) {
        switch (sensorId) {
            case SENSOR_BARO: return BARO_ODR_HZ;
            case SENSOR_HUM: return HUM_ODR_HZ;
            case SENSOR_ACCEL: return ACCEL_ODR_HZ;
            case SENSOR_GYRO: return GYRO_ODR_HZ;
            case SENSOR_MAG: return MAG_ODR_HZ;
            default: return new double[]{0};
        }
    }

    /**
     * Format ODR labels for dropdown display
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     * @return Array of display strings like "1: 10 Hz"
     */
    public static String[] getOdrLabels(int sensorId) {
        double[] values = getOdrValues(sensorId);
        String[] labels = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            if (values[i] == 0) {
                labels[i] = i + ": Off";
            } else if (values[i] < 1) {
                labels[i] = i + ": " + values[i] + " Hz";
            } else {
                labels[i] = i + ": " + (int) values[i] + " Hz";
            }
        }
        return labels;
    }

    /**
     * Calculate output rate given ODR index and divider
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     * @param odrIndex Index into the ODR array for this sensor
     * @param divider BLE divider (0=auto, 1=full rate, 2=half, etc.)
     * @return Output rate in Hz, or -1 for auto mode
     */
    public static double calculateOutputRate(int sensorId, int odrIndex, int divider) {
        double[] odrValues = getOdrValues(sensorId);
        if (odrIndex < 0 || odrIndex >= odrValues.length) {
            return 0;
        }
        double odrHz = odrValues[odrIndex];
        if (divider == 0) {
            return -1; // Auto mode
        }
        return odrHz / divider;
    }

    /**
     * Get cached divider value for a sensor
     * @param sensorId 0=Baro, 1=Hum, 2=Accel, 3=Gyro, 4=Mag
     * @return Current divider value, or -1 if not yet fetched
     */
    public int getCurrentDivider(int sensorId) {
        if (sensorId < 0 || sensorId >= SENSOR_COUNT) {
            return -1;
        }
        return currentDividers[sensorId];
    }

    /**
     * Update cached divider value (called when response received)
     */
    private void updateCurrentDivider(int sensorId, int divider) {
        if (sensorId >= 0 && sensorId < SENSOR_COUNT) {
            currentDividers[sensorId] = divider;
        }
    }
}
