# ChargepointApi.kt

> **File**: `app/src/main/java/com/chargex/india/api/ChargepointApi.kt`  
> **Purpose**: Defines the interface that all charging station data providers must implement. Acts as the contract between the app and any data source.

---

## What Is This File?

`ChargepointApi` is an **interface** — a blueprint that says "any data source that provides charging station data must support these functions." It's the abstraction layer that lets the app work with multiple data sources (OpenChargeMap, OpenStreetMap) through a single API.

---

## Why Use an Interface?

```
Without interface:                  With interface:
                                    
MapViewModel → OpenChargeMapApi     MapViewModel → ChargepointApi (interface)
                                                      │
If you want to add OpenStreetMap:                     ├─ OpenChargeMapApiWrapper
MapViewModel → OpenChargeMapApi     MapViewModel →    └─ OpenStreetMapApiWrapper
MapViewModel → OpenStreetMapApi     
(need to change MapViewModel)       (MapViewModel doesn't change!)
```

The interface means the rest of the app doesn't care WHERE the data comes from.

---

## The Interface Methods

### `getChargepoints()` — Load stations in a map area

```kotlin
suspend fun getChargepoints(
    referenceData: ReferenceData,    // Pre-loaded lookup data (connector types, networks)
    bounds: LatLngBounds,             // Visible map area
    zoom: Float,                      // Map zoom level
    useClustering: Boolean,           // Group nearby stations?
    filters: FilterValues?            // User's filter selections
): Resource<ChargepointList>          // Returns: list of stations or error
```

Called whenever the user **pans or zooms** the map.

### `getChargepointsRadius()` — Load stations near a point

```kotlin
suspend fun getChargepointsRadius(
    referenceData: ReferenceData,
    location: LatLng,        // Center point
    radius: Int,             // Search radius in km
    zoom: Float,
    useClustering: Boolean,
    filters: FilterValues?
): Resource<ChargepointList>
```

Used for **searching near a location** rather than in a map rectangle.

### `getChargepointDetail()` — Get full details for one station

```kotlin
suspend fun getChargepointDetail(
    referenceData: ReferenceData,
    id: Long                 // Station ID
): Resource<ChargeLocation>
```

Called when user **taps a marker** to see full station details (photos, reviews, connectors, etc.).

### `getReferenceData()` — Load lookup tables

```kotlin
suspend fun getReferenceData(): Resource<T>
```

Loads reference data like connector type names, network operators, status types. This is cached and loaded once at app startup.

### `getFilters()` — Get available filter options

```kotlin
fun getFilters(referenceData: ReferenceData, sp: StringProvider): List<Filter<FilterValue>>
```

Returns the list of filters the user can apply (e.g., connector types, minimum power, networks).

### `fullDownload()` — Download all stations

```kotlin
suspend fun fullDownload(): FullDownloadResult<T>
```

Downloads the **entire** station database for offline use. This can take a long time.

---

## The `createApi()` Factory Function

```kotlin
fun createApi(type: String, ctx: Context): ChargepointApi<ReferenceData> {
    return when (type) {
        "openchargemap" → OpenChargeMapApiWrapper(apiKey)
        "openstreetmap" → OpenStreetMapApiWrapper()
        else → OpenChargeMapApiWrapper(apiKey)  // Default for India
    }
}
```

This factory creates the right API implementation based on the user's data source preference.

---

## Supporting Data Classes

### `Resource<T>` — API Result Wrapper

```kotlin
// Represents the state of an API call:
Resource.Success(data)    // Data loaded successfully
Resource.Loading()        // Still loading
Resource.Error(message)   // Something went wrong
```

### `ChargepointList` — Paginated Results

```kotlin
data class ChargepointList(
    val items: List<ChargepointListItem>,  // Stations + clusters
    val isComplete: Boolean                 // Are there more to load?
)
```

### `FiltersSQLQuery` — For Local Database Filtering

```kotlin
data class FiltersSQLQuery(
    val query: String,                   // SQL WHERE clause
    val requiresChargepointQuery: Boolean,
    val requiresChargeCardQuery: Boolean
)
```

---

## How It Connects to Other Files

```
ChargepointApi.kt (interface)
    │
    ├──▶ OpenChargeMapApiWrapper  — Implementation for OpenChargeMap
    │    (OpenChargeMapApi.kt)
    │
    ├──▶ OpenStreetMapApiWrapper  — Implementation for OpenStreetMap
    │    (OpenStreetMapApi.kt)
    │
    ├──◀ MapViewModel.kt         — Uses ChargepointApi to load stations
    │
    ├──◀ PreferenceDataSource.kt  — Stores which data source the user selected
    │
    └──▶ ChargersModel.kt        — Returns ChargeLocation, Chargepoint models
    │                                (later filtered by MarkerUtils.isInRange()
    │                                for range-based station hiding)
```

---

## Key Design Decisions

1. **Interface pattern (Strategy)**: Different data sources implement the same interface, making it easy to switch between them or add new ones.

2. **`suspend` functions**: All API methods are coroutines, ensuring non-blocking network calls.

3. **`Resource` wrapper**: All API responses are wrapped in a `Resource` that can represent loading, success, or error states — this drives the UI's loading indicators.

4. **Default to OpenChargeMap**: For India, OpenChargeMap has the most comprehensive data, so it's the default when no preference is set.
