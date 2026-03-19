package net.vonforst.evmap.api

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Google Directions API service for route calculation.
 * Provides optimal, real-world routes with excellent India road coverage.
 */
interface GoogleDirectionsApi {
    @GET("maps/api/directions/json")
    suspend fun getDirections(
        @Query("origin") origin: String,
        @Query("destination") destination: String,
        @Query("key") apiKey: String,
        @Query("mode") mode: String = "driving",
        @Query("alternatives") alternatives: Boolean = false,
        @Query("region") region: String = "in",
        @Query("departure_time") departureTime: String = "now"
    ): GoogleDirectionsResponse
}

@JsonClass(generateAdapter = true)
data class GoogleDirectionsResponse(
    val status: String,
    val routes: List<GoogleRoute>
)

@JsonClass(generateAdapter = true)
data class GoogleRoute(
    val legs: List<GoogleLeg>,
    @Json(name = "overview_polyline") val overviewPolyline: GooglePolyline
)

@JsonClass(generateAdapter = true)
data class GoogleLeg(
    val distance: GoogleTextValue,
    val duration: GoogleTextValue,
    @Json(name = "duration_in_traffic") val durationInTraffic: GoogleTextValue? = null
)

@JsonClass(generateAdapter = true)
data class GoogleTextValue(
    val text: String,
    val value: Int  // meters for distance, seconds for duration
)

@JsonClass(generateAdapter = true)
data class GooglePolyline(
    val points: String
)

// Keep OSRM types for backward compatibility / fallback
@JsonClass(generateAdapter = true)
data class OsrmRouteResponse(
    val code: String,
    val routes: List<OsrmRoute>
)

@JsonClass(generateAdapter = true)
data class OsrmRoute(
    val distance: Double,  // meters
    val duration: Double,  // seconds
    val geometry: String   // encoded polyline6
)

interface OsrmApi {
    @GET("route/v1/driving/{coordinates}")
    suspend fun getRoute(
        @retrofit2.http.Path("coordinates") coordinates: String,
        @Query("overview") overview: String = "full",
        @Query("geometries") geometries: String = "polyline6",
        @Query("steps") steps: Boolean = false
    ): OsrmRouteResponse
}

/**
 * Decoded route with list of lat/lng points, distance, and duration.
 */
data class DecodedRoute(
    val points: List<Pair<Double, Double>>,  // (lat, lng) pairs
    val distanceMeters: Double,
    val durationSeconds: Double,
    val durationInTrafficSeconds: Double? = null
) {
    val distanceKm: Double get() = distanceMeters / 1000.0
    val durationMinutes: Double get() = durationSeconds / 60.0
    val durationInTrafficMinutes: Double? get() = durationInTrafficSeconds?.let { it / 60.0 }
}

object RouteService {
    private const val TAG = "RouteService"

    private val client = OkHttpClient.Builder().build()

    private val googleApi: GoogleDirectionsApi = Retrofit.Builder()
        .baseUrl("https://maps.googleapis.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(GoogleDirectionsApi::class.java)

    private val osrmApi: OsrmApi = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(OsrmApi::class.java)

    /**
     * Get the best route between two points.
     * Uses Google Directions API (best routes for India) with OSRM as fallback.
     */
    suspend fun getRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
        googleApiKey: String? = null
    ): DecodedRoute? {
        Log.d(TAG, "========== ROUTE REQUEST ==========")
        Log.d(TAG, "Origin: ($originLat, $originLng)")
        Log.d(TAG, "Destination: ($destLat, $destLng)")
        Log.d(TAG, "Google API key present: ${!googleApiKey.isNullOrBlank()}")
        if (!googleApiKey.isNullOrBlank()) {
            Log.d(TAG, "Google API key (first 10 chars): ${googleApiKey.take(10)}...")
        }

        // Try Google Directions first (best routes for India)
        if (!googleApiKey.isNullOrBlank()) {
            Log.d(TAG, ">>> Attempting Google Directions API...")
            val googleRoute = getGoogleRoute(originLat, originLng, destLat, destLng, googleApiKey)
            if (googleRoute != null) {
                Log.d(TAG, "✅ Google Directions API SUCCESS")
                Log.d(TAG, "   Distance: %.2f km".format(googleRoute.distanceKm))
                Log.d(TAG, "   Duration: %.1f min".format(googleRoute.durationMinutes))
                Log.d(TAG, "   Polyline points: ${googleRoute.points.size}")
                Log.d(TAG, "   First point: ${googleRoute.points.firstOrNull()}")
                Log.d(TAG, "   Last point: ${googleRoute.points.lastOrNull()}")
                Log.d(TAG, "====================================")
                return googleRoute
            } else {
                Log.w(TAG, "❌ Google Directions API FAILED — falling back to OSRM")
            }
        } else {
            Log.w(TAG, "⚠️ No Google API key — skipping Google, using OSRM directly")
        }

        // Fall back to OSRM
        Log.d(TAG, ">>> Attempting OSRM fallback...")
        val osrmRoute = getOsrmRoute(originLat, originLng, destLat, destLng)
        if (osrmRoute != null) {
            Log.d(TAG, "✅ OSRM fallback SUCCESS")
            Log.d(TAG, "   Distance: %.2f km".format(osrmRoute.distanceKm))
            Log.d(TAG, "   Duration: %.1f min".format(osrmRoute.durationMinutes))
            Log.d(TAG, "   Polyline points: ${osrmRoute.points.size}")
            Log.d(TAG, "   First point: ${osrmRoute.points.firstOrNull()}")
            Log.d(TAG, "   Last point: ${osrmRoute.points.lastOrNull()}")
        } else {
            Log.e(TAG, "❌ OSRM fallback also FAILED — no route available")
        }
        Log.d(TAG, "====================================")
        return osrmRoute
    }

    private suspend fun getGoogleRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
        apiKey: String
    ): DecodedRoute? {
        return try {
            val origin = "$originLat,$originLng"
            val destination = "$destLat,$destLng"
            Log.d(TAG, "[Google] Requesting: origin=$origin, dest=$destination, region=in")
            Log.d(TAG, "[Google] URL: https://maps.googleapis.com/maps/api/directions/json?origin=$origin&destination=$destination&mode=driving&region=in")

            val response = googleApi.getDirections(
                origin = origin,
                destination = destination,
                apiKey = apiKey
            )

            Log.d(TAG, "[Google] Response status: ${response.status}")
            Log.d(TAG, "[Google] Number of routes: ${response.routes.size}")

            if (response.status == "OK" && response.routes.isNotEmpty()) {
                val route = response.routes[0]
                val leg = route.legs[0]
                Log.d(TAG, "[Google] Distance: ${leg.distance.text} (${leg.distance.value} m)")
                Log.d(TAG, "[Google] Duration: ${leg.duration.text} (${leg.duration.value} s)")
                Log.d(TAG, "[Google] Polyline length: ${route.overviewPolyline.points.length} chars")

                val points = decodePolyline5(route.overviewPolyline.points)
                Log.d(TAG, "[Google] Decoded ${points.size} polyline points")

                DecodedRoute(
                    points,
                    leg.distance.value.toDouble(),
                    leg.duration.value.toDouble(),
                    leg.durationInTraffic?.value?.toDouble()
                )
            } else {
                Log.w(TAG, "[Google] Bad response — status: ${response.status}, routes: ${response.routes.size}")
                if (response.status == "REQUEST_DENIED") {
                    Log.e(TAG, "[Google] API key may be invalid or Directions API not enabled in Google Cloud Console")
                } else if (response.status == "OVER_QUERY_LIMIT") {
                    Log.e(TAG, "[Google] API quota exceeded")
                } else if (response.status == "ZERO_RESULTS") {
                    Log.w(TAG, "[Google] No route found between origin and destination")
                }
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[Google] Exception during API call: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    private suspend fun getOsrmRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ): DecodedRoute? {
        return try {
            val coordinates = "$originLng,$originLat;$destLng,$destLat"
            Log.d(TAG, "[OSRM] Requesting: coordinates=$coordinates")
            Log.d(TAG, "[OSRM] URL: https://router.project-osrm.org/route/v1/driving/$coordinates?overview=full&geometries=polyline6")

            val response = osrmApi.getRoute(coordinates)

            Log.d(TAG, "[OSRM] Response code: ${response.code}")
            Log.d(TAG, "[OSRM] Number of routes: ${response.routes.size}")

            if (response.code == "Ok" && response.routes.isNotEmpty()) {
                val route = response.routes[0]
                Log.d(TAG, "[OSRM] Distance: ${route.distance} m (%.2f km)".format(route.distance / 1000.0))
                Log.d(TAG, "[OSRM] Duration: ${route.duration} s (%.1f min)".format(route.duration / 60.0))
                Log.d(TAG, "[OSRM] Geometry length: ${route.geometry.length} chars")

                val points = decodePolyline6(route.geometry)
                Log.d(TAG, "[OSRM] Decoded ${points.size} polyline points")

                DecodedRoute(points, route.distance, route.duration)
            } else {
                Log.w(TAG, "[OSRM] Bad response — code: ${response.code}, routes: ${response.routes.size}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[OSRM] Exception during API call: ${e.javaClass.simpleName}: ${e.message}")
            e.printStackTrace()
            null
        }
    }

    /**
     * Decode a standard polyline (precision 1e5) — used by Google Directions API.
     */
    private fun decodePolyline5(encoded: String): List<Pair<Double, Double>> {
        return decodePolyline(encoded, 1e5)
    }

    /**
     * Decode a polyline6 encoded string (precision 1e6) — used by OSRM.
     */
    private fun decodePolyline6(encoded: String): List<Pair<Double, Double>> {
        return decodePolyline(encoded, 1e6)
    }

    private fun decodePolyline(encoded: String, precision: Double): List<Pair<Double, Double>> {
        val poly = mutableListOf<Pair<Double, Double>>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        try {
            while (index < len) {
                var b: Int
                var shift = 0
                var result = 0
                do {
                    if (index >= len) break
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lat += dlat

                shift = 0
                result = 0
                do {
                    if (index >= len) break
                    b = encoded[index++].code - 63
                    result = result or ((b and 0x1f) shl shift)
                    shift += 5
                } while (b >= 0x20)
                val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
                lng += dlng

                poly.add(Pair(lat / precision, lng / precision))
            }
        } catch (e: Exception) {
            android.util.Log.e("RouteService", "Error decoding polyline: ${e.message}")
        }
        android.util.Log.v("RouteService", "Decoded polyline (precision=$precision): ${poly.size} points from ${encoded.length} chars")
        return poly
    }
}
