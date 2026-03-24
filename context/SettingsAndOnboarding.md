# Settings & Onboarding Implementation

> **Files**:  
> `app/src/main/java/com/chargex/india/fragment/OnboardingFragment.kt`  
> `app/src/main/java/com/chargex/india/fragment/preference/SettingsFragment.kt`  
> `app/src/main/java/com/chargex/india/viewmodel/SettingsViewModel.kt`  
> **Purpose**: Manages the first-time user experience (FTUE) and persistent app configuration, including data storage, privacy consents, and cache management.

---

## 👋 Onboarding Flow (`OnboardingFragment`)

When a user launches ChargeX for the very first time, they are greeted by a `ViewPager2` driven onboarding sequence.

### The Pages (Fragments)
1. **`WelcomeFragment`**: Animated vector loop showing an EV charging.
2. **`IconsFragment`**: Explains the map marker colors (e.g., green for available, red for fault). Uses `ObjectAnimator` for structural entry animations.
3. **`AndroidAutoFragment`**: Informs the user that ChargeX is compatible with their car dashboard.
4. **`DataSourceSelectFragment`**: The critical final step where the user must explicitly choose their preferred initial data provider (OpenChargeMap or OpenStreetMap) and accept the Privacy Policy before the "Get Started" button unlocks.

**State Management**: `PreferenceDataSource.welcomeDialogShown` ensures the user never sees this again once completed.

---

## ⚙️ Settings Engine (`SettingsFragment`)

ChargeX uses the official `androidx.preference` library for user settings.

### Base Architecture
Instead of building a massive list of switches manually, preferences are defined in `res/xml/settings.xml`. The custom `SettingsFragment` loads these XML hierarchies natively.
- Supports dark mode toggles, radius defaults, and developer mode unlocks.

### Data Management (`SettingsViewModel`)
Settings aren't just for UI toggles; users need to manage the enormous amount of map data ChargeX downloads. 
`SettingsViewModel` binds to the `AppDatabase` to provide tools like:
- `chargerCacheCount`: Live `COUNT(*)` query of all cached chargers in Room.
- `clearChargerCache()`: Wipes all `ChargeLocation` rows that are NOT currently marked as favorites, and deletes saved bounding boxes (`SavedRegion`). This reclaims megabytes of storage space.
- `deleteRecentSearchResults()`: Clears the local autocomplete search history cache.
