package com.chargex.india.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.snackbar.Snackbar
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.mapboxsdk.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.chargex.india.R
import com.chargex.india.databinding.FragmentNavigationBinding
import com.chargex.india.api.RouteService
import com.chargex.india.api.DecodedRoute
import com.chargex.india.model.RangeCalculator
import com.chargex.india.model.VehicleProfile
import com.chargex.india.routing.OfflineRouteManager

class NavigationFragment : Fragment() {
    companion object {
        private const val TAG = "NavigationFrag"
        private const val SOURCE_ROUTE = "route-source"
        private const val SOURCE_MARKERS = "markers-source"
        private const val SOURCE_USER_LOC = "user-location-source"
        private const val LAYER_ROUTE = "route-layer"
        private const val LAYER_MARKERS = "markers-layer"
        private const val LAYER_USER_LOC = "user-location-layer"
        private const val MARKER_ICON = "marker-icon"
        private const val USER_ICON = "user-loc-icon"
    }

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private val args: NavigationFragmentArgs by navArgs()

    private var mapView: MapView? = null
    private var mapLibreMap: MapboxMap? = null
    private var isNavigating = false
    private var locationListener: LocationListener? = null
    private var mapStyle: Style? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize MapLibre
        Mapbox.getInstance(requireContext())

        // Create MapView programmatically
        mapView = MapView(requireContext()).apply {
            id = View.generateViewId()
        }
        binding.mapContainer.addView(mapView)
        mapView?.onCreate(savedInstanceState)

        // Set station name
        binding.tvStationName.text = args.stationName

        // Setup buttons
        binding.fabBack.setOnClickListener {
            stopInAppNavigation()
            findNavController().navigateUp()
        }

        binding.btnStartNavigation.setOnClickListener {
            if (isNetworkAvailable()) {
                launchGoogleMapsNavigation()
            } else {
                startInAppNavigation()
            }
        }

        // Setup map and fetch route
        setupMap()
    }

    // ═══════════════════════════════════════════════════════
    // NETWORK CHECK
    // ═══════════════════════════════════════════════════════

    private fun isNetworkAvailable(): Boolean {
        val cm = requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // ═══════════════════════════════════════════════════════
    // MAP SETUP & ROUTE FETCHING
    // ═══════════════════════════════════════════════════════

    private fun setupMap() {
        val styleUrl = getMapStyleUrl()
        mapView?.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                mapStyle = style
                // Pre-load marker icon into the style
                val markerBitmap = BitmapUtils.getBitmapFromDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.ic_map_marker)
                )
                if (markerBitmap != null) {
                    style.addImage(MARKER_ICON, markerBitmap)
                }

                // Add destination marker using GeoJSON source + SymbolLayer
                val destPoint = Point.fromLngLat(args.destLng.toDouble(), args.destLat.toDouble())
                val markerSource = GeoJsonSource(SOURCE_MARKERS,
                    FeatureCollection.fromFeatures(listOf(
                        Feature.fromGeometry(destPoint)
                    ))
                )
                style.addSource(markerSource)
                style.addLayer(
                    SymbolLayer(LAYER_MARKERS, SOURCE_MARKERS)
                        .withProperties(
                            PropertyFactory.iconImage(MARKER_ICON),
                            PropertyFactory.iconSize(1.0f),
                            PropertyFactory.iconAnchor("bottom"),
                            PropertyFactory.iconAllowOverlap(true)
                        )
                )

                // Move camera to destination first
                val destLatLng = LatLng(args.destLat.toDouble(), args.destLng.toDouble())
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 13.0))

                // Fetch route from user's location
                fetchRoute(style)
            }
        }
    }

    private fun getMapStyleUrl(): String {
        return try {
            val jawgKey = requireContext().getString(
                requireContext().resources.getIdentifier("jawg_key", "string", requireContext().packageName)
            )
            if (jawgKey.isNotEmpty()) {
                "https://tile.jawg.io/jawg-streets.json?access-token=$jawgKey"
            } else {
                "https://demotiles.maplibre.org/style.json"
            }
        } catch (e: Exception) {
            "https://demotiles.maplibre.org/style.json"
        }
    }

    private fun fetchRoute(style: Style) {
        Log.d(TAG, "========== NavigationFragment.fetchRoute() ==========")
        Log.d(TAG, "Destination: ${args.stationName} (${args.destLat}, ${args.destLng})")

        // Get last known location
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Log.e(TAG, "No location permission — cannot fetch route")
            showError("Location permission not granted. Enable location to see route.")
            binding.btnStartNavigation.isEnabled = true
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            gpsLoc ?: netLoc
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting location: ${e.message}")
            null
        }

        if (location == null) {
            showError("Could not get your location. Please ensure GPS is enabled.")
            binding.btnStartNavigation.isEnabled = true
            return
        }

        val originLat = location.latitude
        val originLng = location.longitude
        Log.d(TAG, "Using origin: ($originLat, $originLng)")

        viewLifecycleOwner.lifecycleScope.launch {
            val googleApiKey = try {
                val keyResId = requireContext().resources.getIdentifier(
                    "google_directions_key", "string", requireContext().packageName
                )
                if (keyResId != 0) {
                    val key = requireContext().getString(keyResId)
                    if (key.isNotBlank()) key else null
                } else null
            } catch (e: Exception) {
                null
            }

            val route = withContext(Dispatchers.IO) {
                RouteService.getRoute(
                    originLat, originLng,
                    args.destLat.toDouble(), args.destLng.toDouble(),
                    googleApiKey = googleApiKey
                )
            }

            if (_binding == null) return@launch

            if (route != null) {
                Log.d(TAG, "Route received: %.2f km, %.1f min, ${route.points.size} points".format(route.distanceKm, route.durationMinutes))
                binding.progressRoute.visibility = View.GONE
                binding.routeDetails.visibility = View.VISIBLE
                binding.btnStartNavigation.isEnabled = true

                binding.tvDistance.text = formatDistance(route.distanceKm)
                // Use traffic-aware duration when available (matches real Google Maps)
                val displayMinutes = route.durationInTrafficMinutes ?: route.durationMinutes
                binding.tvDuration.text = formatDuration(displayMinutes)

                // Show route source badge
                showRouteSource(route)

                // Update button text based on connectivity
                if (!isNetworkAvailable()) {
                    binding.btnStartNavigation.text = "Navigate in ChargeX"
                } else {
                    binding.btnStartNavigation.text = "Start Navigation"
                }

                // Draw route using GeoJSON source + LineLayer
                val linePoints = route.points.map { (lat, lng) ->
                    Point.fromLngLat(lng, lat)
                }

                if (linePoints.size >= 2) {
                    val routeSource = GeoJsonSource(SOURCE_ROUTE,
                        FeatureCollection.fromFeatures(listOf(
                            Feature.fromGeometry(LineString.fromLngLats(linePoints))
                        ))
                    )
                    style.addSource(routeSource)
                    style.addLayerBelow(
                        LineLayer(LAYER_ROUTE, SOURCE_ROUTE)
                            .withProperties(
                                PropertyFactory.lineColor(Color.parseColor("#4CAF50")),
                                PropertyFactory.lineWidth(5f),
                                PropertyFactory.lineOpacity(0.85f)
                            ),
                        LAYER_MARKERS // route below markers
                    )
                }

                // Update the existing markers source to include origin marker
                val destPoint = Point.fromLngLat(args.destLng.toDouble(), args.destLat.toDouble())
                val originPoint = Point.fromLngLat(originLng, originLat)
                val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_MARKERS)
                existingSource?.setGeoJson(
                    FeatureCollection.fromFeatures(listOf(
                        Feature.fromGeometry(destPoint),
                        Feature.fromGeometry(originPoint)
                    ))
                )

                // Fit camera to route
                mapLibreMap?.let { map ->
                    val boundsBuilder = LatLngBounds.Builder()
                    route.points.forEach { (lat, lng) ->
                        boundsBuilder.include(LatLng(lat, lng))
                    }
                    boundsBuilder.include(LatLng(originLat, originLng))

                    try {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(boundsBuilder.build(), 100)
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to animate camera: ${e.message}")
                    }
                }

                // === Energy-Aware Route Feasibility ===
                displayEnergyFeasibility(route)
            } else {
                showError("Could not calculate route. Check your internet connection.")
                binding.btnStartNavigation.isEnabled = true
            }
        }
    }

    /**
     * Show route source badge so user knows if this is online or offline route.
     */
    private fun showRouteSource(route: DecodedRoute) {
        val isOnline = isNetworkAvailable()
        val graphHopperReady = OfflineRouteManager.isGraphHopperReady()

        val sourceText = when {
            isOnline -> "🛰 Online route (TomTom / OSRM)"
            graphHopperReady -> "📍 Offline route (GraphHopper — road-following)"
            else -> "📍 Offline route (Haversine estimate — download GraphHopper data for accuracy)"
        }

        binding.tvRouteSource.text = sourceText
        binding.tvRouteSource.isVisible = true
    }

    // ═══════════════════════════════════════════════════════
    // IN-APP NAVIGATION (Offline)
    // ═══════════════════════════════════════════════════════

    /**
     * Start in-app navigation with live location tracking.
     * Used when offline — shows user position on route and tracks progress.
     */
    private fun startInAppNavigation() {
        if (isNavigating) {
            stopInAppNavigation()
            return
        }

        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Snackbar.make(binding.root, "Location permission required for in-app navigation", Snackbar.LENGTH_LONG).show()
            return
        }

        isNavigating = true
        binding.btnStartNavigation.text = "⏹ Stop Navigation"
        binding.tvNavStatus.isVisible = true
        binding.tvNavStatus.text = "🧭 Navigating in ChargeX — follow the green route"

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager

        locationListener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (_binding == null || !isNavigating) return

                val userLatLng = LatLng(location.latitude, location.longitude)
                val destLatLng = LatLng(args.destLat.toDouble(), args.destLng.toDouble())

                // Update user location marker on map
                updateUserLocationOnMap(location.latitude, location.longitude)

                // Calculate remaining distance
                val remainingKm = haversineDistanceKm(
                    location.latitude, location.longitude,
                    args.destLat.toDouble(), args.destLng.toDouble()
                )

                // Calculate bearing for direction
                val bearing = calculateBearing(
                    location.latitude, location.longitude,
                    args.destLat.toDouble(), args.destLng.toDouble()
                )
                val direction = bearingToDirection(bearing)

                // Update status
                binding.tvNavStatus.text = "🧭 Head $direction — ${formatDistance(remainingKm)} remaining"
                binding.tvDistance.text = formatDistance(remainingKm)

                // Estimated time
                val avgSpeedKmh = if (location.speed > 0) {
                    (location.speed * 3.6) // m/s → km/h
                } else {
                    25.0 // Default city speed
                }
                val remainingMinutes = (remainingKm / avgSpeedKmh) * 60
                binding.tvDuration.text = formatDuration(remainingMinutes)

                // Follow user with camera
                mapLibreMap?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(userLatLng, 15.0),
                    300
                )

                // Check if arrived (within 100m)
                if (remainingKm < 0.1) {
                    binding.tvNavStatus.text = "🎉 You have arrived at ${args.stationName}!"
                    stopInAppNavigation()
                }
            }

            override fun onProviderDisabled(provider: String) {}
            override fun onProviderEnabled(provider: String) {}
        }

        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                2000L, // every 2 seconds
                5f,    // or 5 meters
                locationListener!!
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Location permission error: ${e.message}")
        }
    }

    private fun stopInAppNavigation() {
        isNavigating = false
        binding.tvNavStatus.isVisible = false
        locationListener?.let { listener ->
            try {
                val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
                locationManager.removeUpdates(listener)
            } catch (e: Exception) {
                Log.e(TAG, "Error removing location updates: ${e.message}")
            }
        }
        locationListener = null

        if (_binding != null) {
            if (!isNetworkAvailable()) {
                binding.btnStartNavigation.text = "Navigate in ChargeX"
            } else {
                binding.btnStartNavigation.text = "Start Navigation"
            }
        }
    }

    private fun updateUserLocationOnMap(lat: Double, lng: Double) {
        val style = mapStyle ?: return

        val userPoint = Point.fromLngLat(lng, lat)

        // Check if source already exists
        val existingSource = style.getSourceAs<GeoJsonSource>(SOURCE_USER_LOC)
        if (existingSource != null) {
            existingSource.setGeoJson(
                FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(userPoint)))
            )
        } else {
            // Create new source and layer for user location
            val source = GeoJsonSource(SOURCE_USER_LOC,
                FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(userPoint)))
            )
            style.addSource(source)

            // Use existing marker icon with blue tint for user
            style.addLayer(
                SymbolLayer(LAYER_USER_LOC, SOURCE_USER_LOC)
                    .withProperties(
                        PropertyFactory.iconImage(MARKER_ICON),
                        PropertyFactory.iconSize(1.2f),
                        PropertyFactory.iconAnchor("bottom"),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconColor(Color.parseColor("#2196F3"))
                    )
            )
        }
    }

    // ═══════════════════════════════════════════════════════
    // BEARING & DIRECTION HELPERS
    // ═══════════════════════════════════════════════════════

    private fun calculateBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) - Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
        val bearing = Math.toDegrees(Math.atan2(y, x))
        return (bearing + 360) % 360
    }

    private fun bearingToDirection(bearing: Double): String {
        return when {
            bearing < 22.5 || bearing >= 337.5 -> "North ↑"
            bearing < 67.5 -> "Northeast ↗"
            bearing < 112.5 -> "East →"
            bearing < 157.5 -> "Southeast ↘"
            bearing < 202.5 -> "South ↓"
            bearing < 247.5 -> "Southwest ↙"
            bearing < 292.5 -> "West ←"
            else -> "Northwest ↖"
        }
    }

    private fun haversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return R * c
    }

    // ═══════════════════════════════════════════════════════
    // ENERGY FEASIBILITY
    // ═══════════════════════════════════════════════════════

    /**
     * Calculates and displays energy feasibility using the physics-based model.
     * Reads vehicle profile and battery level from SavedStateHandle (set by VehicleInputFragment).
     */
    private fun displayEnergyFeasibility(route: DecodedRoute) {
        val distanceKm = route.distanceKm
        val durationMinutes = route.durationMinutes
        val trafficMultiplier = if (route.durationInTrafficMinutes != null && route.durationMinutes > 0) {
            maxOf(1.0, route.durationInTrafficMinutes!! / route.durationMinutes)
        } else 1.0
        // Try to read vehicle info from previous screen's savedStateHandle
        val savedState = findNavController().previousBackStackEntry?.savedStateHandle
        val vehicleId = savedState?.get<String>("vehicle_id")
        val batteryPercent = savedState?.get<Float>("battery_percent")?.toDouble()

        Log.d(TAG, "Energy feasibility: raw savedState vehicleId=$vehicleId, battery=$batteryPercent")

        val vehicle = if (!vehicleId.isNullOrBlank()) {
            VehicleProfile.findById(vehicleId)
        } else null

        if (vehicle == null || batteryPercent == null || batteryPercent <= 0) {
            // No vehicle info available — hide energy card
            Log.d(TAG, "Energy feasibility: no vehicle data (vehicleId=$vehicleId, battery=$batteryPercent)")
            binding.cardEnergyFeasibility.visibility = View.GONE
            return
        }

        // Use vehicle's hasAC property: scooters don't have AC
        val acOn = vehicle.hasAC
        Log.d(TAG, "Energy feasibility: vehicle=${vehicle.manufacturer} ${vehicle.name}, battery=$batteryPercent%, distance=$distanceKm km, duration=$durationMinutes min, acOn=$acOn")

        val result = RangeCalculator.isRouteFeasible(
            vehicle = vehicle,
            batteryPercent = batteryPercent,
            routeDistanceKm = distanceKm,
            routeDurationMinutes = durationMinutes,
            temperatureC = 35.0,
            acOn = acOn,
            safetyMargin = 0.0,
            trafficMultiplier = trafficMultiplier
        )

        binding.cardEnergyFeasibility.visibility = View.VISIBLE
        binding.tvVehicleInfo.text = "${vehicle.manufacturer} ${vehicle.name} • ${batteryPercent.toInt()}% battery"
        binding.tvEnergyStatus.text = result.statusMessage
        binding.tvArrivalBattery.text = "Arrival Battery: ${result.arrivalBatteryPercent.toInt()}%"
        binding.tvEnergyRequired.text = "Energy Required: %.3f kWh".format(result.energyRequired)
        binding.tvEnergyAvailable.text = "Energy Available: %.3f kWh".format(result.energyAvailable)
        binding.tvTrafficCondition.text = result.trafficFactor

        // Color code based on feasibility
        val statusColor = when {
            !result.isFeasible -> Color.parseColor("#C62828") // Red
            result.arrivalBatteryPercent > 30 -> Color.parseColor("#2E7D32") // Green
            result.arrivalBatteryPercent > 15 -> Color.parseColor("#F57F17") // Amber
            else -> Color.parseColor("#E65100") // Orange
        }
        binding.tvEnergyStatus.setTextColor(statusColor)
    }

    private fun showError(message: String) {
        binding.progressRoute.visibility = View.GONE
        binding.tvError.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    private fun formatDistance(km: Double): String {
        return if (km < 1) {
            "${(km * 1000).toInt()} m"
        } else {
            "%.1f km".format(km)
        }
    }

    private fun formatDuration(minutes: Double): String {
        val mins = minutes.toInt()
        return if (mins < 60) {
            "$mins min"
        } else {
            val hours = mins / 60
            val remainMins = mins % 60
            "${hours}h ${remainMins}m"
        }
    }

    /**
     * Launch Google Maps navigation (only when online).
     */
    private fun launchGoogleMapsNavigation() {
        val gMapsIntent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("google.navigation:q=${args.destLat},${args.destLng}")
            `package` = "com.google.android.apps.maps"
        }
        try {
            startActivity(gMapsIntent)
        } catch (e: ActivityNotFoundException) {
            // Google Maps not installed — try generic geo intent
            val geoIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(
                    "geo:${args.destLat},${args.destLng}?q=${args.destLat},${args.destLng}(${Uri.encode(args.stationName)})"
                )
            }
            try {
                startActivity(geoIntent)
            } catch (e2: ActivityNotFoundException) {
                Snackbar.make(
                    binding.root,
                    "No maps app found. Please install Google Maps.",
                    Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        mapView?.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView?.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView?.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView?.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView?.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopInAppNavigation()
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
        mapStyle = null
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}
