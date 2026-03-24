# NavigationFragment.kt

> **File**: `app/src/main/java/com/chargex/india/fragment/NavigationFragment.kt`  
> **Purpose**: Displays the in-app navigation screen with route preview, distance, duration, **energy feasibility card**, and a button to launch turn-by-turn navigation.

---

## What Is This File?

When a user taps "Navigate" on a charging station, this fragment shows:
- A **map with the route drawn** as a green polyline
- **Distance** (e.g., "12.5 km")
- **Duration** (e.g., "25 min")
- **Energy feasibility card** showing:
  - Vehicle name + input battery % (e.g., "Ather 450X • 5% battery")
  - Status (✅ Comfortable / ⚠️ Tight / ❌ Insufficient)
  - Arrival battery %
  - Energy required vs available (kWh)
  - Traffic conditions
- **Start Navigation** button to open Google Maps/other nav apps

---

## Screen Layout

```
┌──────────────────────────────────┐
│           Map View               │
│    📍 ─── green line ──── ⚡    │
│   (you)                (station) │
├──────────────────────────────────┤
│  Distance: 1.8 km               │
│  Duration: 5 min                 │
│  [🛰 Online Route]               │
│                                  │
│  ┌── ⚡ Energy Feasibility ───┐ │
│  │ Ather 450X • 5% battery    │ │
│  │ ✅ Comfortable — arrive    │ │
│  │    with 2% battery         │ │
│  │ Arrival Battery: 2%        │ │
│  │ Energy Required: 0.015 kWh │ │
│  │ Energy Available: 0.185 kWh│ │
│  │ Light Traffic (avg 22 km/h)│ │
│  └────────────────────────────┘ │
│                                  │
│  [  🧭 Navigate in ChargeX ]      │
└──────────────────────────────────┘
```

---

## How It Works (Step by Step)

```
1. Fragment receives destination coordinates + station name via Safe Args
   (destLat, destLng, stationName)
         │
         ▼
2. Get user's current GPS location
   (requires ACCESS_FINE_LOCATION permission)
         │
         ▼
3. Load Google Maps API key from resources
   Resource name: "google_directions_key"
         │
         ▼
4. Call RouteService.getRoute(origin, destination, apiKey)
   (runs on IO dispatcher, non-blocking)
         │
         ▼
5. If route received:
   ├── Draw green polyline on map
   ├── Add "Your Location" marker at origin
   ├── Display distance and duration text
   ├── Animate camera to fit entire route
   ├── Enable "Start Navigation" button
   └── Call displayEnergyFeasibility(distanceKm, durationMinutes)
         │
   If route is null (both APIs failed):
   └── Show error: "Could not calculate route"
```

---

## Energy Feasibility Display

### `displayEnergyFeasibility(distanceKm, durationMinutes)`

This is the key function that connects the route data with the physics-based energy model:

```kotlin
private fun displayEnergyFeasibility(distanceKm: Double, durationMinutes: Double) {
    // 1. Read vehicle data from MapFragment's savedStateHandle
    val savedState = findNavController().previousBackStackEntry?.savedStateHandle
    val vehicleId = savedState?.get<String>("vehicle_id")
    val batteryPercent = savedState?.get<Float>("battery_percent")?.toDouble()

    // 2. Find vehicle profile
    val vehicle = VehicleProfile.findById(vehicleId)

    // 3. Use vehicle's hasAC property (scooters = false, cars = true)
    val acOn = vehicle.hasAC

    // 4. Calculate feasibility using physics model
    val result = RangeCalculator.isRouteFeasible(
        vehicle = vehicle,
        batteryPercent = batteryPercent,
        routeDistanceKm = distanceKm,
        routeDurationMinutes = durationMinutes,
        temperatureC = 35.0,
        acOn = acOn,           // ← Uses vehicle.hasAC, NOT hardcoded true
        safetyMargin = 0.0     // ← Zero because calculateRange already has corrections
    )

    // 5. Display results
    binding.tvVehicleInfo.text = "${vehicle.name} • ${batteryPercent.toInt()}% battery"
    binding.tvEnergyStatus.text = result.statusMessage
    binding.tvArrivalBattery.text = "Arrival Battery: ${result.arrivalBatteryPercent.toInt()}%"
    binding.tvEnergyRequired.text = "Energy Required: %.3f kWh".format(result.energyRequired)
    binding.tvEnergyAvailable.text = "Energy Available: %.3f kWh".format(result.energyAvailable)
}
```

### Data Flow: Vehicle Data Path

```
VehicleInputFragment                    MapFragment                    NavigationFragment
─────────────────                       ───────────                    ──────────────────
     │                                       │                              │
     │ User taps "Apply Filter"              │                              │
     ▼                                       │                              │
savedStateHandle.set(                        │                              │
  "vehicle_id", "ather_450x"                 │                              │
  "battery_percent", 5.0f                    │                              │
)                                            │                              │
     │                                       ▼                              │
     └──────────────────────────▶  pendingVehicleId = "ather_450x"         │
                                   pendingBatteryPercent = 5.0f             │
                                            │                              │
                                   User taps Directions FAB                │
                                            │                              │
                                   currentBackStackEntry                   │
                                   .savedStateHandle.set(                  │
                                     "vehicle_id", pendingVehicleId        │
                                     "battery_percent", pendingBatteryPct  │
                                   )                                       │
                                            │                              │
                                            └──────────────────────────▶  previousBackStackEntry
                                                                           .savedStateHandle.get(
                                                                             "vehicle_id"
                                                                             "battery_percent"
                                                                           )
```

### Why `acOn = vehicle.hasAC`?

Previously, `acOn` was hardcoded to `true` for ALL vehicles. This meant scooters (which have no AC) were penalized by 1.8 kW of phantom AC power, causing massive energy overestimation. Now:
- **Cars/SUVs**: `hasAC = true` → AC penalty applied
- **Scooters**: `hasAC = false` → no AC penalty

### Why 3 Decimal Places?

For scooter-sized batteries (3.7 kWh at 5% = 0.185 kWh available), 1 decimal rounding (0.2 kWh) loses critical precision. With 3 decimals: "Energy Required: 0.015 kWh" vs "Energy Available: 0.185 kWh" — the user can clearly see the numbers.

---

## Other Key Functions

### `fetchRoute()` — Main route loading function

Orchestrates GPS location, API key, route service call, and display. Checks the `route.algorithm` property to set the `tvRouteSource` badge (Online, GraphHopper, or Haversine).

### `startInAppNavigation()` — Offline GPS Tracking

1. Triggers exact location updates (2s / 5m).
2. Sets map camera zoom to 18x with steep tilt (60 degrees) for 3D navigation perspective.
3. Every GPS change → `onLocationChanged`:
   - Calculates distance `haversineDistanceKm`.
   - Modifies `tvNavStatus` string (e.g., "🧭 Head Northeast ↗ — 3.2 km remaining").
   - Triggers Arrival success if distance < 100 meters.

### `formatDistance(km: Double): String`
```kotlin
formatDistance(0.5)   → "500 m"
formatDistance(12.3)  → "12.3 km"
```

### `formatDuration(minutes: Double): String`
```kotlin
formatDuration(13.5)  → "13 min"
formatDuration(90.0)  → "1h 30m"
```

---

## How It Connects to Other Files

```
NavigationFragment.kt
    │
    ├──◀ MapFragment.kt     — User taps "Navigate" → opens this fragment
    │                          with destination coordinates AND vehicle data
    │
    ├──▶ RouteService.kt     — Calls getRoute() to fetch the driving route
    │
    ├──▶ RangeCalculator.kt  — Calls isRouteFeasible() for energy card
    │
    ├──▶ VehicleProfile.kt   — Uses findById() and vehicle.hasAC
    │
    └──▶ MapsActivity.kt     — "Start Navigation" opens Google Maps (if online)
```

---

## Key Design Decisions

1. **Energy card with vehicle info**: Shows "Ather 450X • 5% battery" so users can confirm their selection was correctly received. Previously, users saw "2% arrival battery" and confused it with their input (5%).

2. **Vehicle-aware AC**: Uses `vehicle.hasAC` instead of hardcoded `true`, preventing scooters from being penalized for nonexistent AC.

3. **Zero safety margin**: `safetyMargin=0.0` because `calculateRange` already applies real-world corrections. Adding margin on small batteries (3.7 kWh at 5%) would show negative energy.

4. **In-app preview first**: Shows the route inside the app before launching external navigation.

5. **Graceful degradation**: If no vehicle data is available, the energy card is hidden entirely.
