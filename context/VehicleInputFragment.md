# VehicleInputFragment.kt

> **File**: `app/src/main/java/net/vonforst/evmap/fragment/VehicleInputFragment.kt`  
> **Purpose**: UI screen where users select their EV model, set battery level, and calculate estimated range for station filtering.

---

## What Is This File?

This is the **"Enter Vehicle Details"** screen. Users come here to tell the app:
1. What vehicle they drive (from 24 Indian EV models)
2. How much battery they have (5%–100% slider, step size 5)
3. Whether AC is on (irrelevant for scooters, but the toggle is available)
4. What driving mode they're in (city/highway/mixed)

The app then calculates their estimated range and (optionally) applies it as a filter on the map to hide unreachable stations.

---

## Screen Layout

```
┌──────────────────────────────────┐
│  ← Back                         │
│                                  │
│  Select Manufacturer             │
│  ┌──────────────────────────┐   │
│  │  Ather               ▼   │   │
│  └──────────────────────────┘   │
│                                  │
│  Select Model                    │
│  ┌──────────────────────────┐   │
│  │  450X               ▼   │   │
│  └──────────────────────────┘   │
│                                  │
│  ┌─── Vehicle Specs Card ────┐  │
│  │ Battery: 3.7 kWh          │  │
│  │ Efficiency: 3.4 kWh/100km │  │
│  │ Official Range: 105 km    │  │
│  └───────────────────────────┘  │
│                                  │
│  ┌─── Battery Input Card ────┐  │
│  │ Battery Level: 5%         │  │
│  │ ●──────────────── slider  │  │
│  │                            │  │
│  │ AC: ON / [OFF]             │  │
│  │                            │  │
│  │ Mode: [City] Highway Mixed │  │
│  └───────────────────────────┘  │
│                                  │
│  ┌─── Range Result Card ─────┐  │
│  │ Estimated Range: 4 km     │  │
│  │ 5% battery • City • AC off│  │
│  └───────────────────────────┘  │
│                                  │
│  [ Show Reachable Stations ]     │
│  [ Clear Filter ]                │
└──────────────────────────────────┘
```

---

## How It Works (User Flow)

```
1. User opens the screen
         │
         ▼
2. Manufacturer dropdown shows: [Ather, BYD, Hyundai, Kia, ...]
   (from VehicleProfile.groupedByManufacturer())
         │
         ▼ User picks "Ather"
3. Model dropdown shows: [450X]
         │
         ▼ User picks "450X"
4. Vehicle specs card appears:
   Battery: 3.7 kWh | Efficiency: 3.4 | Range: 105 km
         │
         ▼
5. Battery slider (5%–100%, step 5) and AC/mode controls become visible
   User adjusts: 5%, AC off, City mode
         │
         ▼
6. Range is calculated in real-time:
   RangeCalculator.calculateRange(vehicle, 5.0, acOn=false, "city")
   → "Estimated Range: 4 km"
         │
         ▼ User taps "Show Reachable Stations"
7. Three values passed back to MapFragment:
   ├── range_filter_km = 4.5f
   ├── vehicle_id = "ather_450x"
   └── battery_percent = 5.0f
         │
         ▼
8. MapFragment applies range filter AND stores vehicle data
   for later forwarding to NavigationFragment
```

---

## Key Functions

### `setupManufacturerDropdown()`
- Loads all manufacturers from `VehicleProfile.groupedByManufacturer()`
- When a manufacturer is selected, populates the model dropdown
- When a model is selected, shows specs and triggers range calculation

### `setupBatterySlider()`
- Battery slider range: **5% to 100%** (not 0%), step size: **5%**
- Updates the range estimate live as the user moves the slider
- Also listens to AC toggle and driving mode chip changes

### `setupButtons()`
- **Apply Filter**: Calculates range, passes `range_filter_km`, `vehicle_id`, and `battery_percent` back to MapFragment via `savedStateHandle`
- **Clear Filter**: Passes `range_filter_km = -1` to indicate "no filter"

### `updateRange()`
Core calculation function called whenever any input changes:

```kotlin
private fun updateRange() {
    val vehicle = selectedVehicle ?: return
    val batteryPercent = binding.sliderBattery.value.toDouble()
    val acOn = binding.switchAc.isChecked
    val drivingMode = getSelectedDrivingMode()

    val rangeKm = RangeCalculator.calculateRange(
        vehicle, batteryPercent, acOn, drivingMode
    )

    binding.tvEstimatedRange.text = "%.0f km".format(rangeKm)
}
```

---

## Data Flow: How Values Get to the Map and Navigation

```
VehicleInputFragment                    MapFragment                    NavigationFragment
─────────────────                       ───────────                    ──────────────────
     │                                       │                              │
     │ User taps "Apply Filter"              │                              │
     ▼                                       │                              │
previousBackStackEntry                       │                              │
  .savedStateHandle.set(                     │                              │
    "range_filter_km", 4.5f                  │                              │
    "vehicle_id", "ather_450x"               │                              │
    "battery_percent", 5.0f                  │                              │
  )                                          │                              │
     │                                       ▼                              │
     │                             Observes via getLiveData()               │
     └──────────────────────────▶  pendingRangeFilterKm = 4.5f             │
                                   pendingVehicleId = "ather_450x"         │
                                   pendingBatteryPercent = 5.0f            │
                                            │                              │
                                   markerManager.rangeFilterKm = 4.5f     │
                                   → 192 visible, 144 filtered out        │
                                            │                              │
                                   User taps Directions on a station      │
                                            │                              │
                                   Forwards vehicle data ──────────────▶  Energy feasibility
                                   via savedStateHandle                    card displayed
```

---

## How It Connects to Other Files

```
VehicleInputFragment.kt
    │
    ├──▶ VehicleProfile.kt      — Gets the list of vehicles and their specs
    │                              (including vehicleType, physics params)
    │
    ├──▶ RangeCalculator.kt     — Calculates range from vehicle + conditions
    │
    ├──▶ MapFragment.kt         — Returns range_filter_km, vehicle_id,
    │                              battery_percent via savedStateHandle
    │
    └──▶ fragment_vehicle_input.xml — Layout with dropdowns, slider, chips
```

---

## Key Design Decisions

1. **Two-step dropdown**: Manufacturer first, then model — avoids a single dropdown with 24+ items.

2. **Real-time updates**: Range recalculates instantly when any input changes — no need to press "Calculate".

3. **savedStateHandle for results**: Standard Android Navigation pattern to pass data back to the previous fragment.

4. **Slider minimum is 5%, not 0%**: Prevents edge cases where 0% battery produces division-by-zero or nonsensical 0 km ranges.

5. **Three values passed back**: `range_filter_km` for map filtering, `vehicle_id` and `battery_percent` for energy feasibility display in NavigationFragment.
