# VehicleProfile.kt

> **File**: `app/src/main/java/net/vonforst/evmap/model/VehicleProfile.kt`  
> **Purpose**: Contains a hardcoded database of popular Indian EV models with their battery and efficiency specifications.

---

## What Is This File?

`VehicleProfile` is a **data class** that represents one electric vehicle model. It stores the vehicle's name, manufacturer, battery size, official range, and energy efficiency. The app uses this data to calculate how far the user can drive on their current battery charge.

Think of it like a **vehicle catalog** built into the app — the user picks "Tata Nexon EV Max" from a dropdown, and the app knows it has a 40.5 kWh battery.

---

## Data Class Fields

```kotlin
data class VehicleProfile(
    val id: String,                      // Unique identifier, e.g. "tata_nexon_lr"
    val name: String,                    // Display name, e.g. "Nexon EV Max (Long Range)"
    val manufacturer: String,            // Brand, e.g. "Tata"
    val batteryCapacityKwh: Double,      // Total battery size in kWh, e.g. 40.5
    val officialRangeKm: Double,         // ARAI/WLTP rated range in km, e.g. 437
    val efficiencyKwhPer100Km: Double    // Energy consumption per 100 km, e.g. 12.5
)
```

### What Each Field Means

| Field | Example | Meaning |
|-------|---------|---------|
| `id` | `"tata_nexon_lr"` | Internal unique key used to save/retrieve the user's selected vehicle |
| `name` | `"Nexon EV Max (Long Range)"` | Shown in the dropdown menu to the user |
| `manufacturer` | `"Tata"` | Used to group vehicles by brand in the UI |
| `batteryCapacityKwh` | `40.5` | Total energy storage in kilowatt-hours |
| `officialRangeKm` | `437.0` | Manufacturer-claimed range (ARAI certified for India) |
| `efficiencyKwhPer100Km` | `12.5` | How much energy the car uses per 100 km of driving |

---

## The Vehicle Database (INDIAN_EVS)

Inside the `companion object`, there's a static list called `INDIAN_EVS` containing all supported vehicles:

```
INDIAN_EVS: List<VehicleProfile>
├── Tata (6 models): Nexon LR, Nexon SR, Punch, Tiago, Tigor, Curvv
├── MG (3 models): ZS EV, Comet, Windsor
├── Mahindra (2 models): XUV400, BE 6
├── Hyundai (2 models): Ioniq 5, Creta EV
├── Kia (1 model): EV6
├── BYD (3 models): Atto 3, Seal, e6
├── Citroen (1 model): eC3
├── BMW (1 model): iX1
├── Mercedes (1 model): EQA
├── Volvo (1 model): XC40 Recharge
├── Audi (1 model): e-tron
├── OLA (1 model): S1 Pro Scooter
└── Ather (1 model): 450X Scooter
```

**Total: 24 vehicles** covering cars, SUVs, and electric scooters available in India.

---

## Helper Functions

### `findById(id: String): VehicleProfile?`
Finds a vehicle by its unique ID. Used when loading a previously saved vehicle selection.

```kotlin
// Example: User opens app, their saved vehicle ID is "tata_nexon_lr"
val vehicle = VehicleProfile.findById("tata_nexon_lr")
// Returns: VehicleProfile(id="tata_nexon_lr", name="Nexon EV Max (Long Range)", ...)
```

### `groupedByManufacturer(): Map<String, List<VehicleProfile>>`
Groups all vehicles by their manufacturer. Used to create the dropdown menus in `VehicleInputFragment`.

```kotlin
val grouped = VehicleProfile.groupedByManufacturer()
// Returns: { "Tata" -> [Nexon LR, Nexon SR, Punch, ...], "MG" -> [ZS EV, Comet, ...], ... }
```

---

## How It Connects to Other Files

```
VehicleProfile.kt
    │
    ├──▶ RangeCalculator.kt     — Takes a VehicleProfile to calculate range
    │                              based on battery %, AC, driving mode, temperature
    │
    ├──▶ VehicleInputFragment.kt — Displays the vehicle selection dropdown UI
    │                              Uses groupedByManufacturer() and findById()
    │
    └──▶ MapFragment.kt          — Receives the selected vehicle's range
                                   to filter charging stations on the map
```

---

## Flow Diagram: How Vehicle Selection Works

```
User opens "Enter Vehicle Details" screen
         │
         ▼
VehicleInputFragment calls VehicleProfile.groupedByManufacturer()
         │
         ▼
Dropdown shows manufacturers: [Tata, MG, Hyundai, ...]
         │
         ▼ User picks "Tata"
         │
Dropdown shows models: [Nexon EV Max, Nexon EV, Punch, Tiago, ...]
         │
         ▼ User picks "Nexon EV Max"
         │
selectedVehicle = VehicleProfile(id="tata_nexon_lr", battery=40.5kWh, ...)
         │
         ▼
RangeCalculator.calculateRange(vehicle, batteryPercent=80%, acOn=true, ...)
         │
         ▼
Estimated Range: ~285 km (shown to user)
         │
         ▼ User taps "Apply Filter"
         │
MapFragment receives range_filter_km = 285.0
         │
         ▼
MarkerUtils hides all charging stations beyond 285 km from user
```

---

## Why Efficiency Is Important

The `efficiencyKwhPer100Km` field is the most critical number for range calculation:

- **Lower number = more efficient** (e.g., Ather 450X at 3.4 kWh/100km)
- **Higher number = less efficient** (e.g., Audi e-tron at 24.0 kWh/100km)

The `RangeCalculator` divides the available battery energy by this efficiency number to get the estimated range:

```
Range = (BatteryCapacity × BatteryPercent/100) ÷ (Efficiency/100)
      = (40.5 kWh × 80/100) ÷ (12.5/100)
      = 32.4 kWh ÷ 0.125 kWh/km
      = 259.2 km (before real-world corrections)
```

---

## Key Design Decisions

1. **Hardcoded data, not from API**: Vehicle data is stored directly in code rather than fetched from a server. This means the app works offline and loads instantly, but new vehicles require an app update.

2. **India-focused**: Only vehicles sold in India are included. Efficiency values are tuned for Indian driving conditions (city traffic, heat, AC usage).

3. **Includes scooters**: OLA S1 Pro and Ather 450X are electric scooters with much smaller batteries (3-4 kWh vs 30-95 kWh for cars).
