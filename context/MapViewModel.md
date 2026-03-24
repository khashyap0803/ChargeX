# MapViewModel.kt

> **File**: `app/src/main/java/com/chargex/india/viewmodel/MapViewModel.kt`  
> **Purpose**: Business logic for the main map screen — loads charging stations, manages filters, handles favorites, and tracks map position.

---

## What Is This File?

`MapViewModel` is the **brain behind the map screen**. While `MapFragment` handles the UI (buttons, map rendering), `MapViewModel` handles all the data:
- Loading charging stations from the API
- Applying filters
- Managing favorites
- Tracking the map's position and zoom level
- Loading charger details when a marker is tapped

It follows the **MVVM (Model-View-ViewModel)** architecture pattern.

---

## Key Properties (LiveData)

LiveData is Android's observable data holder — whenever the data changes, the UI automatically updates.

| Property | Type | Purpose |
|----------|------|---------|
| `chargepoints` | `LiveData<Resource<List<ChargepointListItem>>>` | All visible charging stations |
| `chargerSparse` | `MutableLiveData<ChargeLocation?>` | Currently selected station (summary) |
| `chargerDetails` | `LiveData<Resource<ChargeLocation>>` | Detailed info for selected station |
| `charger` | `MediatorLiveData<Resource<ChargeLocation>>` | Merges sparse and detailed info |
| `availability` | `LiveData<Resource<...>>` | Real-time availability data |
| `teslaPricing` | `LiveData<Pricing?>` | Live pricing data for Tesla Superchargers |
| `favorites` | `LiveData<List<FavoriteWithDetail>>` | User's favorited stations |
| `filterStatus` | `MutableLiveData<Long>` | Which filter profile is active |
| `mapPosition` | `MutableLiveData<MapPosition>` | Current map center and zoom |
| `mapType` | `MutableLiveData<AnyMap.Type>` | Map style (normal, satellite) |
| `bottomSheetState` | `MutableLiveData<Int>` | Bottom sheet UI state |
| `searchResult` | `MutableLiveData<PlaceWithBounds>` | Selected search destination |

---

## How Data Loading Works

```
User pans/zooms the map
         │
         ▼
MapFragment detects map move
         │
         ▼
MapViewModel.mapPosition is updated with new bounds + zoom
         │
         ▼
Chargepoint loading is triggered (via MediatorLiveData transformations)
         │
         ▼
ChargepointApi.getChargepoints(bounds, zoom, filters) is called
         │
         ▼
Results arrive → chargepoints LiveData is updated
         │
         ▼
MapFragment observes the change → MarkerManager updates markers
         │
         ▼
MarkerManager applies range filter (if active) using isInRange()
         └── If view was recreated: clearAll() removes stale markers first
```

> **Note**: The range filter is NOT in MapViewModel — it's applied client-side by `MarkerManager` in `MarkerUtils.kt`. MapViewModel only provides the full list of chargepoints; the filtering happens at the UI layer.

---

## Key Functions

### `reloadChargepoints(overrideCache: Boolean = false)`
Forces a reload of charging stations from the API.

### `loadChargerById(chargerId: Long)`
Loads a specific charger by its ID (used for deep links and navigation).

### `insertFavorite(charger: ChargeLocation)`
Adds a station to the user's favorites (saves to Room database).

### `deleteFavorite(favorite: Favorite)`
Removes a station from favorites.

### `toggleFilters()`
Switches between "no filters" and "custom filters" modes.

### `reloadAvailability()`
Refreshes real-time connector availability for the selected station.

### `downloadOfflineRegion(bounds: LatLngBounds)`
Triggers a background fetch of all charging stations within a specific map region (at zoom level 14f) and caches their detailed information for offline use.

### `extendBounds(bounds: LatLngBounds)`
Expands the visible map bounds by 1.5x to pre-load stations just outside the viewport (prevents markers popping in when scrolling).

---

## Filter System

```
filterStatus values:
├── FILTERS_DISABLED (-1)   — Show all stations
├── FILTERS_FAVORITES (-2)  — Show only favorites
└── FILTERS_CUSTOM (>=0)    — Apply user's custom filter profile
```

When the filter changes, chargepoints are either:
1. **Re-fetched from API** with filter parameters (server-side filtering)
2. **Filtered locally** using SQL queries on the cached database

---

## Architecture (MVVM Pattern)

```
┌─────────────────┐     ┌──────────────┐     ┌──────────────────┐
│  MapFragment     │────▶│  MapViewModel │────▶│  ChargepointApi  │
│  (View Layer)    │◀────│  (ViewModel)  │◀────│  (Model/Data)    │
└─────────────────┘     └──────────────┘     └──────────────────┘
     │                       │                       │
     │ Observes              │ Loads data            │ Network calls
     │ LiveData              │ Manages state         │ Database queries
     │                       │                       │
     UI updates              Business logic          Data access
     automatically           (filtering,             (API + Room)
                             favorites)
```

---

## How It Connects to Other Files

```
MapViewModel.kt
    │
    ├──◀ MapFragment.kt         — UI observes all LiveData properties.
    │                              MapFragment also handles range filter,
    │                              vehicle data forwarding, and clearAll()
    │                              separately from ViewModel.
    │
    ├──▶ ChargepointApi.kt      — Fetches charger data from APIs
    │
    ├──▶ ChargeLocationsDao.kt   — Reads/writes chargers to local database
    │
    ├──▶ PreferenceDataSource.kt — Reads user preferences (data source, filters)
    │
    ├──▶ FavoritesDao.kt         — Manages favorite stations in database
    │
    └──▶ ChargersModel.kt       — Uses ChargeLocation and related data models
```

> **What MapViewModel does NOT handle**: Range filter, vehicle data persistence, energy feasibility, and marker lifecycle — these are handled by `MapFragment` (via `pendingRangeFilterKm`, `pendingVehicleId`, `pendingBatteryPercent`) and `MarkerUtils` (`clearAll()`, `isInRange()`).
