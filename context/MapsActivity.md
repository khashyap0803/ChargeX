# MapsActivity.kt

> **File**: `app/src/main/java/com/chargex/india/MapsActivity.kt`  
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
    │   ├── OCM Web URL (openchargemap.org) → Open specific charger detail
    │   ├── Custom Scheme (com.chargex.india://find_charger) → Map search
    │   ├── EXTRA_CHARGER_ID → Open specific charger
    │   ├── EXTRA_FAVORITES → Open favorites list
    │   └── EXTRA_DONATE → Open donate/about screen
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
         ├── MapFragment          (home screen — map, range filtering,
         │                         vehicle data forwarding, clearAll())
         ├── NavigationFragment    (route preview + energy feasibility
         │                         card with vehicle-aware AC handling)
         ├── VehicleInputFragment  (vehicle selection, 24 models,
         │                         returns range + vehicle + battery)
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
| OCM Web Link | `https://openchargemap.org/...?id=123` | Switches data source to OCM and opens charger #123 |
| Custom Scheme | `com.chargex.india://find_charger?latitude=...&longitude=...&name=...` | Centers map on specific coordinate/name |
| Geo URI | `geo:17.385,78.487` | Centers map on that location |
| Notification/Widget | `EXTRA_CHARGER_ID = 12345` | Opens charger detail |
| Widget | `EXTRA_FAVORITES = true` | Opens favorites list |
| Menu/Widget | `EXTRA_DONATE = true` | Opens donate/about screen |

---

## How It Connects to Other Files

```
MapsActivity.kt
    │
    ├──▶ MapFragment.kt          — The default/home fragment
    │
    ├──▶ NavigationFragment.kt   — Opened when user taps "Navigate"
    │                                (displays route + energy feasibility card)
    │
    ├──▶ NavHostFragment.kt      — Manages fragment back stack
    │
    ├──▶ PreferenceDataSource.kt — Checks which data source to use
    │
    └──▶ ChargeLocation model    — Receives charger data for intents
```
