package net.vonforst.evmap.fragment

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
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
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentNavigationBinding
import net.vonforst.evmap.api.RouteService
import net.vonforst.evmap.model.RangeCalculator
import net.vonforst.evmap.model.VehicleProfile

class NavigationFragment : Fragment() {
    companion object {
        private const val TAG = "NavigationFrag"
        private const val SOURCE_ROUTE = "route-source"
        private const val SOURCE_MARKERS = "markers-source"
        private const val LAYER_ROUTE = "route-layer"
        private const val LAYER_MARKERS = "markers-layer"
        private const val MARKER_ICON = "marker-icon"
    }

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!
    private val args: NavigationFragmentArgs by navArgs()

    private var mapView: MapView? = null
    private var mapLibreMap: MapboxMap? = null

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
            findNavController().navigateUp()
        }

        binding.btnStartNavigation.setOnClickListener {
            launchGoogleMapsNavigation()
        }

        // Setup map and fetch route
        setupMap()
    }

    private fun setupMap() {
        val styleUrl = getMapStyleUrl()
        mapView?.getMapAsync { map ->
            mapLibreMap = map
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
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
                if (keyResId != 0) requireContext().getString(keyResId) else null
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
                binding.tvDuration.text = formatDuration(route.durationMinutes)

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
                displayEnergyFeasibility(route.distanceKm, route.durationMinutes)
            } else {
                showError("Could not calculate route. Check your internet connection.")
                binding.btnStartNavigation.isEnabled = true
            }
        }
    }

    /**
     * Calculates and displays energy feasibility using the physics-based model.
     * Reads vehicle profile and battery level from SavedStateHandle (set by VehicleInputFragment).
     */
    private fun displayEnergyFeasibility(distanceKm: Double, durationMinutes: Double) {
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

        // safetyMargin = 0.0 because calculateRange already applies real-world corrections
        // (1.15x ARAI, temperature, AC, driving mode). A 10% reserve on total battery
        // makes small batteries (e.g. 3.7 kWh scooter at 5%) show negative available energy.
        val result = RangeCalculator.isRouteFeasible(
            vehicle = vehicle,
            batteryPercent = batteryPercent,
            routeDistanceKm = distanceKm,
            routeDurationMinutes = durationMinutes,
            temperatureC = 35.0,
            acOn = acOn,
            safetyMargin = 0.0
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
     * Launch Google Maps navigation. Uses try/catch instead of deprecated resolveActivity().
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
        mapView?.onDestroy()
        mapView = null
        mapLibreMap = null
        _binding = null
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView?.onLowMemory()
    }
}
