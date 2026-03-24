package com.chargex.india.wallet

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * WalletManager — Manages user's wallet balance and emergency fund.
 *
 * ### Fund Priority:
 * 1. **Wallet Balance** — Main fund for all charging (online & offline)
 * 2. **Emergency Fund** — Used only when wallet is empty AND device is offline
 *
 * ### Persistence:
 * All balances stored in SharedPreferences for simplicity.
 * In production, this would sync with a backend server.
 */
object WalletManager {

    private const val TAG = "WalletManager"
    private const val PREFS_NAME = "chargex_wallet"
    private const val KEY_BALANCE = "wallet_balance"
    private const val KEY_EMERGENCY_FUND = "emergency_fund"
    private const val KEY_TOTAL_SPENT = "total_spent"
    private const val KEY_TOTAL_CHARGED_MINUTES = "total_charged_minutes"

    private const val DEFAULT_EMERGENCY_FUND = 500.0  // ₹500 default emergency credit
    private const val CHARGE_RATE_PER_MINUTE = 0.83   // ₹0.83/min ≈ ₹50/hr for L2 charger

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ─── Balance Operations ─────────────────────────────────

    fun getBalance(context: Context): Double =
        prefs(context).getFloat(KEY_BALANCE, 0f).toDouble()

    fun getEmergencyFund(context: Context): Double =
        prefs(context).getFloat(KEY_EMERGENCY_FUND, DEFAULT_EMERGENCY_FUND.toFloat()).toDouble()

    fun getTotalSpent(context: Context): Double =
        prefs(context).getFloat(KEY_TOTAL_SPENT, 0f).toDouble()

    fun getTotalChargedMinutes(context: Context): Double =
        prefs(context).getFloat(KEY_TOTAL_CHARGED_MINUTES, 0f).toDouble()

    fun addFunds(context: Context, amount: Double): Boolean {
        if (amount <= 0) return false
        val current = getBalance(context)
        val newBalance = current + amount
        prefs(context).edit().putFloat(KEY_BALANCE, newBalance.toFloat()).apply()
        Log.d(TAG, "💰 Added ₹${"%.2f".format(amount)} — New balance: ₹${"%.2f".format(newBalance)}")
        return true
    }

    /**
     * Deduct funds from wallet or emergency fund.
     * @param isOnline Whether the device is currently online
     * @return true if deduction was successful, false if insufficient funds
     */
    fun deductFunds(context: Context, amount: Double, isOnline: Boolean): Boolean {
        if (amount <= 0) return true

        val walletBalance = getBalance(context)

        // Try wallet first
        if (walletBalance >= amount) {
            val newBalance = walletBalance - amount
            prefs(context).edit()
                .putFloat(KEY_BALANCE, newBalance.toFloat())
                .putFloat(KEY_TOTAL_SPENT, (getTotalSpent(context) + amount).toFloat())
                .apply()
            Log.d(TAG, "💳 Deducted ₹${"%.2f".format(amount)} from wallet — Remaining: ₹${"%.2f".format(newBalance)}")
            return true
        }

        // If wallet has some, use it and try emergency fund for the rest
        if (walletBalance > 0) {
            val remaining = amount - walletBalance
            prefs(context).edit()
                .putFloat(KEY_BALANCE, 0f)
                .putFloat(KEY_TOTAL_SPENT, (getTotalSpent(context) + walletBalance).toFloat())
                .apply()

            // Fall through to emergency fund for remaining amount
            return deductFromEmergencyFund(context, remaining, isOnline)
        }

        // Wallet is empty — try emergency fund
        return deductFromEmergencyFund(context, amount, isOnline)
    }

    private fun deductFromEmergencyFund(context: Context, amount: Double, isOnline: Boolean): Boolean {
        // Emergency fund only used when offline
        if (isOnline) {
            Log.w(TAG, "🚫 Wallet empty and online — prompt user to add funds")
            return false
        }

        val emergency = getEmergencyFund(context)
        if (emergency >= amount) {
            val newEmergency = emergency - amount
            prefs(context).edit()
                .putFloat(KEY_EMERGENCY_FUND, newEmergency.toFloat())
                .putFloat(KEY_TOTAL_SPENT, (getTotalSpent(context) + amount).toFloat())
                .apply()
            Log.d(TAG, "🆘 Deducted ₹${"%.2f".format(amount)} from EMERGENCY FUND — Remaining: ₹${"%.2f".format(newEmergency)}")
            return true
        }

        Log.e(TAG, "❌ Insufficient funds — Wallet: ₹0, Emergency: ₹${"%.2f".format(emergency)}, Needed: ₹${"%.2f".format(amount)}")
        return false
    }

    fun recordChargingSession(context: Context, durationMinutes: Double) {
        val totalMinutes = getTotalChargedMinutes(context) + durationMinutes
        prefs(context).edit()
            .putFloat(KEY_TOTAL_CHARGED_MINUTES, totalMinutes.toFloat())
            .apply()
    }

    // ─── Utility ─────────────────────────────────

    /**
     * Get the charging rate per minute.
     */
    fun getChargeRatePerMinute(): Double = CHARGE_RATE_PER_MINUTE

    /**
     * Calculate cost for a given duration.
     */
    fun calculateCost(durationMinutes: Double): Double =
        durationMinutes * CHARGE_RATE_PER_MINUTE

    /**
     * Estimate how many minutes of charging can be done with available funds.
     */
    fun estimateChargingMinutes(context: Context, isOnline: Boolean): Double {
        val available = if (isOnline) {
            getBalance(context)
        } else {
            getBalance(context) + getEmergencyFund(context)
        }
        return available / CHARGE_RATE_PER_MINUTE
    }

    /**
     * Determine which fund source will be used.
     */
    fun getFundSource(context: Context, isOnline: Boolean): FundSource {
        val wallet = getBalance(context)
        val emergency = getEmergencyFund(context)

        return when {
            wallet > 0 -> FundSource.WALLET
            !isOnline && emergency > 0 -> FundSource.EMERGENCY
            isOnline -> FundSource.NEEDS_TOPUP
            else -> FundSource.NO_FUNDS
        }
    }

    enum class FundSource {
        WALLET,        // Using wallet balance
        EMERGENCY,     // Using emergency fund (offline only)
        NEEDS_TOPUP,   // Online but no wallet funds — prompt to add
        NO_FUNDS       // No funds available at all
    }
}
