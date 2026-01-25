package com.example.refocus.domain.targets

import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.repository.HiddenAppsRepository
import com.example.refocus.domain.repository.TargetsRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 永続データとして targets ∩ hidden = ∅ を保証するための正規化。
 *
 * overlay 監視側でも差集合を適用するが，データ自体も修復しておくことで
 * 統計・履歴など「targets を前提に再構成するロジック」が破綻しにくくなる。
 */
class EnsureTargetsExcludeHiddenAppsUseCase
    @Inject
    constructor(
        private val targetsRepository: TargetsRepository,
        private val hiddenAppsRepository: HiddenAppsRepository,
    ) {
        companion object {
            private const val TAG = "EnsureTargetsExcludeHidden"
        }

        /**
         * 現在の永続データを読み取り，targets に hidden が混入していれば除去して保存する。
         *
         * @param recordEvent 自動修復をタイムラインへ記録するか（デフォルトはノイズ抑制のため false）
         */
        suspend fun ensure(recordEvent: Boolean = false) {
            val targets = targetsRepository.observeTargets().first()
            val hidden = hiddenAppsRepository.observeHiddenApps().first()

            if (targets.isEmpty() || hidden.isEmpty()) return

            val nextTargets = targets - hidden
            if (nextTargets == targets) return

            try {
                targetsRepository.setTargets(nextTargets, recordEvent = recordEvent)
                RefocusLog.w(TAG) {
                    "Normalized targets to exclude hidden apps. removed=${targets.size - nextTargets.size}"
                }
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "Failed to normalize targets to exclude hidden apps" }
            }
        }
    }
