package com.example.refocus.system.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.refocus.R
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.system.AppLaunchIntents

/**
 * keep-alive / boot など「バックグラウンドからのサービス起動が OS により禁止される」ケースで，
 * ユーザ操作により復旧できる導線を提供するための通知．
 */
class OverlayRecoveryNotificationController(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID: String = "overlay_recovery_channel"
        const val NOTIFICATION_ID: Int = 10_101

        private const val TAG: String = "OverlayRecoveryNotif"
        private const val REQUEST_CONTENT: Int = 10_102
    }

    fun notifyStartBlocked(
        source: String,
        errorSummary: String? = null,
    ) {
        ensureChannel()
        if (!canPostNotifications()) return

        val contentIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CONTENT,
                AppLaunchIntents.mainActivity(context).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val text =
            if (errorSummary.isNullOrBlank()) {
                context.getString(R.string.recovery_notification_text)
            } else {
                context.getString(R.string.recovery_notification_text_with_reason, errorSummary)
            }

        RefocusLog.w(TAG) { "notifyStartBlocked: source=$source, summary=$errorSummary" }

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_qs_refocus)
                .setContentTitle(context.getString(R.string.recovery_notification_title))
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            RefocusLog.w(TAG, e) { "Recovery notification blocked (permission missing?)" }
        }
    }

    fun cancel() {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
        } catch (e: SecurityException) {
            RefocusLog.w(TAG, e) { "Failed to cancel recovery notification" }
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 既存があれば再作成しない（ユーザが重要度を変更している可能性がある）
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return

        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.recovery_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.recovery_notification_channel_description)
            }
        nm.createNotificationChannel(channel)
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        val result =
            context.checkPermission(
                Manifest.permission.POST_NOTIFICATIONS,
                Process.myPid(),
                Process.myUid(),
            )
        return result == PackageManager.PERMISSION_GRANTED
    }
}
