# build.gradle.kts (app module)

> **File**: `app/build.gradle.kts`  
> **Purpose**: Build configuration for the ChargeX Android app — defines dependencies, build flavors, API key injection, and SDK versions.

---

## What Is This File?

This is the **Gradle build script** for the app module. It controls:
- What SDK versions the app targets
- What libraries are included (Retrofit, Room, MapLibre, etc.)
- How API keys are injected from `gradle.properties`
- Build flavors (FOSS vs Google, Normal vs Automotive)

---

## Build Flavors

The app has two flavor dimensions:

### Dimension 1: `maps` — Map Provider
| Flavor | Description |
|--------|-------------|
| `google` | Uses Google Maps SDK (requires Play Services) |
| `foss` | Uses MapLibre (open source, no Google dependency) |

### Dimension 2: `automotive` — Platform
| Flavor | Description |
|--------|-------------|
| `normal` | Regular phone/tablet app |
| `automotive` | Android Auto / car display version |

**Typical build variant**: `fossNormalDebug` (FOSS maps, normal phone, debug build)

---

## API Key Injection

API keys are stored in `gradle.properties` (not checked into Git) and injected as Android string resources:

```kotlin
// gradle.properties:
GOOGLE_MAPS_API_KEY=AIzaSy...
MAPBOX_API_KEY=pk.eyJ...

// build.gradle.kts injects them:
buildConfigField("String", "MAPBOX_API_KEY", "\"${mapboxKey}\"")
resValue("string", "google_maps_key", googleMapsKey)        // google flavor only
resValue("string", "google_directions_key", googleMapsKey)   // ALL flavors
```

### Why Two Google Keys?

| Resource | Available In | Used For |
|----------|-------------|----------|
| `google_maps_key` | Google flavor only | Google Maps SDK (map display) |
| `google_directions_key` | ALL flavors | Google Directions API (routing) |

The FOSS flavor doesn't use Google Maps for display, but still uses Google Directions API for accurate route calculation → needs the key in all flavors.

---

## Key Dependencies

```kotlin
dependencies {
    // Map SDKs
    implementation("org.maplibre.gl:android-sdk")       // FOSS map rendering
    implementation("com.google.android.gms:play-services-maps")  // Google Maps

    // Networking
    implementation("com.squareup.retrofit2:retrofit")    // HTTP client
    implementation("com.squareup.moshi:moshi")           // JSON parsing
    implementation("com.squareup.okhttp3:okhttp")        // Low-level HTTP

    // Database
    implementation("androidx.room:room-runtime")          // SQLite ORM
    implementation("androidx.room:room-ktx")              // Coroutine support

    // Navigation
    implementation("androidx.navigation:navigation-fragment-ktx")
    implementation("androidx.navigation:navigation-ui-ktx")

    // UI
    implementation("com.google.android.material:material")
    implementation("com.github.bumptech.glide:glide")     // Image loading

    // Crash Reporting
    implementation("ch.acra:acra-http")
    implementation("ch.acra:acra-dialog")
}
```

---

## SDK Versions

```kotlin
android {
    compileSdk = 35
    minSdk = 26          // Android 8.0+
    targetSdk = 35       // Android 15
}
```

- **minSdk 26**: Supports Android 8.0 and above (covers ~95% of Indian phones)
- **compileSdk 35**: Uses the latest APIs for building

---

## How It Connects to Other Files

```
build.gradle.kts
    │
    ├──▶ gradle.properties     — API keys are read from here
    │
    ├──▶ NavigationFragment.kt — Uses "google_directions_key" resource
    │
    ├──▶ RouteService.kt       — Receives API key for Google Directions
    │
    ├──▶ MapFragment.kt        — Uses map SDK based on build flavor
    │
    └──▶ EvMapApplication.kt   — BuildConfig.DEBUG for crash reporting
```
