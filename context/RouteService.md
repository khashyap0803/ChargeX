# RouteService.kt

> **File**: `app/src/main/java/com/chargex/india/api/RouteService.kt`  
> **Purpose**: Fetches driving routes between two locations and reachable range polygons, using TomTom APIs (primary) with OSRM, GraphHopper (offline), and Haversine as fallbacks.

---

## What Is This File?

`RouteService` is a **singleton object** that calculates driving routes and reachable ranges. When a user taps "Navigate" or needs range boundaries, this service:

1. Calls **TomTom Routing/Reachable Range API** first (highly accurate, traffic aware)
2. Falls back to **OSRM** (free online routing) if TomTom fails
3. Falls back to **GraphHopper** (on-device offline routing) if online fails
4. Ultimate fallback: **Haversine formula** (straight line estimate)

---

## Key Data Classes

### `DecodedRoute` — The result of a route calculation

```kotlin
data class DecodedRoute(
    val points: List<Pair<Double, Double>>,  // GPS coordinates [(lat, lng), ...]
    val distanceMeters: Double,              // Total distance in meters
    val durationSeconds: Double              // Total travel time in seconds
) {
    val distanceKm: Double get() = distanceMeters / 1000.0      // Convert to km
    val durationMinutes: Double get() = durationSeconds / 60.0   // Convert to minutes
}
```

### API Response Classes

```
```
TomTom Routing API Response:
├── TomTomRouteResponse
│   └── routes: List<TomTomRoute>
│       ├── summary: TomTomSummary (length, travelTimeInSeconds)
│       └── legs: List<TomTomLeg>
│           └── points: List<TomTomPoint>

TomTom Reachable Range API Response:
├── TomTomReachableRangeResponse
│   └── reachableRange: TomTomReachableRange
│       ├── center: TomTomPoint
│       └── boundary: List<TomTomPoint>


OSRM API Response:
├── OsrmRouteResponse
│   ├── code: String ("Ok" or error)
│   └── routes: List<OsrmRoute>
│       ├── distance: Double (meters)
│       ├── duration: Double (seconds)
│       └── geometry: String (encoded polyline)
```

---

## How Route Fetching Works

```
User requests a route (Origin to Destination)
         │
         ▼
┌──────────────────────────────────┐
│ Try TomTom Routing API           │
│ SUCCESS → Return route           │
│ FAILED  → Fall to OSRM           │
└──────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Try OSRM (Online fallback)       │
│ SUCCESS → Apply 1.5x duration    │
│           penalty & Return       │
│ FAILED  → Fall to GraphHopper    │
└──────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Try GraphHopper (Offline)        │
│ SUCCESS → Return route           │
│ FAILED  → Fall to Haversine      │
└──────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Try Haversine Estimate           │
│ SUCCESS → Return straight line   │
└──────────────────────────────────┘
```

---

## The Main Function: `getRoute()`

```kotlin
suspend fun getRoute(
    originLat: Double, originLng: Double,    // Where you are
    destLat: Double, destLng: Double,        // Where you want to go
    googleApiKey: String? = null             // Legacy arg, unused
): DecodedRoute                              // Returns best route
```

This function is `suspend` — it runs asynchronously (doesn't block the UI thread). It's called from a coroutine in `NavigationFragment`.

---

## TomTom Routing API

```kotlin
private suspend fun getTomTomRoute(
    originLat: Double, originLng: Double,
    destLat: Double, destLng: Double
): DecodedRoute?
```

- **URL**: `https://api.tomtom.com/routing/1/calculateRoute/`
- **Why TomTom?**: Preferred over Google Maps due to billing constraints while still offering high accuracy and traffic awareness.
- **Data Returned**: Direct Lat/Lng objects (no complex polyline decoding needed).

---

## TomTom Reachable Range API

```kotlin
suspend fun getReachableRange(
    originLat: Double, originLng: Double,
    distanceBudgetMeters: Int
): List<Pair<Double, Double>>?
```

Calculates an actual road-network polygon showing how far a vehicle can travel. Used by `MarkerManager` to filter out unreachable stations dynamically.

---

## OSRM Fallback

```kotlin
private suspend fun getOsrmRoute(
    originLat: Double, originLng: Double,
    destLat: Double, destLng: Double
): DecodedRoute?
```

- **URL**: `https://router.project-osrm.org/route/v1/driving/{lng},{lat};{lng},{lat}`
- **Free, no API key needed**
- **Polyline encoding**: OSRM uses **polyline6** (precision 1e-6)
- ⚠️ OSRM data for India can be outdated/inaccurate

> **Important**: OSRM uses `lng,lat` order (reversed from Google's `lat,lng`)

---

## Polyline Decoding

### `decodePolyline6()` — For OSRM (precision 1e-6)
OSRM transmits route geometries as an encoded string. This decodes it back into GPS coordinates.
Same algorithm but divides by 1,000,000 instead of 100,000 for higher precision.

---

## Networking Setup

```kotlin
// Two Retrofit API clients, one for each service:

TomTom: Retrofit → "https://api.tomtom.com/"
  └── MoshiConverterFactory (for JSON parsing)

OSRM: Retrofit → "https://router.project-osrm.org/"
  └── MoshiConverterFactory
```

Both use **OkHttp** for HTTP requests and **Moshi** for JSON parsing. GraphHopper is accessed locally via `OfflineRouteManager`.

---

## Debug Logging

Every step is logged with tag `RouteService`:
```
RouteService: ========== ROUTE REQUEST ==========
RouteService: Origin: (17.3850, 78.4867)
RouteService: Destination: (17.4399, 78.4983)
RouteService: >>> Attempting TomTom Routing API...
RouteService: ✅ TomTom API SUCCESS
RouteService:    Distance: 12.30 km
RouteService:    Duration: 25.0 min
RouteService:    Polyline points: 231
```

---

## How It Connects to Other Files

```
RouteService.kt
    │
    ├──◀ NavigationFragment.kt  — Calls getRoute() when user opens
    │                              the navigation screen
    │
    ├──◀ MarkerUtils.kt          — Calls getReachableRange() for polygon filter
    │
    └──▶ DecodedRoute            — Returned to NavigationFragment:
                                   ├── polyline points → drawn on map
                                   ├── distanceKm → displayed + passed to
                                   │   RangeCalculator.isRouteFeasible()
                                   └── durationMinutes → displayed + passed to
                                       calculateEnergyConsumption() for
                                       physics-based energy card
```

---

## Key Design Decisions

1. **TomTom first, 4-tier fallback**: Replaced Google Maps entirely to save costs, using a 4-layer fallback prioritizing online accuracy first, then offline accuracy (GraphHopper), and finally basic estimates (Haversine).

2. **OSRM Duration Penalty**: OSRM often underestimates travel time in Indian traffic, so a 1.5x penalty block (`realisticDurationSeconds = osrmRoute.durationSeconds * 1.5`) was added to compensate.

4. **Reachable Range Context**: Instead of just straight-line `isInRange()`, `RouteService` facilitates actual road-network range boundaries (`getReachableRange()`), significantly improving range feasibility projections.

5. **Route data feeds energy model**: `DecodedRoute.distanceKm` and `durationMinutes` are passed to `RangeCalculator.isRouteFeasible()` by `NavigationFragment`, which uses them for physics-based energy consumption calculation.
