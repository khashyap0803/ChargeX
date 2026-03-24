# Secondary APIs & Availability Integrations

> **Directory**: `app/src/main/java/com/chargex/india/api/`  
> **Purpose**: Documents the secondary data sources (OpenStreetMap, GoingElectric) and the real-time availability detectors (Tesla Superchargers) that supplement the primary OpenChargeMap source.

---

## 🗺️ Secondary Data Sources

While `OpenChargeMap` is the default provider for ChargeX, the app architecture natively supports swapping data backends via the `ChargepointApi` interface.

### 1. OpenStreetMap API (`openstreetmap/`)
ChargeX implements `OpenStreetMapApiWrapper`, connecting to a highly compressed custom endpoint (`https://osm.ev-map.app/charging-stations-osm.json`).
- **Full Download**: OSM data is extremely dense. The `fullDownload()` function streams thousands of POIs from the custom JSON directly into the local Room `ChargeLocations` cache.
- **Filtering Limitations**: Unlike OCM which can execute REST queries, because the OSM integration uses a bulk download, `convertFiltersToSQL()` relies entirely on SQLite queries (like `json_extract(cp.value, '$.power') >= minPower`) against the local Room cache to filter results.

### 2. GoingElectric API (`goingelectric/`)
A legacy/regional API (`GEStubs.kt`) providing coverage primarily for Central Europe.

---

## ⚡ Real-Time Availability (`availability/`)

Knowing a charger exists isn't enough; users need to know if it's currently occupied. ChargeX implements an `AvailabilityDetector` interface capable of cross-referencing static locations with live status APIs.

### 1. Tesla Supercharger Integration (`availability/tesla/`)
ChargeX explicitly queries Tesla's unofficial APIs via Retrofit to determine live stall occupancy.

- **`TeslaGuestApi.kt`**: Unauthenticated endpoint (`/graphql`) checking standard V2/V3/V4 supercharger status (Total Stalls vs Available Stalls).
- **`TeslaOwnerApi.kt`**: Requires an authentication token (`EncryptedPreferenceDataStore`). Capable of probing deeper into vehicle-specific routing and restricted Supercharger clusters.
- **Matching Heuristic (`AvailabilityDetector.kt`)**: Because OCM data and Tesla Live data don't share identical unique IDs in the database, `matchChargepoints()` uses a heuristic algorithm to match the physical properties (Type 2 / CCS combinations) and kW ratings of the location with the live API response to stitch them together seamlessly in the UI.
