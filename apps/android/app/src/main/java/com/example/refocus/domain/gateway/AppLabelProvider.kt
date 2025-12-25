package com.example.refocus.domain.gateway

/**
 * packageName からアプリ表示名を解決するための gateway．
 * Android の PackageManager などの platform 依存を domain から剥がす．
 */
interface AppLabelProvider {
    fun labelOf(packageName: String): String
}
