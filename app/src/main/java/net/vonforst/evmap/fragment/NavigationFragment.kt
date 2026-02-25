package net.vonforst.evmap.fragment

import android.Manifest
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.vonforst.evmap.R
import net.vonforst.evmap.databinding.FragmentNavigationBinding
import net.vonforst.evmap.api.RouteService
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.annotations.PolylineOptions
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style

class NavigationFragment : Fragment() {
    companion object {
        private const val TAG = "NavigationFrag"
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
            map.setStyle(Style.Builder().fromUri(styleUrl)) { _ ->
                // Add destination marker
                val destLatLng = LatLng(args.destLat.toDouble(), args.destLng.toDouble())
                map.addMarker(
                    MarkerOptions()
                        .position(destLatLng)
                        .title(args.stationName)
                )

                // Move camera to destination first
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(destLatLng, 13.0))

                // Fetch route from user's location
                fetchRoute()
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

    private fun fetchRoute() {
        Log.d(TAG, "========== NavigationFragment.fetchRoute() ==========")
        Log.d(TAG, "Destination: ${args.stationName} (${args.destLat}, ${args.destLng})")

        // Get last known location
        val hasPermission = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    requireContext(), Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED

        Log.d(TAG, "Location permission granted: $hasPermission")

        if (!hasPermission) {
            Log.e(TAG, "❌ No location permission — cannot fetch route")
            showError("Location permission not granted. Enable location to see route.")
            binding.btnStartNavigation.isEnabled = true
            return
        }

        val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            val gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            Log.d(TAG, "GPS location: ${gpsLoc?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}")
            Log.d(TAG, "Network location: ${netLoc?.let { "(${it.latitude}, ${it.longitude})" } ?: "null"}")
            gpsLoc ?: netLoc
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException getting location: ${e.message}")
            null
        }

        if (location == null) {
            Log.e(TAG, "❌ Location is null — GPS and Network both failed")
            showError("Could not get your location. Please ensure GPS is enabled.")
            binding.btnStartNavigation.isEnabled = true
            return
        }

        val originLat = location.latitude
        val originLng = location.longitude
        Log.d(TAG, "Using origin: ($originLat, $originLng) from ${location.provider} provider")

        viewLifecycleOwner.lifecycleScope.launch {
            // Get Google Maps API key for best routing in India
            val googleApiKey = try {
                val keyResId = requireContext().resources.getIdentifier(
                    "google_directions_key", "string", requireContext().packageName
                )
                Log.d(TAG, "google_directions_key resource ID: $keyResId")
                if (keyResId != 0) {
                    val key = requireContext().getString(keyResId)
                    Log.d(TAG, "Google API key found (length=${key.length}): ${key.take(10)}...")
                    key
                } else {
                    Log.w(TAG, "⚠️ google_directions_key resource not found — will use OSRM only")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception loading Google API key: ${e.message}")
                null
            }

            Log.d(TAG, "Calling RouteService.getRoute()...")
            val startTime = System.currentTimeMillis()

            val route = withContext(Dispatchers.IO) {
                RouteService.getRoute(
                    originLat, originLng,
                    args.destLat.toDouble(), args.destLng.toDouble(),
                    googleApiKey = googleApiKey
                )
            }

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "RouteService returned in ${elapsed}ms")

            if (_binding == null) {
                Log.w(TAG, "Binding is null after route fetch — fragment was destroyed")
                return@launch
            }

            if (route != null) {
                Log.d(TAG, "✅ Route received: %.2f km, %.1f min, ${route.points.size} points".format(route.distanceKm, route.durationMinutes))
                binding.progressRoute.visibility = View.GONE
                binding.routeDetails.visibility = View.VISIBLE
                binding.btnStartNavigation.isEnabled = true

                binding.tvDistance.text = formatDistance(route.distanceKm)
                binding.tvDuration.text = formatDuration(route.durationMinutes)
                Log.d(TAG, "Displayed: distance=${formatDistance(route.distanceKm)}, duration=${formatDuration(route.durationMinutes)}")

                mapLibreMap?.let { map ->
                    val polylinePoints = route.points.map { (lat, lng) ->
                        LatLng(lat, lng)
                    }
                    Log.d(TAG, "Drawing polyline with ${polylinePoints.size} points on map")

                    map.addPolyline(
                        PolylineOptions()
                            .addAll(polylinePoints)
                            .color(Color.parseColor("#4CAF50"))
                            .width(5f)
                    )

                    map.addMarker(
                        MarkerOptions()
                            .position(LatLng(originLat, originLng))
                            .title("Your Location")
                    )

                    val boundsBuilder = LatLngBounds.Builder()
                    polylinePoints.forEach { boundsBuilder.include(it) }
                    boundsBuilder.include(LatLng(originLat, originLng))

                    try {
                        map.animateCamera(
                            CameraUpdateFactory.newLatLngBounds(
                                boundsBuilder.build(),
                                100
                            )
                        )
                        Log.d(TAG, "Camera animated to fit route bounds")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to animate camera: ${e.message}")
                    }
                }
            } else {
                Log.e(TAG, "❌ Route is NULL — both Google and OSRM failed")
                showError("Could not calculate route. Check your internet connection.")
                binding.btnStartNavigation.isEnabled = true
            }
            Log.d(TAG, "====================================================")
        }
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

    private fun launchGoogleMapsNavigation() {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("google.navigation:q=${args.destLat},${args.destLng}")
            `package` = "com.google.android.apps.maps"
        }
        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            val geoIntent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("geo:${args.destLat},${args.destLng}?q=${args.destLat},${args.destLng}(${Uri.encode(args.stationName)})")
            }
            try {
                startActivity(geoIntent)
            } catch (e: Exception) {
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
