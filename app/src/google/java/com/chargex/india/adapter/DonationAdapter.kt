package com.chargex.india.adapter

import com.chargex.india.R
import com.chargex.india.viewmodel.DonationItem

class DonationAdapter() : DataBindingAdapter<DonationItem>() {
    override fun getItemViewType(position: Int): Int = R.layout.item_donation
}