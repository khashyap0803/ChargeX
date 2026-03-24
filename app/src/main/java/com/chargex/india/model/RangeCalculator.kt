package com.chargex.india.model

import kotlin.math.pow

/**
 * Range Calculation and Energy Consumption Engine for EVs in Indian conditions.
 *
 * ## Research-Grade Energy Model:
 *
 * ### Physics-Based Energy Consumption (per km):
 *     E_total = E_rolling + E_aero + E_gradient + E_auxiliary
 *
 * Where:
 * - E_rolling = Cᵣ × m × g × d           (Rolling resistance)
 * - E_aero    = 0.5 × ρ × Cᵈ × A × v² × d  (Aerodynamic drag)
 * - E_gradient = m × g × sin(θ) × d       (Elevation change)
 * - E_auxiliary = P_ac × t                 (AC, lights, electronics)
 *
 * ### Constants (for Indian conditions):
 * - Cᵣ  = 0.01        (Rolling resistance coefficient, typical EV tires)
 * - ρ   = 1.225 kg/m³  (Air density at sea level, ~35°C)
 * - g   = 9.81 m/s²    (Gravitational acceleration)
 *
 * ### India-Specific Correction Factors:
 * - Temperature: Based on IMD (India Meteorological Department) data
 * - ARAI correction: Indian certification is ~15% optimistic vs real-world
 * - AC load: Continuous AC in 35°C+ climate draws ~1.5-2.0 kW (cars only)
 * - Traffic: Indian city traffic (avg 15-20 km/h) vs highway (80-100 km/h)
 *
 * ### References:
 * - Fiori, C., Ahn, K., & Rakha, H.A. (2016) "Power-based electric vehicle
 *   energy consumption model" — Transportation Research Part C
 * - Genikomsakis, K.N. et al. (2017) "A computationally efficient simulation
 *   model for estimating energy consumption of EVs" — Applied Energy
 * - ARAI (Automotive Research Association of India) — Testing Standards
 */
object RangeCalculator {

    // ═══════════════════════════════════════════════════════════════
    // PHYSICAL CONSTANTS
    // ═══════════════════════════════════════════════════════════════

    /** Rolling resistance coefficient (dimensionless) — typical low-rolling-resistance EV tires */
    private const val ROLLING_RESISTANCE_COEFF = 0.01

    /** Air density at sea level (kg/m³) — adjusted for typical Indian 35°C */
    private const val AIR_DENSITY = 1.146  // ρ at 35°C (lower than 1.225 at 15°C)

    /** Gravitational acceleration (m/s²) */
    private const val GRAVITY = 9.81

    /** AC compressor power draw (kW) — continuous operation in Indian heat (cars/SUVs only) */
    private const val AC_POWER_DRAW_KW = 1.8

    /** Regenerative braking efficiency — fraction of kinetic energy recovered */
    private const val REGEN_EFFICIENCY = 0.65

    /** Drivetrain efficiency — motor + inverter + transmission losses */
    private const val DRIVETRAIN_EFFICIENCY = 0.88

    // ═══════════════════════════════════════════════════════════════
    // CORRECTION FACTORS (India-Specific)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Temperature correction factor for battery performance.
     *
     * Li-ion batteries have an optimal operating range of 20-25°C.
     * In Indian conditions (25-45°C), the Battery Management System (BMS)
     * must actively cool the pack, drawing additional power.
     *
     * Based on research by: Wang et al. (2016) "Impact of ambient temperature
     * on EV energy consumption" — Applied Energy
     *
     * @param temperatureC Ambient temperature in Celsius
     * @return Multiplier > 1.0 means increased consumption
     */
    fun temperatureCorrectionFactor(temperatureC: Double): Double {
        return when {
            temperatureC > 45 -> 1.18   // Extreme: battery thermal throttling
            temperatureC > 40 -> 1.12   // Very hot (peak Indian summer)
            temperatureC > 35 -> 1.08   // Hot (common May-June)
            temperatureC > 30 -> 1.04   // Warm (common March-April, Sept-Oct)
            temperatureC > 25 -> 1.01   // Mild (ideal for most of winter)
            temperatureC > 20 -> 1.00   // Optimal
            temperatureC > 15 -> 1.03   // Cool (North India winter)
            temperatureC > 5  -> 1.10   // Cold (hill stations)
            else -> 1.20                // Very cold (Ladakh, Kashmir)
        }
    }

    /**
     * Driving mode correction factor.
     *
     * City driving in India: Average 15-20 km/h with frequent stops.
     * EVs benefit from regenerative braking in stop-and-go traffic.
     *
     * Highway: Average 80-100 km/h on Indian expressways.
     * Aerodynamic drag dominates at high speeds (∝ v²).
     *
     * @param mode "city", "highway", or "mixed"
     * @return Multiplier (< 1.0 for city due to regen, > 1.0 for highway due to drag)
     */
    fun drivingModeFactor(mode: String): Double {
        return when (mode.lowercase()) {
            "city" -> 0.90       // Regen braking compensates for frequent stops
            "highway" -> 1.15    // Aerodynamic drag at 80-100 km/h
            "mixed" -> 1.00      // Balanced
            else -> 1.00
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // CORE: BASIC RANGE CALCULATION (Original Method)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculate the estimated range in km given vehicle specs and conditions.
     *
     * Uses the simplified correction-factor model (suitable for UI display).
     *
     * @param vehicle The vehicle profile
     * @param batteryPercent Current battery level (0-100)
     * @param acOn Whether AC is running (significant in Indian heat)
     * @param drivingMode "city", "highway", or "mixed"
     * @param temperatureC Ambient temperature in Celsius
     * @return Estimated remaining range in km
     */
    fun calculateRange(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        acOn: Boolean = true,
        drivingMode: String = "mixed",
        temperatureC: Double = 35.0
    ): Double {
        val availableEnergy = vehicle.batteryCapacityKwh * (batteryPercent / 100.0)
        var efficiencyPerKm = vehicle.efficiencyKwhPer100Km / 100.0

        // Apply correction factors
        efficiencyPerKm *= drivingModeFactor(drivingMode)
        efficiencyPerKm *= temperatureCorrectionFactor(temperatureC)
        // Only apply AC penalty for vehicles with AC (not scooters)
        if (acOn && vehicle.hasAC) efficiencyPerKm *= 1.10
        // Only apply ARAI penalty for non-scooters (scooter efficiency already real-world)
        if (vehicle.vehicleType != VehicleType.SCOOTER) {
            efficiencyPerKm *= 1.15  // ARAI/WLTP real-world correction
        }

        return if (efficiencyPerKm > 0) {
            availableEnergy / efficiencyPerKm
        } else 0.0
    }

    // ═══════════════════════════════════════════════════════════════
    // ADVANCED: PHYSICS-BASED ENERGY CONSUMPTION MODEL
    // ═══════════════════════════════════════════════════════════════

    /**
     * Calculates energy consumption for a specific route using physics equations.
     *
     * This model uses per-vehicle parameters (mass, frontal area, drag coefficient)
     * from the VehicleProfile for accurate computation across vehicle types
     * (scooters, cars, SUVs).
     *
     * ### Energy Equation:
     *     E_total = (E_rolling + E_aero + E_aux) / η_drivetrain - E_regen
     *
     * @param vehicle Vehicle profile (provides mass, frontal area, drag, type)
     * @param distanceKm Route distance in km
     * @param avgSpeedKmh Average speed on route (from routing API: distance/duration)
     * @param temperatureC Ambient temperature
     * @param acOn AC status (ignored for scooters)
     * @param gradientPercent Average road gradient (positive = uphill)
     * @return EnergyConsumptionResult with breakdown
     */
    /**
     * Heuristic fallback for ambient temperature when offline.
     * Checks the device calendar month to safely estimate the temperature.
     * Assumes a conservative high temperature to prevent range overestimation.
     */
    fun getOfflineHeuristicTemperature(): Double {
        val month = java.util.Calendar.getInstance().get(java.util.Calendar.MONTH)
        return when (month) {
            java.util.Calendar.APRIL, java.util.Calendar.MAY, java.util.Calendar.JUNE -> 40.0 // Peak Indian Summer
            java.util.Calendar.JULY, java.util.Calendar.AUGUST, java.util.Calendar.SEPTEMBER -> 32.0 // Monsoon/Humid
            java.util.Calendar.DECEMBER, java.util.Calendar.JANUARY -> 20.0 // Winter
            else -> 30.0 // Default Warm
        }
    }

    /**
     * Heuristic fallback for traffic duration multiplier when offline.
     * Checks the device clock to estimate if it's currently rush hour.
     */
    fun getOfflineTrafficMultiplier(): Double {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        return when {
            hour in 8..11 -> 2.0 // Morning Rush Hour (2x time)
            hour in 17..20 -> 2.5 // Evening Rush Hour (2.5x time)
            hour in 12..16 -> 1.5 // Mid-day traffic
            else -> 1.0 // Late night / early morning
        }
    }

    fun calculateEnergyConsumption(
        vehicle: VehicleProfile,
        distanceKm: Double,
        avgSpeedKmh: Double,
        temperatureC: Double = 35.0,
        acOn: Boolean = true,
        gradientPercent: Double = 0.0,
        trafficMultiplier: Double = 1.0,
        isOffline: Boolean = false // Forces heuristic fallbacks
    ): EnergyConsumptionResult {
        val finalTempC = if (isOffline) getOfflineHeuristicTemperature() else temperatureC
        val finalTrafficMultiplier = if (isOffline) getOfflineTrafficMultiplier() else trafficMultiplier

        val distanceM = distanceKm * 1000.0
        val avgSpeedMs = avgSpeedKmh / 3.6  // Convert km/h to m/s
        
        // Travel time is heavily impacted by the traffic multiplier
        val travelTimeHours = if (avgSpeedKmh > 0) (distanceKm / avgSpeedKmh) * finalTrafficMultiplier else 0.0
        val gradientRad = Math.atan(gradientPercent / 100.0) // Radians

        // Use vehicle-specific mass (add rider weight for scooters)
        val vehicleMassKg = when (vehicle.vehicleType) {
            VehicleType.SCOOTER -> vehicle.curbWeightKg + 75.0  // scooter + rider
            else -> vehicle.curbWeightKg + 80.0  // car + driver
        }

        // Use vehicle-specific aerodynamic parameters
        val dragCoeff = vehicle.dragCoefficient
        val frontalArea = vehicle.frontalAreaM2

        // 1. Rolling Resistance Energy (kWh)
        val rollingForce = ROLLING_RESISTANCE_COEFF * vehicleMassKg * GRAVITY
        val rollingEnergyKwh = (rollingForce * distanceM) / 3_600_000.0

        // 2. Aerodynamic Drag Energy (kWh) — using vehicle-specific Cd and A
        val airDensityAdjusted = AIR_DENSITY * temperatureCorrectionForAirDensity(finalTempC)
        val aeroForce = 0.5 * airDensityAdjusted * dragCoeff * frontalArea * avgSpeedMs.pow(2)
        val aeroEnergyKwh = (aeroForce * distanceM) / 3_600_000.0

        // 3. Gradient Energy (kWh) — positive = uphill = extra energy
        val gradientForce = vehicleMassKg * GRAVITY * Math.sin(gradientRad)
        val gradientEnergyKwh = (gradientForce * distanceM) / 3_600_000.0

        // 4. Auxiliary Energy (kWh) — AC + electronics
        // Scooters: no AC, minimal electronics (~0.05 kW for lights/controller)
        // Cars/SUVs: AC (~1.8 kW) + infotainment/BMS (~0.3 kW)
        val acPower = if (acOn && vehicle.hasAC) AC_POWER_DRAW_KW else 0.0
        val electronicsLoad = when (vehicle.vehicleType) {
            VehicleType.SCOOTER -> 0.05  // Controller, lights
            else -> 0.3                   // Infotainment, lights, BMS
        }
        val auxEnergyKwh = (acPower + electronicsLoad) * travelTimeHours

        // 5. Regenerative Braking Recovery (kWh) — based on kinetic stops
        val numStops = when {
            avgSpeedKmh < 30 -> distanceKm * 2.0   // 2 stops per km in city
            avgSpeedKmh < 60 -> distanceKm * 0.5   // 1 stop per 2 km in suburbs
            else -> distanceKm * 0.1               // Minimal stops on highway
        } * finalTrafficMultiplier
        val kineticEnergyPerStopKwh = (0.5 * vehicleMassKg * avgSpeedMs.pow(2)) / 3_600_000.0
        val regenRecovery = (kineticEnergyPerStopKwh * numStops) * REGEN_EFFICIENCY

        // Total Energy = (movement + aux) / drivetrain_efficiency - regen
        val grossEnergy = (rollingEnergyKwh + aeroEnergyKwh + gradientEnergyKwh + auxEnergyKwh)
        val totalEnergy = (grossEnergy / DRIVETRAIN_EFFICIENCY) - regenRecovery

        // Apply temperature penalty to battery
        val tempPenalty = temperatureCorrectionFactor(finalTempC)
        val finalEnergy = totalEnergy * tempPenalty

        return EnergyConsumptionResult(
            totalEnergyKwh = finalEnergy.coerceAtLeast(0.0),
            rollingResistanceKwh = rollingEnergyKwh,
            aerodynamicDragKwh = aeroEnergyKwh,
            gradientEnergyKwh = gradientEnergyKwh,
            auxiliaryEnergyKwh = auxEnergyKwh,
            regenRecoveryKwh = regenRecovery,
            efficiencyKwhPerKm = if (distanceKm > 0) finalEnergy / distanceKm else 0.0,
            avgSpeedKmh = avgSpeedKmh
        )
    }

    /**
     * Air density correction for temperature (ideal gas law approximation).
     * ρ(T) = ρ₀ × (T₀ / T) where T is in Kelvin
     */
    private fun temperatureCorrectionForAirDensity(temperatureC: Double): Double {
        val t0 = 288.15  // 15°C in Kelvin (standard conditions)
        val t = temperatureC + 273.15
        return t0 / t
    }

    // ═══════════════════════════════════════════════════════════════
    // ENERGY-AWARE ROUTE FEASIBILITY
    // ═══════════════════════════════════════════════════════════════

    /**
     * Checks if a route is feasible using actual road distance and speed.
     *
     * This is the key upgrade from Haversine: instead of checking straight-line
     * distance, we use the actual route distance from Google Directions / OSRM
     * and account for average speed (traffic penalty).
     *
     * @param vehicle Vehicle profile
     * @param batteryPercent Current battery level
     * @param routeDistanceKm Actual road distance (from routing API, NOT Haversine)
     * @param routeDurationMinutes Actual travel time (from routing API)
     * @param temperatureC Ambient temperature
     * @param acOn AC status (ignored for scooters)
     * @param safetyMargin Fraction of battery to reserve (default: 10%)
     * @return RouteFeasibilityResult with feasibility status and energy breakdown
     */
    fun isRouteFeasible(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        routeDistanceKm: Double,
        routeDurationMinutes: Double,
        temperatureC: Double = 35.0,
        acOn: Boolean = true,
        safetyMargin: Double = 0.10,
        trafficMultiplier: Double = 1.0
    ): RouteFeasibilityResult {
        val avgSpeedKmh = if (routeDurationMinutes > 0) {
            (routeDistanceKm / routeDurationMinutes) * 60.0
        } else 40.0  // default city speed

        val energyResult = calculateEnergyConsumption(
            vehicle = vehicle,
            distanceKm = routeDistanceKm,
            avgSpeedKmh = avgSpeedKmh,
            temperatureC = temperatureC,
            acOn = acOn,
            trafficMultiplier = trafficMultiplier
        )

        val availableEnergy = vehicle.batteryCapacityKwh * (batteryPercent / 100.0)
        val reserveEnergy = vehicle.batteryCapacityKwh * safetyMargin
        val usableEnergy = availableEnergy - reserveEnergy

        val isFeasible = energyResult.totalEnergyKwh <= usableEnergy

        val arrivalBatteryPercent = if (vehicle.batteryCapacityKwh > 0) {
            ((availableEnergy - energyResult.totalEnergyKwh) / vehicle.batteryCapacityKwh * 100.0)
                .coerceIn(0.0, 100.0)
        } else 0.0

        val trafficFactor = getTrafficFactor(routeDistanceKm, routeDurationMinutes)

        return RouteFeasibilityResult(
            isFeasible = isFeasible,
            energyRequired = energyResult.totalEnergyKwh,
            energyAvailable = usableEnergy,
            arrivalBatteryPercent = arrivalBatteryPercent,
            avgSpeedKmh = avgSpeedKmh,
            trafficFactor = trafficFactor,
            energyBreakdown = energyResult
        )
    }

    /**
     * Derives a traffic penalty factor from route data.
     *
     * If average speed is very low (< 20 km/h), traffic is heavy.
     * If average speed is normal (40-60 km/h), traffic is moderate.
     * If average speed is high (> 80 km/h), it's likely highway.
     *
     * @param routeDistanceKm Road distance
     * @param routeDurationMinutes Travel time
     * @return Traffic factor description
     */
    fun getTrafficFactor(routeDistanceKm: Double, routeDurationMinutes: Double): String {
        val avgSpeed = if (routeDurationMinutes > 0) {
            (routeDistanceKm / routeDurationMinutes) * 60.0
        } else 0.0

        return when {
            avgSpeed < 15 -> "Heavy Traffic (avg ${avgSpeed.toInt()} km/h)"
            avgSpeed < 30 -> "Moderate Traffic (avg ${avgSpeed.toInt()} km/h)"
            avgSpeed < 60 -> "Light Traffic (avg ${avgSpeed.toInt()} km/h)"
            avgSpeed < 100 -> "Highway Flow (avg ${avgSpeed.toInt()} km/h)"
            else -> "Expressway (avg ${avgSpeed.toInt()} km/h)"
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // EXISTING CONVENIENCE METHODS (Backwards Compatible)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Check if a station at a given distance is reachable with current battery.
     * Uses the simplified model (Haversine distance).
     */
    fun isStationReachable(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        distanceKm: Double,
        acOn: Boolean = true,
        drivingMode: String = "mixed",
        temperatureC: Double = 35.0
    ): Boolean {
        val safeRange = calculateRange(vehicle, batteryPercent, acOn, drivingMode, temperatureC) * 0.90
        return distanceKm <= safeRange
    }

    /**
     * Calculate what battery % will remain after reaching a station.
     */
    fun remainingBatteryPercent(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        distanceKm: Double,
        acOn: Boolean = true,
        drivingMode: String = "mixed",
        temperatureC: Double = 35.0
    ): Double {
        val range = calculateRange(vehicle, batteryPercent, acOn, drivingMode, temperatureC)
        if (range <= 0) return 0.0
        val usedPercent = (distanceKm / range) * batteryPercent
        return (batteryPercent - usedPercent).coerceAtLeast(0.0)
    }

    /**
     * Estimates arrival battery using the physics-based model.
     *
     * @param vehicle Vehicle profile
     * @param batteryPercent Current battery
     * @param routeDistanceKm Actual road distance
     * @param routeDurationMinutes Travel time
     * @param temperatureC Temperature
     * @param acOn AC status
     * @return Predicted arrival SoC (State of Charge) in percent
     */
    fun estimateArrivalBattery(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        routeDistanceKm: Double,
        routeDurationMinutes: Double,
        temperatureC: Double = 35.0,
        acOn: Boolean = true
    ): Double {
        val result = isRouteFeasible(
            vehicle, batteryPercent, routeDistanceKm, routeDurationMinutes,
            temperatureC, acOn, safetyMargin = 0.0
        )
        return result.arrivalBatteryPercent
    }
}

// ═══════════════════════════════════════════════════════════════
// DATA CLASSES
// ═══════════════════════════════════════════════════════════════

/**
 * Detailed energy consumption breakdown from the physics model.
 */
data class EnergyConsumptionResult(
    val totalEnergyKwh: Double,          // Total energy consumed
    val rollingResistanceKwh: Double,    // Tire-road friction component
    val aerodynamicDragKwh: Double,      // Air resistance component
    val gradientEnergyKwh: Double,       // Elevation change component
    val auxiliaryEnergyKwh: Double,      // AC + electronics component
    val regenRecoveryKwh: Double,        // Energy recovered via regen braking
    val efficiencyKwhPerKm: Double,      // Effective efficiency (kWh/km)
    val avgSpeedKmh: Double              // Average speed used
) {
    /** Efficiency in Wh/km (more human-readable) */
    val efficiencyWhPerKm: Double get() = efficiencyKwhPerKm * 1000

    /** Percentage of energy recovered through regenerative braking */
    val regenPercentage: Double get() {
        val gross = rollingResistanceKwh + aerodynamicDragKwh + gradientEnergyKwh + auxiliaryEnergyKwh
        return if (gross > 0) (regenRecoveryKwh / gross * 100.0) else 0.0
    }
}

/**
 * Result of energy-aware route feasibility check.
 */
data class RouteFeasibilityResult(
    val isFeasible: Boolean,             // Can the vehicle complete this route?
    val energyRequired: Double,          // Energy needed (kWh)
    val energyAvailable: Double,         // Usable energy after safety margin (kWh)
    val arrivalBatteryPercent: Double,   // Predicted SoC at destination
    val avgSpeedKmh: Double,             // Derived average speed
    val trafficFactor: String,           // Traffic condition description
    val energyBreakdown: EnergyConsumptionResult // Detailed physics breakdown
) {
    /** Energy surplus/deficit in kWh (positive = surplus) */
    val energyMargin: Double get() = energyAvailable - energyRequired

    /** Display-friendly feasibility message */
    val statusMessage: String get() = when {
        !isFeasible -> "⚠️ Insufficient battery — need ${String.format(java.util.Locale.US, "%.1f", energyRequired)} kWh, have ${String.format(java.util.Locale.US, "%.1f", energyAvailable)} kWh"
        arrivalBatteryPercent > 30 -> "✅ Comfortable — arrive with ${arrivalBatteryPercent.toInt()}% battery"
        arrivalBatteryPercent > 15 -> "⚡ Feasible — arrive with ${arrivalBatteryPercent.toInt()}% battery"
        else -> "⚠️ Tight — arrive with only ${arrivalBatteryPercent.toInt()}% battery"
    }
}
