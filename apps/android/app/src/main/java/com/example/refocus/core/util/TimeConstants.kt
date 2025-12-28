package com.example.refocus.core.util

/**
 * 時間計算用の定数（ミリ秒）．
 *
 * Duration を導入していない箇所でも，マジックナンバーの増殖を防ぐために使用する．
 */
const val MILLIS_PER_SECOND: Long = 1_000L
const val MILLIS_PER_MINUTE: Long = 60L * MILLIS_PER_SECOND
const val MILLIS_PER_HOUR: Long = 60L * MILLIS_PER_MINUTE
