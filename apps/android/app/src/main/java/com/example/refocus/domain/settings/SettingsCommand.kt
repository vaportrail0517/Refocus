package com.example.refocus.domain.settings

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.ServiceConfigKind
import com.example.refocus.core.model.ServiceConfigState
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.core.settings.SettingsChangeKey
import com.example.refocus.core.settings.SettingsChangeKeys
import com.example.refocus.domain.overlay.port.OverlayKeepAliveScheduler
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.domain.timeline.EventRecorder
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 設定変更の入口をここに集約するためのコマンド層．
 *
 * 目的
 * - UI 外 (Service / Tile / Receiver 等) からの設定更新でも SettingsChangedEvent を必ず残す
 * - 同値更新は無視し，タイムラインのノイズを減らす
 * - source / reason を付与し，後から「なぜ変わったか」を追えるようにする

 *
 * 例外
 * - overlayEnabled / autoStartOnBoot は SettingsChangedEvent ではなく ServiceConfigEvent として記録する
 *
 * NOTE:
 * - タイムライン再構成の「解釈」では設定値を参照しない前提のため，
 *   newValueDescription はあくまで説明用途 (UI 表示 / デバッグ)．
 */
@Singleton
class SettingsCommand
    @Inject
    constructor(
        private val settingsRepository: SettingsRepository,
        private val eventRecorder: EventRecorder,
        private val overlayKeepAliveScheduler: OverlayKeepAliveScheduler,
    ) {
        object Keys {
            // SettingsChangedEvent のキーは DB に永続化されるため，core.settings で一元管理する．
            val OVERLAY_ENABLED: SettingsChangeKey = SettingsChangeKeys.OVERLAY_ENABLED
            val AUTO_START_ON_BOOT: SettingsChangeKey = SettingsChangeKeys.AUTO_START_ON_BOOT
            val TIMER_TIME_MODE: SettingsChangeKey = SettingsChangeKeys.TIMER_TIME_MODE
            val TIMER_VISUAL_TIME_BASIS: SettingsChangeKey = SettingsChangeKeys.TIMER_VISUAL_TIME_BASIS
            val TOUCH_MODE: SettingsChangeKey = SettingsChangeKeys.TOUCH_MODE
            val SETTINGS_PRESET: SettingsChangeKey = SettingsChangeKeys.SETTINGS_PRESET

            val GRACE_PERIOD_MILLIS: SettingsChangeKey = SettingsChangeKeys.GRACE_PERIOD_MILLIS
            val POLLING_INTERVAL_MILLIS: SettingsChangeKey = SettingsChangeKeys.POLLING_INTERVAL_MILLIS
            val MIN_FONT_SIZE_SP: SettingsChangeKey = SettingsChangeKeys.MIN_FONT_SIZE_SP
            val MAX_FONT_SIZE_SP: SettingsChangeKey = SettingsChangeKeys.MAX_FONT_SIZE_SP
            val TIME_TO_MAX_SECONDS: SettingsChangeKey = SettingsChangeKeys.TIME_TO_MAX_SECONDS

            // 旧キー: 既存のタイムラインイベント互換のため残す
            @Suppress("unused")
            val TIME_TO_MAX_MINUTES: SettingsChangeKey = SettingsChangeKeys.TIME_TO_MAX_MINUTES
            val GROWTH_MODE: SettingsChangeKey = SettingsChangeKeys.GROWTH_MODE
            val COLOR_MODE: SettingsChangeKey = SettingsChangeKeys.COLOR_MODE
            val FIXED_COLOR_ARGB: SettingsChangeKey = SettingsChangeKeys.FIXED_COLOR_ARGB
            val GRADIENT_START_COLOR_ARGB: SettingsChangeKey = SettingsChangeKeys.GRADIENT_START_COLOR_ARGB
            val GRADIENT_MIDDLE_COLOR_ARGB: SettingsChangeKey = SettingsChangeKeys.GRADIENT_MIDDLE_COLOR_ARGB
            val GRADIENT_END_COLOR_ARGB: SettingsChangeKey = SettingsChangeKeys.GRADIENT_END_COLOR_ARGB

            val BASE_COLOR_ANIM_ENABLED: SettingsChangeKey = SettingsChangeKeys.BASE_COLOR_ANIM_ENABLED
            val BASE_ANIMATIONS: SettingsChangeKey = SettingsChangeKeys.BASE_ANIMATIONS
            val BASE_SIZE_ANIM_ENABLED: SettingsChangeKey = SettingsChangeKeys.BASE_SIZE_ANIM_ENABLED
            val BASE_PULSE_ENABLED: SettingsChangeKey = SettingsChangeKeys.BASE_PULSE_ENABLED
            val EFFECTS_ENABLED: SettingsChangeKey = SettingsChangeKeys.EFFECTS_ENABLED
            val EFFECT_INTERVAL_SECONDS: SettingsChangeKey = SettingsChangeKeys.EFFECT_INTERVAL_SECONDS
            val SUGGESTION_ENABLED: SettingsChangeKey = SettingsChangeKeys.SUGGESTION_ENABLED
            val REST_SUGGESTION_ENABLED: SettingsChangeKey = SettingsChangeKeys.REST_SUGGESTION_ENABLED
            val SUGGESTION_TRIGGER_SECONDS: SettingsChangeKey = SettingsChangeKeys.SUGGESTION_TRIGGER_SECONDS
            val SUGGESTION_FOREGROUND_STABLE_SECONDS: SettingsChangeKey =
                SettingsChangeKeys.SUGGESTION_FOREGROUND_STABLE_SECONDS
            val SUGGESTION_COOLDOWN_SECONDS: SettingsChangeKey = SettingsChangeKeys.SUGGESTION_COOLDOWN_SECONDS
            val SUGGESTION_TIMEOUT_SECONDS: SettingsChangeKey = SettingsChangeKeys.SUGGESTION_TIMEOUT_SECONDS
            val SUGGESTION_INTERACTION_LOCKOUT_MILLIS: SettingsChangeKey =
                SettingsChangeKeys.SUGGESTION_INTERACTION_LOCKOUT_MILLIS

            val MINI_GAME_ENABLED: SettingsChangeKey = SettingsChangeKeys.MINI_GAME_ENABLED
            val MINI_GAME_ORDER: SettingsChangeKey = SettingsChangeKeys.MINI_GAME_ORDER
            val MINI_GAME_DISABLED_KINDS: SettingsChangeKey = SettingsChangeKeys.MINI_GAME_DISABLED_KINDS
            val RESET_TO_DEFAULTS: SettingsChangeKey = SettingsChangeKeys.RESET_TO_DEFAULTS

            val OVERLAY_POSITION: SettingsChangeKey = SettingsChangeKeys.OVERLAY_POSITION
        }

        private fun buildValueDescription(
            value: String?,
            source: String,
            reason: String?,
        ): String? {
            val suffixParts =
                buildList {
                    add("source=$source")
                    if (!reason.isNullOrBlank()) add("reason=$reason")
                }
            val suffix = suffixParts.joinToString("|")
            return when {
                value == null -> suffix.ifBlank { null }
                suffix.isBlank() -> value
                else -> "$value|$suffix"
            }
        }

        /**
         * Customize(DataStore) を更新し，必要なら SettingsChangedEvent を記録する．
         *
         * @param markPresetCustom true の場合，設定更新後に preset を Custom に寄せる（必要時のみ）
         * @param recordEvent false の場合，タイムラインへ SettingsChangedEvent を残さない
         */
        suspend fun updateCustomize(
            key: SettingsChangeKey,
            newValueDescription: String?,
            source: String,
            reason: String? = null,
            markPresetCustom: Boolean = false,
            recordEvent: Boolean = true,
            transform: (Customize) -> Customize,
        ): Boolean {
            val before = settingsRepository.observeOverlaySettings().first()
            val after = transform(before)
            if (before == after) return false

            settingsRepository.updateOverlaySettings { current ->
                transform(current)
            }

            if (recordEvent) {
                eventRecorder.onSettingsChanged(
                    key = key.value,
                    newValueDescription =
                        buildValueDescription(
                            value = newValueDescription,
                            source = source,
                            reason = reason,
                        ),
                )
            }

            if (markPresetCustom) {
                setSettingsPresetIfNeeded(
                    preset = CustomizePreset.Custom,
                    source = source,
                    reason = "auto_mark_custom",
                )
            }

            return true
        }

        suspend fun setSettingsPresetIfNeeded(
            preset: CustomizePreset,
            source: String,
            reason: String? = null,
            recordEvent: Boolean = true,
        ): Boolean {
            val before = settingsRepository.observeSettingsPreset().first()
            if (before == preset) return false

            settingsRepository.setSettingsPreset(preset)
            if (recordEvent) {
                eventRecorder.onSettingsChanged(
                    key = Keys.SETTINGS_PRESET.value,
                    newValueDescription =
                        buildValueDescription(
                            value = preset.name,
                            source = source,
                            reason = reason,
                        ),
                )
            }
            return true
        }

        suspend fun applyPreset(
            preset: CustomizePreset,
            source: String,
            reason: String? = null,
        ) {
            // applyPreset 内で OverlaySettings と preset の両方が更新されるため，
            // 記録は「プリセット種別」に一本化する（ノイズ抑制）．
            settingsRepository.applyPreset(preset)
            eventRecorder.onSettingsChanged(
                key = Keys.SETTINGS_PRESET.value,
                newValueDescription =
                    buildValueDescription(
                        value = preset.name,
                        source = source,
                        reason = reason ?: "apply_preset",
                    ),
            )
        }

        suspend fun setOverlayEnabled(
            enabled: Boolean,
            source: String,
            reason: String? = null,
            recordEvent: Boolean = true,
        ) {
            val changed =
                updateCustomize(
                    key = Keys.OVERLAY_ENABLED,
                    newValueDescription = enabled.toString(),
                    source = source,
                    reason = reason,
                    markPresetCustom = false,
                    // overlayEnabled は Service 設定として扱うため，SettingsChangedEvent では記録しない
                    recordEvent = false,
                ) { current ->
                    current.copy(overlayEnabled = enabled)
                }

            // keep-alive は「設定に追従」させる．
            // (実際の起動判定は worker 側で権限と heartbeat に基づいて行う)
            if (changed) {
                overlayKeepAliveScheduler.onOverlayEnabledChanged(enabled)
            }

            if (changed && recordEvent) {
                eventRecorder.onServiceConfigChanged(
                    config = ServiceConfigKind.OverlayEnabled,
                    state = if (enabled) ServiceConfigState.Enabled else ServiceConfigState.Disabled,
                    meta = buildValueDescription(value = null, source = source, reason = reason),
                )
            }
        }

        suspend fun setAutoStartOnBoot(
            enabled: Boolean,
            source: String,
            reason: String? = null,
            recordEvent: Boolean = true,
        ) {
            val changed =
                updateCustomize(
                    key = Keys.AUTO_START_ON_BOOT,
                    newValueDescription = enabled.toString(),
                    source = source,
                    reason = reason,
                    markPresetCustom = false,
                    // autoStartOnBoot は Service 設定として扱うため，SettingsChangedEvent では記録しない
                    recordEvent = false,
                ) { current ->
                    current.copy(autoStartOnBoot = enabled)
                }

            if (changed && recordEvent) {
                eventRecorder.onServiceConfigChanged(
                    config = ServiceConfigKind.AutoStartOnBoot,
                    state = if (enabled) ServiceConfigState.Enabled else ServiceConfigState.Disabled,
                    meta = buildValueDescription(value = null, source = source, reason = reason),
                )
            }
        }

        suspend fun setTimerTimeMode(
            mode: TimerTimeMode,
            source: String,
            reason: String? = null,
            markPresetCustom: Boolean = true,
        ) {
            updateCustomize(
                key = Keys.TIMER_TIME_MODE,
                newValueDescription = mode.name,
                source = source,
                reason = reason,
                markPresetCustom = markPresetCustom,
            ) { current ->
                current.copy(timerTimeMode = mode)
            }
        }

        suspend fun setTimerVisualTimeBasis(
            basis: TimerVisualTimeBasis,
            source: String,
            reason: String? = null,
            markPresetCustom: Boolean = true,
        ) {
            updateCustomize(
                key = Keys.TIMER_VISUAL_TIME_BASIS,
                newValueDescription = basis.name,
                source = source,
                reason = reason,
                markPresetCustom = markPresetCustom,
            ) { current ->
                current.copy(timerVisualTimeBasis = basis)
            }
        }

        suspend fun setTouchMode(
            mode: TimerTouchMode,
            source: String,
            reason: String? = null,
            markPresetCustom: Boolean = true,
        ) {
            updateCustomize(
                key = Keys.TOUCH_MODE,
                newValueDescription = mode.name,
                source = source,
                reason = reason,
                markPresetCustom = markPresetCustom,
            ) { current ->
                current.copy(touchMode = mode)
            }
        }

        suspend fun setOverlayPosition(
            x: Int,
            y: Int,
            source: String,
            reason: String? = null,
            recordEvent: Boolean = false,
        ) {
            // 位置ドラッグは UI 操作中に何度も起き得るため，デフォルトではタイムラインへ残さない．
            updateCustomize(
                key = Keys.OVERLAY_POSITION,
                newValueDescription = "$x,$y",
                source = source,
                reason = reason,
                markPresetCustom = false,
                recordEvent = recordEvent,
            ) { current ->
                current.copy(positionX = x, positionY = y)
            }
        }

        suspend fun resetToDefaults(
            source: String,
            reason: String? = null,
            recordEvent: Boolean = false,
        ) {
            settingsRepository.resetToDefaults()
            if (recordEvent) {
                eventRecorder.onSettingsChanged(
                    key = Keys.RESET_TO_DEFAULTS.value,
                    newValueDescription =
                        buildValueDescription(
                            value = "done",
                            source = source,
                            reason = reason,
                        ),
                )
            }
        }
    }
