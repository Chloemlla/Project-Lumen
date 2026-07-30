package com.projectlumen.app.core.repositories

import com.projectlumen.app.core.insights.AndroidDeviceInsightDataSource
import com.projectlumen.app.core.insights.DeviceInsightsState
import com.projectlumen.app.core.insights.DeviceUsageAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class DeviceInsightsRepository internal constructor(
    private val dataSource: AndroidDeviceInsightDataSource,
) {
    private val refreshMutex = Mutex()
    private val mutableState = MutableStateFlow(DeviceInsightsState())

    fun observe(): StateFlow<DeviceInsightsState> = mutableState.asStateFlow()

    suspend fun refresh() {
        refreshMutex.withLock {
            mutableState.value = mutableState.value.copy(isRefreshing = true)
            mutableState.value = runCatching { dataSource.collect() }
                .getOrElse { error ->
                    mutableState.value.copy(
                        availability = DeviceUsageAvailability.RESTRICTED,
                        isRefreshing = false,
                        failureReason = error.javaClass.simpleName,
                    )
                }
        }
    }
}
