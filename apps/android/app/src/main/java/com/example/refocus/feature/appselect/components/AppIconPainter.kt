package com.example.refocus.feature.appselect.components

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.max

/**
 * アイコン描画のメモリ・速度を改善するための軽量キャッシュ．
 *
 * - Drawable をそのまま保持せず，表示サイズへ縮小した ImageBitmap をキャッシュする
 * - 画面を跨いでも再利用できる（AppSelect と HiddenApps で共有）
 */
private object AppIconBitmapCache {
    // 40dp程度の小さいビットマップを前提に，エントリ数ベースで上限を持つ
    private const val MAX_ENTRIES = 256
    private val cache = LruCache<String, ImageBitmap>(MAX_ENTRIES)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(
        key: String,
        value: ImageBitmap,
    ) {
        cache.put(key, value)
    }
}

@Composable
fun rememberAppIconPainter(
    packageName: String,
    icon: Drawable?,
    size: Dp = 40.dp,
): Painter? {
    if (icon == null) return null

    val density = LocalDensity.current
    val sizePx = remember(density, size) { max(1, with(density) { size.roundToPx() }) }
    val key = remember(packageName, sizePx) { "$packageName@$sizePx" }

    val imageBitmap =
        remember(key) {
            AppIconBitmapCache.get(key)
                ?: run {
                    val bmp = icon.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888)
                    val img = bmp.asImageBitmap()
                    AppIconBitmapCache.put(key, img)
                    img
                }
        }

    return remember(imageBitmap) { BitmapPainter(imageBitmap) }
}
