# ChargersModel.kt

> **File**: `app/src/main/java/net/vonforst/evmap/model/ChargersModel.kt`  
> **Purpose**: Defines the core data models for everything related to charging stations — locations, connectors, addresses, costs, opening hours, and photos.

---

## What Is This File?

This is the **data foundation** of the entire app. Every charging station, connector, address, and piece of metadata is defined here. These classes are:
- Stored in the **Room database**
- Fetched from **APIs** (OpenChargeMap, OpenStreetMap)
- Displayed in **UI** fragments
- Used for **filtering** and **computation**

---

## Class Hierarchy

```
ChargepointListItem (sealed interface)
    ├── ChargeLocation     — A full charging station
    └── ChargeLocationCluster — A group of stations (zoomed out)

ChargeLocation
    ├── id: Long
    ├── dataSource: String
    ├── name: String
    ├── coordinates: Coordinate
    ├── address: Address
    ├── chargepoints: List<Chargepoint>
    ├── network: String?
    ├── cost: Cost?
    ├── openingHours: OpeningHours?
    ├── photos: List<ChargerPhoto>?
    ├── faultReport: FaultReport?
    └── chargepriceData: ChargepriceData?

Chargepoint
    ├── type: String         (e.g., "CCS", "Type 2", "CHAdeMO")
    ├── power: Double?       (kW)
    ├── count: Int           (number of identical sockets)
    ├── voltage: Int?
    └── current: Int?
```

---

## ChargeLocation — The Main Model

A `ChargeLocation` represents one complete **charging site** (like a gas station). It might have multiple chargepoints (connectors/plugs).

```kotlin
@Entity
data class ChargeLocation(
    @PrimaryKey val id: Long,              // Unique ID from data source
    val dataSource: String,                 // "openchargemap" or "openstreetmap"
    val name: String,                       // Station name
    @Embedded val coordinates: Coordinate,  // GPS position
    @Embedded val address: Address?,        // Street address
    
    val chargepoints: List<Chargepoint>,    // Available connectors
    val network: String?,                   // Network operator (e.g., "Tata Power")
    val url: String?,                       // Link to more info
    val editUrl: String?,                   // Link to edit on the data source
    
    @Embedded val cost: Cost?,              // Pricing info
    val freecharging: Boolean?,             // Is it free?
    val freeparking: Boolean?,              // Is parking free?
    
    val openingHours: OpeningHours?,        // When is it open?
    val faultReport: FaultReport?,          // Any reported problems?
    val photos: List<ChargerPhoto>?,        // Station photos
    
    @Embedded val chargepriceData: ChargepriceData?,
    val license: String?,
    
    val timeRetrieved: Instant,             // When this data was fetched
    val isDetailed: Boolean                 // Full details or summary?
)
```

### Key Methods:

| Method | Purpose |
|--------|---------|
| `maxPower(connectors?)` | Gets the highest kW power from specific connectors |
| `isMulti(connectors?)` | Does this station have multiple connector types? |
| `formatChargepoints(...)` | Human-readable list of connectors and powers |

---

## Chargepoint — One Connector

A `Chargepoint` is a single type of plug/socket at a station:

```kotlin
data class Chargepoint(
    val type: String,          // "CCS", "Type 2", "CHAdeMO", etc.
    val power: Double?,        // Power in kW (e.g., 50.0, 22.0)
    val count: Int,            // How many of this type (e.g., 2)
    val voltage: Int?,         // Voltage (e.g., 400V)
    val current: Int?          // Amperage (e.g., 125A)
)
```

**Example**: A station might have:
```
Chargepoint(type="CCS", power=50.0, count=2)     → 2 × CCS at 50 kW
Chargepoint(type="Type 2", power=22.0, count=4)  → 4 × Type 2 at 22 kW
```

### Common Connector Types in India:

| Type | Description | Typical Power |
|------|-------------|---------------|
| CCS (CCS2) | Combined Charging System | 50-350 kW (DC fast) |
| Type 2 | European plug | 3.3-22 kW (AC slow) |
| CHAdeMO | Japanese standard | 50 kW (DC fast) |
| GB/T | Chinese standard | 60-120 kW (DC fast) |

---

## Coordinate — GPS Position

```kotlin
data class Coordinate(
    val lat: Double,    // Latitude, e.g., 17.3850
    val lng: Double     // Longitude, e.g., 78.4867
)
```

---

## Address — Location Details

```kotlin
data class Address(
    val city: String?,
    val country: String?,
    val postcode: String?,
    val street: String?
) {
    override fun toString(): String {
        // Formats as: "Street, City, Postcode, Country"
        // Skips any null/blank fields
    }
}
```

---

## Cost — Pricing Information

```kotlin
data class Cost(
    val freecharging: Boolean?,    // Is charging free?
    val freeparking: Boolean?,     // Is parking free?
    val descriptionShort: String?, // Brief cost description
    val descriptionLong: String?,  // Detailed cost breakdown
) {
    fun getStatusText(ctx, emoji): String
    // Returns: "Free", "Paid", or "Free Parking"
    // With emoji: "⚡ Free", "💰 Paid"
}
```

---

## OpeningHours — When Is It Open?

```kotlin
sealed class OpeningHours {
    data class AlwaysOpen : OpeningHours()
    data class TwentyFourSeven : OpeningHours()
    data class Hours(val days: OpeningHoursDays) : OpeningHours()
    
    fun getStatusText(ctx): String
    // Returns: "Open 24/7", "Open now", "Closed", etc.
}
```

---

## How It Connects to Other Files

```
ChargersModel.kt (data models)
    │
    ├──▶ Room Database          — @Entity annotation → stored in SQLite
    │    (ChargeLocationsDao)
    │
    ├──▶ OpenChargeMapApi       — API responses are converted to these models
    │    (OpenStreetMapApi)
    │
    ├──▶ MapFragment            — Displayed on the map as markers
    │
    ├──▶ MarkerUtils            — Uses ChargeLocation for marker positioning,
    │                              icon generation, and range filtering
    │                              (isInRange + clearAll for lifecycle)
    │
    ├──▶ MapViewModel           — Loads and holds List<ChargeLocation> as LiveData
    │
    ├──▶ NavigationFragment     — ChargeLocation coordinates used as destination
    │                              for route + energy feasibility calculation
    │
    └──▶ Detail screens         — Shows full station info when user taps a marker
```

---

## Key Design Decisions

1. **Room compatibility**: Uses `@Entity`, `@PrimaryKey`, and `@Embedded` annotations so the data can be stored in SQLite without conversion.

2. **`Parcelable`**: `ChargeLocation` implements `Parcelable` so it can be passed between fragments efficiently via Android Bundles.

3. **Sealed class for `ChargepointListItem`**: Allows the chargepoints list to contain both individual stations AND clusters, with type-safe handling.

4. **Nullable fields**: Most fields are nullable because different data sources (OpenChargeMap vs OpenStreetMap) provide different levels of detail.
