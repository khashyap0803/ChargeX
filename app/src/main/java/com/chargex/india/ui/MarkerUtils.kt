package com.chargex.india.ui

import android.animation.ValueAnimator
import android.content.Context
import android.view.animation.BounceInterpolator
import androidx.core.animation.addListener
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.car2go.maps.AnyMap
import com.car2go.maps.model.BitmapDescriptor
import com.car2go.maps.model.LatLng
import com.car2go.maps.model.Marker
import com.car2go.maps.model.MarkerOptions
import io.michaelrocks.bimap.HashBiMap
import io.michaelrocks.bimap.MutableBiMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chargex.india.BuildConfig
import com.chargex.india.R
import com.chargex.india.autocomplete.PlaceWithBounds
import com.chargex.india.model.ChargeLocation
import com.chargex.india.model.ChargeLocationCluster
import com.chargex.india.model.ChargepointListItem
import com.chargex.india.storage.PreferenceDataSource
import com.chargex.india.utils.distanceBetween
import kotlin.math.max

fun getMarkerTint(
    charger: ChargeLocation,
    connectors: Set<String>? = null
): Int {
    val maxPower = charger.maxPower(connectors)
    return when {
        maxPower == null -> R.color.charger_low
        maxPower >= 100 -> R.color.charger_100kw
        maxPower >= 43 -> R.color.charger_43kw
        maxPower >= 20 -> R.color.charger_20kw
        maxPower >= 11 -> R.color.charger_11kw
        else -> R.color.charger_low
    }
}

val chargerZ = 1
val clusterZ = chargerZ + 1
val placeSearchZ = clusterZ + 1

class MarkerManager(
    val context: Context,
    val map: AnyMap,
    val lifecycle: LifecycleOwner,
    markerHeight: Int = 48
) {
    companion object {
        /** Fallback road distance correction factor when TomTom API is unavailable */
        private const val ROAD_DISTANCE_FACTOR = 1.4

        /**
         * Ray-casting point-in-polygon test.
         * Returns true if the point (lat, lng) is inside the polygon.
         */
        fun isPointInPolygon(lat: Double, lng: Double, polygon: List<Pair<Double, Double>>): Boolean {
            var inside = false
            val n = polygon.size
            var j = n - 1
            for (i in 0 until n) {
                val yi = polygon[i].first
                val xi = polygon[i].second
                val yj = polygon[j].first
                val xj = polygon[j].second
                if ((yi > lat) != (yj > lat) &&
                    lng < (xj - xi) * (lat - yi) / (yj - yi) + xi
                ) {
                    inside = !inside
                }
                j = i
            }
            return inside
        }
    }

    private val clusterIconGenerator = ClusterIconGenerator(context)
    private val chargerIconGenerator =
        ChargerIconGenerator(context, map.bitmapDescriptorFactory, height = markerHeight)
    private val prefs = PreferenceDataSource(context)
    private val animator = MarkerAnimator(chargerIconGenerator)

    private var markers: MutableBiMap<Marker, ChargeLocation> = HashBiMap()
    private var clusterMarkers: MutableBiMap<Marker, ChargeLocationCluster> = HashBiMap()
    private var searchResultMarker: Marker? = null
    private var searchResultIcon =
        map.bitmapDescriptorFactory.fromResource(R.drawable.ic_map_marker)

    /** True after we've received at least one non-empty chargepoints list from the API */
    private var hasReceivedData = false

    var mini = false
    var filteredConnectors: Set<String>? = null
    var onChargerClick: ((ChargeLocation) -> Unit)? = null
    var onClusterClick: ((ChargeLocationCluster) -> Unit)? = null
    var onFilterResult: ((Int) -> Unit)? = null

    var chargepoints: List<ChargepointListItem> = emptyList()
        @Synchronized set(value) {
            if (value.isNotEmpty()) hasReceivedData = true
            field = value
            updateChargepoints()
        }

    var highlighedCharger: ChargeLocation? = null
        set(value) {
            field = value
            updateChargerIcons()
        }

    var searchResult: PlaceWithBounds? = null
        set(value) {
            field = value
            updateSearchResultMarker()
        }

    var favorites: Set<Long> = emptySet()
        set(value) {
            field = value
            updateChargerIcons()
        }

    /** Range filter: only stations within this distance (km) are shown. 0 = disabled. */
    var rangeFilterKm: Float = 0f
        set(value) {
            field = value
            updateChargepoints() // re-add/remove markers based on range
        }

    /** User's current location for distance calculation */
    var userLocation: LatLng? = null
        set(value) {
            field = value
            if (rangeFilterKm > 0) updateChargepoints()
        }

    /**
     * Reachable boundary polygon from TomTom Reachable Range API.
     * When set, isInRange() uses point-in-polygon instead of Haversine.
     * Null = API not called or failed (falls back to Haversine × 1.4).
     */
    var reachableBoundary: List<Pair<Double, Double>>? = null
        set(value) {
            field = value
            android.util.Log.d("MarkerManager", "Reachable boundary set: ${value?.size ?: 0} points")
            if (rangeFilterKm > 0) updateChargepoints()
        }

    init {
        map.setOnMarkerClickListener { marker ->
            when (marker) {
                in markers -> {
                    val charger = markers[marker] ?: return@setOnMarkerClickListener false
                    onChargerClick?.invoke(charger)
                    true
                }

                in clusterMarkers -> {
                    val cluster = clusterMarkers[marker] ?: return@setOnMarkerClickListener false
                    onClusterClick?.invoke(cluster)
                    true
                }

                searchResultMarker -> true

                else -> false
            }
        }

        if (BuildConfig.FLAVOR.contains("google") && prefs.mapProvider == "google") {
            // Google Maps: icons can be generated in background thread
            lifecycle.lifecycleScope.launch {
                withContext(Dispatchers.Default) {
                    chargerIconGenerator.preloadCache()
                }
            }
        } else {
            // MapLibre: needs to be run on main thread
            chargerIconGenerator.preloadCache()
        }
    }

    /** Remove ALL markers managed by this instance from the map */
    fun clearAll() {
        animator.cancelAll()
        markers.keys.toList().forEach { marker ->
            animator.deleteMarker(marker)
        }
        markers.clear()
        clusterMarkers.keys.toList().forEach { it.remove() }
        clusterMarkers.clear()
        searchResultMarker?.remove()
        searchResultMarker = null
    }

    /**
     * Release all references WITHOUT calling marker.remove().
     * Use this in onDestroyView() where the NativeMapView is already destroyed
     * and calling remove() would spam "removeAnnotation after MapView destroyed".
     */
    fun cleanupWithoutRemove() {
        animator.cancelAllWithoutRemove()
        markers.clear()
        clusterMarkers.clear()
        searchResultMarker = null
    }

    fun animateBounce(charger: ChargeLocation) {
        val marker = markers.inverse[charger] ?: return
        animator.animateMarkerBounce(marker, mini)
    }

    private fun updateSearchResultMarker() {
        searchResultMarker?.remove()
        searchResultMarker = null

        searchResult?.let {
            searchResultMarker = map.addMarker(
                MarkerOptions()
                    .z(placeSearchZ)
                    .position(it.latLng)
                    .icon(searchResultIcon)
                    .anchor(0.5f, 1f)
            )
        }
    }

    /**
     * Check if a location is within the configured range filter.
     * Primary: Uses TomTom reachable polygon (actual road network).
     * Fallback: Haversine × 1.4 road distance correction.
     */
    private fun isInRange(lat: Double, lng: Double): Boolean {
        if (rangeFilterKm <= 0 || userLocation == null) return true

        // Primary: use TomTom reachable polygon if available
        val polygon = reachableBoundary
        if (polygon != null && polygon.size >= 3) {
            val inRange = isPointInPolygon(lat, lng, polygon)
            if (!inRange) {
                android.util.Log.v("MarkerManager", "FILTERED OUT (polygon): ($lat, $lng) outside reachable area")
            }
            return inRange
        }

        // Fallback: Haversine × road correction factor
        val haversineDist = distanceBetween(
            userLocation!!.latitude, userLocation!!.longitude,
            lat, lng
        ) / 1000.0
        val estimatedRoadDist = haversineDist * ROAD_DISTANCE_FACTOR
        val inRange = estimatedRoadDist <= rangeFilterKm
        return inRange
    }

    private fun updateChargepoints() {
        val clusters = chargepoints.filterIsInstance<ChargeLocationCluster>()
        val chargers = chargepoints.filterIsInstance<ChargeLocation>()

        // Filter to only in-range chargers and clusters when range filter is active
        val visibleChargers = chargers.filter { isInRange(it.coordinates.lat, it.coordinates.lng) }
        val visibleClusters = clusters.filter { isInRange(it.coordinates.lat, it.coordinates.lng) }
        val visibleIds = visibleChargers.map { it.id }.toSet()
        android.util.Log.d("MarkerManager", "updateChargepoints: chargers=${chargers.size}, clusters=${clusters.size}, visChargers=${visibleChargers.size}, visClusters=${visibleClusters.size}, rangeKm=$rangeFilterKm, userLoc=$userLocation")

        // Only fire onFilterResult after we've received real data at least once.
        // During initial loading, chargepoints is empty (count=0) which would
        // falsely trigger the "No Stations Available" popup.
        if (rangeFilterKm > 0 && userLocation != null && hasReceivedData) {
            onFilterResult?.invoke(visibleChargers.size + visibleClusters.size)
        }

        // update icons of existing markers (connector filter may have changed)
        updateChargerIcons()

        // remove markers that disappeared OR are now out of range
        val bounds = map.projection.visibleRegion.latLngBounds
        markers.entries.toList().forEach { (marker, charger) ->
            if (!visibleIds.contains(charger.id)) {
                // animate marker if it is visible, otherwise remove immediately
                if (bounds.contains(marker.position)) {
                    animateMarker(charger, marker, false)
                } else {
                    animator.deleteMarker(marker)
                }
                markers.remove(marker)
            }
        }
        // add new markers (only in-range ones)
        val map1 = markers.values.map { it.id }
        for (charger in visibleChargers) {
            if (!map1.contains(charger.id)) {
                val marker = map.addMarker(
                    MarkerOptions()
                        .position(LatLng(charger.coordinates.lat, charger.coordinates.lng))
                        .z(chargerZ)
                        .icon(makeIcon(charger))
                        .anchor(0.5f, if (mini) 0.5f else 1f)
                )
                animateMarker(charger, marker, true)
                markers[marker] = charger
            }
        }

        if (visibleClusters.toSet() != clusterMarkers.values) {
            // remove clusters that disappeared
            clusterMarkers.entries.toList().forEach { (marker, cluster) ->
                if (!visibleClusters.contains(cluster)) {
                    marker.remove()
                    clusterMarkers.remove(marker)
                }
            }

            // add new clusters
            visibleClusters.forEach { cluster ->
                if (!clusterMarkers.inverse.contains(cluster)) {
                    val marker = map.addMarker(
                        MarkerOptions()
                            .position(LatLng(cluster.coordinates.lat, cluster.coordinates.lng))
                            .z(clusterZ)
                            .icon(
                                map.bitmapDescriptorFactory.fromBitmap(
                                    clusterIconGenerator.makeIcon(
                                        cluster.clusterCount.toString()
                                    )
                                )
                            )
                            .anchor(0.5f, 0.5f)
                    )
                    clusterMarkers[marker] = cluster
                }
            }
        }
    }

    private fun updateChargerIcons() {
        markers.forEach { (m, c) ->
            m.setIcon(makeIcon(c))
            m.setAnchor(0.5f, if (mini) 0.5f else 1f)
        }
    }

    private fun updateSingleChargerIcon(charger: ChargeLocation) {
        markers.inverse[charger]?.apply {
            setIcon(makeIcon(charger))
            setAnchor(0.5f, if (mini) 0.5f else 1f)
        }
    }

    private fun makeIcon(
        charger: ChargeLocation,
        scale: Float = 1f
    ): BitmapDescriptor? {
        val alpha = 255 // out-of-range stations are hidden entirely, no dimming needed
        return chargerIconGenerator.getBitmapDescriptor(
            getMarkerTint(charger, filteredConnectors),
            scale = scale,
            alpha = alpha,
            highlight = charger.id == highlighedCharger?.id,
            fault = charger.faultReport != null,
            multi = charger.isMulti(filteredConnectors),
            fav = charger.id in favorites,
            mini = mini
        )
    }

    private fun animateMarker(charger: ChargeLocation, marker: Marker, appear: Boolean) {
        val tint = getMarkerTint(charger, filteredConnectors)
        val highlight = charger.id == highlighedCharger?.id
        val fault = charger.faultReport != null
        val multi = charger.isMulti(filteredConnectors)
        val fav = charger.id in favorites
        if (appear) {
            animator.animateMarkerAppear(marker, tint, highlight, fault, multi, fav, mini)
        } else {
            animator.animateMarkerDisappear(marker, tint, highlight, fault, multi, fav, mini)
        }
    }
}

class MarkerAnimator(val gen: ChargerIconGenerator) {
    private val animatingMarkers = hashMapOf<Marker, ValueAnimator>()

    fun cancelAll() {
        animatingMarkers.entries.toList().forEach { (marker, anim) ->
            anim.cancel()
            marker.remove()
        }
        animatingMarkers.clear()
    }

    /** Cancel all animations without calling marker.remove() */
    fun cancelAllWithoutRemove() {
        animatingMarkers.values.forEach { it.cancel() }
        animatingMarkers.clear()
    }

    fun animateMarkerAppear(
        marker: Marker,
        tint: Int,
        highlight: Boolean,
        fault: Boolean,
        multi: Boolean,
        fav: Boolean,
        mini: Boolean
    ) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 250
            interpolator = LinearOutSlowInInterpolator()
            addUpdateListener { animationState ->
                val scale = animationState.animatedValue as Float
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault,
                        multi = multi,
                        fav = fav,
                        mini = mini
                    )
                )
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
            }, onCancel = {
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }

    fun animateMarkerDisappear(
        marker: Marker,
        tint: Int,
        highlight: Boolean,
        fault: Boolean,
        multi: Boolean,
        fav: Boolean,
        mini: Boolean
    ) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 200
            interpolator = FastOutLinearInInterpolator()
            addUpdateListener { animationState ->
                val scale = animationState.animatedValue as Float
                marker.setIcon(
                    gen.getBitmapDescriptor(
                        tint,
                        scale = scale,
                        highlight = highlight,
                        fault = fault,
                        multi = multi,
                        fav = fav,
                        mini = mini
                    )
                )
            }
            addListener(onEnd = {
                marker.remove()
                animatingMarkers.remove(marker)
            }, onCancel = {
                marker.remove()
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }

    fun deleteMarker(marker: Marker) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }
        marker.remove()
    }

    fun animateMarkerBounce(marker: Marker, mini: Boolean) {
        animatingMarkers[marker]?.let {
            it.cancel()
            animatingMarkers.remove(marker)
        }

        val anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 700
            interpolator = BounceInterpolator()
            addUpdateListener { state ->
                val t = max(1f - state.animatedValue as Float, 0f) / 2
                marker.setAnchor(0.5f, (if (mini) 0.5f else 1.0f) + t)
            }
            addListener(onEnd = {
                animatingMarkers.remove(marker)
            }, onCancel = {
                animatingMarkers.remove(marker)
            })
        }
        animatingMarkers[marker] = anim
        anim.start()
    }
}