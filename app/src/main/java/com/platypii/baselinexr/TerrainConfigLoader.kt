package com.platypii.baselinexr

import android.content.Context
import android.util.Log
import com.google.gson.Gson

/**
 * Shared loader for terrain configuration data
 */
object TerrainConfigLoader {
    private const val TAG = "TerrainConfigLoader"
    private var cachedConfig: TerrainConfiguration? = null
    private var lastLoadedModel: String? = null

    /**
     * Load terrain configuration from assets, with caching
     */
    fun loadConfig(context: Context, modelPath: String = VROptions.current.terrainModel): TerrainConfiguration? {
        // Return cached config if same model
        if (cachedConfig != null && lastLoadedModel == modelPath) {
            return cachedConfig
        }

        try {
            // Load JSON from assets
            val jsonString = context.assets.open(modelPath)
                .bufferedReader()
                .use { it.readText() }

            // Parse JSON using Gson
            val gson = Gson()
            val config = gson.fromJson(
                jsonString,
                TerrainConfiguration::class.java
            )

            // Cache the result
            cachedConfig = config
            lastLoadedModel = modelPath
            
            Log.i(TAG, "Loaded terrain configuration: $modelPath")
            return config

        } catch (e: Exception) {
            Log.e(TAG, "Failed to load terrain configuration: $modelPath", e)
            return null
        }
    }

    /**
     * Get currently cached configuration, if any
     */
    fun getCurrentConfig(): TerrainConfiguration? {
        return cachedConfig
    }

    /**
     * Clear cached configuration
     */
    fun clearCache() {
        cachedConfig = null
        lastLoadedModel = null
    }
}