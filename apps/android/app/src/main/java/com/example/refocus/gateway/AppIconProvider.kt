package com.example.refocus.gateway

import android.graphics.drawable.Drawable

/**
 * UI 層からアプリのアイコンを解決するための gateway．
 *
 * - Feature 層が system 実装へ直接依存しないための境界．
 * - 返却型に Android の Drawable を含むため，domain ではなく gateway に置く．
 */
interface AppIconProvider {
    fun iconOf(packageName: String): Drawable?
}
