# OfflineRouteManager.kt

> This file manages **100% offline routing capabilities** for the ChargeX app. It handles downloading map data, building routing graphs, generating road-following routes using the GraphHopper engine, and calculating straight-line estimates when data is unavailable.

---

## 1. PURPOSE AND ARCHITECTURE

The `OfflineRouteManager` acts as the bridge between the ChargeX application and the **GraphHopper routing engine**. 
Because Indian EVs often travel through areas with poor cellular service, an offline fallback is critical.

### The Problem
Traditional navigation apps (like Google Maps) require an internet connection to send origin and destination coordinates to a server, which calculates the route and returns a polyline.

### The Solution
`OfflineRouteManager` implements a two-tiered offline solution:
1. **Primary Offline (GraphHopper):** If the user has downloaded the OpenStreetMap (OSM) data for India, GraphHopper calculates a precise, road-following route directly on the phone's CPU.
2. **Secondary Offline (Haversine):** If the user has *not* downloaded the OSM data, it calculates a straight-line estimate between the two points as a last-resort fallback.

---

## 2. GRAPH-HOPPER INTEGRATION

GraphHopper is a fast, open-source routing engine written in Java. In ChargeX, we use `graphhopper-core:9.1`.

### How GraphHopper Works on Android
1. **Raw Data:** OpenStreetMap data is downloaded as an `.osm.pbf` file (Protocolbuffer Binary Format). For India, this file is approximately ~1.2 GB.
2. **Graph Compilation:** When `initialize()` is called, GraphHopper reads the `.pbf` file and compiles it into an optimized routing graph (stored in a `-gh` directory). This is highly CPU-intensive and takes 5-15 minutes on a phone.
3. **Routing:** Once the graph is built, routing requests (A to B) take mere milliseconds.

### Core Dependencies
```gradle
// In build.gradle.kts
implementation("com.graphhopper:graphhopper-core:9.1") {
    exclude(group = "org.slf4j", module = "slf4j-api")
    exclude(group = "ch.qos.logback", module = "logback-classic")
}
```
*(Logging libraries are excluded because GraphHopper's default logging conflicts with Android's SLF4J implementations.)*

---

## 3. FILE STRUCTURE & KEY FUNCTIONS

### Initialization & Data Download
- `downloadOsmData(context: Context)`
  Downloads the latest India OSM data from Geofabrik. It uses Android's `DownloadManager` to handle the large 1.2GB payload in the background, showing a progress notification.

- `initialize()`
  Sets up the `GraphHopper` instance. 
  - `setOSMFile(osmFile.absolutePath)` — points to the downloaded `.pbf`.
  - `setGraphHopperLocation(graphFolder.absolutePath)` — sets where the compiled graph should be saved.
  - `setProfiles(Profile("car").setVehicle("car").setWeighting("fastest"))` — configures the engine to calculate routes for cars prioritizing speed.
  - `importOrLoad()` — Triggers the heavy compilation step if the graph doesn't exist, or loads it instantly into RAM if it does.

### Routing Logic
- `getGraphHopperRoute(originLat, originLng, destLat, destLng): DecodedRoute?`
  The primary function for offline road routing.
  ```kotlin
  val req = GHRequest(originLat, originLng, destLat, destLng)
      .setProfile("car")
      .setLocale(Locale.US)
  
  val rsp = hopper.route(req) // Executes Dijkstra/A* on the local graph
  ```
  It extracts the `PointList` from the best path, converts it to our app's standard `LatLng` data class, and constructs a `DecodedRoute` identical to what the Google/OSRM APIs return.

### Fallback Logic
- `getHaversineRoute(originLat, originLng, destLat, destLng): DecodedRoute`
  If GraphHopper fails (usually because the user hasn't downloaded the 1.2GB map file yet), this function provides a hard fallback.
  It generates a perfectly straight line containing exactly 2 points (Origin → Destination).
  
  **Haversine Math Used:**
  The distance is computed using the Haversine formula, which accounts for the spherical shape of the Earth:
  $$ a = \sin^2(\Delta\phi/2) + \cos(\phi_1) \cdot \cos(\phi_2) \cdot \sin^2(\Delta\lambda/2) $$
  $$ c = 2 \cdot \text{atan2}(\sqrt{a}, \sqrt{1-a}) $$
  $$ d = R \cdot c $$
  Where:
  - $\phi$ is latitude, $\lambda$ is longitude
  - $R$ is Earth's radius (approx 6,371 km)
  
  **Estimating Duration without Roads:**
  Because a straight line over water or through buildings is impossible to drive, the algorithm assumes an arbitrary average speed of **30 km/h** to estimate how long the trip will take.

---

## 4. INTEGRATION INTO FULL ROUTING CHAIN

The `OfflineRouteManager` does not stand alone. It is called by `RouteService.kt` as steps 3 and 4 of the resilient routing chain:

1. **Google Directions API (Online)** — Highest quality, live traffic.
2. **OSRM (Online)** — Free, open-source fallback.
3. **GraphHopper (Offline)** — `OfflineRouteManager.getGraphHopperRoute()` — Real roads, no internet.
4. **Haversine (Offline)** — `OfflineRouteManager.getHaversineRoute()` — Straight line, no internet, no data downloaded.
