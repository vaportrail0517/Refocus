package com.example.refocus.domain.appinfo.port

/**
 * packageName からアプリ表示名を解決するための port．
 * Android の PackageManager などの platform 依存を domain から剥がす．
 */
interface AppLabelProvider {
    fun labelOf(packageName: String): String
}
