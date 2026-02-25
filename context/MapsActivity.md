# MapsActivity.kt

> **File**: `app/src/main/java/net/vonforst/evmap/MapsActivity.kt`  
> **Purpose**: The single main Activity that hosts all fragments. Handles navigation, deep linking, intent processing, and external app launching.

---

## What Is This File?

ChargeX uses a **Single Activity architecture** — there's only ONE activity (`MapsActivity`), and all screens are Fragments hosted inside it. This activity manages:

1. **Navigation** between fragments (Map, Details, Settings, etc.)
2. **Deep links** (opening the app from a shared URL)
3. **External intents** (launching Google Maps for navigation)
4. **Navigation drawer** (side menu)
5. **Splash screen** logic

---

## Key Functions

### `onCreate()` — App UI Setup

```
MapsActivity.onCreate()
    │
    ├── Set up splash screen (Android 12+ API)
    │   └── Keep splash visible until data is loaded
    │
    ├── Set up Navigation Component
    │   └── NavHostFragment hosts all child fragments
    │
    ├── Handle incoming intent
    │   ├── Geo URI (geo:lat,lng) → Navigate to that location on map
    │   ├── Deep link URL → Open specific charger detail
    │   ├── EXTRA_CHARGER_ID → Open specific charger
    │   └── EXTRA_FAVORITES → Open favorites screen
    │
    └── Set up Navigation Drawer (side menu)
```

### `navigateTo(charger, rootView)` — Launch External Navigation

```kotlin
fun navigateTo(charger: ChargeLocation, rootView: View) {
    // Creates intent for turn-by-turn navigation
    // Tries: Google Maps → Waze → Any maps app → Browser fallback
    val uri = Uri.parse("google.navigation:q=${lat},${lng}")
    startActivity(Intent(Intent.ACTION_VIEW, uri))
}
```

### `showLocation(charger, rootView)` — Show on External Map

```kotlin
fun showLocation(charger: ChargeLocation, rootView: View) {
    // Opens the station location in an external maps app
    // Shows a chooser if multiple map apps are installed
}
```

### `openUrl(url, rootView, preferBrowser)` — Open Links

```kotlin
fun openUrl(url: String, rootView: View, preferBrowser: Boolean = false) {
    // Opens URLs using Chrome Custom Tabs if available
    // Falls back to regular browser intent
}
```

---

## Navigation Structure

```
MapsActivity (Single Activity)
    │
    └── NavHostFragment
        │
        ├── MapFragment          (home screen — the map)
        ├── NavigationFragment    (route preview)
        ├── VehicleInputFragment  (vehicle selection)
        ├── FilterFragment        (connector filters)
        ├── FavoritesFragment     (saved stations)
        ├── OnboardingFragment    (first-run setup)
        └── Settings Fragments    (app preferences)
```

---

## Deep Linking

The app can be opened from external sources:

| Source | Intent Data | Action |
|--------|-------------|--------|
| URL share | `chargex://charger/12345` | Opens charger #12345 |
| Geo URI | `geo:17.385,78.487` | Centers map on that location |
| Notification | `EXTRA_CHARGER_ID = 12345` | Opens charger detail |
| Widget | `EXTRA_FAVORITES = true` | Opens favorites list |

---

## How It Connects to Other Files

```
MapsActivity.kt
    │
    ├──▶ MapFragment.kt          — The default/home fragment
    │
    ├──▶ NavigationFragment.kt   — Opened when user taps "Navigate"
    │
    ├──▶ NavHostFragment.kt      — Manages fragment back stack
    │
    ├──▶ PreferenceDataSource.kt — Checks which data source to use
    │
    └──▶ ChargeLocation model    — Receives charger data for intents
```
