package com.example.refocus.system.overlay.keepalive

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.domain.overlay.port.OverlayKeepAliveScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OverlayKeepAliveSchedulerImpl
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : OverlayKeepAliveScheduler {
        companion object {
            private const val TAG = "OverlayKeepAliveScheduler"

            private const val UNIQUE_PERIODIC = "overlay_keepalive_periodic"
            private const val UNIQUE_ONE_SHOT = "overlay_keepalive_one_shot"

            // WorkManager の periodic は 15 分が最小
            private const val PERIODIC_MINUTES = 15L

            // 有効化直後の取りこぼしを減らすための早期チェック
            private const val ONE_SHOT_DELAY_SECONDS = 90L
        }

        override fun onOverlayEnabledChanged(enabled: Boolean) {
            val wm = WorkManager.getInstance(context)

            if (!enabled) {
                RefocusLog.d(TAG) { "cancel keep-alive works" }
                wm.cancelUniqueWork(UNIQUE_PERIODIC)
                wm.cancelUniqueWork(UNIQUE_ONE_SHOT)
                return
            }

            RefocusLog.d(TAG) { "schedule keep-alive works" }

            val periodic =
                PeriodicWorkRequestBuilder<OverlayKeepAliveWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                    .build()

            val oneShot =
                OneTimeWorkRequestBuilder<OverlayKeepAliveWorker>()
                    .setInitialDelay(ONE_SHOT_DELAY_SECONDS, TimeUnit.SECONDS)
                    .build()

            // 多重登録を避けるため Unique Work を使う
            wm.enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC,
                ExistingPeriodicWorkPolicy.REPLACE,
                periodic,
            )

            wm.enqueueUniqueWork(
                UNIQUE_ONE_SHOT,
                ExistingWorkPolicy.REPLACE,
                oneShot,
            )
        }
    }
