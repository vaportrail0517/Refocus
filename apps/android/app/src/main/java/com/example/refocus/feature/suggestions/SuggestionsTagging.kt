package com.example.refocus.feature.suggestions

import androidx.compose.runtime.saveable.listSaver
import com.example.refocus.core.model.SuggestionTimeSlot

internal val TimeSlotsStateSaver = listSaver<Set<SuggestionTimeSlot>, String>(
    save = { slots ->
        // 空→Anytime / Anytimeは排他，の正規化を保存時にも適用
        normalizeTimeSlots(slots).map { it.name }
    },
    restore = { names ->
        val parsed = names
            .mapNotNull { runCatching { SuggestionTimeSlot.valueOf(it) }.getOrNull() }
            .toSet()
        normalizeTimeSlots(parsed)
    }
)

internal fun slotOrder(): List<SuggestionTimeSlot> = listOf(
    SuggestionTimeSlot.Anytime,
    SuggestionTimeSlot.Dawn,
    SuggestionTimeSlot.Morning,
    SuggestionTimeSlot.Noon,
    SuggestionTimeSlot.Afternoon,
    SuggestionTimeSlot.Evening,
    SuggestionTimeSlot.Night,
    SuggestionTimeSlot.LateNight,
)

internal fun SuggestionTimeSlot.labelJa(): String = when (this) {
    SuggestionTimeSlot.Anytime -> "いつでも"
    SuggestionTimeSlot.Dawn -> "早朝"
    SuggestionTimeSlot.Morning -> "午前"
    SuggestionTimeSlot.Noon -> "昼"
    SuggestionTimeSlot.Afternoon -> "午後"
    SuggestionTimeSlot.Evening -> "夕方"
    SuggestionTimeSlot.Night -> "夜"
    SuggestionTimeSlot.LateNight -> "深夜"
}

internal fun SuggestionTimeSlot.hintJa(): String = when (this) {
    SuggestionTimeSlot.Anytime -> "いつでも"
    SuggestionTimeSlot.Dawn -> "4〜7時ごろ"
    SuggestionTimeSlot.Morning -> "8〜11時ごろ"
    SuggestionTimeSlot.Noon -> "11〜14時ごろ"
    SuggestionTimeSlot.Afternoon -> "13〜17時ごろ"
    SuggestionTimeSlot.Evening -> "17〜19時ごろ"
    SuggestionTimeSlot.Night -> "19〜23時ごろ"
    SuggestionTimeSlot.LateNight -> "23〜3時ごろ"
}

internal fun normalizeTimeSlots(slots: Set<SuggestionTimeSlot>): Set<SuggestionTimeSlot> {
    if (slots.isEmpty()) return setOf(SuggestionTimeSlot.Anytime)
    if (slots.contains(SuggestionTimeSlot.Anytime)) return setOf(SuggestionTimeSlot.Anytime)
    return slots
}

internal fun toggleTimeSlots(
    current: Set<SuggestionTimeSlot>,
    tapped: SuggestionTimeSlot,
): Set<SuggestionTimeSlot> {
    val normalized = normalizeTimeSlots(current)
    return if (tapped == SuggestionTimeSlot.Anytime) {
        setOf(SuggestionTimeSlot.Anytime)
    } else {
        val base = normalized - SuggestionTimeSlot.Anytime
        val next = if (base.contains(tapped)) base - tapped else base + tapped
        normalizeTimeSlots(next)
    }
}
