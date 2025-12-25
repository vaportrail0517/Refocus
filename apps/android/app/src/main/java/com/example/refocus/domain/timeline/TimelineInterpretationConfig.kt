package com.example.refocus.domain.timeline

/**
 * タイムラインを「現在の解釈」で再構成するときに必要なパラメータの集合。
 *
 * 重要: Refocus の思想として「設定を変えたら過去も含めて再投影し直す」ため，
 * 設定値はプロジェクタ呼び出しの引数として明示的に渡す。
 */
data class TimelineInterpretationConfig(
    val stopGracePeriodMillis: Long,
)
