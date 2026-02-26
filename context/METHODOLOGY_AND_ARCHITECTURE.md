# ChargeX — Methodology, Architecture & Complete System Flow

> A hyper-detailed, simple-language document covering everything: how the app works, what each layer does, what each function does, and the complete input-to-output flow.

---

## 1. PROJECT OVERVIEW

**ChargeX** is an Android application that helps Electric Vehicle (EV) owners in India find nearby charging stations, calculate their vehicle's driving range, filter stations by reachability, and navigate to chosen stations.

### Problem Statement
EV owners in India face "range anxiety" — the fear of running out of battery with no charging station nearby. Existing apps don't account for Indian driving conditions (extreme heat, heavy traffic, AC usage) when estimating range.

### Solution
ChargeX provides:
- 📍 Real-time charging station locations on an interactive map
- 🔋 India-specific range estimation (temperature, AC, city/highway)
- 🚗 24 pre-loaded Indian EV profiles (Tata, MG, Hyundai, etc.)
- 🗺️ Turn-by-turn navigation to stations
- 🔍 Filtering by connector type, power, and reachability

---

## 2. METHODOLOGY

### Development Approach: Agile + MVVM Architecture

The app follows **MVVM (Model-View-ViewModel)** methodology:

```
┌─────────────────────────────────────────────────┐
│                 METHODOLOGY                      │
│                                                  │
│  1. REQUIREMENTS GATHERING                       │
│     └── Identify Indian EV market needs          │
│         (range anxiety, station discovery)        │
│                                                  │
│  2. DESIGN                                       │
│     └── MVVM architecture with layered design    │
│         (UI → ViewModel → Repository → API)      │
│                                                  │
│  3. IMPLEMENTATION                               │
│     ├── Model Layer: Data classes, DB, APIs       │
│     ├── ViewModel Layer: Business logic           │
│     └── View Layer: Fragments, Adapters, UI       │
│                                                  │
│  4. TESTING                                      │
│     ├── Unit Tests (JUnit, Robolectric)           │
│     └── Manual Testing on device                  │
│                                                  │
│  5. DEPLOYMENT                                   │
│     └── APK generation, signing, distribution     │
└─────────────────────────────────────────────────┘
```

### Technology Stack

| Component | Technology | Why This? |
|-----------|-----------|-----------|
| Language | Kotlin | Official Android language, concise, null-safe |
| UI Framework | Android Fragments + Data Binding | Standard Android UI, supports navigation |
| Map Engine | MapLibre (OpenStreetMap) | Free, open-source, no Google dependency |
| Networking | Retrofit + OkHttp + Moshi | Industry-standard REST client + JSON parser |
| Database | Room (SQLite ORM) | Android's recommended local storage |
| Navigation | Jetpack Navigation Component | Safe fragment transitions with arguments |
| Async | Kotlin Coroutines | Lightweight threads for API calls |
| Routing | Google Directions API + OSRM | Best routes for India + free fallback |
| Data Source | OpenChargeMap API | Most comprehensive EV station data for India |

---

## 3. ARCHITECTURE DIAGRAM (Layered)

```
╔══════════════════════════════════════════════════════════════════════╗
║                        CHARGEX APP ARCHITECTURE                       ║
╠══════════════════════════════════════════════════════════════════════╣
║                                                                       ║
║  ┌─────────────────────────── LAYER 1 ────────────────────────────┐  ║
║  │                     PRESENTATION LAYER (UI)                     │  ║
║  │                                                                 │  ║
║  │  What: The screens the user sees and interacts with             │  ║
║  │  How:  Android Fragments with XML layouts + Data Binding        │  ║
║  │                                                                 │  ║
║  │  ┌──────────────┐ ┌─────────────────┐ ┌───────────────────┐    │  ║
║  │  │ MapFragment  │ │VehicleInputFrag │ │NavigationFragment │    │  ║
║  │  │              │ │                 │ │                   │    │  ║
║  │  │ • Shows map  │ │ • Car dropdown  │ │ • Route on map    │    │  ║
║  │  │ • Station    │ │ • Battery slider│ │ • Distance/time   │    │  ║
║  │  │   markers    │ │ • AC toggle     │ │ • Start nav button│    │  ║
║  │  │ • Search bar │ │ • Range display │ │                   │    │  ║
║  │  │ • Bottom     │ │ • Apply filter  │ │                   │    │  ║
║  │  │   sheet      │ │                 │ │                   │    │  ║
║  │  └──────┬───────┘ └────────┬────────┘ └─────────┬─────────┘    │  ║
║  │         │                  │                     │              │  ║
║  └─────────┼──────────────────┼─────────────────────┼──────────────┘  ║
║            │ observes         │ returns result       │ calls           ║
║            ▼                  ▼                      ▼                ║
║  ┌─────────────────────── LAYER 2 ────────────────────────────────┐  ║
║  │                    VIEWMODEL LAYER (Logic)                      │  ║
║  │                                                                 │  ║
║  │  What: Business logic, data coordination, state management      │  ║
║  │  How:  Android ViewModel + LiveData (observable data holders)    │  ║
║  │                                                                 │  ║
║  │  ┌──────────────────┐  ┌────────────────┐  ┌────────────────┐  │  ║
║  │  │  MapViewModel    │  │ RangeCalculator│  │ RouteService   │  │  ║
║  │  │                  │  │                │  │                │  │  ║
║  │  │ • Load stations  │  │ • Calculate    │  │ • Fetch route  │  │  ║
║  │  │ • Apply filters  │  │   range from   │  │ • Google API   │  │  ║
║  │  │ • Manage favs    │  │   battery %,   │  │   first, OSRM  │  │  ║
║  │  │ • Track map pos  │  │   AC, temp,    │  │   fallback     │  │  ║
║  │  │ • Load details   │  │   drive mode   │  │ • Decode route │  │  ║
║  │  └────────┬─────────┘  └────────────────┘  └────────┬───────┘  │  ║
║  │           │                                          │          │  ║
║  └───────────┼──────────────────────────────────────────┼──────────┘  ║
║              │ queries                                  │ HTTP calls  ║
║              ▼                                          ▼             ║
║  ┌─────────────────────── LAYER 3 ────────────────────────────────┐  ║
║  │                      DATA LAYER (Storage + APIs)                │  ║
║  │                                                                 │  ║
║  │  What: Network calls, database operations, data persistence     │  ║
║  │  How:  Retrofit (HTTP), Room (SQLite), SharedPreferences        │  ║
║  │                                                                 │  ║
║  │  ┌─── Network ──────────┐    ┌─── Local Storage ────────────┐  │  ║
║  │  │                      │    │                              │  │  ║
║  │  │  ChargepointApi      │    │  Room Database               │  │  ║
║  │  │  (Interface)         │    │  ├── ChargeLocationsDao      │  │  ║
║  │  │     │                │    │  ├── FavoritesDao            │  │  ║
║  │  │     ├── OpenCharge   │    │  └── FilterProfileDao        │  │  ║
║  │  │     │   MapApi       │    │                              │  │  ║
║  │  │     └── (wrapper)    │    │  PreferenceDataSource        │  │  ║
║  │  │                      │    │  (SharedPreferences wrapper) │  │  ║
║  │  │  Google Directions   │    │                              │  │  ║
║  │  │  OSRM Routing API    │    │  VehicleProfile (hardcoded)  │  │  ║
║  │  └──────────────────────┘    └──────────────────────────────┘  │  ║
║  │                                                                 │  ║
║  └─────────────────────────────────────────────────────────────────┘  ║
║                                                                       ║
║  ┌─────────────────────── LAYER 4 ────────────────────────────────┐  ║
║  │                    UI UTILITIES LAYER (Helpers)                  │  ║
║  │                                                                 │  ║
║  │  What: Marker rendering, icon generation, map utilities         │  ║
║  │  How:  Custom Bitmap generators, animation helpers              │  ║
║  │                                                                 │  ║
║  │  ┌──────────────┐  ┌────────────────┐  ┌───────────────────┐   │  ║
║  │  │ MarkerUtils  │  │ IconGenerators │  │ BindingAdapters  │   │  ║
║  │  │ • Add/remove │  │ • Create pin   │  │ • XML → Kotlin   │   │  ║
║  │  │   map pins   │  │   bitmaps      │  │   data binding   │   │  ║
║  │  │ • Range      │  │ • Colors for   │  │                  │   │  ║
║  │  │   filtering  │  │   power levels │  │                  │   │  ║
║  │  │ • Clustering │  │ • Fav stars    │  │                  │   │  ║
║  │  └──────────────┘  └────────────────┘  └───────────────────┘   │  ║
║  └─────────────────────────────────────────────────────────────────┘  ║
║                                                                       ║
║  ┌─────────────────────── LAYER 5 ────────────────────────────────┐  ║
║  │                    APP INFRASTRUCTURE LAYER                     │  ║
║  │                                                                 │  ║
║  │  What: App startup, background tasks, configuration             │  ║
║  │  How:  Application class, WorkManager, Gradle build system      │  ║
║  │                                                                 │  ║
║  │  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────┐  │  ║
║  │  │EvMapApplication  │  │ MapsActivity    │  │build.gradle  │  │  ║
║  │  │ • Dark mode      │  │ • Single host   │  │ • API keys   │  │  ║
║  │  │ • Language       │  │   activity      │  │ • Flavors    │  │  ║
║  │  │ • Background     │  │ • Deep linking  │  │ • SDK setup  │  │  ║
║  │  │   workers        │  │ • Navigation    │  │              │  │  ║
║  │  │ • Crash report   │  │   controller    │  │              │  │  ║
║  │  └──────────────────┘  └─────────────────┘  └──────────────┘  │  ║
║  └─────────────────────────────────────────────────────────────────┘  ║
╚══════════════════════════════════════════════════════════════════════╝
```

---

## 4. COMPLETE WORKFLOW — INPUT TO OUTPUT

### WORKFLOW 1: Finding Charging Stations on the Map

```
INPUT: User opens the app
OUTPUT: Map with charging station markers

STEP-BY-STEP BACKEND FLOW:
═══════════════════════════

STEP 1: App launches → EvMapApplication.onCreate()
        ├── Reads dark mode preference from PreferenceDataSource
        ├── Applies night mode via updateNightMode()
        ├── Schedules CleanupCacheWorker (daily cache cleanup)
        └── Schedules UpdateFullDownloadWorker (weekly data sync)

STEP 2: MapsActivity.onCreate()
        ├── Creates NavHostFragment (fragment container)
        ├── Loads navigation graph (nav_graph.xml)
        └── Opens MapFragment as the home screen

STEP 3: MapFragment.onViewCreated()
        ├── Initializes MapLibre map with OpenStreetMap tiles
        ├── Creates MarkerManager (from MarkerUtils.kt)
        │   └── MarkerManager manages all charging station pins
        ├── Creates MapViewModel
        │   └── MapViewModel starts loading data
        ├── Requests location permission
        │   └── If granted: shows blue dot on map at user's GPS position
        └── Sets up search bar, FABs, bottom sheet

STEP 4: MapViewModel loads charging stations
        ├── Gets visible map bounds (lat/lng rectangle)
        ├── Calls ChargepointApi.getChargepoints(bounds, zoom, filters)
        │   └── ChargepointApi is an INTERFACE
        │       └── OpenChargeMapApiWrapper is the IMPLEMENTATION
        │
        │   Inside OpenChargeMapApiWrapper.getChargepoints():
        │   ├── Converts app filters → OCM query parameters
        │   │   Example: "CCS connector" → connectiontypeid=33
        │   ├── Calls OpenChargeMapApi.getChargepoints() via Retrofit
        │   │   HTTP GET → https://api.openchargemap.io/v3/poi?
        │   │              boundingbox=17.3,78.4,17.5,78.6
        │   │              &maxresults=3000
        │   │              &key=81bd6195-ead1-4783-bfa5-29d7b6c8fcae
        │   ├── Retrofit sends HTTP request via OkHttp
        │   ├── OkHttp receives JSON response from server
        │   ├── Moshi parses JSON → List<OCMChargepoint> objects
        │   ├── Wrapper converts OCMChargepoint → ChargeLocation (app model)
        │   │   Each ChargeLocation contains:
        │   │   ├── id, name, coordinates (lat, lng)
        │   │   ├── address (street, city, postcode)
        │   │   ├── chargepoints: List<Chargepoint>
        │   │   │   └── Each: type="CCS", power=50.0kW, count=2
        │   │   ├── network: "Tata Power" / "Ather Grid" / etc.
        │   │   ├── cost: free/paid, pricing details
        │   │   └── openingHours: 24/7 or specific times
        │   ├── If useClustering: groups nearby stations into clusters
        │   └── Returns Resource.Success(ChargepointList)

STEP 5: MapViewModel.chargepoints LiveData is updated
        └── MapFragment observes this LiveData
            └── Calls markerManager.chargepoints = newData

STEP 6: MarkerManager.updateChargepoints()
        ├── For each ChargeLocation in the list:
        │   ├── Check isInRange() — is station within user's battery range?
        │   │   └── If rangeFilterKm > 0 AND userLocation exists:
        │   │       Calculate distance = haversine(user, station)
        │   │       If distance > rangeFilterKm → SKIP this station (hide it)
        │   ├── Create marker icon via makeIcon()
        │   │   └── chargerIconGenerator.getBitmapDescriptor(
        │   │       tint = color based on max power,
        │   │       highlight = is this the selected station?,
        │   │       fav = is this in favorites?,
        │   │       fault = any reported problems?
        │   │   )
        │   └── Add marker to map at station's GPS coordinates
        └── Animate markers with smooth pop-in effect

STEP 7: User sees colored pins on the map
        🟢 Green = high power (50+ kW fast charger)
        🟡 Yellow = medium power (7-49 kW)
        🔴 Red = faulted/offline
        ⭐ Star = user's favorite
```

---

### WORKFLOW 2: Vehicle Selection & Range Calculation

```
INPUT: User taps vehicle FAB → enters vehicle details
OUTPUT: Estimated driving range + filtered map markers

STEP-BY-STEP BACKEND FLOW:
═══════════════════════════

STEP 1: User taps 🔋 FAB on MapFragment
        └── findNavController().navigate(R.id.vehicleInputFragment)
            Opens VehicleInputFragment

STEP 2: VehicleInputFragment.onViewCreated()
        ├── Calls VehicleProfile.groupedByManufacturer()
        │   └── Returns: {
        │       "Tata" → [Nexon LR, Nexon SR, Punch, Tiago, Tigor, Curvv],
        │       "MG" → [ZS EV, Comet, Windsor],
        │       "Hyundai" → [Ioniq 5, Creta EV],
        │       ... 12 manufacturers, 24 vehicles total
        │   }
        └── Populates manufacturer dropdown

STEP 3: User selects manufacturer "Tata"
        └── Model dropdown shows: [Nexon EV Max, Nexon EV, Punch, Tiago, Tigor, Curvv]

STEP 4: User selects model "Nexon EV Max (Long Range)"
        └── selectedVehicle = VehicleProfile(
                id = "tata_nexon_lr",
                batteryCapacityKwh = 40.5,
                officialRangeKm = 437.0,
                efficiencyKwhPer100Km = 12.5
            )
        └── Vehicle specs card appears showing battery, efficiency, range

STEP 5: User adjusts battery slider to 80%, AC = ON, Mode = "city"
        └── Every change triggers updateRange() which calls:

        RangeCalculator.calculateRange(vehicle, 80.0, acOn=true, "city", 35.0)
        │
        │   INSIDE RangeCalculator (step by step):
        │
        │   1. availableEnergy = 40.5 × (80/100) = 32.4 kWh
        │   2. baseEfficiency = 12.5 / 100 = 0.125 kWh/km
        │   3. Apply CITY mode: 0.125 × 0.90 = 0.1125 kWh/km
        │      (EVs are MORE efficient in city due to regenerative braking)
        │   4. Apply TEMPERATURE (35°C): 0.1125 × 1.08 = 0.1215 kWh/km
        │      (Indian summer heat makes battery work harder)
        │   5. Apply AC penalty: 0.1215 × 1.10 = 0.13365 kWh/km
        │      (AC uses ~10% extra energy)
        │   6. Apply REAL-WORLD correction: 0.13365 × 1.15 = 0.15370 kWh/km
        │      (ARAI ratings are ~15% optimistic vs actual driving)
        │   7. FINAL RANGE = 32.4 / 0.15370 = 210.8 km
        │
        └── Display: "Estimated Range: 211 km"

STEP 6: User taps "Apply Filter"
        ├── Saves to Navigation savedStateHandle:
        │   ├── "range_filter_km" = 211.0f
        │   ├── "vehicle_id" = "tata_nexon_lr"
        │   └── "battery_percent" = 80.0f
        └── findNavController().navigateUp() → returns to MapFragment

STEP 7: MapFragment receives the result
        ├── savedStateHandle.getLiveData("range_filter_km").observe { rangeKm ->
        │       markerManager.rangeFilterKm = rangeKm   // = 211.0
        │   }
        └── MarkerManager.rangeFilterKm setter triggers updateChargepoints()

STEP 8: MarkerManager.updateChargepoints() runs again
        ├── For each station, calls isInRange():
        │   ├── Station A is 50 km away  → 50 ≤ 211 → SHOW ✅
        │   ├── Station B is 150 km away → 150 ≤ 211 → SHOW ✅
        │   ├── Station C is 250 km away → 250 > 211 → HIDE ❌ (remove marker)
        │   └── Station D is 300 km away → 300 > 211 → HIDE ❌ (remove marker)
        └── Out-of-range markers are completely removed from the map
            (not dimmed — fully hidden for clean UI)

STEP 9: User sees only reachable stations on the map
```

---

### WORKFLOW 3: Navigation to a Charging Station

```
INPUT: User taps a station marker → taps "Navigate"
OUTPUT: Route drawn on map with distance and duration

STEP-BY-STEP BACKEND FLOW:
═══════════════════════════

STEP 1: User taps a station marker on the map
        └── MarkerManager.onChargerClick(marker)
            └── MapViewModel.chargerSparse = ChargeLocation (summary data)

STEP 2: Bottom sheet slides up showing station info
        ├── Station name: "Tata Power - Hitech City"
        ├── Address: "Plot 42, Hitech City, Hyderabad"
        ├── Connectors: "CCS 50kW × 2, Type 2 22kW × 4"
        ├── Status: 🟢 "Available"
        └── Buttons: [Navigate] [Favorite ⭐] [Share]

STEP 3: User taps "Navigate"
        └── findNavController().navigate(
                R.id.navigationFragment,
                NavigationFragmentArgs(
                    destLat = 17.4399f,
                    destLng = 78.4983f
                )
            )

STEP 4: NavigationFragment.onViewCreated()
        └── Calls fetchRoute(userLat, userLng)

STEP 5: fetchRoute() runs (inside a coroutine on IO thread)
        │
        ├── 5a. Get user's GPS location
        │   └── LocationManager.getLastKnownLocation()
        │       → originLat = 17.3850, originLng = 78.4867
        │
        ├── 5b. Load Google API key
        │   └── resources.getIdentifier("google_directions_key", "string", packageName)
        │       → "AIzaSyDMrT3xAI1p-9Wky2K0OuCUNyItFgUSW_Q"
        │
        └── 5c. Call RouteService.getRoute(origin, destination, apiKey)

STEP 6: RouteService.getRoute() runs
        │
        ├── TRY GOOGLE DIRECTIONS API FIRST:
        │   getGoogleRoute(17.3850, 78.4867, 17.4399, 78.4983, apiKey)
        │   │
        │   ├── HTTP GET → https://maps.googleapis.com/maps/api/directions/json?
        │   │              origin=17.3850,78.4867
        │   │              &destination=17.4399,78.4983
        │   │              &mode=driving
        │   │              &region=in          ← optimized for India
        │   │              &key=AIzaSy...
        │   │
        │   ├── Google returns JSON with:
        │   │   ├── status: "OK"
        │   │   ├── distance: { value: 12300, text: "12.3 km" }
        │   │   ├── duration: { value: 1520, text: "25 mins" }
        │   │   └── overview_polyline: "a~l~Fjk~uO..." (encoded route)
        │   │
        │   ├── decodePolyline5("a~l~Fjk~uO...")
        │   │   └── Converts encoded string → List of (lat, lng) pairs
        │   │       → [(17.385, 78.487), (17.386, 78.488), ..., (17.440, 78.498)]
        │   │       → 156 GPS coordinate points
        │   │
        │   └── Returns DecodedRoute(
        │           points = [156 coordinate pairs],
        │           distanceMeters = 12300.0,
        │           durationSeconds = 1520.0
        │       )
        │
        ├── IF GOOGLE FAILS → FALLBACK TO OSRM:
        │   getOsrmRoute(17.3850, 78.4867, 17.4399, 78.4983)
        │   │
        │   ├── HTTP GET → https://router.project-osrm.org/route/v1/driving/
        │   │              78.4867,17.3850;78.4983,17.4399
        │   │              ?overview=full&geometries=polyline6
        │   │              (Note: OSRM uses lng,lat order!)
        │   │
        │   ├── decodePolyline6() — OSRM uses higher precision (÷ 1,000,000)
        │   └── Returns DecodedRoute(...)
        │
        └── Returns the successful DecodedRoute to NavigationFragment

STEP 7: NavigationFragment displays the route
        ├── Draws GREEN polyline on map (color: #4CAF50, width: 5px)
        │   map.addPolyline(polylinePoints)
        ├── Adds markers: 📍 "You" → ⚡ "Station"
        ├── Displays distance: formatDistance(12.3) → "12.3 km"
        ├── Displays duration: formatDuration(25.3) → "25 min"
        │   (if > 60 min: formatDuration(95) → "1h 35m")
        └── Animates camera to fit entire route on screen
            CameraUpdateFactory.newLatLngBounds(routeBounds, padding=100)

STEP 8: User taps "Start Navigation"
        └── Opens Google Maps / Waze with turn-by-turn directions
            Intent(ACTION_VIEW, "google.navigation:q=17.4399,78.4983")
```

---

### WORKFLOW 4: Station Detail & Favorites

```
INPUT: User taps a marker → views full details → adds to favorites
OUTPUT: Station details shown, saved to local database

STEP 1: User taps station marker
        └── MapViewModel.loadChargepointDetail(stationId)
            └── ChargepointApi.getChargepointDetail(referenceData, id)
                └── HTTP GET → ocm.io/v3/poi?chargepointid=12345&verbose=true

STEP 2: Full details shown in bottom sheet:
        ├── Photos (loaded via Coil image library)
        ├── All connectors with exact specs
        ├── Opening hours
        ├── Cost information
        ├── Network operator
        └── User reviews/comments

STEP 3: User taps ⭐ Favorite button
        ├── MapViewModel.insertFavorite(chargeLocation)
        │   └── FavoritesDao.insert(Favorite(chargerId=12345))
        │       └── Room inserts into SQLite "favorite" table
        └── Marker updates with star icon overlay
```

---

## 5. FUNCTION-LEVEL DETAIL — EVERY KEY FUNCTION

### Layer 1: Presentation (Fragments)

| File | Function | What It Does | Called By |
|------|----------|-------------|-----------|
| **MapFragment** | `onViewCreated()` | Initializes map, creates MarkerManager and MapViewModel, sets up UI | Android system |
| | `observeChargepoints()` | Watches MapViewModel.chargepoints LiveData → passes data to MarkerManager | MapViewModel |
| | `onMarkerClick()` | When user taps a pin → loads station details in bottom sheet | User tap |
| | `setupSearchBar()` | Configures place autocomplete search | onViewCreated |
| **VehicleInputFragment** | `setupManufacturerDropdown()` | Loads manufacturer list from VehicleProfile.groupedByManufacturer() | onViewCreated |
| | `setupBatterySlider()` | Battery % slider (0-100), triggers updateRange() on every change | onViewCreated |
| | `updateRange()` | Calls RangeCalculator.calculateRange() → displays result | Slider/toggle change |
| | `applyFilter()` | Saves range_filter_km to savedStateHandle → navigates back | Apply button |
| **NavigationFragment** | `fetchRoute()` | Gets GPS, loads API key, calls RouteService, draws polyline | onViewCreated |
| | `formatDistance(km)` | 0.5 → "500 m", 12.3 → "12.3 km" | fetchRoute |
| | `formatDuration(min)` | 25 → "25 min", 95 → "1h 35m" | fetchRoute |

### Layer 2: ViewModel & Business Logic

| File | Function | What It Does | Called By |
|------|----------|-------------|-----------|
| **MapViewModel** | `reloadChargepoints()` | Triggers API call to load stations in visible map area | Map pan/zoom |
| | `loadChargerById(id)` | Loads a specific charger (for deep links) | MapsActivity |
| | `insertFavorite()` | Saves station to Room DB favorites table | MapFragment |
| | `deleteFavorite()` | Removes station from favorites | MapFragment |
| | `toggleFilters()` | Switches between no filters / custom filters | Filter button |
| **RangeCalculator** | `calculateRange()` | Main calculation: vehicle + battery% + AC + temp + mode → km | VehicleInputFragment |
| | `isStationReachable()` | Can user reach a station? (includes 10% safety margin) | MarkerUtils |
| | `remainingBatteryPercent()` | What % battery left after driving X km? | Detail screen |
| **RouteService** | `getRoute()` | Orchestrator: tries Google first, falls back to OSRM | NavigationFragment |
| | `getGoogleRoute()` | HTTP call to Google Directions API, decodes polyline5 | getRoute |
| | `getOsrmRoute()` | HTTP call to OSRM API, decodes polyline6 | getRoute |
| | `decodePolyline5()` | Encoded string → GPS coordinates (÷ 100,000) | getGoogleRoute |
| | `decodePolyline6()` | Encoded string → GPS coordinates (÷ 1,000,000) | getOsrmRoute |

### Layer 3: Data (API + Storage)

| File | Function | What It Does | Called By |
|------|----------|-------------|-----------|
| **ChargepointApi** | `getChargepoints()` | INTERFACE: load stations in a map area | MapViewModel |
| | `getChargepointDetail()` | INTERFACE: load full details for one station | MapViewModel |
| | `getReferenceData()` | INTERFACE: load connector/operator lookup tables | App startup |
| | `getFilters()` | INTERFACE: get available filter options | FilterFragment |
| **OpenChargeMapApiWrapper** | `getChargepoints()` | IMPLEMENTATION: calls OCM API, converts to app models | MapViewModel |
| | `postprocessResult()` | Apply local filters OCM API doesn't support | getChargepoints |
| | `convertFiltersToSQL()` | Converts app filters → SQL WHERE clause for local DB | MapViewModel |
| **VehicleProfile** | `findById(id)` | Find a vehicle by its unique ID string | Saved selection |
| | `groupedByManufacturer()` | Group 24 vehicles by brand for dropdown display | VehicleInputFragment |
| **PreferenceDataSource** | `dataSource` (property) | Which API to use: "openchargemap" | MapViewModel |
| | `lastPosition` (property) | Last map position for restore on app reopen | MapFragment |
| | `filterStatus` (property) | Active filter profile ID | MapViewModel |
| **Room Database** | `ChargeLocationsDao.insert()` | Cache stations locally in SQLite | OpenChargeMapApi |
| | `FavoritesDao.getAllFavorites()` | Get all favorited stations (returns LiveData) | MapViewModel |
| | `CleanupCacheWorker` | Delete old cached data (runs daily in background) | WorkManager |

### Layer 4: UI Utilities

| File | Function | What It Does | Called By |
|------|----------|-------------|-----------|
| **MarkerUtils** | `updateChargepoints()` | Add/remove markers based on data + range filter | chargepoints setter |
| | `isInRange()` | Check if station is within user's battery range | updateChargepoints |
| | `makeIcon()` | Generate marker bitmap with correct color, size, star | updateChargepoints |
| | `updateChargerIcons()` | Refresh icons when filter/favorite changes | Filter/fav change |
| **IconGenerators** | `getBitmapDescriptor()` | Create colored pin image with tint, scale, overlays | makeIcon |

---

## 6. DATA MODELS (What data looks like)

```
ChargeLocation (one charging station):
{
    id: 12345,
    name: "Tata Power - Hitech City",
    coordinates: { lat: 17.4399, lng: 78.4983 },
    address: { street: "Plot 42", city: "Hyderabad", postcode: "500081" },
    chargepoints: [
        { type: "CCS", power: 50.0, count: 2 },
        { type: "Type 2", power: 22.0, count: 4 }
    ],
    network: "Tata Power",
    cost: { freecharging: false, descriptionShort: "₹15/kWh" },
    openingHours: "24/7",
    faultReport: null
}

VehicleProfile (one EV model):
{
    id: "tata_nexon_lr",
    name: "Nexon EV Max (Long Range)",
    manufacturer: "Tata",
    batteryCapacityKwh: 40.5,
    officialRangeKm: 437.0,
    efficiencyKwhPer100Km: 12.5
}

DecodedRoute (one calculated route):
{
    points: [(17.385, 78.487), (17.386, 78.488), ..., (17.440, 78.498)],
    distanceMeters: 12300.0,    → distanceKm = 12.3
    durationSeconds: 1520.0     → durationMinutes = 25.3
}
```

---

## 7. EXTERNAL APIS USED

| API | Base URL | Purpose | Key Required? |
|-----|----------|---------|--------------|
| OpenChargeMap | `api.openchargemap.io/v3/` | Charging station data | Yes (free) |
| Google Directions | `maps.googleapis.com/maps/api/directions/` | Route calculation | Yes (paid/free tier) |
| OSRM | `router.project-osrm.org/route/v1/` | Backup route calculation | No (free) |
| MapLibre / OpenStreetMap | Various tile servers | Map tiles (background map) | No (free) |
| Jawg Maps | `tile.jawg.io/` | Premium map tiles | Yes (free tier) |

---

## 8. RANGE CALCULATION CORRECTION FACTORS

| Factor | Multiplier | Explanation |
|--------|-----------|-------------|
| City driving | × 0.90 | EVs regenerate energy during braking in traffic |
| Highway driving | × 1.15 | High speed = more air drag = more energy |
| Mixed driving | × 1.00 | Baseline (no correction) |
| Temperature > 40°C | × 1.12 | Extreme heat degrades battery performance |
| Temperature > 35°C | × 1.08 | Hot Indian summer (most common case) |
| Temperature > 28°C | × 1.03 | Warm weather |
| Temperature < 15°C | × 1.10 | Cold weather (rare in India) |
| AC On | × 1.10 | AC compressor uses ~10% extra energy |
| Real-world correction | × 1.15 | ARAI/official ratings are ~15% optimistic |

**Stacking example (worst case — highway, 42°C, AC on):**
```
Base efficiency × 1.15 (highway) × 1.12 (extreme heat) × 1.10 (AC) × 1.15 (real-world)
= Base × 1.629
= Your actual efficiency is 62.9% worse than the official spec!
```

---

## 9. DATABASE SCHEMA

```
┌─────────────────────────────────────────────────────────┐
│                    ROOM DATABASE                          │
│                  (SQLite under the hood)                   │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  TABLE: chargelocation                                    │
│  ├── id (PRIMARY KEY)                                     │
│  ├── dataSource (TEXT)                                    │
│  ├── name (TEXT)                                          │
│  ├── lat, lng (REAL)                                      │
│  ├── street, city, postcode, country (TEXT)                │
│  ├── chargepoints (TEXT — JSON array)                     │
│  ├── network (TEXT)                                       │
│  ├── freecharging (INTEGER — boolean)                     │
│  ├── timeRetrieved (INTEGER — epoch millis)               │
│  └── isDetailed (INTEGER — boolean)                       │
│                                                           │
│  TABLE: favorite                                          │
│  ├── favId (PRIMARY KEY, auto-increment)                  │
│  ├── chargerId (INTEGER — references chargelocation.id)   │
│  └── dataSource (TEXT)                                    │
│                                                           │
│  TABLE: filterprofile                                     │
│  ├── id (PRIMARY KEY)                                     │
│  ├── dataSource (TEXT)                                    │
│  ├── name (TEXT)                                          │
│  └── order (INTEGER)                                      │
│                                                           │
│  TABLE: filtervalue                                       │
│  ├── profile (FOREIGN KEY → filterprofile.id)             │
│  ├── dataSource (TEXT)                                    │
│  ├── filterType (TEXT)                                    │
│  └── value (TEXT)                                         │
│                                                           │
│  TABLE: savedregion                                       │
│  ├── id (PRIMARY KEY)                                     │
│  ├── dataSource (TEXT)                                    │
│  └── bounds (lat1, lng1, lat2, lng2)                      │
│                                                           │
│  TABLE: recentautocompleteplace                           │
│  ├── id (PRIMARY KEY)                                     │
│  └── place data (TEXT)                                    │
└─────────────────────────────────────────────────────────┘
```

---

## 10. COMPLETE FILE MAP — WHAT EACH FILE DOES (ONE LINE)

### Source Code Files (`app/src/main/java/net/vonforst/evmap/`)

| File Path | One-Line Description |
|-----------|---------------------|
| `EvMapApplication.kt` | App startup: dark mode, language, background workers, crash reporting |
| `MapsActivity.kt` | Single host activity: navigation, deep links, external app launching |
| **Model Layer** | |
| `model/VehicleProfile.kt` | 24 Indian EV models with battery and efficiency specs |
| `model/RangeCalculator.kt` | Range estimation with India-specific temperature/AC/driving corrections |
| `model/ChargersModel.kt` | Data classes: ChargeLocation, Chargepoint, Address, Cost, OpeningHours |
| **API Layer** | |
| `api/ChargepointApi.kt` | Interface — contract that all data sources must implement |
| `api/openchargemap/OpenChargeMapApi.kt` | OpenChargeMap HTTP client + model converter + clustering |
| `api/RouteService.kt` | Google Directions + OSRM routing with polyline decoding |
| **Fragment Layer** | |
| `fragment/MapFragment.kt` | Home screen — map, markers, search, filters, bottom sheet |
| `fragment/NavigationFragment.kt` | Route preview — polyline on map, distance, duration, start nav |
| `fragment/VehicleInputFragment.kt` | Vehicle picker — dropdown, battery slider, range calc, apply filter |
| `fragment/FilterFragment.kt` | Connector type and power level filter UI |
| `fragment/OnboardingFragment.kt` | First-run welcome screen and data source selection |
| **ViewModel Layer** | |
| `viewmodel/MapViewModel.kt` | Map business logic — load stations, filters, favorites, map position |
| **UI Utilities** | |
| `ui/MarkerUtils.kt` | Map marker manager — add/remove/hide pins, range filtering, clustering |
| `ui/IconGenerators.kt` | Bitmap generation for colored station pin icons |
| **Storage Layer** | |
| `storage/Database.kt` | Room database definition — tables, DAOs, migrations, type converters |
| `storage/PreferenceDataSource.kt` | SharedPreferences wrapper — darkmode, language, last position |
| `storage/CleanupCacheWorker.kt` | Daily background job — deletes old cached station data |
| `storage/UpdateFullDownloadWorker.kt` | Weekly background job — updates offline station database |

### Layout Files (`app/src/main/res/layout/`)

| File | Screen |
|------|--------|
| `fragment_map.xml` | Main map screen with search, FABs, bottom sheet |
| `fragment_navigation.xml` | Route preview with map, distance, duration |
| `fragment_vehicle_input.xml` | Vehicle selection with dropdowns, slider, buttons |
| `detail_view.xml` | Station detail bottom sheet content |
| `data_source_select.xml` | Data source selection dialog |

### Configuration Files

| File | Purpose |
|------|---------|
| `build.gradle.kts` (app) | Dependencies, API key injection, build flavors |
| `build.gradle.kts` (root) | Plugin versions (Kotlin, Gradle, Navigation) |
| `gradle.properties` | API keys (OPENCHARGEMAP, GOOGLE_MAPS, JAWG) |
| `AndroidManifest.xml` | Permissions, activities, meta-data, intent filters |
| `nav_graph.xml` | Navigation graph — all fragment routes and arguments |

---

*This document provides a complete, hyper-detailed view of the ChargeX application — from user input to backend processing to final output, covering every layer, every function, and every data flow.*
