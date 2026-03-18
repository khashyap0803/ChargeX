package net.vonforst.evmap.ui

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
import net.vonforst.evmap.BuildConfig
import net.vonforst.evmap.R
import net.vonforst.evmap.autocomplete.PlaceWithBounds
import net.vonforst.evmap.model.ChargeLocation
import net.vonforst.evmap.model.ChargeLocationCluster
import net.vonforst.evmap.model.ChargepointListItem
import net.vonforst.evmap.storage.PreferenceDataSource
import net.vonforst.evmap.utils.distanceBetween
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

    var mini = false
    var filteredConnectors: Set<String>? = null
    var onChargerClick: ((ChargeLocation) -> Unit)? = null
    var onClusterClick: ((ChargeLocationCluster) -> Unit)? = null

    var chargepoints: List<ChargepointListItem> = emptyList()
        @Synchronized set(value) {
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
        markers.keys.toList().forEach { marker ->
            animator.deleteMarker(marker)
        }
        markers.clear()
        clusterMarkers.keys.toList().forEach { it.remove() }
        clusterMarkers.clear()
        searchResultMarker?.remove()
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

    /** Check if a charger is within the configured range filter */
    private fun isInRange(charger: ChargeLocation): Boolean {
        if (rangeFilterKm <= 0 || userLocation == null) return true
        val dist = distanceBetween(
            userLocation!!.latitude, userLocation!!.longitude,
            charger.coordinates.lat, charger.coordinates.lng
        ) / 1000.0 // meters to km
        val inRange = dist <= rangeFilterKm
        if (!inRange) {
            android.util.Log.v("MarkerManager", "FILTERED OUT: ${charger.name} at %.1f km (max: %.1f km)".format(dist, rangeFilterKm.toDouble()))
        }
        return inRange
    }

    private fun updateChargepoints() {
        val clusters = chargepoints.filterIsInstance<ChargeLocationCluster>()
        val chargers = chargepoints.filterIsInstance<ChargeLocation>()

        // Filter to only in-range chargers when range filter is active
        val visibleChargers = chargers.filter { isInRange(it) }
        val visibleIds = visibleChargers.map { it.id }.toSet()
        android.util.Log.d("MarkerManager", "updateChargepoints: total=${chargers.size}, visible=${visibleChargers.size}, filtered=${chargers.size - visibleChargers.size}, rangeKm=$rangeFilterKm, userLoc=$userLocation")

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

        if (clusters.toSet() != clusterMarkers.values) {
            // remove clusters that disappeared
            clusterMarkers.entries.toList().forEach { (marker, cluster) ->
                if (!clusters.contains(cluster)) {
                    marker.remove()
                    clusterMarkers.remove(marker)
                }
            }

            // add new clusters
            clusters.forEach { cluster ->
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