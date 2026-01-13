package com.example.refocus.system.notification

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.refocus.R
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.system.AppLaunchIntents
import com.example.refocus.system.overlay.OverlayService

data class OverlayNotificationUiState(
    val isTracking: Boolean,
    val trackingAppLabel: String? = null,
    val elapsedLabel: String? = null,
    val elapsedMillis: Long? = null,
    val isTimerVisible: Boolean = false,
    val touchMode: TimerTouchMode = TimerTouchMode.Drag,
)

class OverlayServiceNotificationController(
    private val context: Context,
) {
    companion object {
        const val CHANNEL_ID: String = "overlay_service_channel"
        private const val TAG: String = "OverlayServiceNotif"

        private const val REQUEST_CONTENT = 200
        private const val REQUEST_STOP = 201
        private const val REQUEST_TOGGLE_TIMER = 202
        private const val REQUEST_TOGGLE_TOUCH_MODE = 203
    }

    fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                description = context.getString(R.string.notification_channel_description)
            }
        nm.createNotificationChannel(channel)
    }

    fun notify(
        notificationId: Int,
        state: OverlayNotificationUiState,
    ) {
        ensureChannel()
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).notify(notificationId, build(state))
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS が拒否されている場合などは notify が失敗しうる．
            // Refocus は通知なしでも動作させる方針なので，例外は握りつぶす．
            RefocusLog.wRateLimited(
                "Notification",
                "post_failed",
                60_000L,
                e,
            ) { "Failed to post notification (permission missing?)" }
        }
    }

    fun cancel(notificationId: Int) {
        if (!canPostNotifications()) return
        try {
            NotificationManagerCompat.from(context).cancel(notificationId)
        } catch (e: SecurityException) {
            RefocusLog.wRateLimited(
                "Notification",
                "cancel_failed",
                60_000L,
                e,
            ) { "Failed to cancel notification (permission missing?)" }
        }
    }

    private fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        // Lint 対応: dangerous permission なので，呼び出し前に明示的にチェックする．
        val result =
            context.checkPermission(
                Manifest.permission.POST_NOTIFICATIONS,
                Process.myPid(),
                Process.myUid(),
            )
        return result == PackageManager.PERMISSION_GRANTED
    }

    fun build(state: OverlayNotificationUiState): Notification {
        val contentIntent =
            PendingIntent.getActivity(
                context,
                REQUEST_CONTENT,
                AppLaunchIntents.mainActivity(context).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(contentIntent)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)

        if (!state.isTracking) {
            builder
                .setContentTitle(context.getString(R.string.notification_title_running))
                .setContentText(context.getString(R.string.notification_text_waiting))
        } else {
            val appLabel = state.trackingAppLabel ?: ""
            val elapsed = state.elapsedLabel ?: ""
            val elapsedMillis = state.elapsedMillis ?: 0L

            builder
                .setContentTitle(context.getString(R.string.notification_title_tracking, appLabel))
                .setContentText(context.getString(R.string.notification_text_elapsed, elapsed))

            // 通知を「開いている間（展開中）」だけ秒単位にしたい場合，
            // bigContentView に Chronometer を置くのが最も安定する．
            // （通知シェードが開かれたかどうか自体はアプリ側から確実には検出できない）
            val bigView =
                RemoteViews(context.packageName, R.layout.notification_overlay_expanded).apply {
                    // elapsedMillis をベースに，秒単位のカウントアップを OS に任せる
                    setChronometer(
                        R.id.notif_chronometer,
                        SystemClock.elapsedRealtime() - elapsedMillis,
                        context.getString(R.string.notification_text_elapsed, "%s"),
                        true,
                    )

                    if (state.isTimerVisible) {
                        setTextViewText(R.id.notif_line1, context.getString(R.string.notification_line_overlay_visible))
                        val touchLine =
                            when (state.touchMode) {
                                TimerTouchMode.Drag -> context.getString(R.string.notification_line_touch_mode_drag)
                                TimerTouchMode.PassThrough ->
                                    context.getString(R.string.notification_line_touch_mode_passthrough)
                            }
                        setViewVisibility(R.id.notif_line2, View.VISIBLE)
                        setTextViewText(R.id.notif_line2, touchLine)
                    } else {
                        setTextViewText(R.id.notif_line1, context.getString(R.string.notification_line_overlay_hidden))
                        setTextViewText(R.id.notif_line2, "")
                    }
                }

            builder
                .setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(bigView)
        }

        // 対象アプリ計測中は「停止」を出さない．
        // 対象アプリに集中している最中に誤タップで止める事故を防ぐ意図もある．
        if (!state.isTracking) {
            builder.addAction(
                0,
                context.getString(R.string.notification_action_stop),
                servicePendingIntent(OverlayService.ACTION_STOP, REQUEST_STOP),
            )
        }

        if (state.isTracking) {
            val toggleTimerLabel =
                if (state.isTimerVisible) {
                    context.getString(R.string.notification_action_hide_timer)
                } else {
                    context.getString(R.string.notification_action_show_timer)
                }
            builder.addAction(
                0,
                toggleTimerLabel,
                servicePendingIntent(
                    OverlayService.ACTION_TOGGLE_TIMER_VISIBILITY,
                    REQUEST_TOGGLE_TIMER,
                ),
            )

            if (state.isTimerVisible) {
                builder.addAction(
                    0,
                    context.getString(R.string.notification_action_toggle_touch_mode),
                    servicePendingIntent(
                        OverlayService.ACTION_TOGGLE_TOUCH_MODE,
                        REQUEST_TOGGLE_TOUCH_MODE,
                    ),
                )
            }
        }

        return builder.build()
    }

    private fun servicePendingIntent(
        action: String,
        requestCode: Int,
    ): PendingIntent {
        val intent =
            Intent(context, OverlayService::class.java).apply {
                this.action = action
            }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
