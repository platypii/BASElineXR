package com.platypii.baselinexr.calibration

import android.graphics.Color

/**
 * Magnetometer calibration data fetched from the FlySight 2 device via SD_CMD_GET_MAG_CAL (0x34).
 *
 * Hard iron offsets are provided by ST Micro MotionFX running on the device.
 * Apply as: calibrated = raw - hardIron  (in ENU device frame, after axis remap)
 *
 * Quality levels:
 *   0 = UNKNOWN  — no calibration yet (hard iron values are zero)
 *   1 = POOR     — calibration started but needs more motion
 *   2 = OK       — usable calibration
 *   3 = GOOD     — high-quality calibration
 */
data class DeviceMagCal(
    /** Hard iron X offset in gauss (ENU device frame) */
    val hardIronX: Float,
    /** Hard iron Y offset in gauss (ENU device frame) */
    val hardIronY: Float,
    /** Hard iron Z offset in gauss (ENU device frame) */
    val hardIronZ: Float,
    /** Quality level: 0=UNKNOWN, 1=POOR, 2=OK, 3=GOOD */
    val quality: Int
) {
    val qualityLabel: String
        get() = when (quality) {
            0 -> "UNKNOWN"
            1 -> "POOR"
            2 -> "OK"
            3 -> "GOOD"
            else -> "?"
        }

    val qualityColor: Int
        get() = when (quality) {
            0 -> Color.parseColor("#888888")
            1 -> Color.parseColor("#FF8844")
            2 -> Color.parseColor("#FFCC44")
            3 -> Color.parseColor("#44FF88")
            else -> Color.parseColor("#888888")
        }

    /**
     * Apply hard iron calibration to a raw magnetometer reading (ENU device frame).
     * Returns Triple(cx, cy, cz) in gauss.
     */
    fun apply(rawX: Float, rawY: Float, rawZ: Float): Triple<Float, Float, Float> =
        Triple(rawX - hardIronX, rawY - hardIronY, rawZ - hardIronZ)

    /** Format hard iron values as a compact milligauss string for display. */
    fun hardIronMgString(): String {
        val hxMg = Math.round(hardIronX * 1000)
        val hyMg = Math.round(hardIronY * 1000)
        val hzMg = Math.round(hardIronZ * 1000)
        return "hi=($hxMg,$hyMg,$hzMg)mG"
    }
}
