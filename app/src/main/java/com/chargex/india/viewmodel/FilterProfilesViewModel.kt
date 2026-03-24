package com.chargex.india.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.chargex.india.model.FILTERS_DISABLED
import com.chargex.india.storage.AppDatabase
import com.chargex.india.storage.FilterProfile
import com.chargex.india.storage.PreferenceDataSource

class FilterProfilesViewModel(application: Application) : AndroidViewModel(application) {
    private var db = AppDatabase.getInstance(application)
    private var prefs = PreferenceDataSource(application)

    val filterProfiles: LiveData<List<FilterProfile>> by lazy {
        db.filterProfileDao().getProfiles(prefs.dataSource)
    }

    fun delete(itemId: Long) {
        viewModelScope.launch {
            val profile = db.filterProfileDao().getProfileById(itemId, prefs.dataSource)
            profile?.let { db.filterProfileDao().delete(it) }
            if (prefs.filterStatus == profile?.id) {
                prefs.filterStatus = FILTERS_DISABLED
            }
        }
    }

    fun insert(item: FilterProfile) {
        viewModelScope.launch {
            db.filterProfileDao().insert(item)
        }
    }

    fun update(item: FilterProfile) {
        viewModelScope.launch {
            db.filterProfileDao().update(item)
        }
    }

    fun reorderProfiles(list: List<FilterProfile>) {
        viewModelScope.launch {
            db.filterProfileDao().update(*list.toTypedArray())
        }
    }
}