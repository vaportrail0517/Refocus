package com.example.refocus.feature.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.refocus.core.model.OverlayTouchMode
import com.example.refocus.core.model.Settings
import com.example.refocus.core.model.SettingsConfig.FontPreset
import com.example.refocus.core.model.SettingsConfig.GracePreset
import com.example.refocus.core.model.SettingsConfig.SuggestionCooldownPreset
import com.example.refocus.core.model.SettingsConfig.SuggestionTriggerPreset
import com.example.refocus.core.model.SettingsConfig.TimeToMaxPreset
import com.example.refocus.core.model.SettingsConfig.withFontPreset
import com.example.refocus.core.model.SettingsConfig.withGracePreset
import com.example.refocus.core.model.SettingsConfig.withSuggestionCooldownPreset
import com.example.refocus.core.model.SettingsConfig.withSuggestionTriggerPreset
import com.example.refocus.core.model.SettingsConfig.withTimeToMaxPreset
import com.example.refocus.core.model.SettingsPreset
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
        val isLoading: Boolean = true
    )

    private val _uiState = MutableStateFlow(
        UiState(
            settings = Settings(),
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
                    settings = settings,
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

    fun updateSuggestionTriggerSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionTriggerSeconds = seconds)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateSuggestionForegroundStableSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionForegroundStableSeconds = seconds)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateSuggestionCooldownSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionCooldownSeconds = seconds)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateSuggestionTimeoutSeconds(seconds: Int) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionTimeoutSeconds = seconds)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    fun updateSuggestionInteractionLockoutMillis(millis: Long) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.copy(suggestionInteractionLockoutMillis = millis)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    // --- ここからプリセット用の helper（数字は SettingsConfig に集約） ---

    /** タイマーの文字サイズプリセットを適用（Small / Medium / Large） */
    fun applyFontPreset(preset: FontPreset) {
        viewModelScope.launch {
            settingsRepository.updateOverlaySettings { current ->
                current.withFontPreset(preset)
            }
            settingsRepository.setSettingsPreset(SettingsPreset.Custom)
        }
    }

    /** タイマーが最大サイズになるまでの時間プリセットを適用（遅め / ふつう / 早め） */
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

    /** 提案トリガ時間プリセットを適用（10/15/30） */
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
