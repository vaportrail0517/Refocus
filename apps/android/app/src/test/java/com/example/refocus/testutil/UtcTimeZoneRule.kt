package com.example.refocus.testutil

import org.junit.rules.ExternalResource
import java.util.TimeZone

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
