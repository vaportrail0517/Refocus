package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import java.time.Instant
import java.time.ZoneId
import kotlin.random.Random

/**
 * タグ（時間帯・所要時間・優先度）に基づいて、
 * 今出すべき Suggestion を 1 件選ぶためのロジック。
 */
class SuggestionSelector {

    /**
     * @param suggestions 候補一覧（空の場合は null を返す）
     * @param nowMillis 現在時刻（System.currentTimeMillis 相当）
     * @param elapsedMillis 対象アプリの連続利用時間（SuggestionEngine.Input.elapsedMillis と同じ）
     */
    fun select(
        suggestions: List<Suggestion>,
        nowMillis: Long,
        elapsedMillis: Long,
    ): Suggestion? {
        if (suggestions.isEmpty()) return null

        val currentSlot = currentTimeSlot(nowMillis)
//        val desiredDuration = desiredDurationTag(elapsedMillis)


        // 1. 各候補にスコアをつける
        val scored = suggestions.map { s ->
            s to scoreSuggestion(
                suggestion = s,
                currentSlot = currentSlot,
//                desiredDuration = desiredDuration,
            )
        }
        // 2. 最大スコアを求める
        val maxScore = scored.maxOfOrNull { it.second } ?: return null
        // 3. 最大スコアが 0 以下なら「何もマッチしていない」とみなして null を返す
        if (maxScore <= 0) {
            return null
        }
        // 4. 最大スコアと同点の候補だけを集めて、その中からランダムで 1 件選ぶ
        val best = scored.filter { (_, score) -> score == maxScore }
        if (best.isEmpty()) return null
        val index = Random.Default.nextInt(best.size)
        return best[index].first
    }

    private fun currentTimeSlot(nowMillis: Long): SuggestionTimeSlot {
        val time = Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
        return when (time.hour) {
            in 5..9 -> SuggestionTimeSlot.Morning
            in 10..16 -> SuggestionTimeSlot.Afternoon
            in 17..20 -> SuggestionTimeSlot.Evening
            else -> SuggestionTimeSlot.Night
        }
    }

    private fun desiredDurationTag(elapsedMillis: Long): SuggestionDurationTag {
        val minutes = elapsedMillis / 60_000L
        return when {
            minutes < 15L -> SuggestionDurationTag.Short
            minutes < 40L -> SuggestionDurationTag.Medium
            else -> SuggestionDurationTag.Long
        }
    }

    private fun scoreSuggestion(
        suggestion: Suggestion,
        currentSlot: SuggestionTimeSlot,
//        desiredDuration: SuggestionDurationTag,
    ): Int {
        var score = 0
        // 時間帯マッチング
        score += when (suggestion.timeSlot) {
            SuggestionTimeSlot.Anytime -> 1
            currentSlot -> 3
            else -> return score
        }
        // 所要時間マッチング
//        if (suggestion.durationTag == desiredDuration) {
//            score += 2
//        }
        // 優先度
        score += when (suggestion.priority) {
            SuggestionPriority.Low -> 1
            SuggestionPriority.Normal -> 2
            SuggestionPriority.High -> 3
        }
        return score
    }
}
