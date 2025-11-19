package com.example.refocus.feature.overlay

import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * OverlayService を起動する共通ヘルパー。
 * Context さえあればどこからでも呼べるようにしておく。
 */
fun Context.startOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        this.startForegroundService(intent)
    } else {
        this.startService(intent)
    }
}
