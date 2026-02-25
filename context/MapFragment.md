# MapFragment.kt

> **File**: `app/src/main/java/net/vonforst/evmap/fragment/MapFragment.kt`  
> **Purpose**: The main map screen — the heart of the app. Displays charging stations on an interactive map with search, filtering, and charger detail views.

---

## What Is This File?

`MapFragment` is the **home screen** of ChargeX. It's the largest file in the project (~60KB) because it orchestrates:
- Interactive map with charging station markers
- Location tracking and permission handling
- Search bar for finding places
- Filter controls for connector types and power
- Bottom sheet showing charger details
- FABs (floating buttons) for vehicle input, my location, etc.
- Range-based station filtering
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

## Key Responsibilities

### 1. Map Initialization
- Creates a MapLibre (FOSS flavor) or Google Maps instance
- Sets initial position to India (for first launch)
- Configures map styles, compass, zoom controls

### 2. Marker Management
- Creates a `MarkerManager` (from `MarkerUtils.kt`)
- Passes charger data from ViewModel to MarkerManager
- Handles marker click → shows bottom sheet with station details
- Passes `rangeFilterKm` and `userLocation` for range filtering

### 3. Location Tracking
- Requests location permissions
- Shows user's location on the map
- Updates `userLocation` on MarkerManager for range calculations

### 4. Search
- Place autocomplete search bar at the top
- Navigate to searched locations on the map

### 5. Data Observation
- Observes `MapViewModel.chargepoints` → updates markers
- Observes `MapViewModel.chargerDetail` → updates bottom sheet
- Observes `MapViewModel.favorites` → updates marker star icons
- Observes `MapViewModel.filterStatus` → triggers reload

### 6. Range Filter Integration
```kotlin
// Listen for range filter result from VehicleInputFragment
findNavController().currentBackStackEntry?.savedStateHandle
    ?.getLiveData<Float>("range_filter_km")
    ?.observe(viewLifecycleOwner) { rangeKm ->
        if (rangeKm < 0) {
            markerManager.rangeFilterKm = 0f  // Clear filter
        } else {
            markerManager.rangeFilterKm = rangeKm
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
         │           └── Markers appear on map
         │
         ├── User taps a marker
         │   └── markerManager.onChargerClick → MapViewModel loads details
         │       └── chargerDetail LiveData updates
         │           └── Bottom sheet shows station info
         │
         ├── User taps "Navigate"
         │   └── Opens NavigationFragment with (destLat, destLng)
         │
         ├── User taps "Vehicle" FAB
         │   └── Opens VehicleInputFragment
         │       └── Returns range_filter_km → markerManager filters markers
         │
         └── User pans/zooms map
             └── MapViewModel.mapPosition updates → triggers API reload
```

---

## How It Connects to Other Files

```
MapFragment.kt (home screen)
    │
    ├──▶ MapViewModel.kt          — All business logic and data loading
    │
    ├──▶ MarkerUtils.kt           — Creates and manages MarkerManager
    │
    ├──▶ NavigationFragment.kt    — Opened when user taps "Navigate"
    │
    ├──▶ VehicleInputFragment.kt  — Opened when user taps vehicle FAB
    │
    ├──▶ FilterFragment.kt        — Opened when user taps filter button
    │
    ├──▶ MapsActivity.kt          — Uses activity methods for external nav
    │
    └──▶ BindingAdapters.kt       — Data binding helpers for the layout
```

---

## Key Design Decisions

1. **Bottom sheet for details**: Station details slide up from the bottom, keeping the map visible. Users don't lose context of where the station is.

2. **Lazy loading**: Chargers are loaded as the user pans the map, not all at once. This keeps memory usage low and API calls efficient.

3. **Extended bounds**: The MapViewModel loads chargers 1.5x beyond the visible area, so markers don't pop in when the user starts scrolling.

4. **Single fragment for everything**: The map, search, filters, and bottom sheet are all in one fragment. This avoids fragment transaction overhead for frequent interactions.
