package com.chargex.india.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class GalleryViewModel : ViewModel() {
    val galleryPosition = MutableLiveData<Int>()
}