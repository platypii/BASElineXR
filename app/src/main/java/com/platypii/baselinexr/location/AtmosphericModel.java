package com.platypii.baselinexr.location;

import android.util.Log;
import com.platypii.baselinexr.measurements.MLocation;

/**
 * Atmospheric model based on International Standard Atmosphere (ISA)
 * with support for temperature offset and future sensor integration.
 *
 * This class tracks atmospheric conditions from GPS altitude updates
 * and calculates air density for wingsuit performance calculations.
 */
public class AtmosphericModel {
    private static final String TAG = "AtmosphericModel";

    // Physical constants
    private static final double GRAVITY = 9.80665;           // Acceleration due to gravity (m/s²)
    private static final double GAS_CONSTANT = 8.31447;      // Universal gas constant (J/mol/K)

    // ISA standard atmosphere constants
    private static final double RHO_0 = 1.225;              // kg/m³ ISA density at sea level
    private static final double PRESSURE_0 = 101325.0;      // ISA pressure at sea level (Pa)
    private static final double TEMP_0 = 288.15;            // ISA temperature at sea level (K, 15°C)
    private static final double LAPSE_RATE = 0.0065;        // Temperature lapse rate (K/m)
    private static final double MM_AIR = 0.0289644;         // Molar mass of dry air (kg/mol)
    private static final double MM_WATER = 0.018016;        // Molar mass of water vapor (kg/mol)

    // Exponent for barometric formula
    private static final double BARO_EXP = GRAVITY * MM_AIR / (GAS_CONSTANT * LAPSE_RATE);

    // Current atmospheric state
    private float currentAltitude = 0.0f;
    private float temperatureOffset = 0.0f;  // Temperature offset from standard atmosphere (K)
    private float currentDensity = (float) RHO_0;
    private float currentPressure = (float) PRESSURE_0;
    private float currentTemperature = (float) TEMP_0;

    // For future sensor integration
    private boolean hasBarometricSensor = false;
    private boolean hasTemperatureSensor = false;
    private boolean hasHumiditySensor = false;
    private float sensorPressure = 0.0f;
    private float sensorTemperature = 0.0f;
    private float sensorHumidity = 0.0f;  // Relative humidity (0.0 to 1.0)

    public AtmosphericModel() {
        updateAtmosphere(0.0f); // Initialize at sea level
    }

    public AtmosphericModel(float temperatureOffset) {
        this.temperatureOffset = temperatureOffset;
        updateAtmosphere(0.0f); // Initialize at sea level
    }

    /**
     * Update atmospheric conditions based on GPS altitude
     */
    public void updateFromGPS(MLocation location) {
        if (location != null && location.altitude_gps > -1000) { // Sanity check
            updateAtmosphere((float) location.altitude_gps);
        }
    }

    /**
     * Update atmospheric conditions for a given altitude
     * @param altitude Altitude in meters above sea level
     */
    public void updateAtmosphere(float altitude) {
        currentAltitude = altitude;

        // Use sensor data if available, otherwise use ISA model
        if (hasBarometricSensor && hasTemperatureSensor) {
            // Use actual sensor readings with optional humidity
            currentPressure = sensorPressure;
            currentTemperature = sensorTemperature;
            if (hasHumiditySensor) {
                currentDensity = calculateDensityWithHumidity(currentPressure, currentTemperature, sensorHumidity);
            } else {
                currentDensity = calculateDensityFromPressureTemp(currentPressure, currentTemperature);
            }
        } else {
            // Use ISA standard atmosphere with temperature offset (assume dry air)
            currentPressure = altitudeToPressure(altitude);
            currentTemperature = getStandardTemperature(altitude) + temperatureOffset;
            currentDensity = calculateDensity(altitude, temperatureOffset);
        }

        if (hasHumiditySensor) {
            Log.d(TAG, String.format("Altitude: %.1fm, Pressure: %.1fPa, Temp: %.1fK, Humidity: %.1f%%, Density: %.4fkg/m³",
                    altitude, currentPressure, currentTemperature, sensorHumidity * 100, currentDensity));
        } else {
            Log.d(TAG, String.format("Altitude: %.1fm, Pressure: %.1fPa, Temp: %.1fK, Density: %.4fkg/m³",
                    altitude, currentPressure, currentTemperature, currentDensity));
        }
    }

    /**
     * Barometric formula
     * https://en.wikipedia.org/wiki/Atmospheric_pressure#Altitude_variation
     * @param altitude Altitude in meters
     * @return pressure in pascals (Pa)
     */
    public static float altitudeToPressure(float altitude) {
        return (float) (PRESSURE_0 * Math.pow(1.0 - LAPSE_RATE * altitude / TEMP_0, BARO_EXP));
    }

    /**
     * Get standard atmosphere temperature at altitude
     * @param altitude Altitude in meters
     * @return Temperature in Kelvin
     */
    public static float getStandardTemperature(float altitude) {
        return (float) (TEMP_0 - LAPSE_RATE * altitude);
    }

    /**
     * Calculate air density using ISA model with temperature offset
     * @param altitude Altitude in meters
     * @param temperatureOffset Temperature offset from standard atmosphere (K)
     * @return Air density in kg/m³
     */
    public static float calculateDensity(float altitude, float temperatureOffset) {
        float airPressure = altitudeToPressure(altitude);
        float temperature = getStandardTemperature(altitude) + temperatureOffset;
        return (float) (airPressure / (GAS_CONSTANT / MM_AIR) / temperature);
    }

    /**
     * Calculate air density from pressure and temperature (dry air)
     * @param pressure Pressure in pascals
     * @param temperature Temperature in Kelvin
     * @return Air density in kg/m³
     */
    public static float calculateDensityFromPressureTemp(float pressure, float temperature) {
        return (float) (pressure / (GAS_CONSTANT / MM_AIR) / temperature);
    }

    /**
     * Calculate air density with humidity correction
     * @param pressure Total atmospheric pressure in pascals
     * @param temperature Temperature in Kelvin
     * @param relativeHumidity Relative humidity (0.0 to 1.0)
     * @return Air density in kg/m³
     */
    public static float calculateDensityWithHumidity(float pressure, float temperature, float relativeHumidity) {
        // Calculate saturation vapor pressure using Magnus formula
        float tempC = temperature - 273.15f; // Convert to Celsius
        float saturationVaporPressure = (float) (610.78 * Math.exp(17.2694 * tempC / (tempC + 238.3)));

        // Calculate actual water vapor pressure
        float vaporPressure = relativeHumidity * saturationVaporPressure;

        // Calculate partial pressure of dry air
        float dryAirPressure = pressure - vaporPressure;

        // Calculate density using the ideal gas law for moist air
        // ρ = (pd * Md + pv * Mv) / (R * T)
        // where pd = dry air pressure, pv = vapor pressure, Md = molar mass dry air, Mv = molar mass water vapor
        float dryAirDensity = (float) (dryAirPressure / (GAS_CONSTANT / MM_AIR) / temperature);
        float vaporDensity = (float) (vaporPressure / (GAS_CONSTANT / MM_WATER) / temperature);

        return dryAirDensity + vaporDensity;
    }

    /**
     * Calculate saturation vapor pressure using Magnus formula
     * @param temperature Temperature in Kelvin
     * @return Saturation vapor pressure in pascals
     */
    public static float getSaturationVaporPressure(float temperature) {
        float tempC = temperature - 273.15f; // Convert to Celsius
        return (float) (610.78 * Math.exp(17.2694 * tempC / (tempC + 238.3)));
    }

    // Getters for current atmospheric state
    public float getCurrentAltitude() {
        return currentAltitude;
    }

    public float getCurrentDensity() {
        return currentDensity;
    }

    public float getCurrentPressure() {
        return currentPressure;
    }

    public float getCurrentTemperature() {
        return currentTemperature;
    }

    public float getTemperatureOffset() {
        return temperatureOffset;
    }

    public float getCurrentHumidity() {
        return hasHumiditySensor ? sensorHumidity : 0.0f;
    }

    public boolean hasHumidityData() {
        return hasHumiditySensor;
    }

    // Setters
    public void setTemperatureOffset(float temperatureOffset) {
        this.temperatureOffset = temperatureOffset;
        updateAtmosphere(currentAltitude); // Recalculate with new offset
    }

    // Future sensor integration methods
    public void setBarometricSensor(float pressure) {
        this.sensorPressure = pressure;
        this.hasBarometricSensor = true;
        updateAtmosphere(currentAltitude);
    }

    public void setTemperatureSensor(float temperature) {
        this.sensorTemperature = temperature;
        this.hasTemperatureSensor = true;
        updateAtmosphere(currentAltitude);
    }

    public void setHumiditySensor(float relativeHumidity) {
        this.sensorHumidity = Math.max(0.0f, Math.min(1.0f, relativeHumidity)); // Clamp to 0-1
        this.hasHumiditySensor = true;
        updateAtmosphere(currentAltitude);
    }

    public void clearSensorData() {
        this.hasBarometricSensor = false;
        this.hasTemperatureSensor = false;
        this.hasHumiditySensor = false;
        updateAtmosphere(currentAltitude); // Revert to ISA model
    }

    /**
     * Get density ratio compared to sea level
     * Useful for performance calculations
     */
    public float getDensityRatio() {
        return currentDensity / (float) RHO_0;
    }


}
