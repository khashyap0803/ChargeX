package com.chargex.india.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing simulated station occupancy data.
 *
 * This table tracks the real-time (simulated) state of each charging station,
 * including the number of chargers, current occupancy, and the area profile
 * for wait time prediction.
 *
 * In a production system, this data would come from IoT sensors or OCPP.
 * For demo/research purposes, it's populated by the Monte Carlo simulation
 * in StationSimulationWorker.
 */
@Entity(tableName = "station_occupancy")
data class StationOccupancy(
    @PrimaryKey
    @ColumnInfo(name = "station_id")
    val stationId: Long,

    /** Total number of physical charging plugs at this station */
    @ColumnInfo(name = "total_plugs")
    val totalPlugs: Int,

    /** Number of plugs currently occupied (0 to totalPlugs) */
    @ColumnInfo(name = "occupied_plugs")
    val occupiedPlugs: Int,

    /** Maximum power output of the fastest charger (kW) */
    @ColumnInfo(name = "max_power_kw")
    val maxPowerKw: Double,

    /** Area profile name for WaitTimeEngine lookup (e.g., "HITEC_CITY") */
    @ColumnInfo(name = "area_profile")
    val areaProfile: String,

    /** Unix timestamp of last occupancy update */
    @ColumnInfo(name = "last_updated")
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /** Number of available (free) chargers */
    val availablePlugs: Int get() = (totalPlugs - occupiedPlugs).coerceAtLeast(0)

    /** Occupancy ratio (0.0 = empty, 1.0 = full) */
    val occupancyRatio: Double get() = if (totalPlugs > 0) occupiedPlugs.toDouble() / totalPlugs else 0.0

    /** Human-readable status */
    val statusText: String get() = when {
        occupiedPlugs >= totalPlugs -> "Full ($totalPlugs/$totalPlugs)"
        occupiedPlugs == 0 -> "Empty (0/$totalPlugs)"
        else -> "$availablePlugs of $totalPlugs available"
    }
}
