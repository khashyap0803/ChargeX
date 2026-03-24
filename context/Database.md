# Database.kt & DAOs

> **File**: `app/src/main/java/com/chargex/india/storage/Database.kt`  
> **Purpose**: Room database setup — defines the SQLite schema, DAOs (Data Access Objects), migrations, and type converters.

---

## What Is This File?

`Database.kt` defines the **Room database** — Android's SQLite wrapper that stores all charging station data locally. This enables:
- **Offline access** to previously viewed stations
- **Caching** API responses to reduce network calls
- **Favorites** storage that persists across app restarts
- **Filter profiles** saved by the user

---

## Database Schema

```
AppDatabase
├── ChargeLocationsDao    — Stores charging station data
│   └── Table: chargelocation
│       ├── id (PK)
│       ├── dataSource
│       ├── name
│       ├── lat, lng
│       ├── address fields
│       ├── chargepoints (JSON list)
│       ├── network
│       ├── cost fields
│       ├── openingHours (JSON)
│       ├── timeRetrieved
│       └── isDetailed
│
├── FavoritesDao          — User's favorite stations
│   └── Table: favorite
│       ├── favId (PK)
│       ├── chargerId
│       └── dataSource
│
├── FilterProfileDao      — Saved filter profiles
│   └── Table: filterprofile
│       ├── id (PK)
│       ├── dataSource
│       ├── name
│       └── order
│
├── FilterValueDao        — Filter values for each profile
│   └── Table: filtervalue
│       ├── profile (FK)
│       ├── dataSource
│       ├── filterType
│       └── value
│
├── SavedRegionDao        — Tracks which map regions have cached data
│   └── Table: savedregion
│       ├── id (PK)
│       ├── dataSource
│       └── bounds (lat/lng)
│
├── OCMReferenceDataDao   — OpenChargeMap lookup tables
│   ├── Table: ocm_connectiontype
│   ├── Table: ocm_country
│   └── Table: ocm_operator
│
├── GEReferenceDataDao    — GoingElectric API lookup tables
│   ├── Table: ge_chargecard
│   ├── Table: ge_network
│   └── Table: ge_plug
│
├── OSMReferenceDataDao   — OpenStreetMap API lookup tables
│   └── Table: osm_network
│
├── StationOccupancyDao   — Station occupancy for wait time predictions
│   └── Table: station_occupancy
│
└── RecentAutocompletePlaceDao — Recent search history
    └── Table: recentautocompleteplace
```

---

## Key DAOs and Their Methods

### ChargeLocationsDao
The most important DAO — manages charging station caching:

```kotlin
@Dao
interface ChargeLocationsDao {
    @Query("SELECT * FROM chargelocation WHERE id = :id")
    suspend fun getChargerById(id: Long): ChargeLocation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg charger: ChargeLocation)

    @Query("DELETE FROM chargelocation WHERE timeRetrieved < :threshold")
    suspend fun deleteOlderThan(threshold: Instant)

    // Complex query with filters, joins, and spatial search...
    @RawQuery
    suspend fun getChargersFiltered(query: SupportSQLiteQuery): List<ChargeLocation>
}
```

### FavoritesDao
Simple CRUD for favorites:

```kotlin
@Dao
interface FavoritesDao {
    @Query("SELECT * FROM favorite")
    fun getAllFavorites(): LiveData<List<Favorite>>

    @Insert
    suspend fun insert(fav: Favorite)

    @Delete
    suspend fun delete(fav: Favorite)
}
```

---

## Type Converters

Room can't store complex types directly, so `TypeConverters.kt` converts them:

| Kotlin Type | SQLite Storage | Converter |
|-------------|---------------|-----------|
| `List<Chargepoint>` | JSON String | Moshi JSON adapter |
| `OpeningHours` | JSON String | Moshi JSON adapter |
| `List<ChargerPhoto>` | JSON String | Moshi JSON adapter |
| `Instant` | Long (millis) | `Instant.toEpochMilli()` |
| `LocalTime` | String | `LocalTime.toString()` |

---

## Database Migrations

When the schema changes between app versions, migrations update the database without losing data:

```kotlin
val MIGRATION_28_29 = object : Migration(28, 29) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `station_occupancy` ...")
    }
}
```

Includes complex operations like SpatiaLite `InitSpatialMetaData()` and geometry column recovery for bounding box queries.

---

## Database Backup

A safe direct file-copy backup method is provided to prevent `CursorWindowAllocationException` when attempting to load the entire DB into memory.

```kotlin
suspend fun createBackup(context: Context, fileName: String)
```

By passing a `.db` filename, it uses `java.nio.channels.FileChannel` to directly transfer bytes from the application's database file to a destination backup file.

---

## How It Connects to Other Files

```
Database.kt
    │
    ├──◀ MapViewModel.kt         — Reads/writes chargers and favorites.
    │                              Chargers then go to MarkerManager for
    │                              range-filtered display (clearAll + isInRange).
    │
    ├──◀ CleanupCacheWorker.kt   — Deletes old cached entries
    │
    ├──◀ UpdateFullDownloadWorker — Stores full offline download
    │
    ├──▶ ChargersModel.kt        — Database entities match the data models
    │
    └──▶ TypeConverters.kt       — Converts complex types for SQLite storage
```

---

## Key Design Decisions

1. **Room ORM**: Uses Room instead of raw SQLite for type safety, compile-time query validation, and LiveData integration.

2. **JSON for complex fields**: Lists and nested objects are stored as JSON strings because Room doesn't support nested Room entities well.

3. **Cache invalidation**: `timeRetrieved` field tracks when data was fetched, allowing the cleanup worker to purge stale entries.

4. **Exported schema**: Schema JSON is exported to `schemas/` directory for migration testing.
