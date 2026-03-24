package com.chargex.india.fragment

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.LinearLayoutManager
import com.chargex.india.R
import com.chargex.india.adapter.ConnectorAdapter
import com.chargex.india.adapter.ConnectorDetailsAdapter
import com.chargex.india.adapter.SingleViewAdapter
import com.chargex.india.api.availability.ChargeLocationStatus
import com.chargex.india.databinding.DialogConnectorDetailsBinding
import com.chargex.india.databinding.DialogConnectorDetailsHeaderBinding
import com.chargex.india.model.Chargepoint

class ConnectorDetailsDialog(
    binding: DialogConnectorDetailsBinding,
    context: Context,
    onClose: () -> Unit
) {
    private var headerBinding_: DialogConnectorDetailsHeaderBinding? = null
    private val headerBinding get() = headerBinding_!!
    private val detailsAdapter = ConnectorDetailsAdapter()

    init {
        binding.list.apply {
            itemAnimator = null
            layoutManager =
                LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        }
        headerBinding_ = DataBindingUtil.inflate(
            LayoutInflater.from(context),
            R.layout.dialog_connector_details_header, binding.list, false
        )
        binding.list.adapter = ConcatAdapter(
            SingleViewAdapter(headerBinding.root),
            detailsAdapter
        )
        binding.btnClose.setOnClickListener {
            onClose()
        }
    }

    fun setData(cp: Chargepoint, status: ChargeLocationStatus?) {
        val cpStatus = status?.status?.get(cp)
        val items = if (status != null) {
            List(cp.count) { i ->
                ConnectorDetailsAdapter.ConnectorDetails(
                    cpStatus?.get(i),
                    status.evseIds?.get(cp)?.get(i),
                    status.labels?.get(cp)?.get(i),
                    status.lastChange?.get(cp)?.get(i)
                )
            }.sortedBy { it.evseId ?: it.label }
        } else emptyList()
        detailsAdapter.submitList(items)

        headerBinding.divider.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        headerBinding.item = ConnectorAdapter.ChargepointWithAvailability(cp, cpStatus)
    }

    fun onDestroy() {
        headerBinding_ = null
    }
}