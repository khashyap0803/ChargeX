# Android Auto Integration

> **Directory**: `app/src/main/java/com/chargex/india/auto/`  
> **Purpose**: Provides the Android Auto (and Android Automotive OS) implementation for ChargeX, allowing users to find and navigate to charging stations directly from their car's infotainment display.

---

## 🚘 Architecture: Android for Cars App Library

ChargeX uses the official `androidx.car.app` library, meaning it does not draw raw Android Views. Instead, it pushes standardized **Templates** (e.g., `MapWithContentTemplate`, `ListTemplate`, `PaneTemplate`) to the car screen.

### Core Entry Point
- **`CarAppService.kt`**: The background service that the car headunit connects to.
  - Generates the `Session` (which survives configuration changes).
  - Handles foreground service location polling (combining phone GPS and Car Hardware GPS via `CarSensors`).
  - Handles deep link intents (e.g., `find_charger` scheme from the phone).
  - Returns the first `Screen` to display.

### Screen Management
Screens are pushed and popped from a `ScreenManager` stack.

| Core Screen | Template Used | Purpose |
|-------------|---------------|---------|
| `MapScreen.kt` | `MapWithContentTemplate` | The main interface. Shows a map surface alongside a list of nearby chargers. Handles map panning, zoom, compass tracking, and filtering. |
| `ChargerDetailScreen.kt` | `PaneTemplate` | Shows details of a single selected charging station (connectors, price, fault status) and a "Navigate" button. |
| `FilterScreen.kt` | `ListTemplate` | Allows adjusting connector and power filters directly from the car display. |
| `PlaceSearchScreen.kt` | `SearchTemplate` | Triggers a keyboard search for addresses/cities. |
| `PermissionScreen.kt` | `MessageTemplate` | Asks the user to accept location tracking on their phone if not already granted. |

---

## 📍 Location & Map Surface

### 1. Hybrid GPS
`CarAppService` intelligently requests location updates:
- Attempts to read `CarHardwareLocation` via Android Auto sensors first (which uses the car's built-in GPS antenna, usually more accurate).
- Falls back to `FusionEngine` (Smartphone GPS) if the car doesn't support Location APIs.

### 2. Map Surface (API Level 7+)
Android Auto doesn't natively render OSM tiles out-of-the-box. ChargeX uses `MapSurfaceCallback.kt` to render an `AnyMap` instance (MapLibre/Mapbox) onto the virtual car display.
- Marker clicks, map panning, and zooming are handled by passing touch events from the car display back to the map rendering thread.
- `MarkerManager` handles drawing the colored pins on the Auto surface.

---

## ⚡ Energy & Sensor Integration

ChargeX integrates deeply with EV data when running on supported vehicles (especially native Android Automotive OS):
- **`CarSensors.kt`**: Connects to the vehicle's CAN bus abstractions to read parameters like `Compass` heading.
- **`EnergyLevelListener`**: On supported Android Automotive headunits with proper permissions (`CAR_ENERGY`), `MapScreen` observes the vehicle's live battery percentage to calculate range dynamically without needing the user to move a slider.

---

## 🔗 Phone-to-Car Syncing
The Auto application shares the Room database (`AppDatabase`) and `PreferenceDataSource` with the mobile app:
- Active filters changed on the phone apply to the car automatically.
- Favorites saved on the phone appear in the car.
- Offline navigation OSM graphs (`OfflineRouteManager`) downloaded on the mobile app are perfectly usable by the car, enabling completely offline navigation directly on the car dashboard.
