package net.vonforst.evmap.security

import android.util.Base64
import net.vonforst.evmap.model.OfflineTicket
import java.security.*
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec

/**
 * Offline Booking Verification Engine using ECDSA (Elliptic Curve Digital Signature Algorithm).
 *
 * ## Algorithm: SHA256withECDSA on curve secp256r1 (NIST P-256)
 *
 * ### Mathematical Foundation:
 * - Elliptic Curve: y² = x³ + ax + b (mod p)
 * - Curve Parameters (secp256r1): a = -3, p = 2²⁵⁶ - 2²²⁴ + 2¹⁹² + 2⁹⁶ - 1
 * - Security: Based on ECDLP (Elliptic Curve Discrete Logarithm Problem)
 * - Key Size: 256-bit (equivalent to RSA-3072 security, but 85% smaller)
 *
 * ### Verification Flow (Zero Network Dependency):
 * 1. Server signs booking data with Private Key → generates signature
 * 2. QR Code contains: "rawData|sig:base64Signature"
 * 3. Station app verifies signature using hardcoded Public Key (offline)
 * 4. If signature valid AND not expired → booking is authentic
 *
 * ### Security Properties:
 * - **Authenticity**: Only the private key holder can create valid signatures
 * - **Integrity**: Any modification to ticket data invalidates the signature
 * - **Non-repudiation**: Proves the booking originated from the ChargeX system
 * - **Temporal Robustness**: Expiry timestamp prevents replay attacks
 */
object OfflineSecurityManager {

    private const val ALGORITHM = "EC"
    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val CURVE_NAME = "secp256r1" // NIST P-256
    private const val SIGNATURE_DELIMITER = "|sig:"

    /**
     * Encodes a public key to Base64 string for storage/embedding.
     */
    fun encodePublicKey(publicKey: PublicKey): String {
        return Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
    }

    // ═══════════════════════════════════════════════════════════════
    // VERIFICATION (Station-side / Offline)
    // ═══════════════════════════════════════════════════════════════

    /**
     * Verifies a scanned QR code payload completely offline.
     *
     * ### Verification Steps:
     * 1. Parse: Split payload into rawData and signature
     * 2. Authenticate: Verify ECDSA signature using public key
     * 3. Temporal Check: Ensure ticket has not expired
     * 4. Parse Ticket: Extract booking details
     *
     * ### Time Complexity: O(1) — constant time verification
     * ### Space Complexity: O(1) — no database lookup needed
     *
     * @param scannedContent The complete QR code string
     * @param publicKeyBase64 The Base64-encoded public key
     * @return VerificationResult indicating the outcome
     */
    fun verifyTicket(scannedContent: String, publicKeyBase64: String): VerificationResult {
        return try {
            // Step 1: Parse — split data and signature
            val delimiterIndex = scannedContent.lastIndexOf(SIGNATURE_DELIMITER)
            if (delimiterIndex == -1) {
                return VerificationResult.InvalidFormat("Missing signature delimiter")
            }

            val rawData = scannedContent.substring(0, delimiterIndex)
            val signatureBase64 = scannedContent.substring(delimiterIndex + SIGNATURE_DELIMITER.length)

            if (rawData.isBlank() || signatureBase64.isBlank()) {
                return VerificationResult.InvalidFormat("Empty data or signature")
            }

            // Step 2: Reconstruct public key from Base64
            val publicKeyBytes = Base64.decode(publicKeyBase64, Base64.NO_WRAP)
            val keySpec = X509EncodedKeySpec(publicKeyBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM)
            val publicKey = keyFactory.generatePublic(keySpec)

            // Step 3: Verify ECDSA signature (SHA256withECDSA)
            val signature = Signature.getInstance(SIGNATURE_ALGORITHM)
            signature.initVerify(publicKey)
            signature.update(rawData.toByteArray(Charsets.UTF_8))

            val signatureBytes = Base64.decode(signatureBase64, Base64.NO_WRAP)
            val isAuthentic = signature.verify(signatureBytes)

            if (!isAuthentic) {
                return VerificationResult.Tampered
            }

            // Step 4: Parse ticket data
            val ticket = OfflineTicket.fromRawString(rawData)
                ?: return VerificationResult.InvalidFormat("Cannot parse ticket data")

            // Step 5: Check temporal validity
            if (ticket.isExpired()) {
                return VerificationResult.Expired(ticket)
            }

            // All checks passed
            VerificationResult.Valid(ticket)

        } catch (e: Exception) {
            VerificationResult.Error(e.message ?: "Unknown verification error")
        }
    }
}

/**
 * Sealed class representing all possible outcomes of offline ticket verification.
 *
 * Used in VerificationViewModel to drive UI state transitions:
 * - Valid → Green success card with booking details
 * - Expired → Orange warning with expiry time
 * - Tampered → Red alert (signature mismatch)
 * - InvalidFormat → Error state (malformed QR)
 * - Error → Exception state with message
 */
sealed class VerificationResult {
    /** Ticket is authentic, untampered, and within validity window */
    data class Valid(val ticket: OfflineTicket) : VerificationResult()

    /** Ticket signature is valid but the expiry timestamp has passed */
    data class Expired(val ticket: OfflineTicket) : VerificationResult()

    /** Signature verification failed — data has been modified */
    object Tampered : VerificationResult()

    /** QR payload format is invalid (missing delimiter, wrong field count) */
    data class InvalidFormat(val reason: String) : VerificationResult()

    /** Unexpected exception during verification */
    data class Error(val message: String) : VerificationResult()
}
