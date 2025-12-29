package com.example.refocus.feature.appselect

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.targets.UpdateTargetsUseCase
import com.example.refocus.gateway.LaunchableAppProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class AppListViewModel
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val updateTargetsUseCase: UpdateTargetsUseCase,
        private val launchableAppProvider: LaunchableAppProvider,
    ) : ViewModel() {
        data class AppUiModel(
            val label: String,
            val packageName: String,
            val usageTimeMs: Long,
            val isSelected: Boolean,
            val icon: Drawable?,
        )

        private val _apps = MutableStateFlow<List<AppUiModel>>(emptyList())
        val apps: StateFlow<List<AppUiModel>> = _apps.asStateFlow()

        private val selected = MutableStateFlow<Set<String>>(emptySet())

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                val currentTargets = targetsRepository.observeTargets().first()
                selected.value = currentTargets

                // パッケージ一覧取得・UsageStats照会・アイコン読み込みは重いので Main から退避する
                val appList =
                    withContext(Dispatchers.Default) {
                        val lookbackMillis = TimeUnit.DAYS.toMillis(7)
                        launchableAppProvider
                            .loadLaunchableApps(
                                lookbackMillis = lookbackMillis,
                                excludeSelf = true,
                            ).map { app ->
                                AppUiModel(
                                    label = app.label,
                                    packageName = app.packageName,
                                    usageTimeMs = app.usageTimeMs,
                                    isSelected = app.packageName in currentTargets,
                                    icon = app.icon,
                                )
                            }
                    }

                _apps.value = appList
            }
        }

        fun toggleSelection(packageName: String) {
            val current = selected.value
            val new = if (packageName in current) current - packageName else current + packageName
            selected.value = new
            _apps.value =
                _apps.value.map {
                    if (it.packageName == packageName) it.copy(isSelected = !it.isSelected) else it
                }
        }

        fun save(onSaved: () -> Unit) {
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    updateTargetsUseCase.updateTargets(selected.value)
                }
                onSaved()
            }
        }
    }
