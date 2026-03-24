package com.chargex.india.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.chargex.india.R
import com.chargex.india.wallet.WalletManager

/**
 * WalletFragment — Displays wallet balance, emergency fund,
 * add money buttons, and usage statistics.
 */
class WalletFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_wallet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvBalance = view.findViewById<TextView>(R.id.tvWalletBalance)
        val tvEstTime = view.findViewById<TextView>(R.id.tvEstChargingTime)
        val tvEmergency = view.findViewById<TextView>(R.id.tvEmergencyBalance)
        val tvTotalSpent = view.findViewById<TextView>(R.id.tvTotalSpent)
        val tvTotalChargingTime = view.findViewById<TextView>(R.id.tvTotalChargingTime)
        val etCustom = view.findViewById<EditText>(R.id.etCustomAmount)
        val btnAdd100 = view.findViewById<Button>(R.id.btnAdd100)
        val btnAdd500 = view.findViewById<Button>(R.id.btnAdd500)
        val btnAdd1000 = view.findViewById<Button>(R.id.btnAdd1000)
        val btnAddCustom = view.findViewById<Button>(R.id.btnAddCustom)
        val btnBack = view.findViewById<Button>(R.id.btnBackToMap)

        fun refreshUI() {
            val ctx = requireContext()
            val balance = WalletManager.getBalance(ctx)
            val emergency = WalletManager.getEmergencyFund(ctx)
            val totalSpent = WalletManager.getTotalSpent(ctx)
            val totalMin = WalletManager.getTotalChargedMinutes(ctx)
            val estMinutes = WalletManager.estimateChargingMinutes(ctx, true)

            tvBalance.text = "₹${"%.2f".format(balance)}"
            tvEstTime.text = "≈ ${"%.1f".format(estMinutes / 60)} hrs charging time"
            tvEmergency.text = "₹${"%.2f".format(emergency)}"
            tvTotalSpent.text = "₹${"%.2f".format(totalSpent)}"

            val hours = (totalMin / 60).toInt()
            val mins = (totalMin % 60).toInt()
            tvTotalChargingTime.text = if (hours > 0) "${hours}h ${mins}m" else "${mins} min"
        }

        // Quick add buttons
        btnAdd100.setOnClickListener {
            WalletManager.addFunds(requireContext(), 100.0)
            Toast.makeText(context, "₹100 added!", Toast.LENGTH_SHORT).show()
            refreshUI()
        }
        btnAdd500.setOnClickListener {
            WalletManager.addFunds(requireContext(), 500.0)
            Toast.makeText(context, "₹500 added!", Toast.LENGTH_SHORT).show()
            refreshUI()
        }
        btnAdd1000.setOnClickListener {
            WalletManager.addFunds(requireContext(), 1000.0)
            Toast.makeText(context, "₹1000 added!", Toast.LENGTH_SHORT).show()
            refreshUI()
        }

        btnAddCustom.setOnClickListener {
            val text = etCustom.text.toString()
            val amount = text.toDoubleOrNull()
            if (amount != null && amount > 0) {
                WalletManager.addFunds(requireContext(), amount)
                Toast.makeText(context, "₹${"%.2f".format(amount)} added!", Toast.LENGTH_SHORT).show()
                etCustom.text?.clear()
                refreshUI()
            } else {
                Toast.makeText(context, "Enter a valid amount", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener {
            findNavController().popBackStack()
        }

        refreshUI()
    }
}
