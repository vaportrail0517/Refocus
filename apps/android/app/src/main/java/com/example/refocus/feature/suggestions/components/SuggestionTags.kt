package com.example.refocus.feature.suggestions.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.refocus.core.model.SuggestionDurationTag
import com.example.refocus.core.model.SuggestionPriority
import com.example.refocus.core.model.SuggestionTimeSlot
import com.example.refocus.feature.suggestions.hintJa
import com.example.refocus.feature.suggestions.labelJa
import com.example.refocus.feature.suggestions.normalizeTimeSlots
import com.example.refocus.feature.suggestions.slotOrder

@Composable
internal fun SuggestionTagChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SuggestionTagsRow(
    timeSlots: Set<SuggestionTimeSlot>,
    durationTag: SuggestionDurationTag,
    priority: SuggestionPriority,
    interactive: Boolean,
    onToggleTimeSlot: (SuggestionTimeSlot) -> Unit,
    onDurationTagChange: (SuggestionDurationTag) -> Unit,
    onPriorityChange: (SuggestionPriority) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column {
            val normalized = normalizeTimeSlots(timeSlots)
            val hintText =
                if (normalized == setOf(SuggestionTimeSlot.Anytime)) {
                    "いつでも"
                } else {
                    normalized
                        .sortedBy { slotOrder().indexOf(it) }
                        .joinToString(" / ") { "${it.labelJa()}（${it.hintJa()}）" }
                }
            Text(
                text = "時間帯: $hintText",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val slots = normalizeTimeSlots(timeSlots)
                slotOrder().forEach { slot ->
                    SuggestionTagChip(
                        label = slot.labelJa(),
                        selected = slots.contains(slot),
                        enabled = interactive,
                        onClick = { onToggleTimeSlot(slot) },
                    )
                }
            }
        }

        Column {
            Text(
                text = "所要時間",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionTagChip(
                    label = "短め",
                    selected = durationTag == SuggestionDurationTag.Short,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Short) },
                )
                SuggestionTagChip(
                    label = "ふつう",
                    selected = durationTag == SuggestionDurationTag.Medium,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Medium) },
                )
                SuggestionTagChip(
                    label = "じっくり",
                    selected = durationTag == SuggestionDurationTag.Long,
                    enabled = interactive,
                    onClick = { onDurationTagChange(SuggestionDurationTag.Long) },
                )
            }
        }

        Column {
            Text(
                text = "優先度",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SuggestionTagChip(
                    label = "低",
                    selected = priority == SuggestionPriority.Low,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.Low) },
                )
                SuggestionTagChip(
                    label = "通常",
                    selected = priority == SuggestionPriority.Normal,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.Normal) },
                )
                SuggestionTagChip(
                    label = "高",
                    selected = priority == SuggestionPriority.High,
                    enabled = interactive,
                    onClick = { onPriorityChange(SuggestionPriority.High) },
                )
            }
        }
    }
}
