package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.refocus.feature.onboarding.OnboardingState
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.data.datastore.SettingsDataStore
import com.example.refocus.data.repository.OnboardingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val onboardingRepository = OnboardingRepository(context)
                val completed = onboardingRepository.completedFlow.first()
                if (!completed) {
                    Log.d("BootCompletedReceiver", "onboarding not completed → skip")
                    return@launch
                }

                val dataStore = SettingsDataStore(context)
                val settings = dataStore.settingsFlow.first()
                if (!settings.autoStartOnBoot) {
                    Log.d("BootCompletedReceiver", "autoStartOnBoot=false → skip")
                    return@launch
                }

                // 再起動時自動起動が ON の場合は、overlayEnabled も true にして起動
                dataStore.update { current ->
                    current.copy(overlayEnabled = true)
                }
                Log.d("BootCompletedReceiver", "BOOT_COMPLETED → start OverlayService")
                context.startOverlayService()
            } catch (e: Exception) {
                Log.e("BootCompletedReceiver", "error in BOOT_COMPLETED handling", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
