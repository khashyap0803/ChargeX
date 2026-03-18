package net.vonforst.evmap.model

/**
 * Represents a booking ticket for offline verification at EV charging stations.
 *
 * Uses pipe-separated format for compact QR encoding:
 *   "bookingId|stationId|expiryTimestamp|userId"
 *
 * The ticket is signed using ECDSA (SHA256withECDSA) and the signature
 * is appended as: "rawData|sig:base64Signature"
 *
 * @param bookingId Unique booking identifier (e.g., "BK-HYD-00789")
 * @param stationId Station identifier (e.g., "HYD_HITEC_01")
 * @param expiryTimestamp Unix timestamp (seconds) after which the ticket is invalid
 * @param userId User identifier (e.g., "USR_4521")
 */
data class OfflineTicket(
    val bookingId: String,
    val stationId: String,
    val expiryTimestamp: Long,
    val userId: String
) {
    /**
     * Serializes the ticket to a pipe-separated raw string for signing.
     * Format: "bookingId|stationId|expiryTimestamp|userId"
     */
    fun toRawString(): String = "$bookingId|$stationId|$expiryTimestamp|$userId"

    /**
     * Checks if this ticket has expired based on the current system time.
     * Uses device clock — no network required.
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
         * Parses a raw pipe-separated string back into an OfflineTicket.
         * @param rawData Format: "bookingId|stationId|expiryTimestamp|userId"
         * @return OfflineTicket or null if parsing fails
         */
        fun fromRawString(rawData: String): OfflineTicket? {
            return try {
                val parts = rawData.split("|")
                if (parts.size != 4) return null
                OfflineTicket(
                    bookingId = parts[0],
                    stationId = parts[1],
                    expiryTimestamp = parts[2].toLong(),
                    userId = parts[3]
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
