package com.platypii.baselinexr.ui

import android.view.View
import android.widget.*
import com.platypii.baselinexr.R
import com.platypii.baselinexr.polars.Polars
import com.platypii.baselinexr.location.PolarLibrary

/**
 * Controller for polars configuration menu with unit conversions
 */
class PolarsController(private val rootView: View) {

    // Unit conversion constants
    companion object {
        const val SQ_FT_TO_SQ_M = 0.092903 // 1 sq ft = 0.092903 m²
        const val SQ_M_TO_SQ_FT = 10.764 // 1 m² = 10.764 sq ft
        const val LBS_TO_KG = 0.453592 // 1 lb = 0.453592 kg
        const val KG_TO_LBS = 2.20462 // 1 kg = 2.20462 lbs
    }

    // Section containers
    private val wingsuitSection: LinearLayout = rootView.findViewById(R.id.wingsuit_section)
    private val canopySection: LinearLayout = rootView.findViewById(R.id.canopy_section)
    private val airplaneSection: LinearLayout = rootView.findViewById(R.id.airplane_section)

    // Selector buttons
    private val selectWingsuit: Button = rootView.findViewById(R.id.select_wingsuit)
    private val selectCanopy: Button = rootView.findViewById(R.id.select_canopy)
    private val selectAirplane: Button = rootView.findViewById(R.id.select_airplane)

    // Wingsuit controls
    private val wingsuitSpinner: Button = rootView.findViewById(R.id.wingsuit_polar_spinner)
    private val wingsuitAreaValue: TextView = rootView.findViewById(R.id.wingsuit_area_value)
    private val wingsuitMassValue: TextView = rootView.findViewById(R.id.wingsuit_mass_value)
    private val wingsuitMassKg: TextView = rootView.findViewById(R.id.wingsuit_mass_kg)
    private val wingsuitAreaMinus: Button = rootView.findViewById(R.id.wingsuit_area_minus)
    private val wingsuitAreaPlus: Button = rootView.findViewById(R.id.wingsuit_area_plus)
    private val wingsuitMassMinus: Button = rootView.findViewById(R.id.wingsuit_mass_minus)
    private val wingsuitMassPlus: Button = rootView.findViewById(R.id.wingsuit_mass_plus)
    private val wingsuitCustomParams: LinearLayout = rootView.findViewById(R.id.wingsuit_custom_params)

    // Custom wingsuit parameter controls
    private val wingsuitPolarMinDragValue: TextView = rootView.findViewById(R.id.wingsuit_polar_min_drag_value)
    private val wingsuitPolarMinDragMinus: Button = rootView.findViewById(R.id.wingsuit_polar_min_drag_minus)
    private val wingsuitPolarMinDragPlus: Button = rootView.findViewById(R.id.wingsuit_polar_min_drag_plus)
    private val wingsuitPolarCloValue: TextView = rootView.findViewById(R.id.wingsuit_polar_clo_value)
    private val wingsuitPolarCloMinus: Button = rootView.findViewById(R.id.wingsuit_polar_clo_minus)
    private val wingsuitPolarCloPlus: Button = rootView.findViewById(R.id.wingsuit_polar_clo_plus)
    private val wingsuitPolarSlopeValue: TextView = rootView.findViewById(R.id.wingsuit_polar_slope_value)
    private val wingsuitPolarSlopeMinus: Button = rootView.findViewById(R.id.wingsuit_polar_slope_minus)
    private val wingsuitPolarSlopePlus: Button = rootView.findViewById(R.id.wingsuit_polar_slope_plus)
    private val wingsuitRangeMinClValue: TextView = rootView.findViewById(R.id.wingsuit_range_min_cl_value)
    private val wingsuitRangeMinClMinus: Button = rootView.findViewById(R.id.wingsuit_range_min_cl_minus)
    private val wingsuitRangeMinClPlus: Button = rootView.findViewById(R.id.wingsuit_range_min_cl_plus)
    private val wingsuitRangeMaxClValue: TextView = rootView.findViewById(R.id.wingsuit_range_max_cl_value)
    private val wingsuitRangeMaxClMinus: Button = rootView.findViewById(R.id.wingsuit_range_max_cl_minus)
    private val wingsuitRangeMaxClPlus: Button = rootView.findViewById(R.id.wingsuit_range_max_cl_plus)

    // Canopy controls
    private val canopySpinner: Button = rootView.findViewById(R.id.canopy_polar_spinner)
    private val canopyAreaValue: TextView = rootView.findViewById(R.id.canopy_area_value)
    private val canopyAreaM2: TextView = rootView.findViewById(R.id.canopy_area_m2)
    private val canopyMassValue: TextView = rootView.findViewById(R.id.canopy_mass_value)
    private val canopyMassKg: TextView = rootView.findViewById(R.id.canopy_mass_kg)
    private val canopyAreaMinus: Button = rootView.findViewById(R.id.canopy_area_minus)
    private val canopyAreaPlus: Button = rootView.findViewById(R.id.canopy_area_plus)
    private val canopyMassMinus: Button = rootView.findViewById(R.id.canopy_mass_minus)
    private val canopyMassPlus: Button = rootView.findViewById(R.id.canopy_mass_plus)
    private val canopyCustomParams: LinearLayout = rootView.findViewById(R.id.canopy_custom_params)

    // Custom canopy parameter controls
    private val canopyPolarMinDragValue: TextView = rootView.findViewById(R.id.canopy_polar_min_drag_value)
    private val canopyPolarMinDragMinus: Button = rootView.findViewById(R.id.canopy_polar_min_drag_minus)
    private val canopyPolarMinDragPlus: Button = rootView.findViewById(R.id.canopy_polar_min_drag_plus)
    private val canopyPolarCloValue: TextView = rootView.findViewById(R.id.canopy_polar_clo_value)
    private val canopyPolarCloMinus: Button = rootView.findViewById(R.id.canopy_polar_clo_minus)
    private val canopyPolarCloPlus: Button = rootView.findViewById(R.id.canopy_polar_clo_plus)
    private val canopyPolarSlopeValue: TextView = rootView.findViewById(R.id.canopy_polar_slope_value)
    private val canopyPolarSlopeMinus: Button = rootView.findViewById(R.id.canopy_polar_slope_minus)
    private val canopyPolarSlopePlus: Button = rootView.findViewById(R.id.canopy_polar_slope_plus)
    private val canopyRangeMinClValue: TextView = rootView.findViewById(R.id.canopy_range_min_cl_value)
    private val canopyRangeMinClMinus: Button = rootView.findViewById(R.id.canopy_range_min_cl_minus)
    private val canopyRangeMinClPlus: Button = rootView.findViewById(R.id.canopy_range_min_cl_plus)
    private val canopyRangeMaxClValue: TextView = rootView.findViewById(R.id.canopy_range_max_cl_value)
    private val canopyRangeMaxClMinus: Button = rootView.findViewById(R.id.canopy_range_max_cl_minus)
    private val canopyRangeMaxClPlus: Button = rootView.findViewById(R.id.canopy_range_max_cl_plus)

    // Airplane controls (no area controls)
    private val airplaneSpinner: Button = rootView.findViewById(R.id.airplane_polar_spinner)
    private val airplaneStandardMassControls: LinearLayout = rootView.findViewById(R.id.airplane_standard_mass_controls)
    private val airplaneMassValue: TextView = rootView.findViewById(R.id.airplane_mass_value)
    private val airplaneMassMinus: Button = rootView.findViewById(R.id.airplane_mass_minus)
    private val airplaneMassPlus: Button = rootView.findViewById(R.id.airplane_mass_plus)
    private val airplaneCustomParams: LinearLayout = rootView.findViewById(R.id.airplane_custom_params)

    // Custom airplane mass calculation controls
    private val airplaneDryWtValue: TextView = rootView.findViewById(R.id.airplane_dry_wt_value)
    private val airplaneDryWtMinus: Button = rootView.findViewById(R.id.airplane_dry_wt_minus)
    private val airplaneDryWtPlus: Button = rootView.findViewById(R.id.airplane_dry_wt_plus)
    private val airplaneFuelPctValue: TextView = rootView.findViewById(R.id.airplane_fuel_pct_value)
    private val airplaneFuelPctMinus: Button = rootView.findViewById(R.id.airplane_fuel_pct_minus)
    private val airplaneFuelPctPlus: Button = rootView.findViewById(R.id.airplane_fuel_pct_plus)
    private val airplaneNumPeopleValue: TextView = rootView.findViewById(R.id.airplane_num_people_value)
    private val airplaneNumPeopleMinus: Button = rootView.findViewById(R.id.airplane_num_people_minus)
    private val airplaneNumPeoplePlus: Button = rootView.findViewById(R.id.airplane_num_people_plus)
    private val airplanePersonWtValue: TextView = rootView.findViewById(R.id.airplane_person_wt_value)
    private val airplanePersonWtMinus: Button = rootView.findViewById(R.id.airplane_person_wt_minus)
    private val airplanePersonWtPlus: Button = rootView.findViewById(R.id.airplane_person_wt_plus)
    private val airplaneTotalMass: TextView = rootView.findViewById(R.id.airplane_total_mass)

    // Custom airplane aerodynamic controls
    private val airplaneSpanValue: TextView = rootView.findViewById(R.id.airplane_span_value)
    private val airplaneSpanMinus: Button = rootView.findViewById(R.id.airplane_span_minus)
    private val airplaneSpanPlus: Button = rootView.findViewById(R.id.airplane_span_plus)
    private val airplaneAreaValue: TextView = rootView.findViewById(R.id.airplane_area_value)
    private val airplaneAreaMinus: Button = rootView.findViewById(R.id.airplane_area_minus)
    private val airplaneAreaPlus: Button = rootView.findViewById(R.id.airplane_area_plus)
    private val airplaneArValue: TextView = rootView.findViewById(R.id.airplane_ar_value)
    private val airplaneEValue: TextView = rootView.findViewById(R.id.airplane_e_value)
    private val airplaneEMinus: Button = rootView.findViewById(R.id.airplane_e_minus)
    private val airplaneEPlus: Button = rootView.findViewById(R.id.airplane_e_plus)
    private val airplaneCd0Value: TextView = rootView.findViewById(R.id.airplane_cd0_value)
    private val airplaneCd0Minus: Button = rootView.findViewById(R.id.airplane_cd0_minus)
    private val airplaneCd0Plus: Button = rootView.findViewById(R.id.airplane_cd0_plus)
    private val airplanePowerValue: TextView = rootView.findViewById(R.id.airplane_power_value)
    private val airplanePowerMinus: Button = rootView.findViewById(R.id.airplane_power_minus)
    private val airplanePowerPlus: Button = rootView.findViewById(R.id.airplane_power_plus)
    private val airplaneMinClValue: TextView = rootView.findViewById(R.id.airplane_min_cl_value)
    private val airplaneMinClMinus: Button = rootView.findViewById(R.id.airplane_min_cl_minus)
    private val airplaneMinClPlus: Button = rootView.findViewById(R.id.airplane_min_cl_plus)
    private val airplaneMaxClValue: TextView = rootView.findViewById(R.id.airplane_max_cl_value)
    private val airplaneMaxClMinus: Button = rootView.findViewById(R.id.airplane_max_cl_minus)
    private val airplaneMaxClPlus: Button = rootView.findViewById(R.id.airplane_max_cl_plus)

    // Custom airplane polar parameters (defaults from screenshot)
    private var airplaneDryWt = 2150.0  // kg
    private var airplaneFuelPct = 0.5    // 0-1
    private var airplaneNumPeople = 10
    private var airplanePersonWt = 80.0  // kg per person
    private var airplaneSpan = 16.0      // m
    private var airplaneArea = 28.0      // m²
    private var airplaneE = 0.9          // Oswald efficiency
    private var airplaneCd0 = 0.04       // zero-lift drag coefficient
    private var airplanePower = 867.0    // hp (rated engine power)
    private var airplaneMinCl = 0.01     // minimum CL
    private var airplaneMaxCl = 0.81     // maximum CL

    // Custom wingsuit polar parameters (initialized from current polar)
    private var wingsuitPolarMinDrag: Double? = null
    private var wingsuitPolarClo: Double? = null
    private var wingsuitPolarSlope: Double? = null
    private var wingsuitRangeMinCl: Double? = null
    private var wingsuitRangeMaxCl: Double? = null

    // Custom canopy polar parameters (initialized from current polar)
    private var canopyPolarMinDrag: Double? = null
    private var canopyPolarClo: Double? = null
    private var canopyPolarSlope: Double? = null
    private var canopyRangeMinCl: Double? = null
    private var canopyRangeMaxCl: Double? = null

    private var isUpdating = false // Prevent feedback loops

    fun setupPolarUI() {
        setupSectionSelector()
        setupWingsuitUI()
        setupCanopyUI()
        setupAirplaneUI()

        // Show wingsuit by default
        showSection("wingsuit")
    }

    private fun setupSectionSelector() {
        selectWingsuit.setOnClickListener { showSection("wingsuit") }
        selectCanopy.setOnClickListener { showSection("canopy") }
        selectAirplane.setOnClickListener { showSection("airplane") }
    }

    private fun showSection(section: String) {
        wingsuitSection.visibility = if (section == "wingsuit") View.VISIBLE else View.GONE
        canopySection.visibility = if (section == "canopy") View.VISIBLE else View.GONE
        airplaneSection.visibility = if (section == "airplane") View.VISIBLE else View.GONE
    }

    private fun setupWingsuitUI() {
        // Get wingsuit polars
        val wingsuitPolars = PolarLibrary.getPolarsByType("Wingsuit")
        
        // Set initial button text
        val currentPolar = Polars.instance.wingsuitPolar
        wingsuitSpinner.text = currentPolar.name

        // Set initial values
        updateWingsuitFields()

        // Handle button click to cycle through polars
        wingsuitSpinner.setOnClickListener {
            val currentIndex = wingsuitPolars.indexOfFirst { it.name == Polars.instance.wingsuitPolar.name }
            val nextIndex = (currentIndex + 1) % wingsuitPolars.size
            val selectedPolar = wingsuitPolars[nextIndex]
            
            Polars.instance.setWingsuitPolar(selectedPolar)
            wingsuitSpinner.text = selectedPolar.name

            // Show/hide custom parameters
            if (selectedPolar.name == "Custom Wingsuit") {
                wingsuitCustomParams.visibility = View.VISIBLE
                // Initialize custom parameters from current polar if not already set
                if (wingsuitPolarMinDrag == null) {
                    wingsuitPolarMinDrag = selectedPolar.polarMinDrag
                    wingsuitPolarClo = selectedPolar.polarClo
                    wingsuitPolarSlope = selectedPolar.polarSlope
                    wingsuitRangeMinCl = selectedPolar.rangeMinCl
                    wingsuitRangeMaxCl = selectedPolar.rangeMaxCl
                }
                updateCustomWingsuit()
            } else {
                wingsuitCustomParams.visibility = View.GONE
                updateWingsuitFields()
            }
        }

        // Handle +/- buttons - Wingsuit area in m², mass in lbs
        wingsuitAreaMinus.setOnClickListener { adjustWingsuitArea(-0.05) } // -5%
        wingsuitAreaPlus.setOnClickListener { adjustWingsuitArea(0.05) }   // +5%
        wingsuitMassMinus.setOnClickListener { adjustWingsuitMass(-5.0) }  // -5 lbs
        wingsuitMassPlus.setOnClickListener { adjustWingsuitMass(5.0) }    // +5 lbs

        // Setup custom wingsuit parameter controls
        setupCustomWingsuitControls()
    }

    private fun setupCanopyUI() {
        // Get canopy polars
        val canopyPolars = PolarLibrary.getPolarsByType("Canopy")
        
        // Set initial button text
        val currentPolar = Polars.instance.canopyPolar
        canopySpinner.text = currentPolar.name

        // Set initial values
        updateCanopyFields()

        // Handle button click to cycle through polars
        canopySpinner.setOnClickListener {
            val currentIndex = canopyPolars.indexOfFirst { it.name == Polars.instance.canopyPolar.name }
            val nextIndex = (currentIndex + 1) % canopyPolars.size
            val selectedPolar = canopyPolars[nextIndex]
            
            Polars.instance.setCanopyPolar(selectedPolar)
            canopySpinner.text = selectedPolar.name

            // Show/hide custom parameters
            if (selectedPolar.name == "Custom Canopy") {
                canopyCustomParams.visibility = View.VISIBLE
                // Initialize custom parameters from current polar if not already set
                if (canopyPolarMinDrag == null) {
                    canopyPolarMinDrag = selectedPolar.polarMinDrag
                    canopyPolarClo = selectedPolar.polarClo
                    canopyPolarSlope = selectedPolar.polarSlope
                    canopyRangeMinCl = selectedPolar.rangeMinCl
                    canopyRangeMaxCl = selectedPolar.rangeMaxCl
                }
                updateCustomCanopy()
            } else {
                canopyCustomParams.visibility = View.GONE
                updateCanopyFields()
            }
        }

        // Handle +/- buttons - Canopy area in sq ft, mass in lbs
        canopyAreaMinus.setOnClickListener { adjustCanopyArea(-5.0) }  // -5 sq ft
        canopyAreaPlus.setOnClickListener { adjustCanopyArea(5.0) }    // +5 sq ft
        canopyMassMinus.setOnClickListener { adjustCanopyMass(-5.0) }  // -5 lbs
        canopyMassPlus.setOnClickListener { adjustCanopyMass(5.0) }    // +5 lbs

        // Setup custom canopy parameter controls
        setupCustomCanopyControls()
    }

    private fun setupAirplaneUI() {
        // Get airplane polars
        val airplanePolars = PolarLibrary.getPolarsByType("Airplane")
        
        // Set initial button text
        val currentPolar = Polars.instance.airplanePolar
        airplaneSpinner.text = currentPolar.name

        // Set initial values
        updateAirplaneFields()

        // Handle button click to cycle through polars
        airplaneSpinner.setOnClickListener {
            val currentIndex = airplanePolars.indexOfFirst { it.name == Polars.instance.airplanePolar.name }
            val nextIndex = (currentIndex + 1) % airplanePolars.size
            val selectedPolar = airplanePolars[nextIndex]
            
            Polars.instance.setAirplanePolar(selectedPolar)
            airplaneSpinner.text = selectedPolar.name

            // Show/hide custom parameters and toggle mass controls
            if (selectedPolar.name == "Custom Airplane") {
                airplaneStandardMassControls.visibility = View.GONE
                airplaneCustomParams.visibility = View.VISIBLE
                updateCustomAirplane()
            } else {
                airplaneStandardMassControls.visibility = View.VISIBLE
                airplaneCustomParams.visibility = View.GONE
                updateAirplaneFields()
            }
        }

        // Handle +/- buttons - Airplane mass in kg (±100 kg increments)
        airplaneMassMinus.setOnClickListener { adjustAirplaneMass(-100.0) }
        airplaneMassPlus.setOnClickListener { adjustAirplaneMass(100.0) }

        // Setup custom airplane controls
        setupCustomAirplaneControls()
    }

    private fun setupCustomAirplaneControls() {
        // Mass calculation controls
        airplaneDryWtMinus.setOnClickListener {
            airplaneDryWt -= 100.0
            updateCustomAirplane()
        }
        airplaneDryWtPlus.setOnClickListener {
            airplaneDryWt += 100.0
            updateCustomAirplane()
        }

        airplaneFuelPctMinus.setOnClickListener {
            airplaneFuelPct = (airplaneFuelPct - 0.1).coerceAtLeast(0.0)
            updateCustomAirplane()
        }
        airplaneFuelPctPlus.setOnClickListener {
            airplaneFuelPct = (airplaneFuelPct + 0.1).coerceAtMost(1.0)
            updateCustomAirplane()
        }

        airplaneNumPeopleMinus.setOnClickListener {
            airplaneNumPeople = (airplaneNumPeople - 1).coerceAtLeast(0)
            updateCustomAirplane()
        }
        airplaneNumPeoplePlus.setOnClickListener {
            airplaneNumPeople++
            updateCustomAirplane()
        }

        airplanePersonWtMinus.setOnClickListener {
            airplanePersonWt -= 5.0
            updateCustomAirplane()
        }
        airplanePersonWtPlus.setOnClickListener {
            airplanePersonWt += 5.0
            updateCustomAirplane()
        }

        // Aerodynamic controls
        airplaneSpanMinus.setOnClickListener {
            airplaneSpan -= 1.0
            updateCustomAirplane()
        }
        airplaneSpanPlus.setOnClickListener {
            airplaneSpan += 1.0
            updateCustomAirplane()
        }

        airplaneAreaMinus.setOnClickListener {
            airplaneArea -= 1.0
            updateCustomAirplane()
        }
        airplaneAreaPlus.setOnClickListener {
            airplaneArea += 1.0
            updateCustomAirplane()
        }

        airplaneEMinus.setOnClickListener {
            airplaneE -= 0.1
            updateCustomAirplane()
        }
        airplaneEPlus.setOnClickListener {
            airplaneE += 0.1
            updateCustomAirplane()
        }

        airplaneCd0Minus.setOnClickListener {
            airplaneCd0 -= 0.01
            updateCustomAirplane()
        }
        airplaneCd0Plus.setOnClickListener {
            airplaneCd0 += 0.01
            updateCustomAirplane()
        }

        airplanePowerMinus.setOnClickListener {
            airplanePower -= 50.0
            updateCustomAirplane()
        }
        airplanePowerPlus.setOnClickListener {
            airplanePower += 50.0
            updateCustomAirplane()
        }

        airplaneMinClMinus.setOnClickListener {
            airplaneMinCl -= 0.005
            updateCustomAirplane()
        }
        airplaneMinClPlus.setOnClickListener {
            airplaneMinCl += 0.005
            updateCustomAirplane()
        }

        airplaneMaxClMinus.setOnClickListener {
            airplaneMaxCl -= 0.1
            updateCustomAirplane()
        }
        airplaneMaxClPlus.setOnClickListener {
            airplaneMaxCl += 0.1
            updateCustomAirplane()
        }

        // Initialize custom airplane polar with default values
        updateCustomAirplane()
    }

    private fun updateCustomAirplane() {
        if (isUpdating) return

        // Calculate total mass
        val fuelWt = 1019.0 * airplaneFuelPct
        val passengerWt = airplaneNumPeople * airplanePersonWt
        val totalMass = airplaneDryWt + fuelWt + passengerWt

        // Calculate AR
        val ar = if (airplaneArea > 0) airplaneSpan * airplaneSpan / airplaneArea else 0.0

        // Convert to standard form: polarSlope = 1/(PI*ar*e)
        val polarSlope = if (ar > 0 && airplaneE > 0) {
            1.0 / (Math.PI * ar * airplaneE)
        } else {
            0.0
        }

        // Update displays
        airplaneDryWtValue.text = String.format("%.0f", airplaneDryWt)
        airplaneFuelPctValue.text = String.format("%.1f", airplaneFuelPct)
        airplaneNumPeopleValue.text = airplaneNumPeople.toString()
        airplanePersonWtValue.text = String.format("%.0f", airplanePersonWt)
        airplaneTotalMass.text = String.format("Total: %.0f kg", totalMass)

        airplaneSpanValue.text = String.format("%.1f", airplaneSpan)
        airplaneAreaValue.text = String.format("%.1f", airplaneArea)
        airplaneArValue.text = String.format("%.1f", ar)
        airplaneEValue.text = String.format("%.2f", airplaneE)
        airplaneCd0Value.text = String.format("%.3f", airplaneCd0)
        airplanePowerValue.text = String.format("%.0f", airplanePower)
        airplaneMinClValue.text = String.format("%.3f", airplaneMinCl)
        airplaneMaxClValue.text = String.format("%.2f", airplaneMaxCl)

        // Update Polars with custom airplane using customSampleAndCache
        val polarCl0 = 0.0    // CL for minimum drag (typically near zero for symmetric profiles)

        Polars.instance.setCustomAirplanePolar(
            totalMass, airplaneArea, polarSlope, polarCl0, airplaneCd0, airplaneMinCl, airplaneMaxCl
        )
        // Note: Don't call initialize() here as setCustomAirplanePolar() already creates the cache
        // and initialize() would overwrite it with the standard sampleAndCache method
    }

    private fun adjustWingsuitArea(percentChange: Double) {
        val polar = Polars.instance.wingsuitPolar
        val currentArea = Polars.instance.wingsuitArea ?: polar.s
        val newArea = currentArea * (1.0 + percentChange)
        Polars.instance.setWingsuitParameters(newArea, Polars.instance.wingsuitMass)
        Polars.instance.initialize(1.225) // Immediately reinitialize
        updateWingsuitFields()
    }

    private fun adjustWingsuitMass(lbsChange: Double) {
        val polar = Polars.instance.wingsuitPolar
        val currentMassKg = Polars.instance.wingsuitMass ?: polar.m
        val currentMassLbs = currentMassKg * KG_TO_LBS
        val newMassLbs = currentMassLbs + lbsChange
        val newMassKg = newMassLbs * LBS_TO_KG
        Polars.instance.setWingsuitParameters(Polars.instance.wingsuitArea, newMassKg)
        Polars.instance.initialize(1.225)
        updateWingsuitFields()
    }

    private fun adjustCanopyArea(sqFtChange: Double) {
        val polar = Polars.instance.canopyPolar
        val currentAreaM2 = Polars.instance.canopyArea ?: polar.s
        val currentAreaSqFt = currentAreaM2 * SQ_M_TO_SQ_FT
        val newAreaSqFt = currentAreaSqFt + sqFtChange
        val newAreaM2 = newAreaSqFt * SQ_FT_TO_SQ_M
        Polars.instance.setCanopyParameters(newAreaM2, Polars.instance.canopyMass)
        Polars.instance.initialize(1.225)
        updateCanopyFields()
    }

    private fun adjustCanopyMass(lbsChange: Double) {
        val polar = Polars.instance.canopyPolar
        val currentMassKg = Polars.instance.canopyMass ?: polar.m
        val currentMassLbs = currentMassKg * KG_TO_LBS
        val newMassLbs = currentMassLbs + lbsChange
        val newMassKg = newMassLbs * LBS_TO_KG
        Polars.instance.setCanopyParameters(Polars.instance.canopyArea, newMassKg)
        Polars.instance.initialize(1.225)
        updateCanopyFields()
    }

    private fun adjustAirplaneMass(kgChange: Double) {
        val polar = Polars.instance.airplanePolar
        val currentMass = Polars.instance.airplaneMass ?: polar.m
        val newMass = currentMass + kgChange
        Polars.instance.setAirplaneParameters(Polars.instance.airplaneArea, newMass)
        Polars.instance.initialize(1.225)
        updateAirplaneFields()
    }

    private fun updateWingsuitFields() {
        isUpdating = true
        val polar = Polars.instance.wingsuitPolar

        // Area in m²
        val area = Polars.instance.wingsuitArea ?: polar.s
        wingsuitAreaValue.text = String.format("%.2f", area)

        // Mass in lbs (display), kg (converted display)
        val massKg = Polars.instance.wingsuitMass ?: polar.m
        val massLbs = massKg * KG_TO_LBS
        wingsuitMassValue.text = String.format("%.1f", massLbs)
        wingsuitMassKg.text = String.format("(%.1f kg)", massKg)

        isUpdating = false
    }

    private fun updateCanopyFields() {
        isUpdating = true
        val polar = Polars.instance.canopyPolar

        // Area in sq ft (display), m² (converted display)
        val areaM2 = Polars.instance.canopyArea ?: polar.s
        val areaSqFt = areaM2 * SQ_M_TO_SQ_FT
        canopyAreaValue.text = String.format("%.1f", areaSqFt)
        canopyAreaM2.text = String.format("(%.2f m²)", areaM2)

        // Mass in lbs (display), kg (converted display)
        val massKg = Polars.instance.canopyMass ?: polar.m
        val massLbs = massKg * KG_TO_LBS
        canopyMassValue.text = String.format("%.1f", massLbs)
        canopyMassKg.text = String.format("(%.1f kg)", massKg)

        isUpdating = false
    }

    private fun updateAirplaneFields() {
        isUpdating = true
        val polar = Polars.instance.airplanePolar

        // Check if custom airplane is selected
        if (polar.name == "Custom Airplane" && airplaneCustomParams.visibility == View.VISIBLE) {
            // Display calculated total mass
            val fuelWt = 1019.0 * airplaneFuelPct
            val passengerWt = airplaneNumPeople * airplanePersonWt
            val totalMass = airplaneDryWt + fuelWt + passengerWt
            airplaneMassValue.text = String.format("%.0f", totalMass)
        } else {
            // Display standard mass
            val mass = Polars.instance.airplaneMass ?: polar.m
            airplaneMassValue.text = String.format("%.0f", mass)
        }

        isUpdating = false
    }

    private fun setupCustomWingsuitControls() {
        // Wire up +/- buttons for custom parameters (using airplane adjustment magnitudes)
        wingsuitPolarMinDragMinus.setOnClickListener {
            wingsuitPolarMinDrag = (wingsuitPolarMinDrag ?: 0.0) - 0.01
            updateCustomWingsuit()
        }
        wingsuitPolarMinDragPlus.setOnClickListener {
            wingsuitPolarMinDrag = (wingsuitPolarMinDrag ?: 0.0) + 0.01
            updateCustomWingsuit()
        }

        wingsuitPolarCloMinus.setOnClickListener {
            wingsuitPolarClo = (wingsuitPolarClo ?: 0.0) - 0.1
            updateCustomWingsuit()
        }
        wingsuitPolarCloPlus.setOnClickListener {
            wingsuitPolarClo = (wingsuitPolarClo ?: 0.0) + 0.1
            updateCustomWingsuit()
        }

        wingsuitPolarSlopeMinus.setOnClickListener {
            wingsuitPolarSlope = (wingsuitPolarSlope ?: 0.0) - 0.01
            updateCustomWingsuit()
        }
        wingsuitPolarSlopePlus.setOnClickListener {
            wingsuitPolarSlope = (wingsuitPolarSlope ?: 0.0) + 0.01
            updateCustomWingsuit()
        }

        wingsuitRangeMinClMinus.setOnClickListener {
            wingsuitRangeMinCl = (wingsuitRangeMinCl ?: 0.0) - 0.005
            updateCustomWingsuit()
        }
        wingsuitRangeMinClPlus.setOnClickListener {
            wingsuitRangeMinCl = (wingsuitRangeMinCl ?: 0.0) + 0.005
            updateCustomWingsuit()
        }

        wingsuitRangeMaxClMinus.setOnClickListener {
            wingsuitRangeMaxCl = (wingsuitRangeMaxCl ?: 0.0) - 0.1
            updateCustomWingsuit()
        }
        wingsuitRangeMaxClPlus.setOnClickListener {
            wingsuitRangeMaxCl = (wingsuitRangeMaxCl ?: 0.0) + 0.1
            updateCustomWingsuit()
        }
    }

    private fun setupCustomCanopyControls() {
        // Wire up +/- buttons for custom parameters (using airplane adjustment magnitudes)
        canopyPolarMinDragMinus.setOnClickListener {
            canopyPolarMinDrag = (canopyPolarMinDrag ?: 0.0) - 0.01
            updateCustomCanopy()
        }
        canopyPolarMinDragPlus.setOnClickListener {
            canopyPolarMinDrag = (canopyPolarMinDrag ?: 0.0) + 0.01
            updateCustomCanopy()
        }

        canopyPolarCloMinus.setOnClickListener {
            canopyPolarClo = (canopyPolarClo ?: 0.0) - 0.1
            updateCustomCanopy()
        }
        canopyPolarCloPlus.setOnClickListener {
            canopyPolarClo = (canopyPolarClo ?: 0.0) + 0.1
            updateCustomCanopy()
        }

        canopyPolarSlopeMinus.setOnClickListener {
            canopyPolarSlope = (canopyPolarSlope ?: 0.0) - 0.01
            updateCustomCanopy()
        }
        canopyPolarSlopePlus.setOnClickListener {
            canopyPolarSlope = (canopyPolarSlope ?: 0.0) + 0.01
            updateCustomCanopy()
        }

        canopyRangeMinClMinus.setOnClickListener {
            canopyRangeMinCl = (canopyRangeMinCl ?: 0.0) - 0.005
            updateCustomCanopy()
        }
        canopyRangeMinClPlus.setOnClickListener {
            canopyRangeMinCl = (canopyRangeMinCl ?: 0.0) + 0.005
            updateCustomCanopy()
        }

        canopyRangeMaxClMinus.setOnClickListener {
            canopyRangeMaxCl = (canopyRangeMaxCl ?: 0.0) - 0.1
            updateCustomCanopy()
        }
        canopyRangeMaxClPlus.setOnClickListener {
            canopyRangeMaxCl = (canopyRangeMaxCl ?: 0.0) + 0.1
            updateCustomCanopy()
        }
    }

    private fun updateCustomWingsuit() {
        if (isUpdating) return

        val polar = Polars.instance.wingsuitPolar
        val area = Polars.instance.wingsuitArea ?: polar.s
        val massKg = Polars.instance.wingsuitMass ?: polar.m

        // Use custom parameters (guaranteed to be initialized by now)
        val polarMinDrag = wingsuitPolarMinDrag ?: polar.polarMinDrag
        val polarClo = wingsuitPolarClo ?: polar.polarClo
        val polarSlope = wingsuitPolarSlope ?: polar.polarSlope
        val rangeMinCl = wingsuitRangeMinCl ?: polar.rangeMinCl
        val rangeMaxCl = wingsuitRangeMaxCl ?: polar.rangeMaxCl

        // Update TextView fields with current values
        isUpdating = true
        wingsuitPolarMinDragValue.text = String.format("%.4f", polarMinDrag)
        wingsuitPolarCloValue.text = String.format("%.2f", polarClo)
        wingsuitPolarSlopeValue.text = String.format("%.4f", polarSlope)
        wingsuitRangeMinClValue.text = String.format("%.3f", rangeMinCl)
        wingsuitRangeMaxClValue.text = String.format("%.2f", rangeMaxCl)
        isUpdating = false

        // Update custom polar cache
        Polars.instance.setCustomWingsuitPolar(
            massKg, area, polarSlope, polarClo, polarMinDrag, rangeMinCl, rangeMaxCl
        )
    }

    private fun updateCustomCanopy() {
        if (isUpdating) return

        val polar = Polars.instance.canopyPolar
        val area = Polars.instance.canopyArea ?: polar.s
        val massKg = Polars.instance.canopyMass ?: polar.m

        // Use custom parameters (guaranteed to be initialized by now)
        val polarMinDrag = canopyPolarMinDrag ?: polar.polarMinDrag
        val polarClo = canopyPolarClo ?: polar.polarClo
        val polarSlope = canopyPolarSlope ?: polar.polarSlope
        val rangeMinCl = canopyRangeMinCl ?: polar.rangeMinCl
        val rangeMaxCl = canopyRangeMaxCl ?: polar.rangeMaxCl

        // Update TextView fields with current values
        isUpdating = true
        canopyPolarMinDragValue.text = String.format("%.4f", polarMinDrag)
        canopyPolarCloValue.text = String.format("%.2f", polarClo)
        canopyPolarSlopeValue.text = String.format("%.4f", polarSlope)
        canopyRangeMinClValue.text = String.format("%.3f", rangeMinCl)
        canopyRangeMaxClValue.text = String.format("%.2f", rangeMaxCl)
        isUpdating = false

        // Update custom polar cache
        Polars.instance.setCustomCanopyPolar(
            massKg, area, polarSlope, polarClo, polarMinDrag, rangeMinCl, rangeMaxCl
        )
    }
}
