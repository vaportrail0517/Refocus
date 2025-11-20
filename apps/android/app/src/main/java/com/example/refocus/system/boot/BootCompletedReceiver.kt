package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.refocus.feature.onboarding.OnboardingState
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.data.datastore.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("BootCompletedReceiver", "onReceive action=${intent?.action}")
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!OnboardingState.isCompleted(context)) {
            Log.d("BootCompletedReceiver", "onboarding not completed → skip")
            return
        }
        // goAsync で非同期処理を許可
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dataStore = SettingsDataStore(context)
                val settings = dataStore.settingsFlow.first()
                if (!settings.autoStartOnBoot) {
                    Log.d("BootCompletedReceiver", "autoStartOnBoot=false → skip")
                    return@launch
                }
                // 再起動時自動起動が ON の場合は、Refocus を動かすも true にして起動
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
