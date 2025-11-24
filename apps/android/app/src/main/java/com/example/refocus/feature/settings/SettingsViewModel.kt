package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.config.SettingsBasicPresets.withFontPreset
import com.example.refocus.config.SettingsBasicPresets.withGracePreset
import com.example.refocus.config.SettingsBasicPresets.withSuggestionCooldownPreset
import com.example.refocus.config.SettingsBasicPresets.withSuggestionTriggerPreset
import com.example.refocus.config.SettingsBasicPresets.withTimeToMaxPreset
import com.example.refocus.core.model.FontPreset
import com.example.refocus.core.model.GracePreset
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SettingsPreset
import com.example.refocus.core.model.SuggestionCooldownPreset
import com.example.refocus.core.model.SuggestionTriggerPreset
import com.example.refocus.core.model.TimeToMaxPreset
import com.example.refocus.data.repository.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class SettingsViewModel(
    application: Application,
    private val settingsRepository: SettingsRepository
) : AndroidViewModel(application) {

    data class UiState(
        val settings: Settings,
        val preset: SettingsPreset = SettingsPreset.Default,
        val isLoading: Boolean = true,
    )

    private val _uiState = MutableStateFlow(
        UiState(
            settings = Settings(),
            preset = SettingsPreset.Default,
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
                    settings = settings,
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
    private fun updateSettingsAsCustom(transform: Settings.() -> Settings) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.transform()
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
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

    fun updateOverlayTouchMode(mode: OverlayTouchMode) =
        updateSettingsAsCustom { copy(touchMode = mode) }

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
        }
    }

    fun updateAutoStartOnBoot(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setAutoStartOnBoot(enabled)
        }
    }

    // --- 提案機能（Suggestion） ---

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
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** 最大サイズまでの時間プリセットを適用（速い / 普通 / ゆっくり） */
    fun applyTimeToMaxPreset(preset: TimeToMaxPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withTimeToMaxPreset(preset)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** グレース期間プリセットを適用（短め / ふつう / 長め） */
    fun applyGracePreset(preset: GracePreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withGracePreset(preset)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** 提案トリガ時間プリセットを適用（短/中/長） */
    fun applySuggestionTriggerPreset(preset: SuggestionTriggerPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withSuggestionTriggerPreset(preset)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** 次の提案までの待ち時間プリセットを適用（低い / 普通 / 高い） */
    fun applySuggestionCooldownPreset(preset: SuggestionCooldownPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withSuggestionCooldownPreset(preset)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    // --- 全体プリセット（Default / Debug / Custom） ---

    /**
     * 設定プリセットを適用する。
     * - Default / Debug: SettingsPresetValues 由来の値に置き換え
     * - Custom: 現在の値は維持しつつ、preset 種別だけ Custom にする
     */
    fun applyPreset(preset: SettingsPreset) {
        viewModelScope.launch {
            settingsRepository.applyPreset(preset)
        }
    }

    /**
     * 値は変えずにプリセット種別だけ Custom にしたい場合。
     * （ユーザーが個別項目を変更した直後に呼ぶなど）
     */
    fun setPresetCustom() {
        viewModelScope.launch {
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }
}
