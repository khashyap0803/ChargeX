# VehicleInputFragment.kt

> **File**: `app/src/main/java/net/vonforst/evmap/fragment/VehicleInputFragment.kt`  
> **Purpose**: UI screen where users select their EV model, set battery level, and calculate estimated range for station filtering.

---

## What Is This File?

This is the **"Enter Vehicle Details"** screen. Users come here to tell the app:
1. What car they drive
2. How much battery they have
3. Whether AC is on
4. What driving mode they're in

The app then calculates their estimated range and (optionally) applies it as a filter on the map to hide unreachable stations.

---

## Screen Layout

```
┌──────────────────────────────────┐
│  ← Back                         │
│                                  │
│  Select Manufacturer             │
│  ┌──────────────────────────┐   │
│  │  Tata                ▼   │   │
│  └──────────────────────────┘   │
│                                  │
│  Select Model                    │
│  ┌──────────────────────────┐   │
│  │  Nexon EV Max (LR)  ▼   │   │
│  └──────────────────────────┘   │
│                                  │
│  ┌─── Vehicle Specs Card ────┐  │
│  │ Battery: 40.5 kWh         │  │
│  │ Efficiency: 12.5 kWh/100km│  │
│  │ Official Range: 437 km    │  │
│  └───────────────────────────┘  │
│                                  │
│  ┌─── Battery Input Card ────┐  │
│  │ Battery Level: 80%        │  │
│  │ ────●─────────── slider   │  │
│  │                            │  │
│  │ AC: [ON] / OFF             │  │
│  │                            │  │
│  │ Mode: [City] Highway Mixed │  │
│  └───────────────────────────┘  │
│                                  │
│  ┌─── Range Result Card ─────┐  │
│  │ Estimated Range: 215 km   │  │
│  │ 80% battery • City • AC on│  │
│  └───────────────────────────┘  │
│                                  │
│  [ Apply Filter ] [Clear Filter] │
└──────────────────────────────────┘
```

---

## How It Works (User Flow)

```
1. User opens the screen
         │
         ▼
2. Manufacturer dropdown shows: [Tata, MG, Hyundai, Kia, ...]
   (from VehicleProfile.groupedByManufacturer())
         │
         ▼ User picks "Tata"
3. Model dropdown shows: [Nexon EV Max, Nexon EV, Punch, ...]
         │
         ▼ User picks "Nexon EV Max"
4. Vehicle specs card appears showing battery, efficiency, range
         │
         ▼
5. Battery slider and AC/mode controls become visible
   User adjusts: 80%, AC on, City mode
         │
         ▼
6. Range is calculated in real-time:
   RangeCalculator.calculateRange(vehicle, 80%, acOn=true, "city")
   → "Estimated Range: 215 km"
         │
         ▼ User taps "Apply Filter"
7. range_filter_km = 215.0 is passed back to MapFragment
         │
         ▼
8. MapFragment passes this to MarkerUtils.rangeFilterKm
   → All stations beyond 215 km from user are hidden
```

---

## Key Functions

### `setupManufacturerDropdown()`
- Loads all manufacturers from `VehicleProfile.groupedByManufacturer()`
- When a manufacturer is selected, populates the model dropdown
- When a model is selected, shows specs and triggers range calculation

### `setupBatterySlider()`
- Battery slider range: 0% to 100%
- Updates the range estimate live as the user moves the slider
- Also listens to AC toggle and driving mode chip changes

### `setupButtons()`
- **Apply Filter**: Calculates range, passes `range_filter_km` back to MapFragment via `savedStateHandle`
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

## Data Flow: How Range Filter Gets to the Map

```
VehicleInputFragment                    MapFragment
─────────────────                       ───────────
     │                                       │
     │ User taps "Apply Filter"              │
     │                                       │
     ▼                                       │
savedStateHandle.set(                        │
  "range_filter_km", 215.0f                  │
  "vehicle_id", "tata_nexon_lr"              │
  "battery_percent", 80.0f                   │
)                                            │
     │                                       │
     ▼                                       ▼
findNavController().navigateUp()    savedStateHandle.getLiveData(
     │                                "range_filter_km"
     │                              ).observe { rangeKm ->
     └──────────────────────────▶      markerManager.rangeFilterKm = rangeKm
                                    }
```

The data flows **backwards through the navigation stack** using Jetpack Navigation's `savedStateHandle` — this is the standard Android pattern for returning results from a fragment.

---

## How It Connects to Other Files

```
VehicleInputFragment.kt
    │
    ├──▶ VehicleProfile.kt      — Gets the list of vehicles and their specs
    │
    ├──▶ RangeCalculator.kt     — Calculates range from vehicle + conditions
    │
    ├──▶ MapFragment.kt         — Returns range_filter_km via savedStateHandle
    │                              Map then filters visible stations
    │
    └──▶ fragment_vehicle_input.xml — Layout with dropdowns, slider, chips
```

---

## Key Design Decisions

1. **Two-step dropdown**: Manufacturer first, then model — this avoids a single dropdown with 24+ items and makes it easier for users to find their car.

2. **Real-time updates**: Range recalculates instantly when any input changes (battery slider, AC toggle, driving mode) — no need to press "Calculate".

3. **savedStateHandle for results**: Uses the standard Android Navigation component pattern to pass data back to the previous fragment, rather than shared ViewModels or global state.

4. **Clear Filter option**: Users can remove the range filter without having to enter new vehicle details.
