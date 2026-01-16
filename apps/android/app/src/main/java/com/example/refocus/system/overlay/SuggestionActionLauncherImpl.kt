package com.example.refocus.system.overlay

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.example.refocus.core.logging.RefocusLog
import com.example.refocus.core.model.SuggestionAction
import com.example.refocus.domain.overlay.port.SuggestionActionLauncherPort

/**
 * SuggestionAction を Android の Intent として実行する system 側実装。
 *
 * - 起動処理はメインスレッドで行う（OverlayService の scope は Default が多いため）。
 * - 失敗しても落とさず，短い Toast で通知する。
 */
class SuggestionActionLauncherImpl(
    private val context: Context,
) : SuggestionActionLauncherPort {
    companion object {
        private const val TAG = "SuggestionActionLauncher"
        private const val FALLBACK_TOAST_MESSAGE = "開けませんでした"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun launch(action: SuggestionAction) {
        if (action is SuggestionAction.None) return

        // startActivity / Toast はメインスレッドで実行する
        mainHandler.post {
            when (action) {
                SuggestionAction.None -> Unit

                is SuggestionAction.Url -> openUrl(action.url)

                is SuggestionAction.App -> openApp(action.packageName)
            }
        }
    }

    private fun openUrl(url: String) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        if (uri == null) {
            toast(FALLBACK_TOAST_MESSAGE)
            return
        }

        // 念のため http/https 以外は開かない（DB 破損や将来の拡張に備える）
        val scheme = (uri.scheme ?: "").lowercase()
        if (scheme != "http" && scheme != "https") {
            RefocusLog.w(TAG) { "openUrl: rejected scheme=$scheme url=$url" }
            toast(FALLBACK_TOAST_MESSAGE)
            return
        }

        val intent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "openUrl: startActivity failed url=$url" }
            toast(FALLBACK_TOAST_MESSAGE)
        }
    }

    private fun openApp(packageName: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            RefocusLog.w(TAG) { "openApp: launch intent is null package=$packageName" }
            toast(FALLBACK_TOAST_MESSAGE)
            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            RefocusLog.e(TAG, e) { "openApp: startActivity failed package=$packageName" }
            toast(FALLBACK_TOAST_MESSAGE)
        }
    }

    private fun toast(message: String) {
        runCatching {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
}
