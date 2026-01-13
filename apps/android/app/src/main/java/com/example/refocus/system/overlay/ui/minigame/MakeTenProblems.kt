package com.example.refocus.system.overlay.ui.minigame

import android.content.Context
import com.example.refocus.R

/**
 * make ten で出題する「順序つき4整数」問題集合．
 *
 * - 値域は 1..9
 * - 重複を許可
 * - 数字の並び順は問題の一部（UI上，数字は固定表示のため）
 *
 * 注意
 * - Kotlin コードに巨大配列を直書きすると `<clinit>` が肥大化して JVM のメソッドサイズ上限に達するため，
 *   res/raw へ移し，初回アクセス時に読み込んでキャッシュする．
 */
internal object MakeTenProblems {
    private const val ARITY = 4

    @Volatile private var cachedFlat: IntArray? = null

    private fun loadFlat(context: Context): IntArray {
        val text =
            context.resources.openRawResource(R.raw.make_ten_problems).bufferedReader().use { it.readText() }
        // 各行は4桁（1..9）を想定．空白や改行は無視して数字だけを集める．
        val digits = text.filter { it in '1'..'9' }
        require(digits.length % ARITY == 0) { "Invalid problems resource length: ${digits.length}" }
        return IntArray(digits.length) { idx -> digits[idx].code - '0'.code }
    }

    private fun flat(context: Context): IntArray {
        val existing = cachedFlat
        if (existing != null) return existing
        synchronized(this) {
            val again = cachedFlat
            if (again != null) return again
            val loaded = loadFlat(context.applicationContext)
            cachedFlat = loaded
            return loaded
        }
    }

    fun size(context: Context): Int = flat(context).size / ARITY

    fun get(context: Context, index: Int): IntArray {
        val f = flat(context)
        val off = index * ARITY
        require(off + 3 < f.size) { "index out of range: $index" }
        return intArrayOf(f[off], f[off + 1], f[off + 2], f[off + 3])
    }
}
