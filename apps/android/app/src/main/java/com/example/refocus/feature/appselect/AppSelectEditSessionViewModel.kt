package com.example.refocus.feature.appselect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.domain.repository.HiddenAppsRepository
import com.example.refocus.domain.repository.TargetsRepository
import com.example.refocus.domain.targets.UpdateTargetsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class AppSelectEditSessionViewModel
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val hiddenAppsRepository: HiddenAppsRepository,
        private val updateTargetsUseCase: UpdateTargetsUseCase,
    ) : ViewModel() {
        private val baselineTargets = MutableStateFlow<Set<String>>(emptySet())
        private val baselineHidden = MutableStateFlow<Set<String>>(emptySet())

        private val draftTargets = MutableStateFlow<Set<String>>(emptySet())
        private val draftHidden = MutableStateFlow<Set<String>>(emptySet())

        private val isLoaded = MutableStateFlow(false)
        private val isSaving = MutableStateFlow(false)

        val committedTargets: StateFlow<Set<String>> =
            targetsRepository
                .observeTargets()
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        val draftTargetsState: StateFlow<Set<String>> = draftTargets.asStateFlow()
        val draftHiddenState: StateFlow<Set<String>> = draftHidden.asStateFlow()
        val isLoadedState: StateFlow<Boolean> = isLoaded.asStateFlow()
        val isSavingState: StateFlow<Boolean> = isSaving.asStateFlow()

        val isDirtyTargets: StateFlow<Boolean> =
            combine(draftTargets, baselineTargets) { draft, base ->
                draft != base
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val isDirtyHidden: StateFlow<Boolean> =
            combine(draftHidden, baselineHidden) { draft, base ->
                draft != base
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        val isDirtyAny: StateFlow<Boolean> =
            combine(isDirtyTargets, isDirtyHidden) { t, h ->
                t || h
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

        /**
         * 保存確定される targets は hiddenApps を除外したものとする．
         *
         * - overlay や統計側は HiddenApps を参照しないため，targets 側から除外されている必要がある．
         * - draftTargets は hidden を ON にした瞬間に自動で除外されるが，二重にガードしておく．
         */
        val effectiveDraftTargets: StateFlow<Set<String>> =
            combine(draftTargets, draftHidden) { targets, hidden ->
                targets - hidden
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

        init {
            loadInitial()
            keepDraftTargetsConsistentWithHiddenDraft()
        }

        private fun loadInitial() {
            viewModelScope.launch {
                val currentTargets = targetsRepository.observeTargets().first()
                val currentHidden = hiddenAppsRepository.observeHiddenApps().first()

                baselineTargets.value = currentTargets
                baselineHidden.value = currentHidden
                draftTargets.value = currentTargets - currentHidden
                draftHidden.value = currentHidden

                isLoaded.value = true
            }
        }

        private fun keepDraftTargetsConsistentWithHiddenDraft() {
            viewModelScope.launch {
                draftHidden.collect { hidden ->
                    val current = draftTargets.value
                    val updated = current - hidden
                    if (updated != current) {
                        draftTargets.value = updated
                    }
                }
            }
        }

        fun toggleTarget(packageName: String) {
            if (packageName in draftHidden.value) return
            val current = draftTargets.value
            draftTargets.value = if (packageName in current) current - packageName else current + packageName
        }

        fun toggleHidden(packageName: String) {
            val currentHidden = draftHidden.value
            val willHide = packageName !in currentHidden

            // hidden を ON にした瞬間に draftTargets からも同期的に除外する．
            // draftHidden の collect でも整合を保っているが，非同期の反映遅延に依存しないよう即時に反映する．
            if (willHide) {
                draftHidden.value = currentHidden + packageName
                val currentTargets = draftTargets.value
                if (packageName in currentTargets) {
                    draftTargets.value = currentTargets - packageName
                }
            } else {
                draftHidden.value = currentHidden - packageName
            }
        }

        /**
         * 複数の package をまとめて非表示解除する（draft のみ）．
         *
         * - targets への追加は行わない（ユーザが明示的に対象として選び直す）．
         * - 主に「アンインストール済み等で一覧に出ない非表示」を一括解除する用途を想定する．
         */
        fun unhidePackages(packageNames: Set<String>) {
            if (packageNames.isEmpty()) return
            val current = draftHidden.value
            val updated = current - packageNames
            if (updated != current) {
                draftHidden.value = updated
            }
        }

        /**
         * hiddenApps だけを保存する．
         *
         * - hiddenApps は UI 上の「非表示」だけでなく，targets からの除外も保証する必要がある．
         * - ただし，この画面で「対象の追加・削除」まで確定させると，AppSelect 側の未保存編集と衝突する．
         * - よって targets は「保存済み targets から hidden を除外する」最小変更のみを適用する．
         */
        fun saveHiddenOnly(onSaved: () -> Unit) {
            viewModelScope.launch {
                if (!isLoaded.value) return@launch
                if (isSaving.value) return@launch
                isSaving.value = true

                val newHidden = draftHidden.value

                withContext(Dispatchers.IO) {
                    val shouldRecordHidden = newHidden != baselineHidden.value
                    if (shouldRecordHidden) {
                        hiddenAppsRepository.setHiddenApps(newHidden, recordEvent = true)
                    }

                    val currentTargets = targetsRepository.observeTargets().first()
                    val nextTargets = currentTargets - newHidden
                    if (nextTargets != currentTargets) {
                        targetsRepository.setTargets(nextTargets, recordEvent = true)
                    }

                    // baseline を「現在の保存済み状態」へ寄せる（未保存の draftTargets は維持される）
                    baselineHidden.value = newHidden
                    baselineTargets.value = nextTargets
                }

                isSaving.value = false
                onSaved()
            }
        }

        /**
         * targets と hiddenApps をまとめて確定保存する（AppSelect の「完了」用）．
         */
        fun saveAll(onSaved: () -> Unit) {
            viewModelScope.launch {
                if (!isLoaded.value) return@launch
                if (isSaving.value) return@launch
                isSaving.value = true

                val newHidden = draftHidden.value
                // combine の反映遅延に依存しないよう，保存時は同期的にスナップショットを作る
                val targetsToSave = draftTargets.value - newHidden

                withContext(Dispatchers.IO) {
                    if (newHidden != baselineHidden.value) {
                        hiddenAppsRepository.setHiddenApps(newHidden, recordEvent = true)
                    }

                    // 変更があるときだけ targets 更新（タイムラインのノイズ抑制）
                    val currentTargets = targetsRepository.observeTargets().first()
                    if (targetsToSave != currentTargets) {
                        updateTargetsUseCase.updateTargets(targetsToSave)
                    }

                    baselineHidden.value = newHidden
                    baselineTargets.value = targetsToSave
                }

                isSaving.value = false
                onSaved()
            }
        }
    }
