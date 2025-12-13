package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import kotlin.random.Random

/**
 * タグ（時間帯・所要時間・優先度）に基づいて、
 * 今出すべき Suggestion を 1 件選ぶためのロジック。
 * * アルゴリズム: 重み付きランダム選択 (適合度比例選択)
 */
class SuggestionSelector(
    private val timeSlotWeightModel: TimeSlotWeightModel,
    private val random: Random = Random.Default,
) {

    /**
     * @param suggestions 候補一覧（空の場合は null を返す）
     * @param nowMillis 現在時刻（System.currentTimeMillis 相当）
     * @param elapsedMillis 対象アプリの連続利用時間
     */
    fun select(
        suggestions: List<Suggestion>,
        nowMillis: Long,
        elapsedMillis: Long,
    ): Suggestion? {
        if (suggestions.isEmpty()) return null

        val minutesElapsed = elapsedMillis / 60_000L

        // 1. 各候補のスコア（重み）を計算し、候補リストを構築
        val candidatesWithWeight = suggestions.mapNotNull { suggestion ->
            val score = calculateScore(suggestion, nowMillis, minutesElapsed)
            // スコアが0以下の候補（例：時間帯不一致）は除外
            if (score > 0.0) suggestion to score else null
        }

        if (candidatesWithWeight.isEmpty()) return null

        // 2. 重み付きランダム選択 (Weighted Random Selection / ルーレット選択)

        // すべての重みの合計値を計算 (ルーレットの円周の総長さ)
        val totalWeight = candidatesWithWeight.sumOf { it.second }

        // 0 から totalWeight の間でランダムな値を取得 (ダーツを投げる位置)
        val randomValue = random.nextDouble() * totalWeight

        // 累積合計を用いて、ランダム値が該当する区間の候補を選択
        var currentSum = 0.0
        for ((suggestion, weight) in candidatesWithWeight) {
            currentSum += weight
            if (randomValue <= currentSum) {
                return suggestion
            }
        }

        // フォールバック: 処理上の問題でループを抜けてしまった場合、最後の候補を返す
        return candidatesWithWeight.last().first
    }

    /**
     * 重みスコアを計算するロジック (旧 scoreSuggestion)。
     * 適合度比例選択のため、Double型でスコアを返す。
     * * @param minutesElapsed 対象アプリの連続利用時間（分）
     */
    private fun calculateScore(
        suggestion: Suggestion,
        nowMillis: Long,
        minutesElapsed: Long
    ): Double {
        // --- 1. 時間帯のマッチング係数 ---
        val timeMultiplier = when (suggestion.timeSlot) {
            SuggestionTimeSlot.Anytime -> 1.0
            else -> {
                val w = timeSlotWeightModel.weight(suggestion.timeSlot, nowMillis) // 0..1
                if (w < MIN_TIME_WEIGHT) return 0.0
                w * 2.0 // ピーク時は旧実装の「一致=2.0」と同等
            }
        }

        // --- 2. 優先度の基礎点 (ベーススコア) ---
        val baseScore = when (suggestion.priority) {
            SuggestionPriority.High -> 50.0 // 最も基礎点を高く設定
            SuggestionPriority.Normal -> 30.0
            SuggestionPriority.Low -> 10.0
        }

        // --- 3. 所要時間による心理的ハードル係数 (フォッグの行動モデル適用) ---
        // 短いタスクは実行能力(Ability)が高いため優遇
        val frictionMultiplier = when (suggestion.durationTag) {
            // 短いタスク: 特に30分以上の溶かし時間がある場合は、脱出を促すため更に優遇 (1.5倍)
            SuggestionDurationTag.Short -> if (minutesElapsed >= 30) 1.5 else 1.2
            SuggestionDurationTag.Medium -> 1.0
            SuggestionDurationTag.Long -> 0.8 // 長いタスクは抵抗が高いため減点
        }

        // 最終スコア = (ベーススコア) × (時間帯係数) × (摩擦係数)
        return baseScore * timeMultiplier * frictionMultiplier
    }

    companion object {
        // 低すぎる適合度は「その時間帯として不自然」なので除外（重なりは残る）
        private const val MIN_TIME_WEIGHT = 0.10
    }
}