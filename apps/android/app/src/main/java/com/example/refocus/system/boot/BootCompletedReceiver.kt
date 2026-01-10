package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.refocus.core.logging.RefocusLog

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent?,
    ) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        // BOOT_COMPLETED は短時間で return する必要があるため，WorkManager に委譲する．
        // Android 15+ ではバックグラウンドからの FGS 起動が禁止される場合があるため，
        // Worker 側で例外分類し，必要なら復旧通知へフォールバックする．
        RefocusLog.d("Boot") { "BOOT_COMPLETED → enqueue BootAutoStartWorker" }
        BootAutoStartScheduler.enqueue(context, source = "boot_completed")
    }
}
