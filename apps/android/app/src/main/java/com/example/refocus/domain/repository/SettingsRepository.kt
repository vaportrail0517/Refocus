package com.example.refocus.domain.repository

import com.example.refocus.core.model.Customize
import com.example.refocus.core.model.CustomizePreset
import kotlinx.coroutines.flow.Flow

/**
 * 設定の永続化と購読の抽象。
 *
 * domain 層は data 層の実装（DataStore 等）を知らないため，
 * ここにインタフェースを定義し，実装は data 層に置く。
 */
interface SettingsRepository {
    fun observeOverlaySettings(): Flow<Customize>

    fun observeSettingsPreset(): Flow<CustomizePreset>

    suspend fun updateOverlaySettings(transform: (Customize) -> Customize)

    suspend fun setSettingsPreset(preset: CustomizePreset)

    suspend fun applyPreset(preset: CustomizePreset)

    suspend fun setOverlayEnabled(enabled: Boolean)

    suspend fun setAutoStartOnBoot(enabled: Boolean)

    suspend fun setSuggestionEnabled(enabled: Boolean)

    suspend fun setSuggestionTriggerSeconds(seconds: Int)

    suspend fun setSuggestionTimeoutSeconds(seconds: Int)

    suspend fun setSuggestionCooldownSeconds(seconds: Int)

    suspend fun setSuggestionForegroundStableSeconds(seconds: Int)

    suspend fun setRestSuggestionEnabled(enabled: Boolean)

    suspend fun resetToDefaults()
}
