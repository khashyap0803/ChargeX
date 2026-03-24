# ChargeX: High-Level Research Implementations

This document details the mathematical models and algorithmic implementations integrated into the ChargeX ecosystem. It explains *exactly* how these complex theories work in the real, practical world of Electric Vehicle infrastructure, specifically focusing on **100% Offline Resilience**.

---

## 1. Dynamic Wait Time Prediction (M/M/s Queuing Theory)

### The Problem in the Real World
Imagine you are driving to a charging station in an area with zero cellular network. You want to know if there is a queue. The problem is that neither you nor the station has a live internet feed telling us exactly who is plugged in right now.

### The Practical Solution: The "Offline-First" Statistical Prediction Engine
ChargeX uses an **Intention-Aware M/M/s Queuing Model**. The genius of this model for a major project is that **it does not need the internet to make predictions**. 

**How it Works Completely Offline:**
1. **The Historical Database (Stored locally):** The app has a hardcoded database (e.g., RoomDB) of Lambda ($\lambda$) profiles. These dictate the historical traffic patterns (e.g., HITEC City at 6 PM on a Friday has an arrival rate of $\lambda = 3$ cars/hour).
2. **The Offline Calculation:** Even without the internet, your phone knows the GPS location (from offline maps) and the current time from your device clock. 
3. **The Math Engine:** The app runs the **Erlang-C Formula** locally on your mobile processor. It inputs the $\lambda$ value for the current hour and area to calculate the *Probability* that the station is occupied. 
4. **The Result:** The app UI displays: *"Offline Prediction: Based on historical data for 6 PM, there is an estimated 12-minute wait for this station."* It provides a statistically robust guess exactly when live data is unavailable.

---

## 2. Offline Charging Verification & Payment

### The Problem in the Real World
**You asked two brilliant questions:**
1. *What if multiple people book the same station offline?*
2. *What if a user has NO internet AND NO money in their app wallet to top up while at the offline station?*

These are the exact edge cases a research-grade system must handle. 

### The Practical Solution: The Cryptographic Post-Paid Micro-Credit System
To solve the double-booking problem, we use a Wallet System instead of a Time-Slot System. To solve the "Zero Balance + No Internet" problem, we implement a **Deferred Billing (Micro-Credit) Protocol** using Asymmetric Cryptography (RSA).

**How it Works (The Edge Case Scenario):**
1. **The Trust Token (Generated previously):** When you initially registered for the ChargeX app (days ago, with internet), you completed your profile. The server generated an RSA-signed "Trust Token" stored on your phone. This token tells offline stations: *"This user is verified. They are approved for ₹300 of emergency offline credit."*
2. **The Offline Basement:** You arrive at the station. Your phone has **no internet**, the station has **no internet**, and your prepay wallet has **₹0 balance**. You scan your QR code anyway.
3. **The Secure Handshake:** The charging station uses its hard-coded **Public Key** to verify your "Trust Token". It confirms the signature is mathematically authentic and you are allowed emergency credit.
4. **Dispensing & Debt Logging:** The station dispenses up to ₹300 of power. It records a negative balance (debt) for your User ID in its internal memory.
5. **The Sync (Store & Forward):** When the station eventually regains internet (or when you leave the basement and your phone reconnects), the transaction syncs to the central ChargeX server. Your account now shows a balance of -₹300. You cannot use the app again until you clear the debt online.

This mirrors enterprise-grade systems like Fastag or Metro Cards, allowing seamless hardware operation while aggressively preventing fraud through cryptography.

---

## 3. Physics-Based Range Prediction Engine

### The Problem in the Real World
A straight line on a map is useless for EVs. We need traffic, climate, and temperature. But what if your phone is completely offline and cannot fetch the live Google Traffic API or Live Weather API?

### The Practical Solution: The "Heuristic Fallback" Thermodynamic Engine
When the user has internet, the physics formula uses Live APIs. When the user is offline, the app dynamically shifts to a **Heuristic Fallback Engine**.

**The Physics Formula for battery drain:**
$$ E_{\text{total}} = (Rolling Resistance) + (Aerodynamics) + (Hill Climbs) + (Air Conditioning \times Time) $$

**How it Works Offline:**
1. **Offline Traffic (Time Fallback):** The app detects it has no internet. Instead of fetching "live traffic", it uses the offline downloaded map to get the base distance (e.g., 10 km). It then checks the local clock. If it is 6 PM (Rush Hour), it mathematically inflates the driving time by 2.5x compared to 3 AM. 
2. **Offline Climate (Temperature Fallback):** The app cannot fetch the weather. It checks the calendar instead. If the date is May 15th in India, the application defaults the temperature variable to a conservative 40°C. 
3. **The Result:** The formula executes using these safely inflated heuristic values. It assumes *worst-case scenario* for your Air Conditioning and traffic duration, ensuring you do not get stranded offline. The app updates: *"Offline Mode: Using historical traffic and seasonal weather averages. Effectively reduced range applied."*
