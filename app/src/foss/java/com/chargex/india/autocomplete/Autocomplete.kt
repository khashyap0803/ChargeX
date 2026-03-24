package com.chargex.india.autocomplete

import android.content.Context

fun getAutocompleteProviders(context: Context) = listOf(MapboxAutocompleteProvider(context))