package com.example.refocus.system.boot

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.repository.OnboardingRepository
import com.example.refocus.domain.repository.SettingsRepository
import com.example.refocus.system.notification.OverlayRecoveryNotificationController
import com.example.refocus.system.overlay.isForegroundServiceStartNotAllowedError
import com.example.refocus.system.overlay.startOverlayService
import com.example.refocus.system.permissions.PermissionHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

/**
 * 端末再起動後の自動起動を担当する Worker．
 *
 * - 設定（autoStartOnBoot / overlayEnabled）と必須権限を確認してから起動を試みる
 * - OS によってはバックグラウンドからの FGS 起動が禁止されるため，
 *   その場合は復旧通知にフォールバックする
 */
class BootAutoStartWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    companion object {
        private const val TAG = "BootAutoStartWorker"

        const val KEY_SOURCE: String = "source"

        private const val ERROR_SUMMARY_MAX = 160
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WorkerEntryPoint {
        fun onboardingRepository(): OnboardingRepository

        fun settingsRepository(): SettingsRepository
    }

    override suspend fun doWork(): Result {
        val appContext = applicationContext
        val source = inputData.getString(KEY_SOURCE) ?: "boot_auto_start"
        val recoveryNotifier = OverlayRecoveryNotificationController(appContext)

        val entryPoint =
            EntryPointAccessors.fromApplication(
                appContext,
                WorkerEntryPoint::class.java,
            )

        return try {
            val completed = entryPoint.onboardingRepository().completedFlow.first()
            if (!completed) {
                RefocusLog.d(TAG) { "onboarding not completed -> skip (source=$source)" }
                recoveryNotifier.cancel()
                return Result.success()
            }

            val settings = entryPoint.settingsRepository().observeOverlaySettings().first()

            if (!settings.autoStartOnBoot) {
                RefocusLog.d(TAG) { "autoStartOnBoot=false -> skip (source=$source)" }
                recoveryNotifier.cancel()
                return Result.success()
            }

            // ユーザが OFF にしている場合は，端末再起動で勝手に起動しない
            if (!settings.overlayEnabled) {
                RefocusLog.d(TAG) { "overlayEnabled=false -> skip (source=$source)" }
                recoveryNotifier.cancel()
                return Result.success()
            }

            // コア権限が揃っていない場合は自動起動しない
            if (!PermissionHelper.hasAllCorePermissions(appContext)) {
                RefocusLog.d(TAG) { "missing core permissions -> skip (source=$source)" }
                recoveryNotifier.cancel()
                return Result.success()
            }

            RefocusLog.d(TAG) { "try startOverlayService (source=$source)" }
            try {
                appContext.startOverlayService(source = source)
                recoveryNotifier.cancel()
                Result.success()
            } catch (e: Exception) {
                val blocked = isForegroundServiceStartNotAllowedError(e)
                val summary = summarizeError(e)

                if (blocked) {
                    RefocusLog.w(TAG, e) {
                        "Start overlay service not allowed right now -> show recovery notification"
                    }
                    recoveryNotifier.notifyStartBlocked(source = source, errorSummary = summary)
                    Result.success()
                } else {
                    RefocusLog.e(TAG, e) { "Failed to start overlay service -> retry" }
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "BootAutoStartWorker failed unexpectedly -> retry" }
            Result.retry()
        }
    }

    private fun summarizeError(e: Throwable): String {
        val name = e::class.java.simpleName
        val msg = e.message
        val raw = if (msg.isNullOrBlank()) name else "$name: $msg"
        return if (raw.length <= ERROR_SUMMARY_MAX) raw else raw.take(ERROR_SUMMARY_MAX)
    }
}
