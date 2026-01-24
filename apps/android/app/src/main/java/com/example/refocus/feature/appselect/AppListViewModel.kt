package com.example.refocus.feature.appselect

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.HiddenAppsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.targets.UpdateTargetsUseCase
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
class AppListViewModel
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val hiddenAppsRepository: HiddenAppsRepository,
        private val updateTargetsUseCase: UpdateTargetsUseCase,
        private val launchableAppProvider: LaunchableAppProvider,
    ) : ViewModel() {
        /**
         * 「アプリ一覧の固定情報」と「選択状態」を分離して保持するためのモデル．
         *
         * Phase0（安全な下準備）：
         * - 後続フェーズで他の状態（例：hiddenApps）を合成しやすくするため，
         *   isSelected を catalog 側に持たず，選択集合から派生させる構造にする．
         */
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
            val isSelected: Boolean,
            val icon: Drawable?,
        )

        private val catalog = MutableStateFlow<List<AppCatalogItem>>(emptyList())
        private val selectedPackages = MutableStateFlow<Set<String>>(emptySet())

        /**
         * Phase2：候補から除外するアプリ集合（永続化された hiddenApps）．
         *
         * - フェーズ2では編集UIをまだ提供しないため，通常は空集合のまま．
         * - ただし後続フェーズで hiddenApps が導入された際に，自動で UI と保存ガードに反映される．
         */
        val hiddenPackages: StateFlow<Set<String>> =
            hiddenAppsRepository
                .observeHiddenApps()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        val apps: StateFlow<List<AppUiModel>> =
            combine(catalog, selectedPackages) { currentCatalog, currentSelected ->
                currentCatalog.map { item ->
                    AppUiModel(
                        label = item.label,
                        packageName = item.packageName,
                        usageTimeMs = item.usageTimeMs,
                        isSelected = item.packageName in currentSelected,
                        icon = item.icon,
                    )
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            load()
            keepSelectionConsistentWithHiddenApps()
        }

        private fun load() {
            viewModelScope.launch {
                val currentTargets = targetsRepository.observeTargets().first()
                selectedPackages.value = currentTargets

                // パッケージ一覧取得・UsageStats照会・アイコン読み込みは重いので Main から退避する
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

        private fun keepSelectionConsistentWithHiddenApps() {
            viewModelScope.launch {
                hiddenPackages.collect { hidden ->
                    val current = selectedPackages.value
                    val updated = current - hidden
                    if (updated != current) {
                        selectedPackages.value = updated
                    }
                }
            }
        }

        fun toggleSelection(packageName: String) {
            val current = selectedPackages.value
            selectedPackages.value = if (packageName in current) current - packageName else current + packageName
        }

        fun save(onSaved: () -> Unit) {
            viewModelScope.launch {
                withContext(Dispatchers.Default) {
                    // Phase2：hiddenApps を targets に保存しないためのガード．
                    val targetsToSave = selectedPackages.value - hiddenPackages.value
                    updateTargetsUseCase.updateTargets(targetsToSave)
                }
                onSaved()
            }
        }
    }
