package com.example.refocus.system.appinfo

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface AppLabelResolver {
    fun labelOf(packageName: String): String
}

@Singleton
class AndroidAppLabelResolver
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AppLabelResolver {
        private val pm = context.packageManager
        private val cache = ConcurrentHashMap<String, String>()

        override fun labelOf(packageName: String): String {
            cache[packageName]?.let { return it }

            val label =
                try {
                    val appInfo =
                        if (Build.VERSION.SDK_INT >= 33) {
                            pm.getApplicationInfo(
                                packageName,
                                PackageManager.ApplicationInfoFlags.of(0),
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getApplicationInfo(packageName, 0)
                        }
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: PackageManager.NameNotFoundException) {
                    packageName // アンインストール済み等はフォールバック
                }

            cache[packageName] = label
            return label
        }
    }
