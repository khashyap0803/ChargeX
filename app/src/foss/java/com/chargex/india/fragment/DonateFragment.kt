package com.chargex.india.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.findNavController
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.transition.MaterialSharedAxis
import com.chargex.india.MapsActivity
import com.chargex.india.R
import com.chargex.india.databinding.FragmentDonateBinding
import com.chargex.india.databinding.FragmentDonateReferralBinding

class DonateFragment : DonateFragmentBase() {
    private lateinit var binding: FragmentDonateBinding
    private lateinit var referrals: FragmentDonateReferralBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterTransition = MaterialSharedAxis(MaterialSharedAxis.X, true)
        returnTransition = MaterialSharedAxis(MaterialSharedAxis.X, false)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDonateBinding.inflate(inflater, container, false)
        referrals = binding.referrals
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        (requireActivity() as AppCompatActivity).setSupportActionBar(binding.toolbar)

        binding.toolbar.setupWithNavController(
            findNavController(),
            (requireActivity() as MapsActivity).appBarConfiguration
        )

        binding.btnDonate.setOnClickListener {
            (activity as? MapsActivity)?.openUrl(getString(R.string.paypal_link), binding.root)
        }

        setupReferrals(referrals)
    }
}