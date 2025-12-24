package com.example.refocus.domain.settings

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.domain.timeline.EventRecorder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * 設定変更の入口をここに集約するためのコマンド層．
 *
 * 目的
 * - UI 外 (Service / Tile / Receiver 等) からの設定更新でも SettingsChangedEvent を必ず残す
 * - 同値更新は無視し，タイムラインのノイズを減らす
 * - source / reason を付与し，後から「なぜ変わったか」を追えるようにする
 *
 * NOTE:
 * - タイムライン再構成の「解釈」では設定値を参照しない前提のため，
 *   newValueDescription はあくまで説明用途 (UI 表示 / デバッグ)．
 */
@Singleton
class SettingsCommand @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val eventRecorder: EventRecorder,
) {
    object Keys {
        const val OverlayEnabled = "overlayEnabled"
        const val AutoStartOnBoot = "autoStartOnBoot"
        const val TimerTimeMode = "timerTimeMode"
        const val TimerVisualTimeBasis = "timerVisualTimeBasis"
        const val TouchMode = "touchMode"
        const val SettingsPreset = "settingsPreset"

        const val GracePeriodMillis = "gracePeriodMillis"
        const val PollingIntervalMillis = "pollingIntervalMillis"
        const val MinFontSizeSp = "minFontSizeSp"
        const val MaxFontSizeSp = "maxFontSizeSp"
        const val TimeToMaxMinutes = "timeToMaxMinutes"
        const val GrowthMode = "growthMode"
        const val ColorMode = "colorMode"
        const val FixedColorArgb = "fixedColorArgb"
        const val GradientStartColorArgb = "gradientStartColorArgb"
        const val GradientMiddleColorArgb = "gradientMiddleColorArgb"
        const val GradientEndColorArgb = "gradientEndColorArgb"

        const val SuggestionEnabled = "suggestionEnabled"
        const val RestSuggestionEnabled = "restSuggestionEnabled"
        const val SuggestionTriggerSeconds = "suggestionTriggerSeconds"
        const val SuggestionForegroundStableSeconds = "suggestionForegroundStableSeconds"
        const val SuggestionCooldownSeconds = "suggestionCooldownSeconds"
        const val SuggestionTimeoutSeconds = "suggestionTimeoutSeconds"
        const val SuggestionInteractionLockoutMillis = "suggestionInteractionLockoutMillis"

        const val OverlayPosition = "overlayPosition"
        const val FontPreset = "fontPreset"
        const val TimeToMaxPreset = "timeToMaxPreset"
        const val GracePreset = "gracePreset"
        const val SuggestionTriggerPreset = "suggestionTriggerPreset"
    }

    private fun buildValueDescription(
        value: String?,
        source: String,
        reason: String?,
    ): String? {
        val suffixParts = buildList {
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
        key: String,
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
                key = key,
                newValueDescription = buildValueDescription(
                    value = newValueDescription,
                    source = source,
                    reason = reason,
                )
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
                key = Keys.SettingsPreset,
                newValueDescription = buildValueDescription(
                    value = preset.name,
                    source = source,
                    reason = reason,
                )
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
            key = Keys.SettingsPreset,
            newValueDescription = buildValueDescription(
                value = preset.name,
                source = source,
                reason = reason ?: "apply_preset",
            )
        )
    }

    suspend fun setOverlayEnabled(
        enabled: Boolean,
        source: String,
        reason: String? = null,
        recordEvent: Boolean = true,
    ) {
        updateCustomize(
            key = Keys.OverlayEnabled,
            newValueDescription = enabled.toString(),
            source = source,
            reason = reason,
            markPresetCustom = false,
            recordEvent = recordEvent,
        ) { current ->
            current.copy(overlayEnabled = enabled)
        }
    }

    suspend fun setAutoStartOnBoot(
        enabled: Boolean,
        source: String,
        reason: String? = null,
    ) {
        updateCustomize(
            key = Keys.AutoStartOnBoot,
            newValueDescription = enabled.toString(),
            source = source,
            reason = reason,
            markPresetCustom = false,
        ) { current ->
            current.copy(autoStartOnBoot = enabled)
        }
    }

    suspend fun setTimerTimeMode(
        mode: TimerTimeMode,
        source: String,
        reason: String? = null,
        markPresetCustom: Boolean = true,
    ) {
        updateCustomize(
            key = Keys.TimerTimeMode,
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
            key = Keys.TimerVisualTimeBasis,
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
            key = Keys.TouchMode,
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
            key = Keys.OverlayPosition,
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
                key = "resetToDefaults",
                newValueDescription = buildValueDescription(
                    value = "done",
                    source = source,
                    reason = reason,
                )
            )
        }
    }
}
