# PreferenceDataSource.kt

> **File**: `app/src/main/java/net/vonforst/evmap/storage/PreferenceDataSource.kt`  
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
| `darkmode` | `String` | `"system"` | Dark mode: "on", "off", "system" |
| `lastPosition` | `LatLng?` | India center | Last map position for restore |
| `lastZoom` | `Float` | `5.0` | Last map zoom level |
| `filterStatus` | `Long` | `FILTERS_DISABLED` | Active filter profile |
| `searchCardMinimized` | `Boolean` | `false` | Search bar state |
| `welcomed` | `Boolean` | `false` | Has onboarding been shown? |
| `lastDownloadTimestamp` | `Instant?` | `null` | When data was last downloaded |

---

## How LatLng is Stored

SharedPreferences can't store complex objects, so `LatLng` is stored as two separate float entries:

```kotlin
// Saving
fun Editor.putLatLng(key: String, value: LatLng?) {
    putFloat("${key}_lat", value?.latitude?.toFloat() ?: 0f)
    putFloat("${key}_lng", value?.longitude?.toFloat() ?: 0f)
}

// Reading
fun SharedPreferences.getLatLng(key: String): LatLng? {
    if (!contains("${key}_lat")) return null
    return LatLng(
        getFloat("${key}_lat", 0f).toDouble(),
        getFloat("${key}_lng", 0f).toDouble()
    )
}
```

---

## How It Connects to Other Files

```
PreferenceDataSource.kt
    │
    ├──◀ EvMapApplication.kt   — Reads dark mode and language at startup
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
