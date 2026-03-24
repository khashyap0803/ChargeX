# MarkerUtils.kt

> **File**: `app/src/main/java/com/chargex/india/ui/MarkerUtils.kt`  
> **Purpose**: Manages all map markers (charging station pins, clusters, search results) including range-based filtering and lifecycle cleanup.

---

## What Is This File?

`MarkerUtils` is the **central manager for all pins on the map**. Every time you see a colored marker on the map representing a charging station — this file is responsible for:

1. **Adding markers** when new stations come into view
2. **Removing markers** when stations go out of view
3. **Hiding markers** when they're outside the user's battery range
4. **Animating markers** with smooth appear/disappear effects
5. **Generating icons** with the right color, size, and style
6. **Clustering** nearby stations into a single "5+" bubble at low zoom
7. **Clearing all markers** when the view is recreated via `clearAll()`

---

## Class Structure

```kotlin
class MarkerManager(
    val context: Context,
    val map: AnyMap,                    // The map to draw on
    val lifecycle: LifecycleOwner,       // For coroutine scoping
    markerHeight: Int = 48
)
```

---

## Key Properties (What You Can Set)

| Property | Type | Purpose |
|----------|------|---------|
| `chargepoints` | `List<ChargepointListItem>` | Full list of stations from the API |
| `highlighedCharger` | `ChargeLocation?` | Currently selected/highlighted station |
| `filteredConnectors` | `Set<String>?` | Connector types the user wants (e.g., CCS, Type 2) |
| `favorites` | `Set<Long>` | IDs of user's favorite stations (shown with ⭐) |
| `searchResult` | `PlaceWithBounds?` | Place search result marker |
| `rangeFilterKm` | `Float` | Range filter distance in km (0 = disabled) |
| `userLocation` | `LatLng?` | User's current GPS position |
| `reachableBoundary` | `List<Pair<Double, Double>>?` | Reachable boundary polygon from routing API |

When you set any of these properties, the markers **automatically update** (via the setter calling `updateChargepoints()` or `updateChargerIcons()`).

---

## Key Methods

### `clearAll()` — Remove All Markers

```kotlin
fun clearAll() {
    markers.keys.forEach { it.remove() }
    markers.clear()
    clusterMarkers.keys.forEach { it.remove() }
    clusterMarkers.clear()
}
```

**Why this exists**: When the Fragment's view is destroyed and recreated (e.g., after navigating to VehicleInputFragment and back), a NEW MarkerManager is created in `onMapReady()`. The OLD MarkerManager's markers could persist on the map if the MapView is reused. `clearAll()` is called in MapFragment's `onMapReady()` BEFORE creating the new MarkerManager to ensure a clean slate.

**Without this**: Users would see 336 unfiltered markers (from the old instance) stacked on top of the 192 filtered markers (from the new instance), making it look like the filter isn't working.

---

## How Range Filtering Works

When the user enters their vehicle details and applies a range filter:

```
User sets rangeFilterKm = 4.5 and userLocation = (17.385, 78.487)
         │
         ▼
isInRange() is called for EACH charging station:
         │
         ├─ Station A is 2 km away   → IN RANGE → Show marker ✅
         ├─ Station B is 4 km away   → IN RANGE → Show marker ✅
         ├─ Station C is 5 km away   → OUT OF RANGE → HIDE marker ❌
         └─ Station D is 10 km away  → OUT OF RANGE → HIDE marker ❌
```

### The `isInRange()` Function

```kotlin
private fun isInRange(lat: Double, lng: Double): Boolean {
    if (rangeFilterKm <= 0 || userLocation == null) return true

    // Primary: use TomTom reachable polygon if available
    val polygon = reachableBoundary
    if (polygon != null && polygon.size >= 3) {
        return isPointInPolygon(lat, lng, polygon)
    }

    // Fallback: Haversine × road correction factor
    val haversineDist = distanceBetween(
        userLocation!!.latitude, userLocation!!.longitude,
        lat, lng
    ) / 1000.0
    val estimatedRoadDist = haversineDist * 1.4 // ROAD_DISTANCE_FACTOR
    return estimatedRoadDist <= rangeFilterKm
}
```

> **Note**: Uses straight-line (Haversine) distance, not driving distance. A station 4.5 km straight-line away could be 6+ km by road.

---

## How Markers Are Updated

### `updateChargepoints()` — Add/Remove Markers

```
Called when: chargepoints list changes, range filter changes, user location changes
         │
         ▼
1. Filter chargepoints to only in-range stations
         │
         ▼
2. Remove markers for stations that:
   - Disappeared from the API response
   - Are now out of range
   (with smooth animation if visible on screen)
         │
         ▼
3. Add new markers for stations that:
   - Are newly in range
   - Are newly loaded from the API
   (with smooth pop-in animation)
```

### `updateChargerIcons()` — Update Existing Marker Styles

```
Called when: connector filter changes, favorites change, highlighted charger changes
         │
         ▼
For each existing marker on the map:
  - Regenerate the icon with correct color/style
  - Update the marker's icon bitmap
```

---

## Marker Icon System

Each marker icon is generated by `makeIcon()`:

```kotlin
private fun makeIcon(charger: ChargeLocation, scale: Float = 1f): BitmapDescriptor? {
    return chargerIconGenerator.getBitmapDescriptor(
        tint = getMarkerTint(charger, filteredConnectors),  // Color
        scale = scale,                                       // Size
        alpha = 255,                                         // Full opacity
        highlight = charger.id == highlightedCharger?.id,    // Selected?
        fault = charger.faultReport != null,                 // Has faults?
        multi = charger.isMulti(filteredConnectors),         // Multiple connectors?
        fav = charger.id in favorites,                       // Favorite?
        mini = mini                                          // Small mode?
    )
}
```

### Marker Colors (Tints)

| Color | Meaning |
|-------|---------|
| 🟢 Green | Available, high power |
| 🟡 Yellow/Amber | Available, medium power |
| 🔴 Red | Faulted/offline |
| ⭐ Star overlay | User's favorite |
| 🔵 Blue highlight | Currently selected |

---

## Clustering

When zoomed out, nearby stations merge into cluster bubbles:

```
Zoom Level 15 (close):  Individual markers visible
                         📍 📍 📍 📍 📍

Zoom Level 10 (medium): Some clustering
                         📍 📍 ③ 📍

Zoom Level 5 (far):     Heavy clustering
                         ⑫ ⑧ ⑤
```

---

## How It Connects to Other Files

```
MarkerUtils.kt
    │
    ├──◀ MapFragment.kt          — Creates MarkerManager, sets chargepoints,
    │                               range filter, user location. Calls clearAll()
    │                               before creating new instance in onMapReady()
    │
    ├──◀ VehicleInputFragment.kt — Passes rangeFilterKm back to MapFragment
    │                               which updates MarkerManager.rangeFilterKm
    │
    ├──▶ IconGenerators.kt        — Generates the actual bitmap images for markers
    │
    ├──▶ ChargersModel.kt         — Uses ChargeLocation data to position markers
    │
    └──▶ LocationUtils.kt         — distanceBetween() for range calculations
```

---

## Key Design Decisions

1. **Hide, don't dim**: Out-of-range stations are completely **removed from the map**, not just made transparent. This keeps the map clean and avoids confusion.

2. **clearAll() for lifecycle cleanup**: When the Fragment view is recreated, `clearAll()` removes all markers from the OLD MarkerManager before a new one takes over. This prevents stale unfiltered markers from persisting.

3. **Animated transitions**: Markers smoothly appear/disappear rather than blinking in/out, making the map feel polished.

4. **BiMap for marker tracking**: Uses a `HashBiMap<Marker, ChargeLocation>` to efficiently look up markers by charger or charger by marker (bidirectional).

5. **Lazy icon updates**: Icons are only regenerated when something changes (filter, favorite, highlight), not on every frame.
