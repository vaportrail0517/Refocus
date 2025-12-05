package com.example.refocus.domain.suggestion

import com.example.refocus.core.model.Suggestion
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import java.time.Instant
import java.time.ZoneId

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
        val desiredDuration = desiredDurationTag(elapsedMillis)

            
        return suggestions
            .maxByOrNull { s ->
                scoreSuggestion(
                    suggestion = s,
                    currentSlot = currentSlot,
                    desiredDuration = desiredDuration,
                )
            }
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
        desiredDuration: SuggestionDurationTag,
    ): Int {
        var score = 0

        // 時間帯マッチング
        if (suggestion.timeSlot == currentSlot) {
            score += 3
        } else {
            return score
        }

        // 所要時間マッチング
//        if (suggestion.durationTag == desiredDuration) {
//            score += 1
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
