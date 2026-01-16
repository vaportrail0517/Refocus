package com.example.refocus.system.overlay

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.widget.Toast
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.core.model.SuggestionMode
import com.example.refocus.system.overlay.ui.SuggestionOverlay
import com.example.refocus.ui.theme.RefocusTheme
import java.util.concurrent.atomic.AtomicBoolean

class SuggestionOverlayController(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
) {
    private val windowManager =
        context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var suggestionView: View? = null

    private fun resolveAppLabel(packageName: String): String {
        if (packageName.isBlank()) return "このアプリ"
        return try {
            val appInfo =
                if (Build.VERSION.SDK_INT >= 33) {
                    context.packageManager.getApplicationInfo(
                        packageName,
                        PackageManager.ApplicationInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getApplicationInfo(packageName, 0)
                }
            val label = context.packageManager.getApplicationLabel(appInfo).toString()
            if (label.isBlank()) "このアプリ" else label
        } catch (_: Exception) {
            "このアプリ"
        }
    }

    fun showSuggestionOverlay(
        title: String,
        targetPackageName: String,
        mode: SuggestionMode,
        action: SuggestionAction,
        autoDismissMillis: Long,
        interactionLockoutMillis: Long,
        onOpenAction: () -> Unit,
        onSnoozeLater: () -> Unit,
        onCloseTargetApp: () -> Unit,
        onDismissOnly: () -> Unit,
    ): Boolean {
        if (suggestionView != null) {
            RefocusLog.d("SuggestionOverlay") { "showSuggestionOverlay: already showing" }
            return true
        }

        val callbackFired = AtomicBoolean(false)

        fun runOnce(block: () -> Unit) {
            if (callbackFired.compareAndSet(false, true)) {
                block()
            }
        }

        val params =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

        val composeView =
            ComposeView(context).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                val savedStateOwner = OverlaySavedStateOwner()
                setViewTreeSavedStateRegistryOwner(savedStateOwner)
                setContent {
                    RefocusTheme {
                        SuggestionOverlay(
                            title = title,
                            targetAppLabel = resolveAppLabel(targetPackageName),
                            mode = mode,
                            action = action,
                            autoDismissMillis = autoDismissMillis,
                            interactionLockoutMillis = interactionLockoutMillis,
                            onOpenAction = {
                                runOnce {
                                    hideSuggestionOverlay()
                                    onOpenAction()
                                    openAction(action)
                                }
                            },
                            onSnoozeLater = {
                                runOnce {
                                    hideSuggestionOverlay()
                                    onSnoozeLater()
                                }
                            },
                            onCloseTargetApp = {
                                runOnce {
                                    hideSuggestionOverlay()
                                    onCloseTargetApp()
                                    navigateToHome()
                                }
                            },
                            onDismissOnly = {
                                runOnce {
                                    hideSuggestionOverlay()
                                    onDismissOnly()
                                }
                            },
                        )
                    }
                }
            }

        try {
            windowManager.addView(composeView, params)
            suggestionView = composeView
            return true
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "showSuggestionOverlay: addView failed" }
            suggestionView = null
            return false
        }
    }

    private fun openAction(action: SuggestionAction) {
        when (action) {
            SuggestionAction.None -> return

            is SuggestionAction.Url -> openUrl(action.url)

            is SuggestionAction.App -> openApp(action.packageName)
        }
    }

    private fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null) {
            toast("開けませんでした")
            return
        }
        val intent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "openUrl: startActivity failed url=$url" }
            toast("開けませんでした")
        }
    }

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            RefocusLog.w("SuggestionOverlay") { "openApp: launch intent is null package=$packageName" }
            toast("開けませんでした")
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "openApp: startActivity failed package=$packageName" }
            toast("開けませんでした")
        }
    }

    private fun toast(message: String) {
        runCatching {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun navigateToHome() {
        val intent =
            Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_HOME)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "navigateToHome: startActivity failed" }
        }
    }

    fun hideSuggestionOverlay() {
        val view = suggestionView ?: return
        try {
            if (view is ComposeView) {
                view.setContent { }
            }
            windowManager.removeView(view)
        } catch (e: Exception) {
            RefocusLog.e("SuggestionOverlay", e) { "hideSuggestionOverlay: removeView failed" }
        } finally {
            suggestionView = null
        }
    }
}
