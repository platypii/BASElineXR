package com.platypii.baselinexr.events;

import androidx.annotation.NonNull;

/**
 * Indicates that the FlySight 2 device mode has changed.
 * Posted via EventBus when DS_Mode characteristic updates.
 * 
 * Mode values:
 * - 0x00 SLEEP: Idle, low power. File access available.
 * - 0x01 ACTIVE: GNSS logging, audio feedback. Sensor streaming available.
 * - 0x02 CONFIG: Configuration selection mode.
 * - 0x03 USB: USB Mass Storage mode.
 * - 0x04 PAIRING: BLE pairing request mode.
 * - 0x05 START: Starter pistol mode.
 */
public class FlysightModeEvent {
    
    // Mode constants matching FS_Mode_State_t enum
    public static final int MODE_SLEEP = 0x00;
    public static final int MODE_ACTIVE = 0x01;
    public static final int MODE_CONFIG = 0x02;
    public static final int MODE_USB = 0x03;
    public static final int MODE_PAIRING = 0x04;
    public static final int MODE_START = 0x05;

    public final int mode;
    public final int previousMode;

    public FlysightModeEvent(int mode, int previousMode) {
        this.mode = mode;
        this.previousMode = previousMode;
    }

    public boolean isActive() {
        return mode == MODE_ACTIVE || mode == MODE_START;
    }

    public boolean wasSleep() {
        return previousMode == MODE_SLEEP;
    }

    @NonNull
    public static String modeName(int mode) {
        switch (mode) {
            case MODE_SLEEP: return "SLEEP";
            case MODE_ACTIVE: return "ACTIVE";
            case MODE_CONFIG: return "CONFIG";
            case MODE_USB: return "USB";
            case MODE_PAIRING: return "PAIRING";
            case MODE_START: return "START";
            default: return "UNKNOWN(" + mode + ")";
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "FlysightModeEvent(" + modeName(previousMode) + " -> " + modeName(mode) + ")";
    }
}
