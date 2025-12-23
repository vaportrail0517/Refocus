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
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.domain.app.AppDataResetter
import com.example.refocus.domain.timeline.EventRecorder
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
    private val appDataResetter: AppDataResetter,
    private val eventRecorder: EventRecorder,
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
    private fun updateSettingsAsCustom(transform: Customize.() -> Customize) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.transform()
            }
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
            eventRecorder.onSettingsChanged(
                key = "overlaySettingsCustom",
                newValueDescription = null,  // 詳細を入れたければここに入れる
            )
        }
    }

    // --- セッション / 監視設定 ---

    fun updateGracePeriodMillis(ms: Long) =
        updateSettingsAsCustom { copy(gracePeriodMillis = ms) }

    fun updatePollingIntervalMillis(ms: Long) =
        updateSettingsAsCustom { copy(pollingIntervalMillis = ms) }

    // --- オーバーレイ見た目 ---

    fun updateMinFontSizeSp(minSp: Float) =
        updateSettingsAsCustom { copy(minFontSizeSp = minSp) }

    fun updateMaxFontSizeSp(maxSp: Float) =
        updateSettingsAsCustom { copy(maxFontSizeSp = maxSp) }

    fun updateTimeToMaxMinutes(minutes: Int) =
        updateSettingsAsCustom { copy(timeToMaxMinutes = minutes) }

    fun updateOverlayTouchMode(mode: TimerTouchMode) =
        updateSettingsAsCustom { copy(touchMode = mode) }

    fun updateTimerTimeMode(mode: TimerTimeMode) =
        updateSettingsAsCustom { copy(timerTimeMode = mode) }

    fun updateGrowthMode(mode: TimerGrowthMode) =
        updateSettingsAsCustom { copy(growthMode = mode) }

    fun updateColorMode(mode: TimerColorMode) =
        updateSettingsAsCustom { copy(colorMode = mode) }

    fun updateFixedColorArgb(argb: Int) =
        updateSettingsAsCustom { copy(fixedColorArgb = argb) }

    fun updateGradientStartColorArgb(argb: Int) =
        updateSettingsAsCustom { copy(gradientStartColorArgb = argb) }

    fun updateGradientMiddleColorArgb(argb: Int) =
        updateSettingsAsCustom { copy(gradientMiddleColorArgb = argb) }

    fun updateGradientEndColorArgb(argb: Int) =
        updateSettingsAsCustom { copy(gradientEndColorArgb = argb) }

    fun updateOverlayPosition(x: Int, y: Int) {
        // 位置変更はプリセット種別を変えなくてよいなら、あえて Custom にしない。
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(positionX = x, positionY = y)
            }
        }
    }

    // --- 有効/無効・起動関連 ---

    fun updateOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setOverlayEnabled(enabled)
            eventRecorder.onSettingsChanged(
                key = "overlayEnabled",
                newValueDescription = enabled.toString(),
            )
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
            eventRecorder.onSettingsChanged(
                key = "autoStartOnBoot",
                newValueDescription = enabled.toString(),
            )
        }
    }

    // --- 提案機能（Suggestion） ---

    fun updateSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setSuggestionEnabled(enabled)
            eventRecorder.onSettingsChanged(
                key = "suggestionEnabled",
                newValueDescription = enabled.toString(),
            )
        }
    }

    fun updateRestSuggestionEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setRestSuggestionEnabled(enabled)
            eventRecorder.onSettingsChanged(
                key = "restSuggestionEnabled",
                newValueDescription = enabled.toString(),
            )
        }
    }

    fun updateSuggestionTriggerSeconds(seconds: Int) =
        updateSettingsAsCustom { copy(suggestionTriggerSeconds = seconds) }

    fun updateSuggestionForegroundStableSeconds(seconds: Int) =
        updateSettingsAsCustom { copy(suggestionForegroundStableSeconds = seconds) }

    fun updateSuggestionCooldownSeconds(seconds: Int) =
        updateSettingsAsCustom { copy(suggestionCooldownSeconds = seconds) }

    fun updateSuggestionTimeoutSeconds(seconds: Int) =
        updateSettingsAsCustom { copy(suggestionTimeoutSeconds = seconds) }

    fun updateSuggestionInteractionLockoutMillis(millis: Long) =
        updateSettingsAsCustom { copy(suggestionInteractionLockoutMillis = millis) }

    // --- プリセット適用（部分プリセット） ---

    /** フォントサイズのプリセットを適用（小さめ / ふつう / 大きめ） */
    fun applyFontPreset(preset: FontPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withFontPreset(preset)
            }
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
            eventRecorder.onSettingsChanged(
                key = "settingsPreset",
                newValueDescription = CustomizePreset.Custom.name,
            )
        }
    }

    /** 最大サイズまでの時間プリセットを適用（速い / 普通 / ゆっくり） */
    fun applyTimeToMaxPreset(preset: TimeToMaxPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withTimeToMaxPreset(preset)
            }
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
            eventRecorder.onSettingsChanged(
                key = "settingsPreset",
                newValueDescription = CustomizePreset.Custom.name,
            )
        }
    }

    /** グレース期間プリセットを適用（短め / ふつう / 長め） */
    fun applyGracePreset(preset: GracePreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withGracePreset(preset)
            }
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
            eventRecorder.onSettingsChanged(
                key = "settingsPreset",
                newValueDescription = CustomizePreset.Custom.name,
            )
        }
    }

    /** 提案トリガ時間プリセットを適用（短/中/長） */
    fun applySuggestionTriggerPreset(preset: SuggestionTriggerPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withSuggestionTriggerPreset(preset)
            }
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
            eventRecorder.onSettingsChanged(
                key = "settingsPreset",
                newValueDescription = CustomizePreset.Custom.name,
            )
        }
    }

    // --- 全体プリセット（Default / Debug / Custom） ---

    /**
     * 設定プリセットを適用する。
     * - Default / Debug: CustomizePresetValues 由来の値に置き換え
     * - Custom: 現在の値は維持しつつ、preset 種別だけ Custom にする
     */
    fun applyPreset(preset: CustomizePreset) {
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
            eventRecorder.onSettingsChanged(
                key = "settingsPreset",
                newValueDescription = preset.name,
            )
        }
    }

    /**
     * 値は変えずにプリセット種別だけ Custom にしたい場合。
     * （ユーザーが個別項目を変更した直後に呼ぶなど）
     */
    fun setPresetCustom() {
        viewModelScope.launch {
            settingsRepository.setSettingsPreset(CustomizePreset.Custom)
        }
    }

    fun resetAllData() {
        viewModelScope.launch {
            appDataResetter.resetAll()
        }
    }
}
