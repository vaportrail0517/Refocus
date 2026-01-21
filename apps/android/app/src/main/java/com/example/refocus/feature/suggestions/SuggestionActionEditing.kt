package com.example.refocus.feature.suggestions

import android.net.Uri
import com.example.refocus.core.model.SuggestionAction

internal data class NormalizedUrl(
    val url: String,
    val display: String?,
)

/**
 * URL 入力を「http(s) のみ許可，scheme 無しは https を補完」というルールで正規化する．
 * 正規化できない場合は null を返す．
 */
internal fun normalizeHttpUrlOrNull(raw: String): NormalizedUrl? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null

    val withScheme =
        if (trimmed.contains("://")) {
            trimmed
        } else {
            "https://$trimmed"
        }

    val uri = runCatching { Uri.parse(withScheme) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null

    val host = uri.host?.takeIf { it.isNotBlank() } ?: return null

    // Uri.parse は入力をそのまま保持することが多いので，ここでは withScheme を採用する．
    return NormalizedUrl(url = withScheme, display = host)
}

internal fun buildSuggestionActionForSave(
    kind: SuggestionActionKind,
    value: String,
    display: String,
): SuggestionAction =
    when (kind) {
        SuggestionActionKind.None -> SuggestionAction.None
        SuggestionActionKind.Url -> {
            val normalized = normalizeHttpUrlOrNull(value)
            if (normalized != null) {
                SuggestionAction.Url(url = normalized.url, display = normalized.display)
            } else {
                SuggestionAction.None
            }
        }

        SuggestionActionKind.App -> {
            val packageName = value.trim()
            if (packageName.isNotEmpty()) {
                SuggestionAction.App(
                    packageName = packageName,
                    display = display.trim().ifBlank { null },
                )
            } else {
                SuggestionAction.None
            }
        }
    }

internal fun actionSummaryForDisplay(action: SuggestionAction): String? =
    when (action) {
        SuggestionAction.None -> null
        is SuggestionAction.Url -> {
            val label =
                action.display
                    ?.takeIf { it.isNotBlank() }
                    ?: runCatching { Uri.parse(action.url).host }.getOrNull()
                    ?: "リンク"
            "リンク: $label"
        }

        is SuggestionAction.App -> {
            val label =
                action.display
                    ?.takeIf { it.isNotBlank() }
                    ?: action.packageName
            "アプリ: $label"
        }
    }
