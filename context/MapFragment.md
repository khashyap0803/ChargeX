# MapFragment.kt

> **File**: `app/src/main/java/net/vonforst/evmap/fragment/MapFragment.kt`  
> **Purpose**: The main map screen — the heart of the app. Displays charging stations on an interactive map with search, filtering, range-based station hiding, vehicle data persistence, and charger detail views.

---

## What Is This File?

`MapFragment` is the **home screen** of ChargeX. It's the largest file in the project (~68KB) because it orchestrates:
- Interactive map with charging station markers
- Location tracking and permission handling
- Search bar for finding places
- Filter controls for connector types and power
- Bottom sheet showing charger details
- FABs (floating buttons) for vehicle input, my location, etc.
- Range-based station filtering with pending state management
- Vehicle data forwarding to NavigationFragment
- Marker lifecycle cleanup (clearAll)
- Marker click handling

---

## Screen Layout

```
┌──────────────────────────────────────────┐
│  🔍 Search charging stations...          │
├──────────────────────────────────────────┤
│                                          │
│           Interactive Map                │
│                                          │
│     📍  ⚡  ⚡        ⚡                  │
│           ⚡    📍        ⚡              │
│        ⚡     ⚡  ③                      │
│                              📍 ← You   │
│                                          │
│  [🔋] [📍] [🔧]  ← FABs               │
├──────────────────────────────────────────┤
│  ┌── Bottom Sheet (slides up) ────────┐ │
│  │ Station Name                       │ │
│  │ 📍 Address                         │ │
│  │ ⚡ CCS 50kW × 2, Type 2 22kW × 4  │ │
│  │ 🟢 Available                       │ │
│  │ [Navigate] [Favorite] [Share]      │ │
│  └────────────────────────────────────┘ │
└──────────────────────────────────────────┘
```

---

## Key State Fields

### Pending Fields (Survive View Recreation)

| Field | Type | Purpose |
|-------|------|---------|
| `pendingRangeFilterKm` | `Float` | Range filter value from VehicleInputFragment. Applied in `onMapReady()` because savedStateHandle fires BEFORE the map is ready. |
| `pendingUserLocation` | `LatLng?` | User's GPS location, cached so the range filter can use it even before the map's location engine fires. |
| `pendingVehicleId` | `String` | Selected vehicle ID (e.g., `"ather_450x"`), forwarded to NavigationFragment when user taps Directions. |
| `pendingBatteryPercent` | `Float` | Selected battery %, forwarded to NavigationFragment for energy feasibility display. |

### Why Pending Fields Exist

The savedStateHandle observer fires when the view is created — but `onMapReady()` (where MarkerManager is created) runs **later**. Without pending fields:
1. `range_filter_km = 4.5` arrives → but `markerManager` is null → lost!
2. `onMapReady()` creates MarkerManager → no filter set → all 336 markers shown

With pending fields:
1. `range_filter_km = 4.5` arrives → stored in `pendingRangeFilterKm`
2. `onMapReady()` → creates MarkerManager → applies `pendingRangeFilterKm` → 192 visible, 144 filtered

---

## Key Responsibilities

### 1. Map Initialization (`onMapReady`)
- Creates MapLibre map instance
- **Calls `markerManager?.clearAll()`** to remove stale markers from previous instance
- Creates new `MarkerManager` with pending range filter and user location
- Applies pending filter values BEFORE setting chargepoints

### 2. Marker Management
- Creates a `MarkerManager` (from `MarkerUtils.kt`)
- Passes charger data from ViewModel to MarkerManager
- Handles marker click → shows bottom sheet with station details
- Passes `rangeFilterKm` and `userLocation` for range filtering

### 3. Vehicle Data Persistence & Forwarding
```kotlin
// Observe vehicle data from VehicleInputFragment
findNavController().currentBackStackEntry?.savedStateHandle
    ?.getLiveData<String>("vehicle_id")
    ?.observe(viewLifecycleOwner) { vehicleId ->
        pendingVehicleId = vehicleId
    }

findNavController().currentBackStackEntry?.savedStateHandle
    ?.getLiveData<Float>("battery_percent")
    ?.observe(viewLifecycleOwner) { batteryPct ->
        pendingBatteryPercent = batteryPct
    }

// Forward to NavigationFragment when directions FAB tapped
findNavController().currentBackStackEntry?.savedStateHandle?.apply {
    set("vehicle_id", pendingVehicleId)
    set("battery_percent", pendingBatteryPercent)
}
```

### 4. Location Tracking
- Requests location permissions
- Shows user's location on the map
- **Always syncs** `markerManager.userLocation` regardless of filter state

### 5. Range Filter Integration
```kotlin
findNavController().currentBackStackEntry?.savedStateHandle
    ?.getLiveData<Float>("range_filter_km")
    ?.observe(viewLifecycleOwner) { rangeKm ->
        pendingRangeFilterKm = rangeKm
        pendingUserLocation = currentUserLocation
        if (markerManager != null) {
            markerManager.rangeFilterKm = rangeKm
            markerManager.userLocation = currentUserLocation
            markerManager.chargepoints = vm.chargepoints.value  // Re-trigger filtering
        }
    }
```

---

## Data Flow

```
MapFragment creates & configures MapViewModel
         │
         ├── MapViewModel loads chargers from API
         │   └── chargepoints LiveData updates
         │       └── MapFragment observes → markerManager.chargepoints = data
         │           └── Markers appear on map (filtered by range if active)
         │
         ├── User taps a marker
         │   └── markerManager.onChargerClick → MapViewModel loads details
         │       └── chargerDetail LiveData updates
         │           └── Bottom sheet shows station info
         │
         ├── User taps "Navigate" (directions FAB)
         │   └── Forwards pendingVehicleId + pendingBatteryPercent
         │       via currentBackStackEntry.savedStateHandle
         │       └── Opens NavigationFragment with (destLat, destLng)
         │           └── NavigationFragment reads vehicle data from
         │               previousBackStackEntry.savedStateHandle
         │
         ├── User taps "Vehicle" FAB
         │   └── Opens VehicleInputFragment
         │       └── Returns range_filter_km, vehicle_id, battery_percent
         │           └── markerManager filters markers
         │
         └── User pans/zooms map
             └── MapViewModel.mapPosition updates → triggers API reload
                 └── New chargepoints arrive → immediately filtered
```

---

## How It Connects to Other Files

```
MapFragment.kt (home screen)
    │
    ├──▶ MapViewModel.kt          — All business logic and data loading
    │
    ├──▶ MarkerUtils.kt           — Creates MarkerManager, calls clearAll()
    │                                for lifecycle cleanup
    │
    ├──▶ NavigationFragment.kt    — Opened on "Navigate", receives vehicle
    │                                data via savedStateHandle
    │
    ├──▶ VehicleInputFragment.kt  — Returns range_filter_km, vehicle_id,
    │                                battery_percent via savedStateHandle
    │
    ├──▶ FilterFragment.kt        — Opened when user taps filter button
    │
    ├──▶ MapsActivity.kt          — Uses activity methods for external nav
    │
    └──▶ BindingAdapters.kt       — Data binding helpers for the layout
```

---

## Key Design Decisions

1. **Pending fields**: savedStateHandle fires before onMapReady, so pending fields bridge the gap. Without them, filter/vehicle data is lost during view recreation.

2. **clearAll() before new MarkerManager**: Ensures stale unfiltered markers from the old instance don't persist alongside the new filtered set.

3. **Bottom sheet for details**: Station details slide up from the bottom, keeping the map visible. Users don't lose context of where the station is.

4. **Vehicle data forwarding**: MapFragment acts as a relay — it receives vehicle data from VehicleInputFragment and forwards it to NavigationFragment via savedStateHandle, since these fragments can't communicate directly.

5. **Lazy loading**: Chargers are loaded as the user pans the map, not all at once. Each new batch is immediately filtered by the active range filter.
