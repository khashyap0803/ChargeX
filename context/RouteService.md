# RouteService.kt

> **File**: `app/src/main/java/net/vonforst/evmap/api/RouteService.kt`  
> **Purpose**: Fetches driving routes between two locations, using Google Directions API (primary) with OSRM as fallback.

---

## What Is This File?

`RouteService` is a **singleton object** that calculates driving routes. When a user taps "Navigate" on a charging station, this service:

1. Calls **Google Directions API** first (best for India, real-time traffic)
2. Falls back to **OSRM** (free, but less accurate for India) if Google fails
3. Returns the route as a list of GPS coordinates + distance + duration

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
Google Directions API Response:
├── GoogleDirectionsResponse
│   ├── status: String ("OK", "REQUEST_DENIED", etc.)
│   └── routes: List<GoogleRoute>
│       └── legs: List<GoogleLeg>
│           ├── distance: GoogleTextValue (value in meters)
│           ├── duration: GoogleTextValue (value in seconds)
│           └── overviewPolyline: GooglePolyline (encoded path)

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
User taps "Navigate" on a charging station
         │
         ▼
NavigationFragment calls RouteService.getRoute(
    originLat, originLng,      ← User's current GPS location
    destLat, destLng,          ← Charging station location
    googleApiKey               ← API key from gradle.properties
)
         │
         ▼
┌──────────────────────────────────┐
│ Is Google API key available?     │
│                                  │
│  YES → Try Google Directions API │
│         │                        │
│         ├─ SUCCESS → Return route│
│         │                        │
│         └─ FAILED → Fall to OSRM│
│                                  │
│  NO → Skip Google, go to OSRM   │
└──────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────┐
│ Try OSRM (free, no key needed)   │
│                                  │
│  SUCCESS → Return route          │
│  FAILED  → Return null (error)   │
└──────────────────────────────────┘
```

---

## The Main Function: `getRoute()`

```kotlin
suspend fun getRoute(
    originLat: Double, originLng: Double,    // Where you are
    destLat: Double, destLng: Double,        // Where you want to go
    googleApiKey: String? = null              // API key (optional)
): DecodedRoute?                              // Returns route or null
```

This function is `suspend` — it runs asynchronously (doesn't block the UI thread). It's called from a coroutine in `NavigationFragment`.

---

## Google Directions API

```kotlin
private suspend fun getGoogleRoute(
    originLat: Double, originLng: Double,
    destLat: Double, destLng: Double,
    apiKey: String
): DecodedRoute?
```

- **URL**: `https://maps.googleapis.com/maps/api/directions/json`
- **Parameters**: `origin`, `destination`, `mode=driving`, `region=in`
- **`region=in`**: Tells Google to optimize for India (better route choices)
- **Polyline encoding**: Google uses **polyline5** (precision 1e-5)

### Error Handling:
| Status | Meaning |
|--------|---------|
| `OK` | Route found successfully |
| `REQUEST_DENIED` | API key invalid or Directions API not enabled |
| `OVER_QUERY_LIMIT` | Too many requests, quota exceeded |
| `ZERO_RESULTS` | No route exists between the two points |

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

Routes are transmitted as encoded strings to save bandwidth. The service includes two decoders:

### `decodePolyline5()` — For Google (precision 1e-5)
```
Input:  "a~l~Fjk~uOwHJy@P"  (compact encoded string)
Output: [(38.5, -120.2), (40.7, -120.95), ...]  (GPS coordinates)
```

### `decodePolyline6()` — For OSRM (precision 1e-6)
Same algorithm but divides by 1,000,000 instead of 100,000 for higher precision.

---

## Networking Setup

```kotlin
// Two Retrofit API clients, one for each service:

Google: Retrofit → "https://maps.googleapis.com/"
  └── MoshiConverterFactory (for JSON parsing)

OSRM: Retrofit → "https://router.project-osrm.org/"
  └── MoshiConverterFactory
```

Both use **OkHttp** for HTTP requests and **Moshi** for JSON parsing.

---

## Debug Logging

Every step is logged with tag `RouteService`:
```
RouteService: ========== ROUTE REQUEST ==========
RouteService: Origin: (17.3850, 78.4867)
RouteService: Destination: (17.4399, 78.4983)
RouteService: Google API key present: true
RouteService: >>> Attempting Google Directions API...
RouteService: [Google] Distance: 12.3 km (12300 m)
RouteService: [Google] Duration: 25 mins (1500 s)
RouteService: ✅ Google Directions API SUCCESS
```

---

## How It Connects to Other Files

```
RouteService.kt
    │
    ├──◀ NavigationFragment.kt  — Calls getRoute() when user opens
    │                              the navigation screen
    │
    ├──◀ build.gradle.kts       — Provides the Google API key via
    │                              gradle.properties
    │
    └──▶ DecodedRoute            — Returned to NavigationFragment
                                   for display (polyline on map,
                                   distance text, duration text)
```

---

## Key Design Decisions

1. **Google first, OSRM fallback**: Google gives the best routes for India (toll roads, real-time traffic, correct one-ways). OSRM uses OpenStreetMap data which can be outdated in India.

2. **`region=in` parameter**: Explicitly tells Google to optimize for Indian routes.

3. **Separate polyline decoders**: Google and OSRM use different polyline precision (5 vs 6 decimal places). Using the wrong decoder would shift coordinates by ~11x.

4. **Comprehensive error logging**: Every API call, response, and error is logged to help debug routing issues in production.
