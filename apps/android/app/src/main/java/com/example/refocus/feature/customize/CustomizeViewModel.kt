package com.example.refocus.feature.customize

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.config.CustomizeBasicPresets.withFontPreset
import com.example.refocus.config.CustomizeBasicPresets.withGracePreset
import com.example.refocus.config.CustomizeBasicPresets.withSuggestionTriggerPreset
import com.example.refocus.config.CustomizeBasicPresets.withTimeToMaxPreset
import com.example.refocus.config.FontPreset
import com.example.refocus.config.GracePreset
import com.example.refocus.config.SuggestionTriggerPreset
import com.example.refocus.config.TimeToMaxPreset
import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import com.example.refocus.core.model.TimerColorMode
import com.example.refocus.core.model.TimerGrowthMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.app.reset.AppDataResetter
import com.example.refocus.domain.settings.SettingsCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CustomizeViewModel @Inject constructor(
    application: Application,
    private val settingsRepository: SettingsRepository,
    private val settingsCommand: SettingsCommand,
    private val appDataResetter: AppDataResetter,
) : AndroidViewModel(application) {

    data class UiState(
        val customize: Customize,
        val preset: CustomizePreset = CustomizePreset.Default,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            customize = Customize(),
            preset = CustomizePreset.Default,
            isLoading = true,
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                settingsRepository.observeOverlaySettings(),
                settingsRepository.observeSettingsPreset(),
            ) { settings, preset ->
                UiState(
                    customize = settings,
                    preset = preset,
                    isLoading = false,
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }

    /**
     * 「プリセット = Custom」として設定値を更新する共通ヘルパ。
     * 同じパターンの関数を大量に書かなくてよくなる。
     */
    private fun updateSettingsAsCustom(
        key: String,
        newValueDescription: String?,
        transform: Customize.() -> Customize,
    ) {
        viewModelScope.launch {
            settingsCommand.updateCustomize(
                key = key,
                newValueDescription = newValueDescription,
                source = "ui_customize",
                markPresetCustom = true,
            ) { current ->
                current.transform()
            }
        }
    }

    private fun updateSettingsWithoutPresetChange(
        key: String,
        newValueDescription: String?,
        transform: Customize.() -> Customize,
    ) {
        viewModelScope.launch {
            settingsCommand.updateCustomize(
                key = key,
                newValueDescription = newValueDescription,
                source = "ui_customize",
                markPresetCustom = false,
            ) { current ->
                current.transform()
            }
        }
    }

    // --- セッション / 監視設定 ---

    fun updateGracePeriodMillis(ms: Long) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GracePeriodMillis,
            newValueDescription = ms.toString(),
        ) { copy(gracePeriodMillis = ms) }

    fun updatePollingIntervalMillis(ms: Long) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.PollingIntervalMillis,
            newValueDescription = ms.toString(),
        ) { copy(pollingIntervalMillis = ms) }

    // --- タイマーの見た目 ---

    fun updateMinFontSizeSp(sp: Float) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.MinFontSizeSp,
            newValueDescription = sp.toString(),
        ) { copy(minFontSizeSp = sp) }

    fun updateMaxFontSizeSp(sp: Float) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.MaxFontSizeSp,
            newValueDescription = sp.toString(),
        ) { copy(maxFontSizeSp = sp) }

    fun updateTimeToMaxMinutes(minutes: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.TimeToMaxMinutes,
            newValueDescription = minutes.toString(),
        ) { copy(timeToMaxMinutes = minutes) }

    fun updateOverlayTouchMode(mode: TimerTouchMode) {
        viewModelScope.launch {
            settingsCommand.setTouchMode(
                mode = mode,
                source = "ui_customize",
                markPresetCustom = true,
            )
        }
    }

    fun updateTimerTimeMode(mode: TimerTimeMode) {
        viewModelScope.launch {
            settingsCommand.setTimerTimeMode(
                mode = mode,
                source = "ui_customize",
                markPresetCustom = true,
            )
        }
    }

    fun updateTimerVisualTimeBasis(basis: TimerVisualTimeBasis) {
        viewModelScope.launch {
            settingsCommand.setTimerVisualTimeBasis(
                basis = basis,
                source = "ui_customize",
                markPresetCustom = true,
            )
        }
    }

    fun updateGrowthMode(mode: TimerGrowthMode) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GrowthMode,
            newValueDescription = mode.name,
        ) { copy(growthMode = mode) }

    fun updateColorMode(mode: TimerColorMode) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.ColorMode,
            newValueDescription = mode.name,
        ) { copy(colorMode = mode) }

    fun updateFixedColorArgb(argb: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.FixedColorArgb,
            newValueDescription = "0x${argb.toUInt().toString(16)}",
        ) { copy(fixedColorArgb = argb) }

    fun updateGradientStartColorArgb(argb: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GradientStartColorArgb,
            newValueDescription = "0x${argb.toUInt().toString(16)}",
        ) { copy(gradientStartColorArgb = argb) }

    fun updateGradientMiddleColorArgb(argb: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GradientMiddleColorArgb,
            newValueDescription = "0x${argb.toUInt().toString(16)}",
        ) { copy(gradientMiddleColorArgb = argb) }

    fun updateGradientEndColorArgb(argb: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GradientEndColorArgb,
            newValueDescription = "0x${argb.toUInt().toString(16)}",
        ) { copy(gradientEndColorArgb = argb) }

    fun updateOverlayPosition(x: Int, y: Int) {
        viewModelScope.launch {
            settingsCommand.setOverlayPosition(
                x = x,
                y = y,
                source = "ui_customize",
                reason = "drag",
                recordEvent = false,
            )
        }
    }

    // --- 有効化 / 自動起動 ---

    /**
     * overlayEnabled の永続化が完了するまで待つ（サービス起動と競合させないため）．
     *
     * Composable 側では rememberCoroutineScope().launch { ... } から呼び出す想定．
     */
    suspend fun setOverlayEnabledAndWait(enabled: Boolean) {
        settingsCommand.setOverlayEnabled(
            enabled = enabled,
            source = "ui_customize",
        )
    }

    fun updateOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsCommand.setOverlayEnabled(
                enabled = enabled,
                source = "ui_customize",
            )
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsCommand.setAutoStartOnBoot(
                enabled = enabled,
                source = "ui_customize",
            )
        }
    }

    // --- 提案設定 ---

    fun updateSuggestionEnabled(enabled: Boolean) =
        updateSettingsWithoutPresetChange(
            key = SettingsCommand.Keys.SuggestionEnabled,
            newValueDescription = enabled.toString(),
        ) { copy(suggestionEnabled = enabled) }

    fun updateRestSuggestionEnabled(enabled: Boolean) =
        updateSettingsWithoutPresetChange(
            key = SettingsCommand.Keys.RestSuggestionEnabled,
            newValueDescription = enabled.toString(),
        ) { copy(restSuggestionEnabled = enabled) }

    fun updateSuggestionTriggerSeconds(seconds: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionTriggerSeconds,
            newValueDescription = seconds.toString(),
        ) { copy(suggestionTriggerSeconds = seconds) }

    fun updateSuggestionForegroundStableSeconds(seconds: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionForegroundStableSeconds,
            newValueDescription = seconds.toString(),
        ) { copy(suggestionForegroundStableSeconds = seconds) }

    fun updateSuggestionCooldownSeconds(seconds: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionCooldownSeconds,
            newValueDescription = seconds.toString(),
        ) { copy(suggestionCooldownSeconds = seconds) }

    fun updateSuggestionTimeoutSeconds(seconds: Int) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionTimeoutSeconds,
            newValueDescription = seconds.toString(),
        ) { copy(suggestionTimeoutSeconds = seconds) }

    fun updateSuggestionInteractionLockoutMillis(ms: Long) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionInteractionLockoutMillis,
            newValueDescription = ms.toString(),
        ) { copy(suggestionInteractionLockoutMillis = ms) }

    // --- プリセット ---

    fun applyFontPreset(preset: FontPreset) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.FontPreset,
            newValueDescription = preset.name,
        ) { withFontPreset(preset) }

    fun applyTimeToMaxPreset(preset: TimeToMaxPreset) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.TimeToMaxPreset,
            newValueDescription = preset.name,
        ) { withTimeToMaxPreset(preset) }

    fun applyGracePreset(preset: GracePreset) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.GracePreset,
            newValueDescription = preset.name,
        ) { withGracePreset(preset) }

    fun applySuggestionTriggerPreset(preset: SuggestionTriggerPreset) =
        updateSettingsAsCustom(
            key = SettingsCommand.Keys.SuggestionTriggerPreset,
            newValueDescription = preset.name,
        ) { withSuggestionTriggerPreset(preset) }

    fun applyPreset(preset: CustomizePreset) {
        viewModelScope.launch {
            settingsCommand.applyPreset(
                preset = preset,
                source = "ui_customize",
            )
        }
    }

    fun setPresetCustom() {
        viewModelScope.launch {
            settingsCommand.setSettingsPresetIfNeeded(
                preset = CustomizePreset.Custom,
                source = "ui_customize",
                reason = "set_preset_custom",
            )
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            appDataResetter.resetAll()
        }
    }
}
