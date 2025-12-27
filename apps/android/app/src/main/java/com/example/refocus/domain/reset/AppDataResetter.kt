package com.example.refocus.domain.reset

/**
 * 端末内の永続データを全消去するユースケース（抽象）。
 *
 * feature 層が app/data の具体へ依存しないための境界（port）として，domain に置く．
 */
interface AppDataResetter {
    suspend fun resetAll()
}
