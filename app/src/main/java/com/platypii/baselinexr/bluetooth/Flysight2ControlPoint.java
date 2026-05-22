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
 * - 0x12: SET_GNSS_MODEL
 * - 0x13: SET_GNSS_RATE
 * - 0x20: SET_FUSION_MAG_HARD
 * - 0x21: SET_FUSION_MAG_SOFT
 * 
 * DS_Control_Point (Device State) commands:
 * - 0x01: GET_FW_VERSION
 * - 0x02: REBOOT_DEVICE
 * - 0x03: GET_DEVICE_ID
 * - 0x10: REQUEST_SLEEP
 * - 0x11: REQUEST_ACTIVE
 */
public class Flysight2ControlPoint {
    private static final String TAG = "FlysightProtocol";  // Use same tag for easier filtering

    // Sensor Data service and control point
    private static final UUID sensorDataService = UUID.fromString("00000001-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID sdControlPoint = UUID.fromString("00000006-8e22-4541-9d4c-21edae82ed19");
    
    // Device State service and control point
    private static final UUID deviceStateService = UUID.fromString("00000003-cc7a-482a-984a-7f2ed5b3e58f");
    private static final UUID dsControlPoint = UUID.fromString("00000007-8e22-4541-9d4c-21edae82ed19");
    private static final UUID dsMode = UUID.fromString("00000005-8e22-4541-9d4c-21edae82ed19");

    // SD_Control_Point opcodes
    public static final byte SD_CMD_SET_GNSS_BLE_MASK = 0x01;
    public static final byte SD_CMD_GET_GNSS_BLE_MASK = 0x02;
    public static final byte SD_CMD_SET_BLE_DIVIDER = 0x10;
    public static final byte SD_CMD_GET_BLE_DIVIDER = 0x11;
    public static final byte SD_CMD_SET_GNSS_MODEL = 0x12;
    public static final byte SD_CMD_SET_GNSS_RATE = 0x13;
    public static final byte SD_CMD_SET_FUSION_MAG_HARD = 0x20;
    public static final byte SD_CMD_SET_FUSION_MAG_SOFT = 0x21;
    public static final byte SD_CMD_RESET_MAG_CAL = 0x22;
    public static final byte SD_CMD_GET_SENSOR_ODRS = 0x31;
    public static final byte SD_CMD_GET_RATES = 0x32;
    public static final byte SD_CMD_GET_BLE_BUDGET = 0x33;

    // DS_Control_Point opcodes
    public static final byte DS_CMD_GET_FW_VERSION = 0x01;
    public static final byte DS_CMD_REBOOT_DEVICE = 0x02;
    public static final byte DS_CMD_GET_DEVICE_ID = 0x03;
    public static final byte DS_CMD_INSTALL_UPLOADED_FIRMWARE = 0x04;
    public static final byte DS_CMD_REQUEST_SLEEP = 0x10;
    public static final byte DS_CMD_REQUEST_ACTIVE = 0x11;
    public static final byte DS_CMD_REQUEST_START = 0x12;
    public static final byte DS_CMD_REQUEST_CONFIG = 0x13;
    public static final byte DS_CMD_REQUEST_PAIRING = 0x14;
    public static final byte DS_CMD_SET_EXT_SYNC = 0x15;

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

    // GNSS runtime settings
    public static final int[] GNSS_DYNAMIC_MODEL_VALUES = {0, 2, 3, 4, 5, 6, 7, 8};
    public static final String[] GNSS_DYNAMIC_MODEL_LABELS = {
            "Portable", "Stationary", "Pedestrian", "Automotive",
            "Sea", "Airborne 1G", "Airborne 2G", "Airborne 4G"
    };
    public static final int[] GNSS_RATE_VALUES_MS = {200, 100, 67, 50, 40};
    public static final String[] GNSS_RATE_LABELS = {"5 Hz", "10 Hz", "15 Hz", "20 Hz", "25 Hz"};

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
     * Set the GNSS dynamic model at runtime.
     * @param model u-blox dynamic model value (0, 2, 3, 4, 5, 6, 7, or 8)
     */
    public boolean setGnssModel(int model) {
        if (peripheral == null) {
            Log.w(TAG, "setGnssModel: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
                SD_CMD_SET_GNSS_MODEL,
                (byte) model
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setGnssModel model=" + model + " ok=" + ok);
        return ok;
    }

    /**
     * Set the GNSS measurement interval at runtime.
     * @param rateMs GNSS update interval in milliseconds
     */
    public boolean setGnssRateMs(int rateMs) {
        if (peripheral == null) {
            Log.w(TAG, "setGnssRateMs: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] {
                SD_CMD_SET_GNSS_RATE,
                (byte) (rateMs & 0xFF),
                (byte) ((rateMs >> 8) & 0xFF)
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setGnssRateMs rateMs=" + rateMs + " ok=" + ok);
        return ok;
    }

    /**
     * Set magnetometer hard-iron calibration offsets on FlySight 2.
     * Payload: [opcode(0x20)] [x_offset (int16_t LE)] [y_offset (int16_t LE)] [z_offset (int16_t LE)]
     * Offsets are in milligauss.
     *
     * @param offsetXGauss Hard iron X offset in gauss
     * @param offsetYGauss Hard iron Y offset in gauss
     * @param offsetZGauss Hard iron Z offset in gauss
     * @return true if write was queued successfully
     */
    public boolean setMagHardIron(float offsetXGauss, float offsetYGauss, float offsetZGauss) {
        if (peripheral == null) {
            Log.w(TAG, "setMagHardIron: no peripheral connected");
            return false;
        }
        // Convert gauss to milligauss
        short xMg = (short) Math.round(offsetXGauss * 1000.0);
        short yMg = (short) Math.round(offsetYGauss * 1000.0);
        short zMg = (short) Math.round(offsetZGauss * 1000.0);
        byte[] cmd = new byte[] {
            SD_CMD_SET_FUSION_MAG_HARD,
            (byte) (xMg & 0xFF), (byte) ((xMg >> 8) & 0xFF),
            (byte) (yMg & 0xFF), (byte) ((yMg >> 8) & 0xFF),
            (byte) (zMg & 0xFF), (byte) ((zMg >> 8) & 0xFF)
        };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setMagHardIron x=" + xMg + " y=" + yMg + " z=" + zMg + " mg, ok=" + ok);
        return ok;
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
        switch (mode) {
            case 0:
                return requestSleep();
            case 1:
                return requestActive();
            case 2:
                return requestConfig();
            case 4:
                return requestPairing();
            case 5:
                return requestStart();
            default:
                Log.w(TAG, "setMode: unsupported mode=" + mode);
                return false;
        }
    }

    public boolean requestSleep() {
        if (peripheral == null) {
            Log.w(TAG, "requestSleep: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REQUEST_SLEEP };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "requestSleep ok=" + ok);
        return ok;
    }

    public boolean requestActive() {
        if (peripheral == null) {
            Log.w(TAG, "requestActive: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REQUEST_ACTIVE };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "requestActive ok=" + ok);
        return ok;
    }

    public boolean requestStart() {
        if (peripheral == null) {
            Log.w(TAG, "requestStart: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REQUEST_START };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "requestStart ok=" + ok);
        return ok;
    }

    public boolean requestConfig() {
        if (peripheral == null) {
            Log.w(TAG, "requestConfig: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REQUEST_CONFIG };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "requestConfig ok=" + ok);
        return ok;
    }

    public boolean requestPairing() {
        if (peripheral == null) {
            Log.w(TAG, "requestPairing: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[] { DS_CMD_REQUEST_PAIRING };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "requestPairing ok=" + ok);
        return ok;
    }

    /**
     * Set the external synchronization timestamp used in log headers.
     * @param extSync Unsigned 32-bit timestamp value
     */
    public boolean setExtSync(long extSync) {
        if (peripheral == null) {
            Log.w(TAG, "setExtSync: no peripheral connected");
            return false;
        }
        if (extSync < 0 || extSync > 0xFFFFFFFFL) {
            Log.w(TAG, "setExtSync: invalid value=" + extSync);
            return false;
        }
        byte[] cmd = new byte[] {
            DS_CMD_SET_EXT_SYNC,
            (byte) (extSync & 0xFF),
            (byte) ((extSync >> 8) & 0xFF),
            (byte) ((extSync >> 16) & 0xFF),
            (byte) ((extSync >> 24) & 0xFF)
        };
        boolean ok = peripheral.writeCharacteristic(deviceStateService, dsControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "setExtSync extSync=" + extSync + " ok=" + ok);
        return ok;
    }

    /**
     * Read the current device mode via the DS_Mode characteristic.
     * The result will arrive via onCharacteristicUpdate.
     */
    public boolean readMode() {
        if (peripheral == null) {
            Log.w(TAG, "readMode: no peripheral connected");
            return false;
        }
        boolean ok = peripheral.readCharacteristic(deviceStateService, dsMode);
        Log.i(TAG, "readMode ok=" + ok);
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
     * Request ODR index + source for all 5 sensors.
     * Response: [0xF0][0x31][status][baro_idx][baro_src][hum_idx][hum_src][accel_idx][accel_src][gyro_idx][gyro_src][mag_idx][mag_src]
     */
    public boolean getSensorOdrs() {
        if (peripheral == null) {
            Log.w(TAG, "getSensorOdrs: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[]{ SD_CMD_GET_SENSOR_ODRS };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getSensorOdrs ok=" + ok);
        return ok;
    }

    /**
     * Request GNSS rate, AL rate, and AL enabled flag.
     * Response: [0xF0][0x32][status][gnss_req×2][gnss_eff×2][gnss_src][al_req×2][al_eff×2][al_src][al_enabled]
     */
    public boolean getRates() {
        if (peripheral == null) {
            Log.w(TAG, "getRates: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[]{ SD_CMD_GET_RATES };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getRates ok=" + ok);
        return ok;
    }

    /**
     * Request BLE bandwidth summary and warning flags.
     * Response: [0xF0][0x33][status][est_bps×4][sensor_bps×4][al_bps×4][budget_ok][warn_flags×4]
     */
    public boolean getBleBudget() {
        if (peripheral == null) {
            Log.w(TAG, "getBleBudget: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[]{ SD_CMD_GET_BLE_BUDGET };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "getBleBudget ok=" + ok);
        return ok;
    }

    /**
     * Reset magnetometer hard-iron calibration and restart collection from scratch.
     * Deletes MAGCAL.BIN, zeroes fusion hard-iron correction, restarts MotionFX MagCal algorithm.
     * Only valid in Active Mode (SD card must be mounted).
     * Response: [0xF0][0x22][status]
     */
    public boolean resetMagCal() {
        if (peripheral == null) {
            Log.w(TAG, "resetMagCal: no peripheral connected");
            return false;
        }
        byte[] cmd = new byte[]{ SD_CMD_RESET_MAG_CAL };
        boolean ok = peripheral.writeCharacteristic(sensorDataService, sdControlPoint, cmd, WriteType.WITH_RESPONSE);
        Log.i(TAG, "resetMagCal ok=" + ok);
        return ok;
    }

    /**
     * Process incoming control point response.
     * Standard format: [0xF0] [Request Opcode] [Status] [Optional Data...]
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

        int frameType = value[0] & 0xFF;
        if (frameType != 0xF0) {
            Log.w(TAG, "CP Response: unexpected frame type 0x" + Integer.toHexString(frameType));
            return;
        }

        int opcode = value[1] & 0xFF;
        int statusCode = value[2] & 0xFF;
        String statusStr = getStatusString(statusCode);
        Log.i(TAG, "CP Response: opcode=0x" + Integer.toHexString(opcode) + " status=" + statusStr);

        // Extract optional response data
        byte[] data = null;
        if (value.length > 3) {
            data = new byte[value.length - 3];
            System.arraycopy(value, 3, data, 0, data.length);

            // Log specific response data for known opcodes
            if (opcode == (SD_CMD_GET_BLE_DIVIDER & 0xFF) && data.length >= 3) {
                int sensorId = data[0] & 0xFF;
                int divider = (data[1] & 0xFF) | ((data[2] & 0xFF) << 8);
                Log.i(TAG, "  -> sensor=" + sensorId + " divider=" + divider);
            } else if (opcode == (DS_CMD_GET_FW_VERSION & 0xFF)) {
                String version = new String(data);
                Log.i(TAG, "  -> firmware=" + version);
            } else if (opcode == (DS_CMD_GET_DEVICE_ID & 0xFF)) {
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
}
