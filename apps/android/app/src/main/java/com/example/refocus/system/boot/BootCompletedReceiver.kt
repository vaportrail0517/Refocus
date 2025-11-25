package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.refocus.data.repository.OnboardingRepository
import com.example.refocus.data.repository.SettingsRepository
import com.example.refocus.feature.overlay.startOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BootCompletedReceiver : BroadcastReceiver() {
    @Inject
    lateinit var onboardingRepository: OnboardingRepository

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val completed = onboardingRepository.completedFlow.first()
                if (!completed) {
                    Log.d("BootCompletedReceiver", "onboarding not completed → skip")
                    return@launch
                }

                val settings = settingsRepository.observeOverlaySettings().first()
                if (!settings.autoStartOnBoot) {
                    Log.d("BootCompletedReceiver", "autoStartOnBoot=false → skip")
                    return@launch
                }

                // コア権限が揃っていない場合は自動起動しない
                val hasCorePermissions = PermissionHelper.hasAllCorePermissions(context)
                if (!hasCorePermissions) {
                    Log.d(
                        "BootCompletedReceiver",
                        "missing core permissions (usage/overlay) → skip auto start"
                    )
                    // overlayEnabled を無理に true にしない
                    return@launch
                }

                // 再起動時自動起動が ON かつ 権限も揃っている場合のみ起動
                settingsRepository.setOverlayEnabled(true)
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
