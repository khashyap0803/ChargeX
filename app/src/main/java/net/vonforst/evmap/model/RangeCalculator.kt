package net.vonforst.evmap.model

/**
 * Range calculation engine for EVs in Indian conditions.
 * Applies real-world correction factors for temperature, AC,
 * driving style, and road conditions.
 */
object RangeCalculator {

    /**
     * Calculate the estimated range in km given vehicle specs and conditions.
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
        // Available energy in kWh
        val availableEnergy = vehicle.batteryCapacityKwh * (batteryPercent / 100.0)

        // Base efficiency (kWh per km)
        var efficiencyPerKm = vehicle.efficiencyKwhPer100Km / 100.0

        // Driving mode correction
        efficiencyPerKm *= when (drivingMode) {
            "city" -> 0.90    // Stop-start traffic is actually more efficient for EVs (regen)
            "highway" -> 1.15 // Higher speeds = more drag
            else -> 1.0       // Mixed
        }

        // Temperature penalty (Indian climate: usually 25-45°C)
        // Battery and cabin cooling both draw power
        efficiencyPerKm *= when {
            temperatureC > 40 -> 1.12  // Extreme heat
            temperatureC > 35 -> 1.08  // Hot
            temperatureC > 28 -> 1.03  // Warm
            temperatureC < 15 -> 1.10  // Cold (rare in most of India)
            else -> 1.0                // Ideal range
        }

        // AC penalty (very common in India)
        if (acOn) {
            efficiencyPerKm *= 1.10  // ~10% penalty for AC
        }

        // Real-world correction: ARAI/WLTP ratings are optimistic
        // Apply a 15% haircut by default
        efficiencyPerKm *= 1.15

        return if (efficiencyPerKm > 0) {
            availableEnergy / efficiencyPerKm
        } else {
            0.0
        }
    }

    /**
     * Check if a station at a given distance is reachable with current battery.
     */
    fun isStationReachable(
        vehicle: VehicleProfile,
        batteryPercent: Double,
        distanceKm: Double,
        acOn: Boolean = true,
        drivingMode: String = "mixed",
        temperatureC: Double = 35.0
    ): Boolean {
        // Use only 90% of calculated range as safety margin
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
}
