package com.example.refocus.feature.suggestions

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.gateway.LaunchableAppProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class SuggestionActionAppPickerViewModel
    @Inject
    constructor(
        private val launchableAppProvider: LaunchableAppProvider,
    ) : ViewModel() {
        data class AppUiModel(
            val label: String,
            val packageName: String,
            val icon: Drawable?,
        )

        private val _apps = MutableStateFlow<List<AppUiModel>>(emptyList())
        val apps: StateFlow<List<AppUiModel>> = _apps.asStateFlow()

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                val appList =
                    withContext(Dispatchers.Default) {
                        launchableAppProvider
                            .loadLaunchableApps(
                                lookbackMillis = TimeUnit.DAYS.toMillis(7),
                                excludeSelf = true,
                            ).map { app ->
                                AppUiModel(
                                    label = app.label,
                                    packageName = app.packageName,
                                    icon = app.icon,
                                )
                            }
                    }
                _apps.value = appList
            }
        }
    }
