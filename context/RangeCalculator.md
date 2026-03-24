# RangeCalculator.kt

> **File**: `app/src/main/java/com/chargex/india/model/RangeCalculator.kt`  
> **Purpose**: Calculates estimated driving range, energy consumption, and route feasibility using a **physics-based model** with per-vehicle parameters tuned for Indian driving conditions.

---

## What Is This File?

`RangeCalculator` is a **singleton object** (only one instance exists) that answers three questions:
1. *"How far can I drive with my current battery?"* → `calculateRange()`
2. *"Can I reach this station?"* → `isStationReachable()`
3. *"Is this route feasible? What battery will I arrive with?"* → `isRouteFeasible()` + `calculateEnergyConsumption()`

It uses a **physics-based energy consumption model** that accounts for:
- 🏋️ Vehicle mass (108 kg scooter vs 2520 kg SUV)
- 🌬️ Aerodynamic drag (frontal area × drag coefficient)
- 🌡️ Temperature (Indian heat drains battery faster)
- ❄️ AC usage (scooters: none, cars: 1.8 kW)
- 🚗 Driving mode (city/highway/mixed)
- 📉 Real-world vs official range gap (15% haircut on ARAI ratings)

---

## The Five Main Functions

### 1. `calculateRange()` — Simplified range estimate

```kotlin
fun calculateRange(
    vehicle: VehicleProfile,      // Which vehicle (includes physics params)
    batteryPercent: Double,        // Current charge (0-100)
    acOn: Boolean = true,          // Is AC running? (ignored for scooters via hasAC)
    drivingMode: String = "mixed", // "city", "highway", or "mixed"
    temperatureC: Double = 35.0    // Outside temperature in °C
): Double                          // Returns: estimated range in km
```

Uses stacking correction factors on the vehicle's base efficiency. Good for quick UI estimates.

### 2. `calculateEnergyConsumption()` — Physics-based energy model

```kotlin
fun calculateEnergyConsumption(
    vehicle: VehicleProfile,
    distanceKm: Double,
    avgSpeedKmh: Double,
    temperatureC: Double = 35.0,
    acOn: Boolean = true,
    gradientPercent: Double = 0.0,
    trafficMultiplier: Double = 1.0,
    isOffline: Boolean = false // Forces heuristic fallbacks
): EnergyConsumptionResult         // Returns: detailed breakdown of energy consumed
```

This is the **detailed physics model** that computes energy from first principles:

```
Energy = Rolling Resistance + Aerodynamic Drag + Acceleration + Electronics + AC

Where:
├── Rolling Resistance = mass × g × Crr × distance
│   (mass = curbWeightKg + 75 kg rider/driver)
│   (Crr = 0.015 for Indian roads)
│
├── Aerodynamic Drag = 0.5 × ρ × Cd × A × v² × distance
│   (ρ = 1.225 kg/m³ air density)
│   (Cd = vehicle.dragCoefficient)
│   (A = vehicle.frontalAreaM2)
│   (v = distance / time → average speed)
│
├── Acceleration = mass × a × distance × factor
│   (assumes 0.5 m/s² avg acceleration for city driving)
│
├── Electronics = power × time
│   (Scooter: 0.05 kW | Car/SUV: 0.3 kW)
│
└── AC = power × time  (ONLY if vehicle.hasAC && acOn)
    (Car/SUV: 1.8 kW | Scooter: 0 kW)
```

**Per-vehicle parameter usage:**

| Parameter | Scooter (Ather 450X) | Car (Tata Nexon EV) | Source |
|-----------|---------------------|--------------------|----|
| Total mass | 108 + 75 = 183 kg | 1580 + 75 = 1655 kg | `vehicle.curbWeightKg` |
| Frontal area | 0.5 m² | 2.3 m² | `vehicle.frontalAreaM2` |
| Drag coefficient | 0.9 | 0.35 | `vehicle.dragCoefficient` |
| Electronics | 0.05 kW | 0.3 kW | Based on `vehicleType` |
| AC power | 0 kW | 1.8 kW | Based on `vehicle.hasAC` |

### 3. `isRouteFeasible()` — Complete route feasibility check

```kotlin
fun isRouteFeasible(
    vehicle: VehicleProfile,
    batteryPercent: Double,
    routeDistanceKm: Double,
    routeDurationMinutes: Double,
    temperatureC: Double = 35.0,
    acOn: Boolean = true,
    safetyMargin: Double = 0.10,
    trafficMultiplier: Double = 1.0
): RouteFeasibilityResult
```

Returns a `FeasibilityResult` with:
- `isFeasible`: Boolean — can the vehicle make the trip?
- `energyRequired`: Double — kWh needed for the route
- `energyAvailable`: Double — kWh in the battery
- `arrivalBatteryPercent`: Double — remaining battery at destination
- `statusMessage`: String — human-readable status ("✅ Comfortable", "⚠️ Tight", "❌ Insufficient")
- `trafficFactor`: String — traffic condition description

### 4. `isStationReachable()` — Can I reach a station?

```kotlin
fun isStationReachable(
    vehicle: VehicleProfile,
    batteryPercent: Double,
    distanceKm: Double,
    acOn: Boolean = true,
    drivingMode: String = "mixed",
    temperatureC: Double = 35.0
): Boolean                      // true = you can reach it
```

Includes a **10% safety margin** — only says "yes" if you can reach using 90% of range.

### 5. `remainingBatteryPercent()` — Battery left after a trip

```kotlin
fun remainingBatteryPercent(
    vehicle: VehicleProfile,
    batteryPercent: Double,
    distanceKm: Double, ...
): Double  // Returns: battery % remaining after driving distanceKm
```

---

## How Range Calculation Works (Simplified — `calculateRange`)

```
Step 1: Calculate available energy
   availableEnergy = batteryCapacityKwh × (batteryPercent / 100)

Step 2: Get base efficiency
   efficiencyPerKm = efficiencyKwhPer100Km / 100

Step 3: Apply driving mode
   City:    × 0.90 (regen braking helps in stop-start)
   Highway: × 1.15 (more air drag at high speed)
   Mixed:   × 1.00 (baseline)

Step 4: Apply temperature penalty
   > 40°C: × 1.12 | > 35°C: × 1.08 | > 28°C: × 1.03
   < 15°C: × 1.10 | else: × 1.00

Step 5: Apply AC penalty (ONLY if vehicle.hasAC && acOn)
   AC On:  × 1.10 (+10% energy use)
   AC Off: × 1.00

Step 6: Apply real-world correction
   × 1.15 (ARAI ratings are ~15% optimistic)

Step 7: Calculate final range
   range = availableEnergy / adjustedEfficiency
```

### Worked Example: Ather 450X Scooter, 5%, City, 35°C

```
Available energy: 3.7 × 0.05 = 0.185 kWh
Base efficiency:  3.4 / 100  = 0.034 kWh/km
After city mode:  0.034 × 0.90 = 0.0306 kWh/km
After 35°C heat:  0.0306 × 1.08 = 0.03305 kWh/km
After AC:         0.03305 × 1.00 = 0.03305 kWh/km  ← NO AC (scooter!)
After real-world: 0.03305 × 1.15 = 0.03800 kWh/km

Range = 0.185 / 0.03800 ≈ 4.5 km
```

---

## How It Connects to Other Files

```
RangeCalculator.kt
    │
    ├──▶ VehicleInputFragment.kt  — Calls calculateRange() when user
    │                                adjusts battery slider or picks a vehicle
    │
    ├──▶ NavigationFragment.kt    — Calls isRouteFeasible() to display
    │                                energy feasibility card with arrival
    │                                battery %, energy required/available
    │
    ├──▶ VehicleProfile.kt        — Uses per-vehicle physics params:
    │                                curbWeightKg, frontalAreaM2,
    │                                dragCoefficient, vehicleType, hasAC
    │
    └──▶ MarkerUtils.kt           — The calculated range (in km) is passed
                                     to the map to hide stations beyond range
```

---

## Key Design Decisions

1. **Dual calculation paths**: `calculateRange()` uses simple efficiency-based math for quick UI display. `calculateEnergyConsumption()` uses full physics for accurate route feasibility.

2. **Per-vehicle physics**: Each vehicle defines its own mass, drag, and frontal area. Previously, hardcoded 1600 kg car defaults caused 20× overestimation for 108 kg scooters.

3. **No AC for scooters**: The `hasAC` property from VehicleProfile ensures scooter energy calculations don't include a phantom 1.8 kW AC load.

4. **Conservative estimates**: Multiple stacking penalties (temperature + AC + real-world) give realistic "worst case" range, preventing users from getting stranded.

5. **India-optimized**: Temperature ranges and defaults (35°C, AC on for cars) reflect typical Indian driving conditions. The 15% correction accounts for ARAI rating optimism.

6. **Zero safety margin for feasibility display**: `isRouteFeasible` uses `safetyMargin=0.0` because `calculateRange` already applies real-world corrections. Adding a margin on top makes small batteries (3.7 kWh at 5%) show negative energy.
