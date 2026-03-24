package com.chargex.india.storage

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.chargex.india.api.goingelectric.GEChargeCard
import com.chargex.india.api.goingelectric.GENetwork
import com.chargex.india.api.goingelectric.GEPlug

/**
 * Minimal stub DAO for GoingElectric reference data tables.
 * Kept solely for Room database schema compatibility — GoingElectric
 * API support has been removed from ChargeX India.
 */
@Dao
interface GEReferenceDataDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlugs(vararg plugs: GEPlug)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworks(vararg networks: GENetwork)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChargeCards(vararg chargeCards: GEChargeCard)

    @Query("SELECT * FROM GEPlug")
    suspend fun getPlugs(): List<GEPlug>

    @Query("SELECT * FROM GENetwork")
    suspend fun getNetworks(): List<GENetwork>

    @Query("SELECT * FROM GEChargeCard")
    suspend fun getChargeCards(): List<GEChargeCard>

    @Query("DELETE FROM GEPlug")
    suspend fun deleteAllPlugs()

    @Query("DELETE FROM GENetwork")
    suspend fun deleteAllNetworks()

    @Query("DELETE FROM GEChargeCard")
    suspend fun deleteAllChargeCards()
}
