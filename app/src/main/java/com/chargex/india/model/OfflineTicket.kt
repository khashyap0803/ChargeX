package com.chargex.india.model

/**
 * Represents a Trust Token for offline Micro-Credit verification at EV charging stations.
 *
 * Uses pipe-separated format for compact QR encoding:
 *   "tokenId|maxCreditInr|expiryTimestamp|userId"
 *
 * The ticket is signed using ECDSA (SHA256withECDSA) and the signature
 * is appended as: "rawData|sig:base64Signature"
 *
 * @param tokenId Unique token identifier (e.g., "TRST-USR-1029")
 * @param maxCreditInr Maximum offline credit allowed in INR (e.g., 300)
 * @param expiryTimestamp Unix timestamp (seconds) after which this trust is revoked
 * @param userId User identifier (e.g., "USR_4521")
 */
data class OfflineTicket(
    val tokenId: String,
    val maxCreditInr: Int,
    val expiryTimestamp: Long,
    val userId: String
) {
    /**
     * Serializes the token to a pipe-separated raw string for signing.
     * Format: "tokenId|maxCreditInr|expiryTimestamp|userId"
     */
    fun toRawString(): String = "$tokenId|$maxCreditInr|$expiryTimestamp|$userId"

    /**
     * Checks if this token has expired based on the current system time.
     */
    fun isExpired(): Boolean = System.currentTimeMillis() / 1000 > expiryTimestamp

    /**
     * Returns the remaining validity in minutes, or 0 if expired.
     */
    fun remainingValidityMinutes(): Int {
        val remaining = expiryTimestamp - (System.currentTimeMillis() / 1000)
        return if (remaining > 0) (remaining / 60).toInt() else 0
    }

    companion object {
        /**
         * Parses a raw pipe-separated string back into an OfflineTicket Trust Token.
         * @param rawData Format: "tokenId|maxCreditInr|expiryTimestamp|userId"
         * @return OfflineTicket or null if parsing fails
         */
        fun fromRawString(rawData: String): OfflineTicket? {
            return try {
                val parts = rawData.split("|")
                if (parts.size != 4) return null
                OfflineTicket(
                    tokenId = parts[0],
                    maxCreditInr = parts[1].toInt(),
                    expiryTimestamp = parts[2].toLong(),
                    userId = parts[3]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
