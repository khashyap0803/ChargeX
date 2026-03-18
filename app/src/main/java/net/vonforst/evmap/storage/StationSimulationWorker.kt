package net.vonforst.evmap.storage

import android.content.Context
import android.util.Log
import androidx.work.*
import net.vonforst.evmap.model.StationOccupancy
import net.vonforst.evmap.model.WaitTimeEngine
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Monte Carlo Simulation Worker for station occupancy.
 *
 * ## Simulation Method:
 * Uses Poisson process-based Monte Carlo simulation to generate
 * realistic occupancy changes at each station over time.
 *
 * ### Algorithm (per station, per cycle):
 * 1. Get current hour → determine λ (arrival rate) from area profile
 * 2. Simulate arrivals: For each time slot, draw from Poisson(λ)
 * 3. Simulate departures: Each occupied charger has probability = 1/T_avg of finishing
 * 4. Update occupancy: new_occupied = current + arrivals - departures
 * 5. Clamp to [0, totalPlugs]
 *
 * ### Monte Carlo aspect:
 * Each simulation run uses Random.nextDouble() to introduce stochastic variation,
 * meaning the same station at the same time will show slightly different occupancy
 * each run — mimicking real-world randomness.
 *
 * Runs every 10 minutes via WorkManager PeriodicWorkRequest.
 */
class StationSimulationWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "StationSimWorker"
        private const val WORK_NAME = "station_simulation"

        /**
         * Schedules the periodic simulation worker.
         * Should be called from EvMapApplication.onCreate()
         */
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<StationSimulationWorker>(
                15, TimeUnit.MINUTES  // Minimum for WorkManager
            ).setConstraints(constraints)
             .setInitialDelay(1, TimeUnit.MINUTES) // Start after 1 min
             .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Station simulation worker scheduled")
        }

        /**
         * Seeds initial occupancy data for demo stations.
         * Call this once to populate the database with sample stations.
         */
        suspend fun seedDemoData(dao: StationOccupancyDao) {
            val demoStations = listOf(
                StationOccupancy(1001, 4, 2, 50.0, "HITEC_CITY"),
                StationOccupancy(1002, 6, 3, 150.0, "HITEC_CITY"),
                StationOccupancy(1003, 3, 1, 22.0, "GACHIBOWLI"),
                StationOccupancy(1004, 8, 5, 50.0, "GACHIBOWLI"),
                StationOccupancy(1005, 2, 0, 50.0, "SHAMSHABAD"),
                StationOccupancy(1006, 4, 2, 50.0, "SECUNDERABAD"),
                StationOccupancy(1007, 3, 3, 22.0, "KUKATPALLY"),
                StationOccupancy(1008, 5, 1, 150.0, "BANJARA_HILLS"),
                StationOccupancy(1009, 2, 2, 50.0, "LB_NAGAR"),
                StationOccupancy(1010, 4, 0, 50.0, "MIYAPUR")
            )
            dao.insertOrUpdateAll(demoStations)
            Log.d(TAG, "Seeded ${demoStations.size} demo stations")
        }
    }

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Running Monte Carlo simulation cycle")

            val db = AppDatabase.getInstance(applicationContext)
            val dao = db.stationOccupancyDao()
            val stations = dao.getAll()

            if (stations.isEmpty()) {
                // Seed demo data if no stations exist yet
                seedDemoData(dao)
                return Result.success()
            }

            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

            for (station in stations) {
                val newOccupancy = simulateStation(station, currentHour)
                dao.updateOccupancy(
                    stationId = station.stationId,
                    occupiedPlugs = newOccupancy
                )
            }

            Log.d(TAG, "Simulation complete: updated ${stations.size} stations")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Simulation failed", e)
            Result.retry()
        }
    }

    /**
     * Simulates occupancy change for a single station using Monte Carlo method.
     *
     * @param station Current station state
     * @param currentHour Hour of day (0-23)
     * @return New occupied plug count
     */
    private fun simulateStation(station: StationOccupancy, currentHour: Int): Int {
        val areaProfile = try {
            WaitTimeEngine.AreaProfile.valueOf(station.areaProfile)
        } catch (e: Exception) {
            WaitTimeEngine.AreaProfile.DEFAULT
        }

        // Get arrival rate λ for current area and time
        val lambda = WaitTimeEngine.getArrivalRate(areaProfile, currentHour)

        // Get average service time for this charger type
        val avgServiceTime = WaitTimeEngine.getServiceTimeMinutes(station.maxPowerKw)

        // --- Monte Carlo: Simulate arrivals ---
        // Draw from Poisson(λ × Δt) where Δt = simulation interval in hours
        // For 15-minute intervals: Δt = 0.25
        val expectedArrivals = lambda * 0.25  // arrivals in 15 minutes
        var arrivals = 0
        // Approximate Poisson draw using random threshold
        for (i in 0 until (expectedArrivals * 2 + 1).toInt()) {
            if (Random.nextDouble() < (expectedArrivals / (expectedArrivals * 2 + 1))) {
                arrivals++
            }
        }

        // --- Monte Carlo: Simulate departures ---
        // Each occupied charger has probability P_depart = Δt / T_service
        val departProbability = (15.0 / avgServiceTime).coerceIn(0.0, 1.0)
        var departures = 0
        for (i in 0 until station.occupiedPlugs) {
            if (Random.nextDouble() < departProbability) {
                departures++
            }
        }

        // Update occupancy
        val newOccupied = (station.occupiedPlugs + arrivals - departures)
            .coerceIn(0, station.totalPlugs)

        Log.d(TAG, "Station ${station.stationId}: " +
              "λ=${"%.2f".format(lambda)}, " +
              "+$arrivals arrivals, -$departures departures, " +
              "occupancy: ${station.occupiedPlugs}→$newOccupied/${station.totalPlugs}")

        return newOccupied
    }
}
