package com.example.refocus.core.util

fun String.displayLength(): Double {
    var length = 0.0

    for (c in this) {
        length += when (// ASCII (英数字・基本的な記号)
            c.code) {
            in 0x0000..0x007F -> 0.5

            // 半角カナ
            in 0xFF61..0xFF9F -> 0.5

            // それ以外（ひらがな・カタカナ・漢字など）は全角とみなす
            else -> 1.0
        }
    }
    return length
}
