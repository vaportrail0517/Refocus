package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.OverlaySettings
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

// 基本設定用のプリセット種別（1項目ごとのプリセット状態）
enum class FontPreset { Small, Medium, Large }
enum class TimeToMaxPreset { Slow, Normal, Fast }
enum class GracePreset { Short, Normal, Long }
enum class SuggestionTriggerPreset { Min10, Min15, Min30, Min60 }

// OverlaySettings から「どのプリセットにハマるか」を逆算する
fun OverlaySettings.fontPresetOrNull(): FontPreset? {
    val min = minFontSizeSp
    val max = maxFontSizeSp
    return when (min) {
        10f if max == 30f -> FontPreset.Small
        12f if max == 40f -> FontPreset.Medium
        14f if max == 50f -> FontPreset.Large
        else -> null
    }
}

fun OverlaySettings.timeToMaxPresetOrNull(): TimeToMaxPreset? {
    return when (timeToMaxMinutes) {
        45 -> TimeToMaxPreset.Slow
        30 -> TimeToMaxPreset.Normal
        15 -> TimeToMaxPreset.Fast
        else -> null
    }
}

fun OverlaySettings.gracePresetOrNull(): GracePreset? {
    return when (gracePeriodMillis) {
        30_000L -> GracePreset.Short
        60_000L -> GracePreset.Normal
        180_000L -> GracePreset.Long
        else -> null
    }
}

fun OverlaySettings.suggestionTriggerPresetOrNull(): SuggestionTriggerPreset? {
    return when (suggestionTriggerSeconds) {
        10 * 60 -> SuggestionTriggerPreset.Min10
        15 * 60 -> SuggestionTriggerPreset.Min15
        30 * 60 -> SuggestionTriggerPreset.Min30
        60 * 60 -> SuggestionTriggerPreset.Min60
        else -> null
    }
}

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    data class UiState(
        val overlaySettings: OverlaySettings,
        val preset: SettingsPreset = SettingsPreset.Default,
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(
        UiState(
            overlaySettings = OverlaySettings(),
            preset = SettingsPreset.Default,
            isLoading = true
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
                    overlaySettings = settings,
                    preset = preset,
                    isLoading = false,
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
    }

    fun updateGracePeriodMillis(ms: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(gracePeriodMillis = ms)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updatePollingIntervalMillis(ms: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(pollingIntervalMillis = ms)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateMinFontSizeSp(minSp: Float) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    minFontSizeSp = minSp
                )
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateMaxFontSizeSp(maxSp: Float) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    maxFontSizeSp = maxSp
                )
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateTimeToMaxMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(
                    timeToMaxMinutes = minutes
                )
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateOverlayTouchMode(mode: OverlayTouchMode) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(touchMode = mode)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateOverlayPosition(x: Int, y: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(positionX = x, positionY = y)
            }
        }
    }

    fun updateOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
        }
    }

    fun updateSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSuggestionEnabled(enabled)
        }
    }

    fun updateRestSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRestSuggestionEnabled(enabled)
        }
    }

    // --- ここからプリセット用の helper ---
    /** タイマーの文字サイズプリセットを適用（Small / Medium / Large） */
    fun applyFontPreset(preset: FontPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                val (min, max) = when (preset) {
                    FontPreset.Small -> 10f to 30f
                    FontPreset.Medium -> 12f to 40f
                    FontPreset.Large -> 14f to 50f
                }
                current.copy(
                    minFontSizeSp = min,
                    maxFontSizeSp = max,
                )
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** タイマーが最大サイズになるまでの時間プリセットを適用（遅め / ふつう / 早め） */
    fun applyTimeToMaxPreset(preset: TimeToMaxPreset) {
        viewModelScope.launch {
            val minutes = when (preset) {
                TimeToMaxPreset.Slow -> 45
                TimeToMaxPreset.Normal -> 30
                TimeToMaxPreset.Fast -> 15
            }
            settingsRepository.updateOverlaySettings { current ->
                current.copy(timeToMaxMinutes = minutes)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** グレース期間プリセットを適用（短め / ふつう / 長め） */
    fun applyGracePreset(preset: GracePreset) {
        viewModelScope.launch {
            val millis = when (preset) {
                GracePreset.Short -> 30_000L
                GracePreset.Normal -> 60_000L
                GracePreset.Long -> 180_000L
            }
            settingsRepository.updateOverlaySettings { current ->
                current.copy(gracePeriodMillis = millis)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** 提案トリガ時間プリセットを適用（10/15/30/60分） */
    fun applySuggestionTriggerPreset(preset: SuggestionTriggerPreset) {
        viewModelScope.launch {
            val seconds = when (preset) {
                SuggestionTriggerPreset.Min10 -> 10 * 60
                SuggestionTriggerPreset.Min15 -> 15 * 60
                SuggestionTriggerPreset.Min30 -> 30 * 60
                SuggestionTriggerPreset.Min60 -> 60 * 60
            }
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionTriggerSeconds = seconds)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /**
     * 設定プリセットを適用する。
     * Default / Debug は値を一括リセット、Custom は種別だけ変更。
     */
    fun applyPreset(preset: SettingsPreset) {
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
        }
    }

    /**
     * 値は変えずにプリセット種別だけ Custom にしたい場合。
     */
    fun setPresetCustom() {
        viewModelScope.launch {
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }
}
