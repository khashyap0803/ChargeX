package net.vonforst.evmap.api.goingelectric

import android.os.Parcelable
import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.parcelize.Parcelize
import net.vonforst.evmap.model.ChargerPhoto
import net.vonforst.evmap.model.Chargepoint

/**
 * Minimal stub classes for GoingElectric Room entities.
 * These are kept solely to maintain Room database schema compatibility
 * and migration chain integrity. GoingElectric API support has been
 * removed from ChargeX India.
 */

@Entity
data class GEPlug(
    @PrimaryKey val name: String
)

@Entity
data class GENetwork(
    @PrimaryKey val name: String
)

@Entity
data class GEChargeCard(
    @PrimaryKey val id: Long,
    val name: String,
    val url: String
)

/**
 * Stub for GEChargepoint utility function used in DB migrations.
 */
object GEChargepoint {
    /**
     * Converts GoingElectric plug type names to the generic format.
     * Kept for backward compatibility with DB migration code.
     */
    fun convertTypeFromGE(type: String): String {
        return when (type) {
            "Schuko" -> Chargepoint.SCHUKO
            "Typ1" -> Chargepoint.TYPE_1
            "Typ2" -> Chargepoint.TYPE_2_UNKNOWN
            "CHAdeMO" -> Chargepoint.CHADEMO
            "CCS" -> Chargepoint.CCS_TYPE_2
            "Tesla Supercharger CCS" -> Chargepoint.SUPERCHARGER
            "Tesla Supercharger" -> Chargepoint.SUPERCHARGER
            else -> type
        }
    }
}

/**
 * Stub adapter for GE charger photos used in TypeConverters polymorphic JSON.
 */
@Parcelize
data class GEChargerPhotoAdapter(
    override val id: String = ""
) : ChargerPhoto(id) {
    override fun getUrl(height: Int?, width: Int?, size: Int?, allowOriginal: Boolean): String = ""
}

