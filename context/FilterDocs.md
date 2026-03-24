# Filter Fragment & View Model

> **Files**:  
> `app/src/main/java/com/chargex/india/fragment/FilterFragment.kt`  
> `app/src/main/java/com/chargex/india/viewmodel/FilterViewModel.kt`  
> **Purpose**: Allows the user to construct complex filtering criteria (connector type, minimum power level, free/paid, operator) to narrow down the chargers shown on the map. It also supports saving these criteria as named reusable "Profiles".

---

## 🔍 Core Functionality

### 1. `FilterFragment.kt` (UI Layer)
This fragment hosts a `RecyclerView` populated by an adapter (`FiltersAdapter`) that dynamically renders rows based on the filter definitions provided by the active data source (e.g., OpenChargeMap).
- **Navigation**: Uses Jetpack Navigation's dialog-like transitions (`MaterialSharedAxis`) to slide up over the Map.
- **Menu Actions**: 
  - **Apply**: Commits the active filter selection to the local database and returns to the map.
  - **Save Profile**: Triggers a dialog asking for a profile name before saving.
  - **Reset**: Clears all current filter criteria.

### 2. `FilterViewModel.kt` (Logic Layer)
Handles the heavy lifting of bridging the UI dropdowns/checkboxes with the Room database (`filterprofile` and `filtervalue` tables).

#### Key LiveData Properties:
- `filtersWithValue`: Merges the available filters for the active data source with the user's currently selected values.
- `filterProfile`: Checks the `PreferenceDataSource` to see if a named profile is currently active (e.g., "My Tata Nexon Profile"). If an ID is present, it fetches the actual profile name from Room to display in the Toolbar.

#### Persistence Operations:
- `saveFilterValues()`: Takes the ephemeral UI selections (`filtersWithValue.value`) and persists them to the Room database under the generic `FILTERS_CUSTOM` profile ID. Updates SharedPreferences so the Map knows to load custom filters.
- `saveAsProfile(name: String)`:
  - Generates a new unique `profileId` in the `filterprofile` Room table under the provided name.
  - Saves the current criteria under that new ID in `filtervalue`.
  - Sets the app's `filterStatus` to this new profile.
- `resetValues()`: Purges records for `FILTERS_CUSTOM` from the table, effectively turning off active filtering.

---

## ⛓️ How Filters Reach the Map

1. **User Action**: Taps "Apply" in `FilterFragment`.
2. **Persistence**: `FilterViewModel` writes to Room and sets `prefs.filterStatus = FILTERS_CUSTOM`.
3. **Map Screen Reaction**: `MapFragment` / `MapViewModel` detects the lifecycle return.
4. **Data Load**: `MapViewModel` reads `prefs.filterStatus`.
5. **Retrieval**: `MapViewModel` queries Room for the filter values associated with that status.
6. **API Query**: Passes the filter criteria to `OpenChargeMapApiWrapper`, which translates them into URL query parameters (e.g., `&connectiontypeid=33&minpowerkw=50`).
7. **Map Update**: The marker manager clears old pins and drops new pins that strictly match the criteria.
