package com.example.refocus.system.overlay.service

import android.content.Context
import android.content.Intent

fun Context.startOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    this.startForegroundService(intent)
}

fun Context.stopOverlayService() {
    val intent = Intent(this, OverlayService::class.java)
    stopService(intent)
}