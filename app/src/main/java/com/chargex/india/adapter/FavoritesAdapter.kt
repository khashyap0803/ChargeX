package com.chargex.india.adapter

import android.annotation.SuppressLint
import android.view.animation.AccelerateInterpolator
import com.chargex.india.R
import com.chargex.india.databinding.ItemFavoriteBinding
import com.chargex.india.viewmodel.FavoritesViewModel

class FavoritesAdapter(val onDelete: (FavoritesViewModel.FavoritesListItem) -> Unit) :
    DataBindingAdapter<FavoritesViewModel.FavoritesListItem>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int = R.layout.item_favorite

    override fun getItemId(position: Int): Long = getItem(position).fav.favorite.favoriteId

    @SuppressLint("ClickableViewAccessibility")
    override fun bind(
        holder: ViewHolder<FavoritesViewModel.FavoritesListItem>,
        item: FavoritesViewModel.FavoritesListItem
    ) {
        super.bind(holder, item)

        val binding = holder.binding as ItemFavoriteBinding
        binding.foreground.translationX = 0f
        binding.btnDelete.setOnClickListener {
            binding.foreground.animate()
                .translationX(binding.foreground.width.toFloat())
                .setDuration(250)
                .setInterpolator(AccelerateInterpolator())
                .withEndAction {
                    onDelete(item)
                }
                .start()
        }
    }
}