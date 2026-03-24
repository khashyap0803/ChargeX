# BookingVerificationFragment.kt

> **File**: `app/src/main/java/com/chargex/india/fragment/BookingVerificationFragment.kt`  
> **Purpose**: Handles offline authentication at physical charging stations. It scans QR codes to verify presence, initiates charging sessions, displays a real-time elapsed timer and cost ticker, and triggers offline wallet deductions when charging stops.

---

## What Is This File?

When a user pulls up to a charging station, they need to authenticate and start charging. In remote areas without 4G/5G, this fragment provides the UI for the **Offline Booking Flow**.

### Core Features
1. **Dual-Mode UI**:
   - **User Mode**: Generates a digitally signed booking token (ECDSA secp256r1) and displays it as a QR code.
   - **Station Mode**: Verifies a booking QR code offline, preventing tampering or replay attacks.
2. **QR Code Scanner**: Uses the ZXing (Zebra Crossing) library via camera preview to scan physical QR codes affixed to charging terminals, plus a gallery fallback.
3. **State Management**: Reacts to `VerificationViewModel`'s state changes (Idle → Valid → Charging → Finished).
4. **Live Timer & Cost updates**: Collects `StateFlow` streams from the ViewModel to display an active stopwatch (`00:14:32`) and a real-time rupee cost counter (`₹12.45`) during an active charge.

---

## Screen Layouts (State Dependent)

The fragment uses a `TabLayout` to switch between User Mode and Station Mode.

### 1. User Mode (Generation)
```
┌──────────────────────────────────┐
│  [User Mode]      Station Mode   │
├──────────────────────────────────┤
│                                  │
│  Credit Amount: [ ₹300      ]    │
│  Validity Mins: [ 60        ]    │
│                                  │
│     [ Generate Trust Token ]     │
│                                  │
│        ▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄▄          │
│        █ ▄▄▄ █ ▄ █▄▄ ▄█          │
│        █ ███ █ █ █ ▄▀ █          │
│        █ ▀▀▀ █▄▄▀ ▄ ▄ █          │
│        ▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀▀          │
│                                  │
└──────────────────────────────────┘
```

### 2. Station Mode (Scanning/Idle)
```
┌──────────────────────────────────┐
│      « Back to Station           │
├──────────────────────────────────┤
│                                  │
│       [ CAMERA PREVIEW ]         │
│     (Scanning for QR Code)       │
│                                  │
│  "Point camera at the terminal"  │
│                                  │
│       [ Pick from Gallery ]      │
└──────────────────────────────────┘
```

### 3. Station Mode (Verified & Ready)
```
┌──────────────────────────────────┐
│      « Back to Station           │
├──────────────────────────────────┤
│           [ SUCCESS ]            │
│       Terminal Validated         │
│        STATION_ID_12345          │
│                                  │
│   Rate: ₹0.83 / minute           │
│   Wallet Balance: ₹500.00        │
│                                  │
│     [ ▶ START CHARGING ]         │
└──────────────────────────────────┘
```

### 4. Station Mode (Active Session)
```
┌──────────────────────────────────┐
│      « Back to Station           │
├──────────────────────────────────┤
│          ⚡ CHARGING ⚡          │
│                                  │
│        00:15:42                  │
│        ₹ 13.04                   │
│                                  │
│     [ ⏹ STOP CHARGING ]         │
└──────────────────────────────────┘
```

---

## How It Works (Step by Step)

```
1. User taps "Offline Auth" FAB on map → opens BookingVerificationFragment
         │
         ▼
2. Camera starts via ZXing `DecoratedBarcodeView`
         │
         ▼
3. User scans a QR code
         ├── success → barcodeRead() callback fired
         ├── pauses scanning to prevent double-reads
         └── Calls `VerificationViewModel.verifyTerminal(qrText)`
         │
         ▼
4. ViewModel transitions to `VerificationResult.Valid`
         ├── UI observes the state change
         ├── Camera preview is hidden
         └── "Ready to Charge" card appears with the "Start" button
         │
         ▼
5. User taps "Start Charging"
         ├── Calls `VerificationViewModel.startCharging()`
         │   (which checks WalletManager for sufficient funds first)
         ├── State changes to `VerificationResult.Charging`
         ├── "Ready" card is hidden
         └── Live Timer card appears
         │
         ▼
6. Live Flow Emissions
         ├── Fragment `lifecycleScope` collects `elapsedSeconds` and `sessionCost`
         ├── Modulus math formats seconds into HH:MM:SS
         └── UI elements `tvTimerDisplay` and `tvRunningCost` update every 1 second
         │
         ▼
7. User taps "Stop Charging"
         ├── Calls `VerificationViewModel.stopCharging()`
         │   (which pauses timer AND executes `WalletManager.deductFunds`)
         ├── State changes to `VerificationResult.Finished`
         └── Shows final summary string: "Session ended. Time: 15m 42s, Cost: ₹13.04"
```

---

## Data Flow: ViewModel Integration

The fragment uses Kotlin's `lifecycleScope.launch` with `repeatOnLifecycle(Lifecycle.State.STARTED)` to safely observe the hot `StateFlow` streams from the `VerificationViewModel`. This ensures the UI only updates when the user is actively looking at the screen, saving CPU cycles and battery.

```kotlin
viewLifecycleOwner.lifecycleScope.launch {
    viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
        // Collect QR payload for display in User Mode
        launch {
            viewModel.generatedQrPayload.collect { payload ->
                if (payload != null) showQrCode(payload)
            }
        }
        
        // Collect verification results in Station Mode
        launch {
            viewModel.verificationResult.collect { result ->
                if (result != null) {
                    showVerificationResult(result)
                    if (result is VerificationResult.Valid) {
                        binding.cardChargingTimer.isVisible = true
                    }
                }
            }
        }
        
        // Collect Timer Updates
        launch {
            viewModel.elapsedSeconds.collect { elapsed ->
                binding.tvTimerDisplay.text = viewModel.formatElapsed(elapsed)
            }
        }
        
        // Collect Cost Updates
        launch {
            viewModel.sessionCost.collect { cost ->
                binding.tvRunningCost.text = "₹${"%.2f".format(cost)}"
            }
        }
    }
}
```

---

## PKI Workflow Demonstration

This fragment implements a complete offline Public Key Infrastructure (PKI) workflow without needing a database:
1. **Key Pair Generation**: Uses Android Keystore to generate ECDSA `secp256r1` keys securely on-device.
2. **Ticket Signing**: `OfflineTicket` payload is serialized and signed with SHA256withECDSA using the private key.
3. **QR Code Encoding**: The payload + Base64 signature is rendered into a visible QR code.
4. **Offline Verification**: The simulated charging station reads the QR code and uses the public key to mathematically verify the signature.
5. **Tamper Detection**: Even changing `TRST-` to `FAKE-` inside the QR payload instantly causes the signature math to fail, proving the data was modified.

---

## Key Design Decisions

1. **StateFlow over LiveData**: Used `StateFlow` for the high-frequency UI updates (1 per second) because it handles backpressure better and enforces initial states, avoiding null-checks.
2. **ZXing Wrapper**: Used the `journeyapps:zxing-android-embedded` library because it handles camera hardware lifecycle edges (sleeping, rotating, backgrounding) automatically, which is notoriously difficult on Android.
3. **Gallery Fallback**: Provided an image upload fallback in case the phone's physical camera hardware fails or the terminal's QR code is scratched but the user has a reference photo.
4. **Offline Primacy**: The entire flow is purposely disconnected from Retrofit. The `WalletManager` handles the deduction entirely via secure SharedPreferences locally upon `stopCharging()`.
