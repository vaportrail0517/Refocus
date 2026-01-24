package com.example.refocus.feature.appselect

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.HiddenAppsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.gateway.LaunchableAppProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltViewModel
class HiddenAppsViewModel
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val hiddenAppsRepository: HiddenAppsRepository,
        private val launchableAppProvider: LaunchableAppProvider,
    ) : ViewModel() {
        private data class AppCatalogItem(
            val label: String,
            val packageName: String,
            val usageTimeMs: Long,
            val icon: Drawable?,
        )

        data class AppUiModel(
            val label: String,
            val packageName: String,
            val usageTimeMs: Long,
            val isHidden: Boolean,
            val isTarget: Boolean,
            val icon: Drawable?,
        )

        private val catalog = MutableStateFlow<List<AppCatalogItem>>(emptyList())
        private val targets = MutableStateFlow<Set<String>>(emptySet())
        private val hiddenDraft = MutableStateFlow<Set<String>>(emptySet())
        private val originalHidden = MutableStateFlow<Set<String>>(emptySet())

        private val confirmTargetsToRemove = MutableStateFlow<Set<String>>(emptySet())
        private val isSaving = MutableStateFlow(false)
        private val didSave = MutableStateFlow(false)

        val apps: StateFlow<List<AppUiModel>> =
            combine(catalog, targets, hiddenDraft) { currentCatalog, currentTargets, currentHidden ->
                currentCatalog.map { item ->
                    AppUiModel(
                        label = item.label,
                        packageName = item.packageName,
                        usageTimeMs = item.usageTimeMs,
                        isHidden = item.packageName in currentHidden,
                        isTarget = item.packageName in currentTargets,
                        icon = item.icon,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val isDirty: StateFlow<Boolean> =
            combine(hiddenDraft, originalHidden) { draft, original ->
                draft != original
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val confirmTargetsToRemovePackages: StateFlow<Set<String>> =
            confirmTargetsToRemove.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        val isSavingState: StateFlow<Boolean> =
            isSaving.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val didSaveState: StateFlow<Boolean> =
            didSave.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        init {
            load()
        }

        private fun load() {
            viewModelScope.launch {
                val currentTargets = targetsRepository.observeTargets().first()
                val currentHidden = hiddenAppsRepository.observeHiddenApps().first()

                targets.value = currentTargets
                hiddenDraft.value = currentHidden
                originalHidden.value = currentHidden

                val appList =
                    withContext(Dispatchers.Default) {
                        val lookbackMillis = TimeUnit.DAYS.toMillis(7)
                        launchableAppProvider
                            .loadLaunchableApps(
                                lookbackMillis = lookbackMillis,
                                excludeSelf = true,
                            ).map { app ->
                                AppCatalogItem(
                                    label = app.label,
                                    packageName = app.packageName,
                                    usageTimeMs = app.usageTimeMs,
                                    icon = app.icon,
                                )
                            }
                    }

                catalog.value = appList
            }
        }

        fun toggleHidden(packageName: String) {
            val current = hiddenDraft.value
            hiddenDraft.value = if (packageName in current) current - packageName else current + packageName
        }

        fun onSaveClicked() {
            val toRemove = targets.value intersect hiddenDraft.value
            if (toRemove.isNotEmpty()) {
                confirmTargetsToRemove.value = toRemove
                return
            }
            saveConfirmed()
        }

        fun dismissConfirmDialog() {
            confirmTargetsToRemove.value = emptySet()
        }

        fun confirmSave() {
            saveConfirmed()
        }

        private fun saveConfirmed() {
            viewModelScope.launch {
                if (isSaving.value) return@launch
                isSaving.value = true

                val newHidden = hiddenDraft.value

                withContext(Dispatchers.Default) {
                    // Phase4：非表示集合を永続化し，タイムラインイベントとして記録する
                    hiddenAppsRepository.setHiddenApps(newHidden, recordEvent = true)

                    // Phase3：hiddenApps は targets と排他的に扱う．
                    // hidden に含まれるアプリが targets に存在する場合は targets から除外し，対象変更イベントのみ記録する．
                    val currentTargets = targetsRepository.observeTargets().first()
                    val nextTargets = currentTargets - newHidden
                    if (nextTargets != currentTargets) {
                        targetsRepository.setTargets(nextTargets, recordEvent = true)
                    }

                    targets.value = nextTargets
                    originalHidden.value = newHidden
                }

                confirmTargetsToRemove.value = emptySet()
                didSave.value = true
                isSaving.value = false
            }
        }
    }
