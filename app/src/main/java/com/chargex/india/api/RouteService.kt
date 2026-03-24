package com.chargex.india.api

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query



@JsonClass(generateAdapter = true)
data class TomTomRouteResponse(val routes: List<TomTomRoute>)

@JsonClass(generateAdapter = true)
data class TomTomRoute(val summary: TomTomSummary, val legs: List<TomTomLeg>)

@JsonClass(generateAdapter = true)
data class TomTomSummary(val lengthInMeters: Int, val travelTimeInSeconds: Int, val trafficDelayInSeconds: Int)

@JsonClass(generateAdapter = true)
data class TomTomLeg(val points: List<TomTomPoint>)

@JsonClass(generateAdapter = true)
data class TomTomPoint(val latitude: Double, val longitude: Double)

// ── TomTom Reachable Range API response models ──

@JsonClass(generateAdapter = true)
data class TomTomReachableRangeResponse(val reachableRange: TomTomReachableRange)

@JsonClass(generateAdapter = true)
data class TomTomReachableRange(
    val center: TomTomPoint,
    val boundary: List<TomTomPoint>
)

interface TomTomApi {
    @GET("routing/1/calculateRoute/{locations}/json")
    suspend fun calculateRoute(
        @retrofit2.http.Path("locations", encoded = true) locations: String,
        @Query("key") apiKey: String
    ): TomTomRouteResponse

    @GET("routing/1/calculateReachableRange/{origin}/json")
    suspend fun calculateReachableRange(
        @retrofit2.http.Path("origin", encoded = true) origin: String,
        @Query("key") apiKey: String,
        @Query("distanceBudgetInMeters") distanceBudgetInMeters: Int
    ): TomTomReachableRangeResponse
}

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

    private val osrmApi: OsrmApi = Retrofit.Builder()
        .baseUrl("https://router.project-osrm.org/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(OsrmApi::class.java)

    private val tomTomApi: TomTomApi = Retrofit.Builder()
        .baseUrl("https://api.tomtom.com/")
        .client(client)
        .addConverterFactory(MoshiConverterFactory.create())
        .build()
        .create(TomTomApi::class.java)

    // Hardcoding TomTom API key as per user preference (since Google billing is not possible)
    private const val TOMTOM_API_KEY = "ifvt9M0Jf6W5NP3NdtModzREqsuFfGUK"

    /**
     * Get the best route between two points.
     * Uses TomTom Routing API as primary to replace Google Directions, falling back to OSRM.
     */
    suspend fun getRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double,
        googleApiKey: String? = null
    ): DecodedRoute? {
        Log.d(TAG, "========== ROUTE REQUEST ==========")
        Log.d(TAG, "Origin: ($originLat, $originLng)")
        Log.d(TAG, "Destination: ($destLat, $destLng)")

        // Primary: TomTom Routing API (Free & Traffic-aware)
        Log.d(TAG, ">>> Attempting TomTom Routing API...")
        val tomTomRoute = getTomTomRoute(originLat, originLng, destLat, destLng)
        if (tomTomRoute != null) {
            Log.d(TAG, "✅ TomTom API SUCCESS")
            Log.d(TAG, "   Distance: %.2f km".format(tomTomRoute.distanceKm))
            Log.d(TAG, "   Duration: %.1f min".format(tomTomRoute.durationMinutes))
            Log.d(TAG, "   Polyline points: ${tomTomRoute.points.size}")
            return tomTomRoute
        } else {
            Log.w(TAG, "❌ TomTom API FAILED — falling back to OSRM...")
        }

        // Fallback: OSRM
        Log.d(TAG, ">>> Attempting OSRM fallback...")
        val osrmRoute = getOsrmRoute(originLat, originLng, destLat, destLng)
        if (osrmRoute != null) {
            Log.d(TAG, "✅ OSRM fallback SUCCESS")
            Log.d(TAG, "   Distance: %.2f km".format(osrmRoute.distanceKm))
            
            val realisticDurationSeconds = osrmRoute.durationSeconds * 1.5
            Log.d(TAG, "   Realistic Duration (1.5x): %.1f min".format(realisticDurationSeconds / 60.0))
            Log.d(TAG, "====================================")
            return osrmRoute.copy(durationSeconds = realisticDurationSeconds)
        }

        // Offline Fallback: GraphHopper (uses downloaded OSM data)
        Log.d(TAG, ">>> Both online APIs failed — trying OFFLINE routing...")
        val ghRoute = com.chargex.india.routing.OfflineRouteManager
            .getGraphHopperRoute(originLat, originLng, destLat, destLng)
        if (ghRoute != null) {
            Log.d(TAG, "✅ GraphHopper OFFLINE route SUCCESS")
            Log.d(TAG, "   Distance: %.2f km".format(ghRoute.distanceKm))
            Log.d(TAG, "   Duration: %.1f min".format(ghRoute.durationMinutes))
            Log.d(TAG, "====================================")
            return ghRoute
        }

        // Ultimate Fallback: Haversine straight-line estimate
        Log.w(TAG, ">>> GraphHopper not available — using Haversine fallback")
        val haversineRoute = com.chargex.india.routing.OfflineRouteManager
            .getHaversineRoute(originLat, originLng, destLat, destLng)
        Log.d(TAG, "⚠️ Haversine fallback: %.2f km, %.1f min (approximate)".format(
            haversineRoute.distanceKm, haversineRoute.durationMinutes))
        Log.d(TAG, "====================================")
        return haversineRoute
    }

    /**
     * Get the reachable range polygon from a given origin within a distance budget.
     *
     * Calls TomTom Calculate Reachable Range API which returns a polygon boundary
     * of all areas reachable via actual roads within the given distance.
     *
     * @param originLat Origin latitude
     * @param originLng Origin longitude
     * @param distanceBudgetMeters Maximum road distance in meters
     * @return List of (lat, lng) pairs forming the reachable boundary polygon, or null on failure
     */
    suspend fun getReachableRange(
        originLat: Double, originLng: Double,
        distanceBudgetMeters: Int
    ): List<Pair<Double, Double>>? {
        Log.d(TAG, "========== REACHABLE RANGE REQUEST ==========")
        Log.d(TAG, "Origin: ($originLat, $originLng), Budget: ${distanceBudgetMeters}m (${distanceBudgetMeters / 1000.0}km)")

        return try {
            val origin = "$originLat,$originLng"
            val response = tomTomApi.calculateReachableRange(origin, TOMTOM_API_KEY, distanceBudgetMeters)
            val boundary = response.reachableRange.boundary.map {
                Pair(it.latitude, it.longitude)
            }
            Log.d(TAG, "✅ Reachable Range SUCCESS — ${boundary.size} boundary points")
            Log.d(TAG, "   Center: (${response.reachableRange.center.latitude}, ${response.reachableRange.center.longitude})")
            boundary
        } catch (e: Exception) {
            Log.e(TAG, "❌ Reachable Range FAILED: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private suspend fun getTomTomRoute(
        originLat: Double, originLng: Double,
        destLat: Double, destLng: Double
    ): DecodedRoute? {
        return try {
            val locations = "$originLat,$originLng:$destLat,$destLng"
            Log.d(TAG, "[TomTom] Requesting locations: $locations")
            
            val response = tomTomApi.calculateRoute(locations, TOMTOM_API_KEY)
            
            if (response.routes.isNotEmpty()) {
                val route = response.routes[0]
                val summary = route.summary
                
                // Parse points correctly
                val points = route.legs.flatMap { leg ->
                    leg.points.map { Pair(it.latitude, it.longitude) }
                }

                Log.d(TAG, "[TomTom] Extracted ${points.size} points")

                DecodedRoute(
                    points,
                    summary.lengthInMeters.toDouble(),
                    summary.travelTimeInSeconds.toDouble(),
                    null // Traffic delay is already factored into travelTimeInSeconds by TomTom
                )
            } else {
                Log.w(TAG, "[TomTom] No routes found in response")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "[TomTom] Exception during API call: ${e.javaClass.simpleName}: ${e.message}")
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
            null
        }
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
