# OpenChargeMapApi.kt

> **File**: `app/src/main/java/com/chargex/india/api/openchargemap/OpenChargeMapApi.kt`  
> **Purpose**: Implementation of the ChargepointApi interface for the OpenChargeMap data source — the primary source of charging station data for India.

---

## What Is This File?

This file contains two classes:

1. **`OpenChargeMapApi`** — Low-level Retrofit interface that defines the HTTP endpoints
2. **`OpenChargeMapApiWrapper`** — High-level wrapper that implements `ChargepointApi`, converting OCM responses into the app's data models

---

## OpenChargeMap (OCM) — The Data Source

[OpenChargeMap](https://openchargemap.org/) is a free, community-maintained database of EV charging stations worldwide. It has good coverage in India with data from:
- Government charging networks (EESL, NTPC)
- Private networks (Tata Power, Ather Grid, ChargeZone)
- Community contributions

**Base URL**: `https://api.openchargemap.io/v3/`

---

## API Endpoints

### GET `/poi` — Get charging stations

```
Parameters:
├── boundingbox     — Map area (lat1,lng1,lat2,lng2)
├── latitude/longitude/distance — Radius search
├── connectiontypeid — Filter by connector type (CCS=33, Type2=25, ...)
├── minpowerkw      — Minimum charging power in kW
├── operatorid      — Filter by network operator
├── statustypeid    — Filter by status (operational, planned, ...)
├── maxresults      — Maximum results (default: 3000)
├── compact         — Return minimal data (default: true)
└── verbose         — Return full details (default: false)
```

### GET `/referencedata` — Get lookup tables

Returns connector types, operators, status types, countries, etc. This data is cached and used to display human-readable names.

---

## Data Flow

```
User pans map
    │
    ▼
MapViewModel calls api.getChargepoints(bounds, zoom, filters)
    │
    ▼
OpenChargeMapApiWrapper.getChargepoints()
    │
    ├── 1. Extract filter values (connector types, min power, operators)
    │
    ├── 2. Call OpenChargeMapApi.getChargepoints(boundingbox, filters)
    │        → HTTP GET to ocm.io/v3/poi?boundingbox=...
    │
    ├── 3. Receive List<OCMChargepoint> (OCM's format)
    │
    ├── 4. postprocessResult() — Apply local filters
    │       (some filters can't be done server-side)
    │
    ├── 5. Convert OCMChargepoint → ChargeLocation (app's format)
    │
    ├── 6. If useClustering: cluster nearby stations
    │
    └── 7. Return Resource.Success(ChargepointList)
    │
    ▼
MapViewModel updates LiveData → MapFragment → MarkerManager
    └── MarkerManager applies range filter (isInRange()) and
        uses clearAll() to remove stale markers on view recreation
```

---

## Filter Conversion

The wrapper converts the app's generic filters into OCM-specific query parameters:

```kotlin
// App's generic filter:
FilterValue(type="connector_type", values=["CCS", "Type 2"])

// Converted to OCM parameter:
connectiontypeid = "33,25"  // OCM IDs for CCS and Type 2
```

It also generates SQL queries for local database filtering:

```kotlin
fun convertFiltersToSQL(filters, referenceData): FiltersSQLQuery
// Returns WHERE clause like:
// "maxPower >= 50 AND connectionType IN (33, 25)"
```

---

## Key Wrapper Methods

### `getChargepoints()` — Fetch by map bounds
Calls the OCM API with a bounding box and filters, then post-processes and clusters results.

### `getChargepointsRadius()` — Fetch by radius
Same as above but searches within a radius of a point instead of a rectangle.

### `getChargepointDetail()` — Fetch single station
Gets full details including photos, comments, and exact connector specifications.

### `getReferenceData()` — Load lookup data
Fetches connector types, operators, and status types from OCM. Cached locally.

---

## Networking Setup

```kotlin
companion object {
    fun create(
        apikey: String,
        baseurl: String = "https://api.openchargemap.io/v3/",
        context: Context? = null
    ): OpenChargeMapApi {
        val client = OkHttpClient.Builder().apply {
            addInterceptor { chain ->
                // Add API key and handles HTTP 429 Too Many Requests
                // with exponential backoff (up to 3 retries)
                ...
            }
            if (context != null) cache(Cache(context.cacheDir, cacheSize))
             // ... timeouts ...
        }.build()

        return Retrofit.Builder()
            .baseUrl(baseurl)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(OpenChargeMapApi::class.java)
    }
}
```

---

## How It Connects to Other Files

```
OpenChargeMapApi.kt
    │
    ├──▶ ChargepointApi.kt       — Implements the ChargepointApi interface
    │
    ├──▶ ChargersModel.kt        — Converts OCM data to ChargeLocation models
    │
    ├──◀ MapViewModel.kt          — Calls the wrapper to load station data
    │
    └──▶ OCMReferenceDataDao.kt   — Caches reference data in Room database
```

---

## Key Design Decisions

1. **Wrapper pattern**: Separates HTTP concerns (OpenChargeMapApi) from business logic (OpenChargeMapApiWrapper).

2. **Post-processing**: Some filters are applied locally (after API response) because OCM's API doesn't support all filter types.

3. **Inline Rate limiting/Backoff**: Uses an inline interceptor to implement exponential backoff on HTTP 429 errors.

4. **Clustering at wrapper level**: Nearby stations are grouped into clusters based on zoom level, reducing the number of markers on the map.
