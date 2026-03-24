# ChargeX: Major Project Demo & Testing Guide

This guide provides exact step-by-step instructions on how to demonstrate the research-grade features of the ChargeX application to your professors and examiners in both **Online** and **100% Offline** scenarios.

---

## 1. Dynamic Wait Time Prediction

**Where to find it:** The Main Map View (`MapFragment`).

### ▶️ Test 1: Online Mode (Live Prediction)
1. Turn **ON** your phone's Wi-Fi and Mobile Data.
2. Open the ChargeX App to the Main Map.
3. Tap on any charging station marker (e.g., in HITEC City).
4. **Observe:** The bottom sheet pops up. Look at the subtitle under the station name. It will say `⚡ Estimated wait: X min` or `⚡ Available`. This is using the online M/M/s model with zero known app users.

### ▶️ Test 2: Offline Mode (Heuristic Prediction)
1. Turn **OFF** your phone's Wi-Fi and Mobile Data (Enable Airplane Mode).
2. While still in the app, tap on a *different* charging station marker (or the same one).
3. **Observe:** The app instantly detects the lack of network. It bypasses the cloud, reads your device's clock, matches the hour against the Lambda profiles in RoomDB, and calculates the Erlang-C formula natively.
4. The UI subtitle will clearly change to: `⚠️ Offline Prediction: Estimated wait...` or `⚠️ Offline Prediction: Available...`. This proves the app can run complex statistical AI on the edge (mobile processor) without a server.

---

## 2. Offline Charging Verification (Micro-credit)

**Where to find it:** The "Offline Verification" Screen (`BookingVerificationFragment`).

### ▶️ Test 1: Online Registration (Generating the Trust Token)
1. Ensure your phone is **ONLINE**.
2. Navigate to the Offline Verification screen in the app.
3. Tap the **"Generate Demo Token"** button.
4. **Behind the scenes:** The app securely simulates the ChargeX backend. It generates the new `"TRST-DEMO"` Trust Token for ₹300 credit and signs it with an isolated Private Key.

### ▶️ Test 2: Completely Offline (Basement Scenario)
1. Turn **OFF** Wi-Fi and Mobile Data (Airplane Mode). 
2. Tap the **"Verify Last Generated (Offline)"** button.
3. **Observe:** The app uses its hardcoded Public Key to instantly verify the ECDSA signature mathematically. A green "Valid" card appears showing the `₹300 Max Credit`.
4. **The "Examiner Test" (Tampering):** To prove it's actually secure, tap the **"Test Tampered Payload"** button. The app maliciously modifies the string (changing `TRST-` to `FAKE-`).
5. **Observe:** The math fails, and an instant red `Tampered` alert is thrown. This proves it is impossible to hack the offline wallet system.

---

## 3. Physics-Based Range Prediction

**Where to find it:** The "Vehicle Input" (`VehicleInputFragment`) and "Navigation" (`NavigationFragment`) screens.

### ▶️ Test 1: Standard / Online Baseline
1. Ensure the app is **ONLINE**.
2. Go to the Vehicle Specification screen and enter your vehicle details (e.g., Nexon EV).
3. Check the calculated estimated range. The app uses the baseline $E_{total}$ physics formula with standard 35°C temperature.
4. Try routing to a station via the Navigation feature. The backend equations will calculate route feasibility based on this baseline.

### ▶️ Test 2: Offline Extreme Heuristics (Traffic & Climate)
1. Turn **OFF** your Wi-Fi and Mobile Data.
2. Wait for 10 seconds or restart the app component (go back and re-enter Vehicle Specs).
3. **The Trick:** Before doing this, go to your Android Settings and change the device Date to **May 15th** and the Time to **6:00 PM**.
4. **Observe:** When the app is offline and sees May at 6 PM, the `RangeCalculator.kt` triggers its **Heuristic Fallback**. It mathematically inflates the driving time by 2.5x (Evening Rush Hour default) and locks the temperature to a brutal 40°C (Indian Summer default).
5. The resulting Range Estimate will aggressively plummet because the algorithm calculates that you will be stuck in traffic with your AC blasting for hours. 

This proves to your professors that your app is not just a UI over Google Maps, but a completely standalone, defensively-programmed thermodynamic and statistical engine!
