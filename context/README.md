# ChargeX — Context & Documentation

This directory contains detailed documentation for every important source file in the ChargeX application. Each `.md` file corresponds to a Kotlin source file and explains its purpose, architecture, code flow, and how it connects to other parts of the app.

## 📁 File Index

### Core App
| File | Description |
|------|-------------|
| [ChargeXApplication.md](ChargeXApplication.md) | Application class — app startup, crash reporting, background workers |
| [MapsActivity.md](MapsActivity.md) | Main activity — navigation, intent handling, deep linking |

### Model Layer (Data Classes & Business Logic)
| File | Description |
|------|-------------|
| [VehicleProfile.md](VehicleProfile.md) | EV vehicle database with battery specs, **VehicleType enum**, and **per-vehicle physics parameters** (mass, drag, frontal area) |
| [ChargersModel.md](ChargersModel.md) | Core data models — `ChargeLocation`, `Chargepoint`, `Address`, etc. |
| [RangeCalculator.md](RangeCalculator.md) | **Physics-based** range and energy estimation engine with per-vehicle parameters and India-specific corrections |

### API Layer (Network & Data Sources)
| File | Description |
|------|-------------|
| [ChargepointApi.md](ChargepointApi.md) | Interface for all charging station data providers |
| [OpenChargeMapApi.md](OpenChargeMapApi.md) | OpenChargeMap implementation — primary data source |
| [RouteService.md](RouteService.md) | Routing engine — Google Directions + OSRM fallback |

### Fragment Layer (UI Screens)
| File | Description |
|------|-------------|
| [MapFragment.md](MapFragment.md) | Main map screen — markers, range filtering, **vehicle data forwarding**, `clearAll()` lifecycle cleanup |
| [NavigationFragment.md](NavigationFragment.md) | In-app route display, navigation, and **energy feasibility card** with vehicle-aware AC handling |
| [VehicleInputFragment.md](VehicleInputFragment.md) | Vehicle selection (24 models), battery slider (5-100%), and range calculation UI |

### ViewModel Layer
| File | Description |
|------|-------------|
| [MapViewModel.md](MapViewModel.md) | Business logic for the map — data loading, favorites, filtering |

### UI Utilities
| File | Description |
|------|-------------|
| [MarkerUtils.md](MarkerUtils.md) | Map marker management — icons, clustering, range filtering, **`clearAll()` for lifecycle cleanup** |

### Storage Layer
| File | Description |
|------|-------------|
| [PreferenceDataSource.md](PreferenceDataSource.md) | User settings and preferences |
| [Database.md](Database.md) | Room database — schema, DAOs, migrations |

### Build & Configuration
| File | Description |
|------|-------------|
| [BuildGradle.md](BuildGradle.md) | Build configuration — flavors, API keys, dependencies |

### Architecture
| File | Description |
|------|-------------|
| [METHODOLOGY_AND_ARCHITECTURE.md](METHODOLOGY_AND_ARCHITECTURE.md) | Complete system architecture, methodology, workflows, and function-level detail |

---

## 🏗️ Architecture Overview

```
┌──────────────────────────────────────────────────────────────┐
│                      ChargeX App                              │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐  │
│  │  Fragments   │───▶│  ViewModels  │───▶│  API + Storage  │  │
│  │  (UI Layer)  │◀───│  (Logic)     │◀───│  (Data Layer)   │  │
│  └─────────────┘    └──────────────┘    └─────────────────┘  │
│                                                               │
│  MapFragment ─────── MapViewModel ────── ChargepointApi      │
│  NavigationFrag ──── RouteService ────── Google Directions    │
│       └── RangeCalculator.isRouteFeasible() + VehicleProfile │
│  VehicleInputFrag ── RangeCalculator ─── VehicleProfile DB   │
│  FilterFragment ──── FiltersModel ────── PreferenceDataSource│
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐│
│  │  UI Utilities: MarkerUtils (clearAll), IconGenerators     ││
│  └──────────────────────────────────────────────────────────┘│
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐│
│  │  Storage: Room Database, SharedPreferences, Cache        ││
│  └──────────────────────────────────────────────────────────┘│
└──────────────────────────────────────────────────────────────┘
```

## 🔑 Key Concepts

- **ChargeLocation**: A single charging station (site) on the map
- **Chargepoint**: One connector/socket at a station (a station can have many)
- **VehicleProfile**: Specs for an EV model — battery size, efficiency, **VehicleType** (SCOOTER/CAR/SUV), **physics params** (mass, drag, frontal area), **hasAC**
- **RangeCalculator**: Estimates range using efficiency factors AND calculates energy using a **physics-based model** with per-vehicle parameters
- **MarkerUtils**: Manages all the pins on the map, including hiding out-of-range ones and **clearing stale markers** via `clearAll()`
- **RouteService**: Calculates driving routes using Google Maps API (with OSRM fallback)
- **Energy Feasibility**: NavigationFragment shows arrival battery %, energy required/available, vehicle info, using `vehicle.hasAC` for AC handling
