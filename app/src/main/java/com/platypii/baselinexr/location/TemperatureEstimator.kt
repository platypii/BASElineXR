package com.platypii.baselinexr.location

import android.util.Log
import com.platypii.baselinexr.measurements.MLocation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Estimates outside air temperature offset from ISA based on GPS location and time.
 * Uses latitude, longitude, day of year, and solar time to model temperature variations.
 * 
 * The model accounts for:
 * - Latitude effect (warmer at equator)
 * - Seasonal variation (warmer in summer)
 * - Diurnal (daily) variation (warmer in afternoon)
 *
 * To tune the temperature offset model for a different location, follow these steps:
 * - Update the latitude and longitude in your Inputs sheet to match the new location.
 * - Adjust the time zone meridian based on the location’s standard time zone (e.g., −105° for MST).
 * - Use the lag formula to recalculate the diurnal phase shift:
 * h_{\mathrm{lag}}=h_0+\alpha (1-\cos (\phi ))+\beta \cos \left( \frac{2\pi }{365}(d-172)\right) - Tune h_0,\alpha ,\beta  to match the observed peak temperature time.
 * - Fit amplitude terms:
 * - A_{\mathrm{lat}}: baseline offset due to latitude
 * - A_{\mathrm{year}}: seasonal swing (based on annual high/low range)
 * - A_{\mathrm{day}}: daily swing (based on typical diurnal range)
 * - Use historical hourly temperature data from WeatherSpark or NOAA to compare modeled vs observed curves. Match the peak timing, amplitude, and seasonal envelope.
 * This approach ensures your model adapts to local climate patterns while preserving its parametric structure.
 *
 */
object TemperatureEstimator {
    private const val TAG = "TempEstimator"

    // Model parameters (tune these as needed)
    private const val A_LAT = 10.0   // K - latitude amplitude
    private const val A_YEAR = 15.0  // K - yearly/seasonal amplitude
    private const val A_DAY = 15.0    // K - daily amplitude
    private const val H0 = 3.0       // base lag (hours)
    private const val ALPHA = 1.2    // latitude lag effect (hours)
    private const val BETA = 1.0     // seasonal lag effect (hours)

    // Track if we've already initialized with estimated offset
    var hasInitializedOffset = false
        private set

    /**
     * Estimate the temperature offset from ISA based on GPS location and time.
     * 
     * @param location MLocation with valid latitude, longitude, and time (millis in GPS time)
     * @return Estimated temperature offset in Celsius (add to ISA temperature)
     */
    fun estimateTemperatureOffset(location: MLocation): Double {
        val latitude = location.latitude
        val longitude = location.longitude
        val gpsTimeMillis = location.millis

        return estimateTemperatureOffsetFromParams(latitude, longitude, gpsTimeMillis)
    }

    /**
     * Estimate temperature offset from raw parameters.
     * @param gpsTimeMillis GPS time in milliseconds (not phone time!)
     */
    fun estimateTemperatureOffsetFromParams(latitude: Double, longitude: Double, gpsTimeMillis: Long): Double {
        // Convert GPS time to day of year and hour (GPS time is essentially UTC)
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = gpsTimeMillis
        
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val utcHour = calendar.get(java.util.Calendar.HOUR_OF_DAY) + 
                      calendar.get(java.util.Calendar.MINUTE) / 60.0

        // Calculate local solar time from UTC and longitude
        // Each 15° of longitude = 1 hour offset from UTC
        // Equation of Time correction for Earth's orbital eccentricity
        val B = 2 * PI * (dayOfYear - 81) / 365.0
        val EoT = 9.87 * sin(2 * B) - 7.53 * cos(B) - 1.5 * sin(B) // minutes

        // Solar time (hours) - longitude directly gives offset from UTC
        val solarHour = utcHour + (longitude / 15.0) + EoT / 60.0

        // Normalize solar hour to 0-24
        val normalizedSolarHour = ((solarHour % 24.0) + 24.0) % 24.0

        // Lag function (hours) - peak temperature occurs after solar noon
        val lag = H0 + ALPHA * (1 - cos(Math.toRadians(latitude))) +
                  BETA * cos(2 * PI * (dayOfYear - 172) / 365.0)

        // Temperature offset from ISA sea-level baseline (15°C)
        // Combines latitude, seasonal, and diurnal effects
        // Diurnal term: peak at solar noon + lag (typically 14:00-15:00 solar time)
        // We subtract 12 so that cos peaks at solarHour = 12 + lag (afternoon)
        val deltaT = A_LAT * cos(Math.toRadians(latitude)) +
                     A_YEAR * cos(2 * PI * (dayOfYear - 172) / 365.0) +
                     A_DAY * cos(2 * PI * (normalizedSolarHour - 12.0 - lag) / 24.0)

        return deltaT
    }

    /**
     * Estimate the absolute air temperature at sea level based on GPS location and time.
     * 
     * @param location MLocation with valid latitude, longitude, and time
     * @return Estimated temperature in Celsius at sea level
     */
    fun estimateSeaLevelTemperature(location: MLocation): Double {
        val isaSeaLevelTemp = 15.0 // ISA standard temperature at sea level in °C
        return isaSeaLevelTemp + estimateTemperatureOffset(location)
    }

    /**
     * Estimate the air temperature at a given altitude based on GPS location and time.
     * Uses ISA lapse rate to adjust for altitude.
     * 
     * @param location MLocation with valid latitude, longitude, altitude, and time
     * @return Estimated temperature in Celsius at the GPS altitude
     */
    fun estimateTemperatureAtAltitude(location: MLocation): Double {
        val seaLevelTemp = estimateSeaLevelTemperature(location)
        val altitudeMeters = location.altitude_gps
        
        // ISA lapse rate: -6.5°C per 1000m (up to tropopause ~11km)
        val lapseRate = 0.0065 // °C per meter
        return seaLevelTemp - lapseRate * altitudeMeters
    }

    /**
     * Calculate the ISA temperature offset (deviation from standard atmosphere)
     * that should be used in AtmosphereSettings based on GPS.
     * Also marks that we've initialized the offset.
     * 
     * @param location MLocation with valid latitude, longitude, and time
     * @return Temperature offset in Celsius to set in AtmosphereSettings
     */
    fun calculateIsaOffset(location: MLocation): Float {
        hasInitializedOffset = true
        return estimateTemperatureOffset(location).toFloat()
    }

    /**
     * TEMPORARY DEBUG: Log estimated temperature offsets for each hour of the day.
     * This helps verify the diurnal temperature model is working correctly.
     * Remove this method after testing.
     * 
     * @param location MLocation with GPS time (millis field)
     */
    fun logDailyTemperatureProfile(location: MLocation) {
        val latitude = location.latitude
        val longitude = location.longitude
        val gpsTimeMillis = location.millis

        // Get the local timezone offset based on longitude (approximate)
        val localOffsetHours = (longitude / 15.0).toInt()
        
        Log.i(TAG, "=== DAILY TEMPERATURE OFFSET PROFILE ===")
        Log.i(TAG, "Location: lat=${"%.4f".format(latitude)}, lon=${"%.4f".format(longitude)}")
        Log.i(TAG, "GPS Time (millis): $gpsTimeMillis")
        
        // Get current time info from GPS time
        val calendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = gpsTimeMillis
        val currentDayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val currentUtcHour = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        val currentMinute = calendar.get(java.util.Calendar.MINUTE)
        val currentMonth = calendar.get(java.util.Calendar.MONTH) + 1
        val currentDay = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        val currentYear = calendar.get(java.util.Calendar.YEAR)
        
        Log.i(TAG, "GPS Date: $currentYear-$currentMonth-$currentDay, UTC time: $currentUtcHour:${"%.0f".format(currentMinute.toDouble()).padStart(2, '0')}")
        Log.i(TAG, "Day of year: $currentDayOfYear")
        Log.i(TAG, "Approx local offset from UTC: $localOffsetHours hours")
        Log.i(TAG, "")
        Log.i(TAG, "Hour(Local) | Hour(UTC) | Solar Hour | Offset(°C) | Offset(°F) | SeaLevel(°F)")
        Log.i(TAG, "------------|-----------|------------|------------|------------|-------------")
        
        // Calculate for each hour of the day
        for (localHour in 0..23) {
            // Calculate what UTC hour corresponds to this local hour
            val utcHour = (localHour - localOffsetHours + 24) % 24
            
            // Create a GPS timestamp for this hour (same day as the GPS fix)
            val testCalendar = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            testCalendar.timeInMillis = gpsTimeMillis
            testCalendar.set(java.util.Calendar.HOUR_OF_DAY, utcHour)
            testCalendar.set(java.util.Calendar.MINUTE, 0)
            testCalendar.set(java.util.Calendar.SECOND, 0)
            val testGpsTimeMillis = testCalendar.timeInMillis
            
            val offsetC = estimateTemperatureOffsetFromParams(latitude, longitude, testGpsTimeMillis)
            val offsetF = offsetC * 9.0 / 5.0
            val seaLevelTempC = 15.0 + offsetC
            val seaLevelTempF = seaLevelTempC * 9.0 / 5.0 + 32.0
            
            // Calculate solar hour for display
            val B = 2 * PI * (currentDayOfYear - 81) / 365.0
            val EoT = 9.87 * sin(2 * B) - 7.53 * cos(B) - 1.5 * sin(B)
            val solarHour = utcHour + (longitude / 15.0) + EoT / 60.0
            val normalizedSolarHour = ((solarHour % 24.0) + 24.0) % 24.0
            
            Log.i(TAG, "%02d:00       | %02d:00     | %05.2f      | %+6.2f     | %+6.2f     | %6.1f".format(
                localHour, utcHour, normalizedSolarHour, offsetC, offsetF, seaLevelTempF
            ))
        }
        
        Log.i(TAG, "=== END TEMPERATURE PROFILE ===")
    }
}
