package com.example.refocus.system.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.refocus.R
import com.example.refocus.app.MainActivity
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.system.overlay.OverlayService

data class OverlayNotificationUiState(
    val isTracking: Boolean,
    val trackingAppLabel: String? = null,
    val elapsedLabel: String? = null,
    val isTimerVisible: Boolean = false,
    val touchMode: TimerTouchMode = TimerTouchMode.Drag,
)

class OverlayServiceNotificationController(
    private val context: Context,
) {

    companion object {
        const val CHANNEL_ID: String = "overlay_service_channel"

        private const val REQUEST_CONTENT = 200
        private const val REQUEST_STOP = 201
        private const val REQUEST_TOGGLE_TIMER = 202
        private const val REQUEST_TOGGLE_TOUCH_MODE = 203
    }

    fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.notification_channel_description)
        }
        nm.createNotificationChannel(channel)
    }

    fun notify(notificationId: Int, state: OverlayNotificationUiState) {
        ensureChannel()
        NotificationManagerCompat.from(context).notify(notificationId, build(state))
    }

    fun build(state: OverlayNotificationUiState): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQUEST_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
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

            builder
                .setContentTitle(context.getString(R.string.notification_title_tracking, appLabel))
                .setContentText(context.getString(R.string.notification_text_elapsed, elapsed))

            val lines = mutableListOf<String>()
            lines += context.getString(R.string.notification_text_elapsed, elapsed)

            if (state.isTimerVisible) {
                lines += context.getString(R.string.notification_line_overlay_visible)
                val touchLine = when (state.touchMode) {
                    TimerTouchMode.Drag -> context.getString(R.string.notification_line_touch_mode_drag)
                    TimerTouchMode.PassThrough -> context.getString(R.string.notification_line_touch_mode_passthrough)
                }
                lines += touchLine
            } else {
                lines += context.getString(R.string.notification_line_overlay_hidden)
            }

            val style = NotificationCompat.InboxStyle()
            lines.forEach { style.addLine(it) }
            builder.setStyle(style)
        }

        builder.addAction(
            0,
            context.getString(R.string.notification_action_stop),
            servicePendingIntent(OverlayService.ACTION_STOP, REQUEST_STOP)
        )

        if (state.isTracking) {
            val toggleTimerLabel = if (state.isTimerVisible) {
                context.getString(R.string.notification_action_hide_timer)
            } else {
                context.getString(R.string.notification_action_show_timer)
            }
            builder.addAction(
                0,
                toggleTimerLabel,
                servicePendingIntent(OverlayService.ACTION_TOGGLE_TIMER_VISIBILITY, REQUEST_TOGGLE_TIMER)
            )

            if (state.isTimerVisible) {
                builder.addAction(
                    0,
                    context.getString(R.string.notification_action_toggle_touch_mode),
                    servicePendingIntent(OverlayService.ACTION_TOGGLE_TOUCH_MODE, REQUEST_TOGGLE_TOUCH_MODE)
                )
            }
        }

        return builder.build()
    }

    private fun servicePendingIntent(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, OverlayService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
