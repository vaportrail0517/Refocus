package com.example.refocus.system.boot

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.example.refocus.core.logging.RefocusLog
import java.util.concurrent.TimeUnit

/**
 * 端末再起動後の自動起動を WorkManager 経由で行うためのスケジューラ．
 *
 * - BroadcastReceiver は短時間で return する必要があるため，重い処理は Worker に寄せる
 * - OS によっては「バックグラウンドからの FGS 起動」が禁止されるため，Worker 側で復旧通知へフォールバックする
 */
object BootAutoStartScheduler {
    private const val TAG = "BootAutoStartScheduler"

    /** 多重登録を避けるため，Unique Work にする */
    private const val UNIQUE_WORK = "overlay_auto_start_on_boot"

    /** 起動直後は端末が忙しいため，少し待ってから判定・起動する */
    private const val INITIAL_DELAY_SECONDS = 90L

    /** 失敗時のバックオフ（OS による一時的な制限を吸収する） */
    private const val BACKOFF_MIN_SECONDS = 30L

    fun enqueue(
        context: Context,
        source: String,
    ) {
        val appContext = context.applicationContext
        val request =
            OneTimeWorkRequestBuilder<BootAutoStartWorker>()
                .setInputData(workDataOf(BootAutoStartWorker.KEY_SOURCE to source))
                .setInitialDelay(INITIAL_DELAY_SECONDS, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_MIN_SECONDS,
                    TimeUnit.SECONDS,
                ).build()

        RefocusLog.d(TAG) { "enqueue unique work: $UNIQUE_WORK (source=$source)" }

        WorkManager
            .getInstance(appContext)
            .enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                request,
            )
    }
}
