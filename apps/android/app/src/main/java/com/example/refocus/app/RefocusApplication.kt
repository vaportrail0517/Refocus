package com.example.refocus.app

import android.app.Application
import com.example.refocus.core.logging.RefocusLog
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class RefocusApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        RefocusLog.init(this)
    }
}
