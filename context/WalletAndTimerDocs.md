# Wallet & Offline Charging Simulation

> This document details the inner workings of the payment deduction and timer systems used during a charging session, particularly when the user has no internet access.

---

## 1. PURPOSE AND ARCHITECTURE

The **Wallet System** combines two critical files:
1. `VerificationViewModel.kt` (The Timing & State Machine)
2. `WalletManager.kt` (The Ledger & Logic Gate)

When a user parks at an EV charging station out of cellular range (e.g., in a basement parking structure or remote highway stop), they can still authenticate and charge securely using an offline, wallet-first approach with an emergency fallback limit.

---

## 2. THE CHARGING TIMER (VerificationViewModel)

### State Machine Design
The `VerificationViewModel` orchestrates the charging flow using Kotlin Coroutines and `StateFlow` for immediate UI reactivity.

States:
- **`Idle`**: Scanner is ready, wait for QR code decode.
- **`Valid`**: QR accepted. User can start charging.
- **`Charging`**: Background timer is running, session cost accumulates.
- **`Finished`**: Charge stopped, final deduction posted.

### The Infinity Timer Loop
Once the user presses "Start Charging", the app spins up a background coroutine linked to the ViewModel's lifecycle (`viewModelScope.launch`):

```kotlin
private var timerJob: Job? = null

fun startCharging() {
    timerJob = viewModelScope.launch(Dispatchers.Default) {
        while (isActive) { // Infinite loop that stops if canceled
            delay(1000)    // Wait precisely 1 second
            
            val newSeconds = _elapsedSeconds.value + 1
            _elapsedSeconds.value = newSeconds // Expose to UI
            
            // MATH: ₹50 / 60 mins = ₹0.83 per minute
            // cost = (seconds / 60) * 0.83
            val currentCost = (newSeconds / 60.0) * WalletManager.getChargeRatePerMinute()
            _sessionCost.value = currentCost
        }
    }
}
```

The UI (in `BookingVerificationFragment.kt`) collects these `StateFlow`s and mathematically formats them:
- `elapsedSeconds` (e.g., `124`) → formatted via modulus arithmetic to `%02d:%02d:%02d` → `"00:02:04"`
- `sessionCost` (e.g., `1.7258`) → string formatted `%.2f` → `"₹1.73"`

### Pre-Charge Validation
Before starting the timer, `VerificationViewModel` calls `WalletManager.hasSufficientFunds(isOnline = false)`.
If the user's wallet is completely empty (₹0) and they have consumed their emergency threshold offline, the charge is rejected.

---

## 3. LEDGER & DEDUCTION (WalletManager)

The `WalletManager` is a singleton object object that enforces the payment hierarchy. It acts as the local source of truth for the user's funds, storing values persistently via Android `SharedPreferences`.

### The Financial Variables
1. **Wallet Balance (`WALLET_BALANCE`)**: Hardcoded initial value. Simulates a prepaid account top-up.
2. **Emergency Limit (`EMERGENCY_FUND_LIMIT`)**: Set to **₹500.0**. This is a safety margin for offline users.
3. **Emergency Used (`EMERGENCY_USED`)**: Tracks how much of the ₹500 has been burned.

### The Waterfall Deduction Logic

When `stopCharging()` is triggered, `WalletManager.deductFunds(cost)` executes a precise ledger waterfall operation.

```
SCENARIO 1: Cost = ₹100, Wallet = ₹500
- Wallet easily covers it.
- Action: Wallet = 500 - 100 = 400.
- Result: ✅ SUCCESS
```

```
SCENARIO 2: Cost = ₹100, Wallet = ₹20, Offline
- Wallet covers part of it (Wallet becomes ₹0).
- Remaining ₹80 must come from Emergency Fund.
- Action: EmergencyUsed = EmergencyUsed + 80.
- Result: ✅ SUCCESS
```

```
SCENARIO 3: Cost = ₹100, Wallet = ₹0, Offline, EmergencyUsed = ₹450
- Wallet is empty.
- Emergency Fund remaining = (500 - 450) = ₹50.
- Cost (100) > Emergency Remaining (50).
- Action: CANNOT DEDUCT. The charge exceeds allowed offline limits.
- Result: ❌ FAILURE
```

### Online vs. Offline Rule
The `deductFunds` method accepts a boolean `isOnline`.
- If the phone is **Online**, it *refuses* to use the Emergency Fund. Emergency funds are strictly a trust-based mechanism for when server verification is impossible. The user must top up their wallet via an online payment gateway.
- If the phone is **Offline**, it unlocks the ₹500 leeway.

### Data Persistence
Because this is financial data, it cannot be held in RAM. Every deduction triggers `EncryptedSharedPreferences` (or standard SharedPreferences as a fallback) `.edit().putFloat("key", newValue).apply()`, writing the ledger to disk immediately to survive a sudden app crash or battery die-out mid-charge.
