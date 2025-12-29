package com.example.refocus.system.overlay.service

import com.example.refocus.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * overlayEnabled が false になったら，OverlayService を停止するための監視クラス．
 *
 * - domain の設定ストリームを購読して停止条件を検知する
 * - Android の停止実処理はコールバックで OverlayService 側へ委譲する
 */
class OverlayServiceRunSupervisor(
    private val scope: CoroutineScope,
    private val settingsRepository: SettingsRepository,
    private val onOverlayDisabled: () -> Unit,
) {
    private var job: Job? = null
    private var hasEverBeenEnabled: Boolean = false

    fun start() {
        if (job?.isActive == true) return
        hasEverBeenEnabled = false
        job =
            scope.launch {
                settingsRepository
                    .observeOverlaySettings()
                    .map { it.overlayEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        if (enabled) {
                            hasEverBeenEnabled = true
                            return@collect
                        }

                        // 起動直後は DataStore の初期値（false）が先に流れてくることがある．
                        // 「一度でも true を観測した後」に false になった場合のみ停止する．
                        if (hasEverBeenEnabled) onOverlayDisabled()
                    }
            }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
