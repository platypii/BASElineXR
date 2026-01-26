package com.platypii.baselinexr.calibration

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.platypii.baselinexr.ahrs.FusionAhrsAdapter
import org.json.JSONArray
import org.json.JSONObject

/**
 * Storage utility for magnetometer calibration data.
 * 
 * Saves and loads calibration to SharedPreferences as JSON.
 * Matches the format from the reference TypeScript implementation.
 */
object CalibrationStorage {
    private const val TAG = "CalibrationStorage"
    private const val PREFS_NAME = "mag_calibration"
    private const val KEY_CALIBRATION = "calibration_json"
    private const val CALIBRATION_VERSION = 1
    
    /**
     * Save calibration result to SharedPreferences.
     * 
     * @param context Android context
     * @param result Calibration result to save
     * @param includeSoftIron Whether to include soft iron matrix
     */
    fun saveCalibration(context: Context, result: MagCalibrationResult, includeSoftIron: Boolean) {
        try {
            val json = JSONObject().apply {
                put("version", CALIBRATION_VERSION)
                put("createdAt", System.currentTimeMillis())
                
                // Hard iron offset
                put("hardIron", JSONObject().apply {
                    put("x", result.offsetX)
                    put("y", result.offsetY)
                    put("z", result.offsetZ)
                })
                
                // Soft iron matrix (if requested and available)
                if (includeSoftIron && result.calibrationType == "soft_iron") {
                    // Store as 3x3 nested array for JSON compatibility
                    val matrixArray = JSONArray()
                    for (row in 0 until 3) {
                        val rowArray = JSONArray()
                        for (col in 0 until 3) {
                            rowArray.put(result.softIronMatrix[row * 3 + col])
                        }
                        matrixArray.put(rowArray)
                    }
                    put("softIronMatrix", matrixArray)
                }
                
                // Quality metrics
                put("referenceMagnitude", result.referenceMagnitude)
                put("sphericity", result.sphericity)
                put("quality", result.quality)
                put("calibrationType", result.calibrationType)
            }
            
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().putString(KEY_CALIBRATION, json.toString()).apply()
            
            Log.i(TAG, "Saved calibration: hardIron=(${result.offsetX}, ${result.offsetY}, ${result.offsetZ}), includeSoftIron=$includeSoftIron")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save calibration", e)
        }
    }
    
    /**
     * Load calibration from SharedPreferences.
     * 
     * @param context Android context
     * @return Calibration data or null if not found
     */
    fun loadCalibration(context: Context): SavedCalibration? {
        try {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val jsonString = prefs.getString(KEY_CALIBRATION, null) ?: return null
            
            val json = JSONObject(jsonString)
            
            // Parse hard iron
            val hardIron = json.optJSONObject("hardIron") ?: return null
            val offsetX = hardIron.getDouble("x").toFloat()
            val offsetY = hardIron.getDouble("y").toFloat()
            val offsetZ = hardIron.getDouble("z").toFloat()
            
            // Parse soft iron matrix if present
            var softIronMatrix: FloatArray? = null
            val matrixJson = json.optJSONArray("softIronMatrix")
            if (matrixJson != null) {
                softIronMatrix = FloatArray(9)
                var idx = 0
                for (i in 0 until matrixJson.length()) {
                    val row = matrixJson.getJSONArray(i)
                    for (j in 0 until row.length()) {
                        softIronMatrix[idx++] = row.getDouble(j).toFloat()
                    }
                }
            }
            
            val calibrationType = json.optString("calibrationType", "hard_iron")
            val createdAt = json.optLong("createdAt", 0)
            
            Log.i(TAG, "Loaded calibration: hardIron=($offsetX, $offsetY, $offsetZ), hasSoftIron=${softIronMatrix != null}")
            
            return SavedCalibration(
                offsetX = offsetX,
                offsetY = offsetY,
                offsetZ = offsetZ,
                softIronMatrix = softIronMatrix,
                calibrationType = calibrationType,
                createdAt = createdAt
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load calibration", e)
            return null
        }
    }
    
    /**
     * Apply saved calibration to AHRS adapter.
     * 
     * @param adapter The FusionAhrsAdapter to configure
     * @param calibration Saved calibration data
     */
    fun applyToAdapter(adapter: FusionAhrsAdapter, calibration: SavedCalibration) {
        // Apply hard iron offset
        adapter.setMagCalibration(
            FusionAhrsAdapter.MagCalibration(
                offsetX = calibration.offsetX,
                offsetY = calibration.offsetY,
                offsetZ = calibration.offsetZ,
                scaleX = 1f,
                scaleY = 1f,
                scaleZ = 1f
            )
        )
        
        // Apply soft iron matrix if present
        adapter.setSoftIronMatrix(calibration.softIronMatrix)
        
        val calType = if (calibration.softIronMatrix != null) "full" else "hard iron"
        Log.i(TAG, "Applied $calType calibration to adapter")
    }
    
    /**
     * Clear saved calibration.
     */
    fun clearCalibration(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CALIBRATION).apply()
        Log.i(TAG, "Cleared saved calibration")
    }
    
    /**
     * Check if calibration is saved.
     */
    fun hasCalibration(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.contains(KEY_CALIBRATION)
    }
}

/**
 * Data class for saved calibration.
 */
data class SavedCalibration(
    val offsetX: Float,
    val offsetY: Float,
    val offsetZ: Float,
    val softIronMatrix: FloatArray?,
    val calibrationType: String,
    val createdAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SavedCalibration) return false
        return offsetX == other.offsetX &&
               offsetY == other.offsetY &&
               offsetZ == other.offsetZ &&
               softIronMatrix.contentEquals(other.softIronMatrix) &&
               calibrationType == other.calibrationType
    }

    override fun hashCode(): Int {
        var result = offsetX.hashCode()
        result = 31 * result + offsetY.hashCode()
        result = 31 * result + offsetZ.hashCode()
        result = 31 * result + (softIronMatrix?.contentHashCode() ?: 0)
        result = 31 * result + calibrationType.hashCode()
        return result
    }
}
