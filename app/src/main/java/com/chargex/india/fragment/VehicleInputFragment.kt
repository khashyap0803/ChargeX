package com.chargex.india.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chargex.india.R
import com.chargex.india.databinding.FragmentVehicleInputBinding
import com.chargex.india.model.RangeCalculator
import com.chargex.india.model.VehicleProfile

class VehicleInputFragment : Fragment() {

    private var _binding: FragmentVehicleInputBinding? = null
    private val binding get() = _binding!!

    private var selectedVehicle: VehicleProfile? = null
    private var currentModels: List<VehicleProfile> = emptyList()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVehicleInputBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupManufacturerDropdown()
        setupBatterySlider()
        setupButtons()
    }

    private fun setupManufacturerDropdown() {
        val grouped = VehicleProfile.groupedByManufacturer()
        val manufacturers = grouped.keys.sorted()
        val mfgAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            manufacturers
        )
        binding.spinnerManufacturer.setAdapter(mfgAdapter)

        binding.spinnerManufacturer.setOnItemClickListener { _, _, position, _ ->
            val manufacturer = manufacturers[position]
            currentModels = grouped[manufacturer] ?: emptyList()
            val modelNames = currentModels.map { it.name }
            val modelAdapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_dropdown_item_1line,
                modelNames
            )
            binding.spinnerModel.setAdapter(modelAdapter)
            binding.spinnerModel.setText("", false)
            selectedVehicle = null
            hideResults()
        }

        binding.spinnerModel.setOnItemClickListener { _, _, position, _ ->
            if (position < currentModels.size) {
                selectedVehicle = currentModels[position]
                showVehicleSpecs()
                updateRange()
            }
        }
    }

    private fun setupBatterySlider() {
        binding.sliderBattery.addOnChangeListener { _, value, _ ->
            binding.tvBatteryPercent.text = "${value.toInt()}%"
            updateRange()
        }

        binding.switchAc.setOnCheckedChangeListener { _, _ -> updateRange() }

        binding.chipGroupDrivingMode.setOnCheckedStateChangeListener { _, _ -> updateRange() }
    }

    private fun setupButtons() {
        binding.fabBack.setOnClickListener {
            findNavController().navigateUp()
        }

        binding.btnApplyFilter.setOnClickListener {
            val vehicle = selectedVehicle ?: return@setOnClickListener
            val batteryPercent = binding.sliderBattery.value.toDouble()
            val acOn = binding.switchAc.isChecked
            val drivingMode = getSelectedDrivingMode()

            val rangeKm = RangeCalculator.calculateRange(
                vehicle, batteryPercent, acOn, drivingMode
            )

            // Navigate back with filter result
            findNavController().previousBackStackEntry?.savedStateHandle?.apply {
                set("range_filter_km", rangeKm.toFloat())
                set("vehicle_id", vehicle.id)
                set("battery_percent", batteryPercent.toFloat())
            }
            findNavController().navigateUp()
        }

        binding.btnClearFilter.setOnClickListener {
            findNavController().previousBackStackEntry?.savedStateHandle?.apply {
                set("range_filter_km", -1f)
                set("vehicle_id", "")
                set("battery_percent", -1f)
            }
            findNavController().navigateUp()
        }
    }

    private fun showVehicleSpecs() {
        val vehicle = selectedVehicle ?: return
        binding.cardVehicleSpecs.visibility = View.VISIBLE
        binding.cardBatteryInput.visibility = View.VISIBLE
        binding.tvVehicleName.text = "${vehicle.manufacturer} ${vehicle.name}"
        binding.tvBatteryCapacity.text = "%.1f".format(vehicle.batteryCapacityKwh)
        binding.tvEfficiency.text = "%.1f".format(vehicle.efficiencyKwhPer100Km)
        binding.tvOfficialRange.text = "${vehicle.officialRangeKm.toInt()}"
    }

    private fun hideResults() {
        binding.cardVehicleSpecs.visibility = View.GONE
        binding.cardBatteryInput.visibility = View.GONE
        binding.cardRangeResult.visibility = View.GONE
        binding.btnApplyFilter.visibility = View.GONE
        binding.btnClearFilter.visibility = View.GONE
    }

    private fun updateRange() {
        val vehicle = selectedVehicle ?: return
        val batteryPercent = binding.sliderBattery.value.toDouble()
        val acOn = binding.switchAc.isChecked
        val drivingMode = getSelectedDrivingMode()

        val rangeKm = RangeCalculator.calculateRange(
            vehicle, batteryPercent, acOn, drivingMode
        )

        binding.cardRangeResult.visibility = View.VISIBLE
        binding.btnApplyFilter.visibility = View.VISIBLE
        binding.btnClearFilter.visibility = View.VISIBLE
        binding.tvEstimatedRange.text = "%.0f km".format(rangeKm)

        val conditions = buildString {
            append("${batteryPercent.toInt()}% battery")
            append(" • ${drivingMode.replaceFirstChar { it.uppercase() }}")
            if (acOn) append(" • AC on")
        }
        binding.tvRangeConditions.text = conditions
    }

    private fun getSelectedDrivingMode(): String {
        return when (binding.chipGroupDrivingMode.checkedChipId) {
            R.id.chipCity -> "city"
            R.id.chipHighway -> "highway"
            else -> "mixed"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
