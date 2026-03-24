package com.chargex.india.viewmodel

import android.app.Application
import android.content.SharedPreferences
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.chargex.india.model.OfflineTicket
import com.chargex.india.security.OfflineSecurityManager
import com.chargex.india.security.VerificationResult
import com.chargex.india.wallet.WalletManager
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.UUID

/**
 * ViewModel for Booking Verification + Charging Timer.
 *
 * ### Modes:
 * 1. **User Mode**: Generate ECDSA-signed trust tokens → QR payload
 * 2. **Station Mode**: Verify QR codes → Start charging session timer
 *
 * ### Charging Timer:
 * After successful QR verification, start a session timer.
 * Tracks elapsed time + real-time cost (₹0.83/min ≈ ₹50/hr).
 * On stop, deducts from wallet (or emergency fund if offline + empty).
 */
class VerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("chargex_security", android.content.Context.MODE_PRIVATE)

    // ═══════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════

    private val _verificationResult = MutableStateFlow<VerificationResult?>(null)
    val verificationResult: StateFlow<VerificationResult?> = _verificationResult.asStateFlow()

    private val _generatedQrPayload = MutableStateFlow<String?>(null)
    val generatedQrPayload: StateFlow<String?> = _generatedQrPayload.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val _offlineCreditInr = MutableStateFlow(300)
    val offlineCreditInr: StateFlow<Int> = _offlineCreditInr.asStateFlow()

    private val _estimatedChargingHours = MutableStateFlow(6.0f)
    val estimatedChargingHours: StateFlow<Float> = _estimatedChargingHours.asStateFlow()

    // ═══════════════════════════════════════
    // CHARGING TIMER STATE
    // ═══════════════════════════════════════

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    private val _sessionCost = MutableStateFlow(0.0)
    val sessionCost: StateFlow<Double> = _sessionCost.asStateFlow()

    private val _sessionSummary = MutableStateFlow<String?>(null)
    val sessionSummary: StateFlow<String?> = _sessionSummary.asStateFlow()

    private var timerJob: Job? = null
    private var chargingStartTime: Long = 0L

    private val avgCostPerHourInr = 50

    init {
        val savedCredit = prefs.getInt("offline_credit_inr", 300)
        _offlineCreditInr.value = savedCredit
        updateChargingEstimate(savedCredit)
    }

    private fun updateChargingEstimate(creditInr: Int) {
        _estimatedChargingHours.value = creditInr.toFloat() / avgCostPerHourInr.toFloat()
    }

    // ═══════════════════════════════════════
    // CHARGING TIMER — Start / Stop / Track
    // ═══════════════════════════════════════

    /**
     * Starts a charging session timer.
     * Updates every second with elapsed time and running cost.
     */
    fun startCharging() {
        if (_isCharging.value) return

        _isCharging.value = true
        _elapsedSeconds.value = 0
        _sessionCost.value = 0.0
        _sessionSummary.value = null
        chargingStartTime = System.currentTimeMillis()

        timerJob = viewModelScope.launch {
            while (_isCharging.value) {
                delay(1000)
                val elapsed = (System.currentTimeMillis() - chargingStartTime) / 1000
                _elapsedSeconds.value = elapsed
                _sessionCost.value = (elapsed / 60.0) * WalletManager.getChargeRatePerMinute()
            }
        }
    }

    /**
     * Stops the charging session and deducts credits.
     * @param isOnline Whether the device is currently online
     * @return true if deduction succeeded
     */
    fun stopCharging(isOnline: Boolean): Boolean {
        if (!_isCharging.value) return true

        _isCharging.value = false
        timerJob?.cancel()

        val elapsed = _elapsedSeconds.value
        val totalMinutes = elapsed / 60.0
        val cost = _sessionCost.value
        val ctx = getApplication<Application>()

        val deductionSuccess = WalletManager.deductFunds(ctx, cost, isOnline)
        WalletManager.recordChargingSession(ctx, totalMinutes)

        val hours = elapsed / 3600
        val mins = (elapsed % 3600) / 60
        val secs = elapsed % 60

        _sessionSummary.value = buildString {
            append("⚡ Charging Session Complete\n")
            append("━━━━━━━━━━━━━━━━━━━━━━━━\n")
            append("⏱ Duration: ${"%02d:%02d:%02d".format(hours, mins, secs)}\n")
            append("💰 Cost: ₹${"%.2f".format(cost)}\n")
            if (!deductionSuccess) {
                append("⚠️ DEDUCTION FAILED — Insufficient funds!\n")
            } else {
                append("✅ Deducted successfully\n")
            }
            append("━━━━━━━━━━━━━━━━━━━━━━━━")
        }

        return deductionSuccess
    }

    fun formatElapsed(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return "%02d:%02d:%02d".format(h, m, s)
    }

    // ═══════════════════════════════════════
    // USER MODE — Generate ECDSA-Signed Bookings
    // ═══════════════════════════════════════

    fun generateDemoBooking(maxCreditInr: Int = 300, validityMinutes: Int = 60) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                val keyPair = getOrCreateKeyPair()
                    ?: throw SecurityException("Failed to create ECDSA key pair")

                val tokenId = "TRST-${UUID.randomUUID().toString().take(8).uppercase()}"
                val expiryTimestamp = (System.currentTimeMillis() / 1000) + (validityMinutes * 60L)
                val userId = "USR_${prefs.getString("user_id", "CHRGX_01") ?: "CHRGX_01"}"

                val ticket = OfflineTicket(
                    tokenId = tokenId,
                    maxCreditInr = maxCreditInr,
                    expiryTimestamp = expiryTimestamp,
                    userId = userId
                )

                val rawData = ticket.toRawString()
                val signature = Signature.getInstance("SHA256withECDSA")
                signature.initSign(keyPair.private)
                signature.update(rawData.toByteArray(Charsets.UTF_8))
                val signatureBytes = signature.sign()
                val signatureBase64 = Base64.encodeToString(signatureBytes, Base64.NO_WRAP)

                val payload = "$rawData|sig:$signatureBase64"
                _generatedQrPayload.value = payload

                _offlineCreditInr.value = maxCreditInr
                updateChargingEstimate(maxCreditInr)
                prefs.edit().putInt("offline_credit_inr", maxCreditInr).apply()

            } catch (e: Exception) {
                _generatedQrPayload.value = null
                _verificationResult.value = VerificationResult.Error(
                    "QR Generation Failed: ${e.message}"
                )
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ═══════════════════════════════════════
    // STATION MODE — Verify Scanned QR Codes
    // ═══════════════════════════════════════

    fun verifyScannedCode(scannedContent: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                val publicKeyBase64 = getStoredPublicKey()
                if (publicKeyBase64 == null) {
                    _verificationResult.value = VerificationResult.Error(
                        "No public key found. Generate a trust token first."
                    )
                    return@launch
                }
                val result = OfflineSecurityManager.verifyTicket(scannedContent, publicKeyBase64)
                _verificationResult.value = result
            } finally {
                _isProcessing.value = false
            }
        }
    }

    fun verifyLastGenerated() {
        val payload = _generatedQrPayload.value
        if (payload != null) {
            verifyScannedCode(payload)
        } else {
            _verificationResult.value = VerificationResult.Error(
                "No token generated yet. Generate a trust token first."
            )
        }
    }

    fun testTamperedVerification() {
        val payload = _generatedQrPayload.value
        if (payload != null) {
            val tampered = payload.replaceFirst("TRST-", "FAKE-")
            verifyScannedCode(tampered)
        } else {
            _verificationResult.value = VerificationResult.Error(
                "No token generated yet. Generate a trust token first."
            )
        }
    }

    fun clearResult() {
        _verificationResult.value = null
    }

    // ═══════════════════════════════════════
    // KEY MANAGEMENT — ECDSA (secp256r1)
    // ═══════════════════════════════════════

    private fun getOrCreateKeyPair(): KeyPair? {
        return try {
            val storedPrivate = prefs.getString("ecdsa_private_key", null)
            val storedPublic = prefs.getString("ecdsa_public_key", null)

            if (storedPrivate != null && storedPublic != null) {
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKeyBytes = Base64.decode(storedPrivate, Base64.NO_WRAP)
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(privateKeyBytes))
                val publicKeyBytes = Base64.decode(storedPublic, Base64.NO_WRAP)
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
                KeyPair(publicKey, privateKey)
            } else {
                val keyPairGenerator = KeyPairGenerator.getInstance("EC")
                keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"), SecureRandom())
                val keyPair = keyPairGenerator.generateKeyPair()
                val privateBase64 = Base64.encodeToString(keyPair.private.encoded, Base64.NO_WRAP)
                val publicBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
                prefs.edit()
                    .putString("ecdsa_private_key", privateBase64)
                    .putString("ecdsa_public_key", publicBase64)
                    .apply()
                keyPair
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getStoredPublicKey(): String? {
        return prefs.getString("ecdsa_public_key", null)
    }
}
