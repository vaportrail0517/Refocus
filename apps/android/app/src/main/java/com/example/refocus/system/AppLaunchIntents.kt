package com.example.refocus.system

import android.content.Context
import android.content.Intent

/**
 * system レイヤから app（MainActivity）へコンパイル時依存しないための Intent 生成ヘルパ．
 *
 * - Activity クラス参照はせず，className 文字列で Intent を構築する
 * - MainActivity の移動が発生した場合は，ここだけ修正すればよい
 */
object AppLaunchIntents {

    fun mainActivity(context: Context): Intent {
        val packageName = context.packageName
        val className = "$packageName.app.MainActivity"
        return Intent().setClassName(packageName, className)
    }
}
