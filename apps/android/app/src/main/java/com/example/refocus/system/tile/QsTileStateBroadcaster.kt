package com.example.refocus.system.tile

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import com.example.refocus.core.logging.RefocusLog

/**
 * クイック設定タイルの状態を即時更新するための通知ユーティリティ．
 *
 * - タイルパネルが開いていて RefocusTileService が listening 中の場合は Broadcast で即時反映する
 * - タイルが listening でない場合は requestListeningState で次回表示時に更新されるよう促す
 */
object QsTileStateBroadcaster {

    private const val TAG = "QsTileState"

    const val ACTION_TILE_STATE_CHANGED: String =
        "com.example.refocus.action.QS_TILE_STATE_CHANGED"

    const val EXTRA_EXPECTED_RUNNING: String = "expected_running"

    fun notifyExpectedRunning(context: Context, expectedRunning: Boolean) {
        // 1) listening 中のタイルに即時反映する
        try {
            val intent = Intent(ACTION_TILE_STATE_CHANGED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_EXPECTED_RUNNING, expectedRunning)
            }
            context.sendBroadcast(intent)
        } catch (e: Exception) {
            RefocusLog.w(TAG, e) { "Failed to broadcast QS tile state" }
        }

        // 2) listening でない場合も次回表示で更新されるよう促す
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    context,
                    ComponentName(context, RefocusTileService::class.java),
                )
            } catch (e: Exception) {
                RefocusLog.w(TAG, e) { "Failed to request QS tile refresh" }
            }
        }
    }
}
