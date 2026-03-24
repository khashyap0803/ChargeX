# WalletFragment.kt

> **File**: `app/src/main/java/com/chargex/india/fragment/WalletFragment.kt`  
> **Purpose**: Provides the user interface for managing the ChargeX offline authorization wallet. It displays the current standard balance, the remaining emergency fund limit, and allows the user to manually add funds.

---

## What Is This File?

The `WalletFragment` is a newly added screen accessible from the main map via the `💰 Wallet` Floating Action Button (FAB). Because the core ChargeX experience relies on **Wallet-First deductions** for both online and offline charging sessions, users need clear visibility into their financial status before traveling out of cellular range.

### Core Features
1. **Balance Display**: Shows the primary wallet balance synced directly from `WalletManager`.
2. **Emergency Context**: Displays the remaining ₹500 offline emergency fund (e.g., "Emergency Fund: ₹485 / ₹500").
3. **Quick Top-ups**: Provides one-tap buttons (+₹100, +₹500, +₹1000) to instantly add simulated funds to the wallet.
4. **Custom Top-ups**: Allows text input for arbitrary deposit amounts.
5. **Ledger Syncing**: Immediately refreshes UI text fields after a deposit transaction completes.

---

## Screen Layout

```
┌──────────────────────────────────┐
│      « Back to Map               │
├──────────────────────────────────┤
│                                  │
│       💰 Current Balance         │
│           ₹ 500.00               │
│                                  │
│  [ Emergency Fund: ₹500 / ₹500 ] │
│  (Available only when offline)   │
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
