package com.example.refocus.system.appinfo

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

interface AppIconResolver {
    fun iconOf(packageName: String): Drawable?
}

@Singleton
class AndroidAppIconResolver @Inject constructor(
    @ApplicationContext context: Context,
) : AppIconResolver {

    private val pm = context.packageManager
    private val cache = ConcurrentHashMap<String, Drawable?>()

    override fun iconOf(packageName: String): Drawable? {
        cache[packageName]?.let { return it }

        val icon = try {
            pm.getApplicationIcon(packageName)
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Throwable) {
            null
        }

        cache[packageName] = icon
        return icon
    }
}
