# PreferenceDataSource.kt

> **File**: `app/src/main/java/com/chargex/india/storage/PreferenceDataSource.kt`  
> **Purpose**: Centralized access to all user preferences stored in SharedPreferences. A clean wrapper around Android's preference system.

---

## What Is This File?

`PreferenceDataSource` is a **wrapper class** around Android's `SharedPreferences`. Instead of accessing preferences with raw string keys throughout the app, this class provides typed Kotlin properties:

```kotlin
// Without PreferenceDataSource (messy):
val source = prefs.getString("data_source", "openchargemap")

// With PreferenceDataSource (clean):
val source = preferenceDataSource.dataSource
```

---

## Key Preferences

| Property | Type | Default | Purpose |
|----------|------|---------|---------|
| `dataSource` | `String` | `"openchargemap"` | Which charging station API to use |
| `mapProvider` | `String` | `"maplibre"` | Map renderer (MapLibre/Google Maps) |
| `language` | `String?` | `null` | App language override |
| `darkmode` | `String` | `"default"` | Dark mode: "on", "off", "default" |
| `currentMapLocation` | `LatLng` | India center | Last map position for restore |
| `currentMapZoom` | `Float` | `3.5` | Last map zoom level |
| `filterStatus` | `Long` | `FILTERS_DISABLED` | Active filter profile |
| `welcomeDialogShown` | `Boolean` | `false` | Has onboarding been shown? |
| `developerModeEnabled` | `Boolean` | `false` | Is Developer mode active? |
| `predictionEnabled` | `Boolean` | `true` | Is wait-time prediction enabled? |

---

## How LatLng is Stored

SharedPreferences can't store complex objects, so `LatLng` is stored as two separate `Long` entries representing IEEE 754 double precision floats:

```kotlin
// Saving
fun Editor.putLatLng(key: String, value: LatLng?): Editor {
    if (value == null) {
        remove("${key}_lat")
        remove("${key}_lng")
    } else {
        putLong("${key}_lat", value.latitude.toBits())
        putLong("${key}_lng", value.longitude.toBits())
    }
    return this
}

// Reading
fun SharedPreferences.getLatLng(key: String): LatLng? =
    if (containsLatLng(key)) {
        LatLng(
            Double.fromBits(getLong("${key}_lat", 0L)),
            Double.fromBits(getLong("${key}_lng", 0L))
        )
    } else null
```

---

## How It Connects to Other Files

```
PreferenceDataSource.kt
    │
    ├──◀ ChargeXApplication.kt   — Reads dark mode and language at startup
    │
    ├──◀ MapFragment.kt         — Reads/writes lastPosition, zoom, filter state.
    │                              (Note: vehicle data & range filter are stored
    │                              separately via savedStateHandle, not here.)
    │
    ├──◀ MapViewModel.kt        — Reads dataSource, filterStatus
    │
    ├──◀ MapsActivity.kt        — Reads dataSource to create correct API
    │
    └──◀ Settings Fragments      — Reads/writes all settings preferences
```

---

## Key Design Decisions

1. **Centralized access**: All preference reads/writes go through this class, preventing typo bugs from string keys.
2. **Type safety**: Properties return the correct Kotlin types instead of raw strings.
3. **Extension functions**: `putLatLng`, `getLatLng`, `putLatLngBounds`, `getLatLngBounds` extend SharedPreferences for geographic data.
