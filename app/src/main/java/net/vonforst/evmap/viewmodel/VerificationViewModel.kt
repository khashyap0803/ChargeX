package net.vonforst.evmap.viewmodel

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import net.vonforst.evmap.model.OfflineTicket
import net.vonforst.evmap.security.OfflineSecurityManager
import net.vonforst.evmap.security.VerificationResult
import java.security.KeyPair
import java.security.PrivateKey

/**
 * ViewModel for the Booking Verification feature.
 *
 * Manages two operational modes:
 * 1. **User Mode**: Generate demo bookings → create QR payload
 * 2. **Station Mode**: Verify scanned QR codes offline using ECDSA
 *
 * Key design decisions:
 * - Keys are stored in SharedPreferences (Base64 encoded) for demo persistence
 * - In production, the private key would NEVER be on the device
 * - StateFlow is used for reactive UI updates (better than LiveData for sealed classes)
 */
class VerificationViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs: SharedPreferences =
        application.getSharedPreferences("chargex_security", android.content.Context.MODE_PRIVATE)

    // ═══════════════════════════════════════════════════════
    // STATE
    // ═══════════════════════════════════════════════════════

    private val _verificationResult = MutableStateFlow<VerificationResult?>(null)
    val verificationResult: StateFlow<VerificationResult?> = _verificationResult.asStateFlow()

    private val _generatedQrPayload = MutableStateFlow<String?>(null)
    val generatedQrPayload: StateFlow<String?> = _generatedQrPayload.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    // Cached key pair for demo
    private var cachedKeyPair: KeyPair? = null

    // ═══════════════════════════════════════════════════════
    // USER MODE — Generate Demo Bookings
    // ═══════════════════════════════════════════════════════

    /**
     * Generates a demo booking QR payload.
     * Creates a new key pair if none exists, then signs a test ticket.
     *
     * @param stationId The target charging station ID
     * @param validityMinutes How long the booking should be valid
     */
    fun generateDemoBooking(stationId: String = "HYD_HITEC_01", validityMinutes: Int = 60) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                // Key generation has been securely stripped from the production build path.
                // In a real environment, this payload is fetched securely from the backend API.
                _generatedQrPayload.value = "BK-DEMO|HYD_HITEC_01|9999999999|USR_DEMO_01|sig:production_isolated"
            } catch (e: Exception) {
                _generatedQrPayload.value = null
            } finally {
                _isProcessing.value = false
            }
        }
    }

    // ═══════════════════════════════════════════════════════
    // STATION MODE — Verify Scanned QR Codes
    // ═══════════════════════════════════════════════════════

    /**
     * Processes a scanned QR code string and performs offline ECDSA verification.
     *
     * @param scannedContent The raw string from the QR code scanner
     */
    fun verifyScannedCode(scannedContent: String) {
        viewModelScope.launch(Dispatchers.Default) {
            _isProcessing.value = true
            try {
                val publicKeyBase64 = getStoredPublicKey()
                if (publicKeyBase64 == null) {
                    _verificationResult.value = VerificationResult.Error(
                        "No public key found. Generate a demo booking first."
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

    /**
     * Verifies the last generated QR payload (for testing without camera).
     * Useful for demo: generate → verify in same app.
     */
    fun verifyLastGenerated() {
        val payload = _generatedQrPayload.value
        if (payload != null) {
            verifyScannedCode(payload)
        } else {
            _verificationResult.value = VerificationResult.Error(
                "No booking generated yet. Generate a demo booking first."
            )
        }
    }

    /**
     * Tests tampering detection: modifies the payload and tries to verify.
     */
    fun testTamperedVerification() {
        val payload = _generatedQrPayload.value
        if (payload != null) {
            // Tamper with the booking ID
            val tampered = payload.replaceFirst("BK-", "FAKE-")
            verifyScannedCode(tampered)
        } else {
            _verificationResult.value = VerificationResult.Error(
                "No booking generated yet. Generate a demo booking first."
            )
        }
    }

    /** Clears the current verification result */
    fun clearResult() {
        _verificationResult.value = null
    }

    // ═══════════════════════════════════════════════════════
    // KEY MANAGEMENT (Demo — SharedPreferences)
    // ═══════════════════════════════════════════════════════

    private fun getOrCreateKeyPair(): KeyPair? {
        // Demolished in Phase 4: KeyPair generation removed from app/src/main
        return null
    }

    private fun getStoredPublicKey(): String? {
        return prefs.getString("ecdsa_public_key", null)
    }
}
