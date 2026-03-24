package com.chargex.india.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.switchMap
import com.chargex.india.model.Filter
import com.chargex.india.model.FilterValue
import com.chargex.india.model.FilterValues
import com.chargex.india.model.FilterWithValue
import com.chargex.india.storage.FilterValueDao
import kotlin.reflect.full.cast

fun filtersWithValue(
    filters: LiveData<List<Filter<FilterValue>>>,
    filterValues: LiveData<List<FilterValue>?>
): MediatorLiveData<FilterValues?> =
    MediatorLiveData<FilterValues?>().apply {
        listOf(filters, filterValues).forEach {
            addSource(it) {
                val f = filters.value ?: run {
                    value = null
                    return@addSource
                }
                val values = filterValues.value ?: run {
                    value = null
                    return@addSource
                }
                value = filtersWithValue(f, values)
            }
        }
    }

fun filtersWithValue(
    filters: List<Filter<FilterValue>>,
    values: List<FilterValue>
) = filters.map { filter ->
    val value =
        values.find { it.key == filter.key } ?: filter.defaultValue()
    FilterWithValue(filter, filter.valueClass.cast(value))
}

fun FilterValueDao.getFilterValues(filterStatus: LiveData<Long>, dataSource: String) =
    filterStatus.switchMap {
        getFilterValues(it, dataSource)
    }