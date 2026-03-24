package com.chargex.india.autocomplete

import android.content.Context
import com.chargex.india.storage.PreferenceDataSource

fun getAutocompleteProviders(context: Context) =
    if (PreferenceDataSource(context).searchProvider == "google") {
        listOf(GooglePlacesAutocompleteProvider(context), MapboxAutocompleteProvider(context))
    } else {
        listOf(MapboxAutocompleteProvider(context), GooglePlacesAutocompleteProvider(context))
    }