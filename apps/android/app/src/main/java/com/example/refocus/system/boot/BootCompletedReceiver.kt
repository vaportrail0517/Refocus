package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.system.notification.OverlayRecoveryNotificationController
import com.example.refocus.system.overlay.isForegroundServiceStartNotAllowedError
import com.example.refocus.system.overlay.startOverlayService
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

    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            val recoveryNotifier = OverlayRecoveryNotificationController(context)
            try {
                val completed = onboardingRepository.completedFlow.first()
                if (!completed) {
                    RefocusLog.d("Boot") { "onboarding not completed → skip" }
                    recoveryNotifier.cancel()
                    return@launch
                }

                val settings = settingsRepository.observeOverlaySettings().first()

                if (!settings.autoStartOnBoot) {
                    RefocusLog.d("Boot") { "autoStartOnBoot=false → skip" }
                    recoveryNotifier.cancel()
                    return@launch
                }

                // ユーザが OFF にしている場合は，端末再起動で勝手に起動しない
                if (!settings.overlayEnabled) {
                    RefocusLog.d("Boot") { "overlayEnabled=false → skip auto start" }
                    recoveryNotifier.cancel()
                    return@launch
                }

                // コア権限が揃っていない場合は自動起動しない
                val hasCorePermissions = PermissionHelper.hasAllCorePermissions(context)
                if (!hasCorePermissions) {
                    RefocusLog.d("Boot") { "missing core permissions (usage/overlay) → skip auto start" }
                    recoveryNotifier.cancel()
                    return@launch
                }

                // 再起動時自動起動が ON かつ overlayEnabled=true かつ 権限も揃っている場合のみ起動
                RefocusLog.d("Boot") { "BOOT_COMPLETED → start OverlayService" }
                try {
                    context.startOverlayService(source = "boot_completed")
                    // 以前に復旧通知が出ていた場合は消す
                    recoveryNotifier.cancel()
                } catch (e: Exception) {
                    val blocked = isForegroundServiceStartNotAllowedError(e)
                    val summary = summarizeError(e)
                    if (blocked) {
                        recoveryNotifier.notifyStartBlocked(
                            source = "boot_completed",
                            errorSummary = summary,
                        )
                    }
                    RefocusLog.w("Boot", e) { "Failed to start OverlayService on boot. blocked=$blocked" }
                }
            } catch (e: Exception) {
                RefocusLog.e("Boot", e) { "error in BOOT_COMPLETED handling" }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

private fun summarizeError(e: Throwable): String {
    val name = e::class.java.simpleName
    val msg = e.message
    val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
    return if (raw.length <= 160) raw else raw.take(160)
}
