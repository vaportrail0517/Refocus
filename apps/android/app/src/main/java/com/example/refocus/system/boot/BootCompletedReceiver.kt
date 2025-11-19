package com.example.refocus.system.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.refocus.feature.onboarding.OnboardingState
import com.example.refocus.feature.overlay.startOverlayService

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        Log.d("BootCompletedReceiver", "onReceive action=${intent?.action}")
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        if (!OnboardingState.isCompleted(context)) {
            Log.d("BootCompletedReceiver", "onboarding not completed → skip")
            return
        }

        Log.d("BootCompletedReceiver", "BOOT_COMPLETED → start OverlayService")
        context.startOverlayService()
    }
}
