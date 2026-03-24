# WalletFragment.kt

> **File**: `app/src/main/java/com/chargex/india/fragment/WalletFragment.kt`  
> **Purpose**: Provides the user interface for managing the ChargeX offline authorization wallet. It displays the current standard balance, the remaining emergency fund limit, and allows the user to manually add funds.

---

## What Is This File?

The `WalletFragment` is a newly added screen accessible from the main map via the `💰 Wallet` Floating Action Button (FAB). Because the core ChargeX experience relies on **Wallet-First** deductions for both online and offline charging sessions, users need clear visibility into their financial status before traveling out of cellular range.

## 💼 Key Functions & Behaviors

### 🛠 UI Components

1.  **Balance Display (`tvWalletBalance`)**
    - Shows the user's current effective balance, synced directly from `WalletManager`.
2.  **Emergency Context (`tvEmergencyFund`)**: Displays the remaining ₹500 offline emergency fund (e.g., "Emergency Fund: ₹485").

### 3. Usage Statistics Observer

```kotlin
// Observes WalletManager for updates to usage statistics
// 2. **Lifetime Total Spent (`tvTotalSpent`)**
// 3. **Lifetime Charging Time (`tvTotalTime`)**
// 4. **Estimated Charge Time Available (`tvEstChargeTime`)**
```

### ➕ Fund Management

1.  **Quick Top-ups**: Provides one-tap buttons (+₹100, +₹500, +₹1000) to instantly add simulated funds to the wallet.
2.  **Custom Top-ups**: Allows text input for arbitrary deposit amounts.
3.  **Ledger Syncing**: Immediately refreshes UI text fields after a deposit transaction completes.

---

## Screen Layout

```
┌──────────────────────────────────┐
│      « Back to Map               │
├──────────────────────────────────┤
│                                  │
│       💰 Current Balance         │
│           ₹ 500.00               │
│   ≈ 10.0 hrs charging time       │
│                                  │
│   Emergency Fund: ₹500.00        │
│                                  │
│   Total Spent: ₹125.50           │
│   Total Charging Time: 2h 30m    │
│                                  │
├──────────────────────────────────┤
│                                  │
│         Add Funds                │
│                                  │
│   [ +₹100 ] [ +₹500 ] [ +₹1000 ] │
│                                  │
│   [ Custom Amount...          ]  │
│                                  │
│        [ Add to Wallet ]         │
│                                  │
└──────────────────────────────────┘
```

---

## How It Works (Step by Step)

```
1. User taps "💰" FAB on map 
         └── findNavController().navigate(R.id.wallet_fragment)
         │
         ▼
2. WalletFragment is created
         ├── Calls `WalletManager.getBalance()` 
         ├── Calls `WalletManager.getEmergencyFundBalance()`
         └── Updates UI TextViews with formatted currency strings
         │
         ▼
3. User taps quick-add button (e.g., "[ +₹500 ]")
         ├── Hides keyboard if open
         ├── Reads value (500)
         └── Calls `addFunds(500f)`
         │
         ▼
4. User types "250" into custom field and taps "Add to Wallet"
         ├── Validates input parsing (`toFloatOrNull()`)
         ├── Calls `addFunds(250f)`
         └── Clears the input field
         │
         ▼
5. Add Funds Logic
         ├── `WalletManager.addFunds(amount)` executes
         │   └── EncryptedSharedPreferences saves new balance natively
         ├── Shows Snackback: "Added ₹250 to wallet"
         └── Calls `updateUI()` to refresh the big balance text live
```

---

## Financial Source of Truth

The `WalletFragment` contains *no financial logic*. It acts purely as a dumb terminal. 

If the user starts a charging session offline (via `BookingVerificationFragment.kt`), the 1-second interval timer calculates the cost (₹0.83/min) and calls `WalletManager.deductFunds(cost)`. If the user has the WalletFragment open during this time (or reopens it later), `updateUI()` simply queries the `WalletManager` which reads the true state directly from disk.

---

## Dependencies

- **`WalletManager.kt`**: The singleton responsible for holding the actual math and SharedPreferences persistence.
- **`Navigation Component`**: Used for the safe-args standard transition entering/exiting the fragment layout.
