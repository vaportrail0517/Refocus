package com.example.refocus.feature.appselect

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.gateway.LaunchableAppProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AppListViewModel
    @Inject
    constructor(
        private val launchableAppProvider: LaunchableAppProvider,
    ) : ViewModel() {
        data class AppCatalogUiModel(
            val label: String,
            val packageName: String,
            val usageTimeMs: Long,
            val icon: Drawable?,
        )

        private val _apps = MutableStateFlow<List<AppCatalogUiModel>>(emptyList())
        val apps: StateFlow<List<AppCatalogUiModel>> = _apps

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                val appList =
                    withContext(Dispatchers.Default) {
                        val lookbackMillis = TimeUnit.DAYS.toMillis(7)
                        launchableAppProvider
                            .loadLaunchableApps(
                                lookbackMillis = lookbackMillis,
                                excludeSelf = true,
                            ).map { app ->
                                AppCatalogUiModel(
                                    label = app.label,
                                    packageName = app.packageName,
                                    usageTimeMs = app.usageTimeMs,
                                    icon = app.icon,
                                )
                            }
                    }
                _apps.value = appList
            }
        }
    }
