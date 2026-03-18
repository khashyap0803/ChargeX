# VehicleProfile.kt

> **File**: `app/src/main/java/net/vonforst/evmap/model/VehicleProfile.kt`  
> **Purpose**: Contains a hardcoded database of popular Indian EV models with their battery, efficiency, and **physics specifications** for accurate energy and range calculations.

---

## What Is This File?

`VehicleProfile` is a **data class** that represents one electric vehicle model. It stores the vehicle's name, manufacturer, battery size, official range, energy efficiency, and **physics parameters** (mass, frontal area, drag coefficient). The app uses this data to calculate how far the user can drive on their current battery charge and whether a route is energy-feasible.

Think of it like a **vehicle catalog** built into the app — the user picks "Tata Nexon EV Max" from a dropdown, and the app knows it has a 40.5 kWh battery, weighs 1580 kg, and has a frontal area of 2.3 m².

---

## VehicleType Enum

```kotlin
enum class VehicleType { SCOOTER, CAR, SUV }
```

Categorizes vehicles so the energy model can apply type-specific physics:
- **SCOOTER**: No AC, low mass (~108 kg), small frontal area (0.5 m²), 0.05 kW electronics
- **CAR**: Has AC (1.8 kW), medium mass (~1200-1600 kg), 2.0-2.3 m² frontal area
- **SUV**: Has AC (1.8 kW), heavier mass (~1700-2500 kg), 2.5-2.8 m² frontal area

---

## Data Class Fields

```kotlin
data class VehicleProfile(
    val id: String,                      // Unique identifier, e.g. "tata_nexon_lr"
    val name: String,                    // Display name, e.g. "Nexon EV Max (Long Range)"
    val manufacturer: String,            // Brand, e.g. "Tata"
    val batteryCapacityKwh: Double,      // Total battery size in kWh, e.g. 40.5
    val officialRangeKm: Double,         // ARAI/WLTP rated range in km, e.g. 437
    val efficiencyKwhPer100Km: Double,   // Energy consumption per 100 km, e.g. 12.5
    val vehicleType: VehicleType,        // SCOOTER, CAR, or SUV
    val curbWeightKg: Double,            // Vehicle weight without passengers, in kg
    val frontalAreaM2: Double,           // Frontal cross-section area in m²
    val dragCoefficient: Double          // Aerodynamic drag coefficient (Cd)
)
```

### Computed Properties

| Property | Logic | Purpose |
|----------|-------|---------|
| `hasAC` | `vehicleType != VehicleType.SCOOTER` | Scooters don't have AC — used to skip AC penalty in energy calculations |

### What Each Field Means

| Field | Example (Car) | Example (Scooter) | Meaning |
|-------|--------------|-------------------|---------|
| `id` | `"tata_nexon_lr"` | `"ather_450x"` | Internal unique key |
| `vehicleType` | `CAR` | `SCOOTER` | Determines physics model parameters |
| `curbWeightKg` | `1580.0` | `108.0` | Vehicle weight — dominant factor in energy use |
| `frontalAreaM2` | `2.3` | `0.5` | Air resistance area — cars are 4-5× larger than scooters |
| `dragCoefficient` | `0.35` | `0.9` | Aerodynamic shape factor |
| `hasAC` | `true` | `false` | Whether AC penalty applies |

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

### Physics Parameters by Vehicle Type

| Vehicle | Type | Mass (kg) | Frontal Area (m²) | Cd | AC |
|---------|------|-----------|-------------------|-----|-----|
| Ather 450X | SCOOTER | 108 | 0.5 | 0.90 | No |
| OLA S1 Pro | SCOOTER | 125 | 0.5 | 0.85 | No |
| Tata Tiago EV | CAR | 1100 | 2.0 | 0.32 | Yes |
| Tata Nexon EV Max | CAR | 1580 | 2.3 | 0.35 | Yes |
| MG ZS EV | SUV | 1560 | 2.5 | 0.35 | Yes |
| Audi e-tron | SUV | 2520 | 2.65 | 0.28 | Yes |

---

## Helper Functions

### `findById(id: String): VehicleProfile?`
Finds a vehicle by its unique ID. Used when loading a previously saved vehicle selection.

```kotlin
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
    ├──▶ RangeCalculator.kt     — Takes a VehicleProfile to calculate range AND
    │                              energy consumption using per-vehicle physics
    │                              (mass, frontal area, drag, AC capability)
    │
    ├──▶ VehicleInputFragment.kt — Displays the vehicle selection dropdown UI
    │                              Uses groupedByManufacturer() and findById()
    │
    ├──▶ NavigationFragment.kt   — Uses vehicle.hasAC to determine AC penalty
    │                              in energy feasibility calculation
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
         ▼ User picks "Ather"
         │
Dropdown shows models: [450X]
         │
         ▼ User picks "450X"
         │
selectedVehicle = VehicleProfile(
    id="ather_450x", battery=3.7kWh, vehicleType=SCOOTER,
    curbWeightKg=108, frontalAreaM2=0.5, hasAC=false
)
         │
         ▼
RangeCalculator.calculateRange(vehicle, batteryPercent=5%, acOn=false, ...)
         │
         ▼
Estimated Range: ~4.5 km (shown to user)
         │
         ▼ User taps "Apply Filter"
         │
MapFragment receives range_filter_km = 4.5
         │
         ▼
MarkerUtils hides all charging stations beyond 4.5 km from user
```

---

## Why Physics Parameters Matter

Previously, `RangeCalculator` used **hardcoded car values** (1600 kg, 2.3 m², 1.8 kW AC) for ALL vehicles. This caused the Ather 450X (108 kg scooter) to show 0.3 kWh energy for a 1.8 km trip — **20× too high**. The correct value was ~0.015 kWh.

With per-vehicle physics:
- **Scooters**: 108 kg + 75 kg rider = 183 kg total mass, 0.5 m² frontal area, no AC
- **Cars**: 1580 kg + 75 kg driver = 1655 kg total mass, 2.3 m² frontal area, 1.8 kW AC
- **SUVs**: 2520 kg + 75 kg driver = 2595 kg total mass, 2.65 m² frontal area, 1.8 kW AC

---

## Key Design Decisions

1. **Hardcoded data, not from API**: Vehicle data is stored directly in code rather than fetched from a server. This means the app works offline and loads instantly, but new vehicles require an app update.

2. **India-focused**: Only vehicles sold in India are included. Efficiency values are tuned for Indian driving conditions (city traffic, heat, AC usage).

3. **Includes scooters**: OLA S1 Pro and Ather 450X are electric scooters with much smaller batteries (3-4 kWh vs 30-95 kWh for cars).

4. **Per-vehicle physics**: Each vehicle defines its own mass, frontal area, and drag coefficient so the energy model produces accurate results across the full range from 108 kg scooters to 2520 kg SUVs.

5. **hasAC computed property**: Returns `false` for scooters, `true` for cars/SUVs. This prevents the energy model from adding a 1.8 kW AC penalty to vehicles that don't have air conditioning.
