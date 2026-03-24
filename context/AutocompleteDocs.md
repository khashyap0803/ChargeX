# Search & Autocomplete Architecture

> **Directory**: `app/src/main/java/com/chargex/india/autocomplete/`  
> **Purpose**: Provides a unified, provider-agnostic interface for searching places, addresses, and cities so that the map camera can navigate to the user's desired destination.

---

## 🔍 The Contract: `AutocompleteProvider`

Because location APIs change pricing and terms frequently, ChargeX does not hardcode its search logic to Google Places. Instead, it uses an interface:

```kotlin
interface AutocompleteProvider {
    val id: String
    fun autocomplete(query: String, location: LatLng?): List<AutocompletePlace>
    suspend fun getDetails(id: String): PlaceWithBounds
}
```

This abstraction allows swapping out the search backend entirely without touching the UI code. 

### Data Models
- **`AutocompletePlace`**: The unified result displayed in the dropdown. Contains primary text (e.g., "10 Downing Street"), secondary text (e.g., "London"), distance from the user, and an enum of `AutocompletePlaceType` (derived from Google's standard Place Types).
- **`PlaceWithBounds`**: The final resolved location when a user taps a search result. Contains a specific GPS coordinate (`LatLng`) and a bounding box (`LatLngBounds`) so the map can zoom perfectly to encompass the entire city or neighborhood.

---

## 🗺️ Current Implementation: Mapbox

Currently, ChargeX implements **`MapboxAutocompleteProvider.kt`**.
- It uses the official `MapboxGeocoding` Java SDK.
- It injects the `mapbox_key` string resource.
- It applies a bias to the current `location` (if the map has a GPS lock) so it returns results near the user rather than across the world.

### Features
1. **Highlighting**: Uses Android `SpannableString` to explicitly bold (`Typeface.BOLD`) the text that matches the user's query as they type.
2. **Category Mapping**: Maps Mapbox's `Feature.placeType()` array into ChargeX's standard `AutocompletePlaceType` enum (e.g., mapping `GeocodingCriteria.TYPE_POSTCODE` to `POSTAL_CODE`).
3. **Address Ordering**: Gracefully handles international address formatting, handling cases where the house number precedes the street vs follows the street.

---

## 🔌 UI Integration
The search results from this provider are primarily fed into:
- The `PlaceAutocompleteAdapter` which populates the dropdown below the search bar in `MapFragment`.
- The `PlaceSearchScreen` in Android Auto, which allows users to search from their car display.
