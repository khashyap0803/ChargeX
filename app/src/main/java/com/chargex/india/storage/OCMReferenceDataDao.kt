package com.chargex.india.storage

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.room.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.chargex.india.api.openchargemap.*
import com.chargex.india.viewmodel.Status
import java.time.Duration
import java.time.Instant

@Dao
abstract class OCMReferenceDataDao {
    // CONNECTION TYPES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg connectionTypes: OCMConnectionType)

    @Query("DELETE FROM ocmconnectiontype")
    abstract fun deleteAllConnectionTypes()

    @Transaction
    open suspend fun updateConnectionTypes(connectionTypes: List<OCMConnectionType>) {
        deleteAllConnectionTypes()
        for (connectionType in connectionTypes) {
            insert(connectionType)
        }
    }

    @Query("SELECT * FROM ocmconnectiontype")
    abstract fun getAllConnectionTypes(): LiveData<List<OCMConnectionType>>

    @Query("SELECT * FROM ocmconnectiontype")
    abstract suspend fun getAllConnectionTypesAsync(): List<OCMConnectionType>

    // COUNTRIES
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg countries: OCMCountry)

    @Query("DELETE FROM ocmcountry")
    abstract fun deleteAllCountries()

    @Transaction
    open suspend fun updateCountries(countries: List<OCMCountry>) {
        deleteAllCountries()
        for (country in countries) {
            insert(country)
        }
    }

    @Query("SELECT * FROM ocmcountry")
    abstract fun getAllCountries(): LiveData<List<OCMCountry>>

    // OPERATORS
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun insert(vararg operators: OCMOperator)

    @Query("DELETE FROM ocmoperator")
    abstract fun deleteAllOperators()

    @Transaction
    open suspend fun updateOperators(operators: List<OCMOperator>) {
        deleteAllOperators()
        for (operator in operators) {
            insert(operator)
        }
    }

    @Query("SELECT * FROM ocmoperator")
    abstract fun getAllOperators(): LiveData<List<OCMOperator>>
}

class OCMReferenceDataRepository(
    private val api: OpenChargeMapApiWrapper, private val scope: CoroutineScope,
    private val dao: OCMReferenceDataDao, private val prefs: PreferenceDataSource
) {
    fun getReferenceData(): LiveData<OCMReferenceData> {
        Log.d(TAG, "getReferenceData() called, launching updateData()")
        scope.launch {
            try {
                updateData()
            } catch (e: Exception) {
                Log.e(TAG, "updateData() threw exception", e)
            }
        }
        val connectionTypes = dao.getAllConnectionTypes()
        val countries = dao.getAllCountries()
        val operators = dao.getAllOperators()
        return MediatorLiveData<OCMReferenceData>().apply {
            value = null
            listOf(countries, connectionTypes, operators).map { source ->
                addSource(source) { _ ->
                    val ct = connectionTypes.value
                    val c = countries.value
                    val o = operators.value
                    Log.d(TAG, "MediatorLiveData update: ct=${ct?.size}, c=${c?.size}, o=${o?.size}")
                    if (ct.isNullOrEmpty() || c.isNullOrEmpty() || o.isNullOrEmpty()) return@addSource
                    Log.d(TAG, "Reference data ready! ct=${ct.size}, c=${c.size}, o=${o.size}")
                    value = OCMReferenceData(ct, c, o)
                }
            }
        }
    }

    private suspend fun updateData() {
        Log.d(TAG, "updateData() started")
        // Check if DB tables are actually populated before honoring cache.
        val dbHasData = try {
            val count = dao.getAllConnectionTypesAsync().size
            Log.d(TAG, "DB connection types count: $count")
            count > 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking DB", e)
            false
        }
        
        val lastUpdate = prefs.lastOcmReferenceDataUpdate
        val timeSince = Duration.between(lastUpdate, Instant.now())
        Log.d(TAG, "dbHasData=$dbHasData, lastUpdate=$lastUpdate, timeSince=$timeSince")
        
        if (dbHasData && timeSince < Duration.ofDays(1)) {
            Log.d(TAG, "Skipping API call - cache is fresh and DB has data")
            return
        }

        Log.d(TAG, "Calling OpenChargeMap API for reference data...")
        val response = api.getReferenceData()
        Log.d(TAG, "API response: status=${response.status}, message=${response.message}, hasData=${response.data != null}")
        if (response.status == Status.ERROR) {
            Log.e(TAG, "API returned error: ${response.message}")
            return
        }

        val data = response.data!!
        Log.d(TAG, "Got reference data: ${data.connectionTypes.size} connTypes, ${data.countries.size} countries, ${data.operators.size} operators")
        dao.updateConnectionTypes(data.connectionTypes)
        dao.updateCountries(data.countries)
        dao.updateOperators(data.operators)

        prefs.lastOcmReferenceDataUpdate = Instant.now()
        Log.d(TAG, "Reference data saved to DB successfully")
    }

    companion object {
        private const val TAG = "OCMRefData"
    }
}