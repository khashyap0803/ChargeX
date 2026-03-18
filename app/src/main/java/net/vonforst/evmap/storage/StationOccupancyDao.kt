package net.vonforst.evmap.storage

import androidx.room.*
import net.vonforst.evmap.model.StationOccupancy

/**
 * Data Access Object for StationOccupancy.
 * Provides CRUD operations for the simulated occupancy data.
 */
@Dao
interface StationOccupancyDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(occupancy: StationOccupancy)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateAll(occupancies: List<StationOccupancy>)

    @Query("SELECT * FROM station_occupancy WHERE station_id = :stationId")
    suspend fun getByStationId(stationId: Long): StationOccupancy?

    @Query("SELECT * FROM station_occupancy ORDER BY last_updated DESC")
    suspend fun getAll(): List<StationOccupancy>

    @Query("UPDATE station_occupancy SET occupied_plugs = :occupiedPlugs, last_updated = :timestamp WHERE station_id = :stationId")
    suspend fun updateOccupancy(stationId: Long, occupiedPlugs: Int, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM station_occupancy WHERE station_id = :stationId")
    suspend fun delete(stationId: Long)

    @Query("DELETE FROM station_occupancy")
    suspend fun deleteAll()
}
