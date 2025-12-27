package com.example.refocus.testutil

import java.util.TimeZone
import org.junit.rules.ExternalResource

/**
 * JVM 単体テスト用にデフォルトタイムゾーンを UTC に固定する Rule．
 *
 * LocalDate や startOfDay の解釈が，開発環境のタイムゾーンに依存して
 * テストが不安定になるのを防ぐ．
 */
class UtcTimeZoneRule : ExternalResource() {

    private lateinit var original: TimeZone

    override fun before() {
        original = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    override fun after() {
        TimeZone.setDefault(original)
    }
}
