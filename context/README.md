# ChargeX — Context & Documentation

This directory contains detailed documentation for every important source file in the ChargeX application. Each `.md` file corresponds to a Kotlin source file and explains its purpose, architecture, code flow, and how it connects to other parts of the app.

## 📁 File Index

### Core App
| File | Description |
|------|-------------|
| [EvMapApplication.md](EvMapApplication.md) | Application class — app startup, crash reporting, background workers |
| [MapsActivity.md](MapsActivity.md) | Main activity — navigation, intent handling, deep linking |

### Model Layer (Data Classes & Business Logic)
| File | Description |
|------|-------------|
| [VehicleProfile.md](VehicleProfile.md) | EV vehicle database with battery specs for Indian cars |
| [ChargersModel.md](ChargersModel.md) | Core data models — `ChargeLocation`, `Chargepoint`, `Address`, etc. |
| [RangeCalculator.md](RangeCalculator.md) | Range estimation engine with India-specific corrections |
| [FiltersModel.md](FiltersModel.md) | Filter system for charging station search |

### API Layer (Network & Data Sources)
| File | Description |
|------|-------------|
| [ChargepointApi.md](ChargepointApi.md) | Interface for all charging station data providers |
| [OpenChargeMapApi.md](OpenChargeMapApi.md) | OpenChargeMap implementation — primary data source |
| [RouteService.md](RouteService.md) | Routing engine — Google Directions + OSRM fallback |

### Fragment Layer (UI Screens)
| File | Description |
|------|-------------|
| [MapFragment.md](MapFragment.md) | Main map screen — the heart of the app |
| [NavigationFragment.md](NavigationFragment.md) | In-app route display and navigation |
| [VehicleInputFragment.md](VehicleInputFragment.md) | Vehicle selection and range calculation UI |
| [FilterFragment.md](FilterFragment.md) | Connector type & power filtering UI |

### ViewModel Layer
| File | Description |
|------|-------------|
| [MapViewModel.md](MapViewModel.md) | Business logic for the map — data loading, favorites, filtering |

### UI Utilities
| File | Description |
|------|-------------|
| [MarkerUtils.md](MarkerUtils.md) | Map marker management — icons, clustering, range filtering |
| [IconGenerators.md](IconGenerators.md) | Bitmap generation for charger markers |

### Storage Layer
| File | Description |
|------|-------------|
| [PreferenceDataSource.md](PreferenceDataSource.md) | User settings and preferences |
| [Database.md](Database.md) | Room database — schema, DAOs, migrations |

### Build & Configuration
| File | Description |
|------|-------------|
| [BuildGradle.md](BuildGradle.md) | Build configuration — flavors, API keys, dependencies |

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
│  VehicleInputFrag ── RangeCalculator ─── VehicleProfile DB   │
│  FilterFragment ──── FiltersModel ────── PreferenceDataSource│
│                                                               │
│  ┌──────────────────────────────────────────────────────────┐│
│  │  UI Utilities: MarkerUtils, IconGenerators, Clustering   ││
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
- **VehicleProfile**: Specs for an EV model (battery size, efficiency)
- **RangeCalculator**: Estimates how far a vehicle can go based on battery, weather, driving mode
- **MarkerUtils**: Manages all the pins on the map, including hiding out-of-range ones
- **RouteService**: Calculates driving routes using Google Maps API (with OSRM fallback)
