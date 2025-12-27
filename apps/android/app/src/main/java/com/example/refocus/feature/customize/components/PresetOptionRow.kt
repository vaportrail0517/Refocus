package com.example.refocus.feature.customize.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
private fun RowScope.PresetChoiceButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .height(32.dp),               // ← 少し小さめで統一感
        shape = shape,
        border = BorderStroke(
            width = 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            else
                Color.Transparent,
            contentColor = if (selected)
                MaterialTheme.colorScheme.primary
            else
                MaterialTheme.colorScheme.onSurface
        ),
        contentPadding = PaddingValues(0.dp) // ← 中央にぴったり配置
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                )
            )
        }
    }
}


/**
 * タイトル + サブタイトル + 横並びボタン群の行。
 * selectedIndex が null のときは「どのボタンも選択されていない状態」になる。
 */
@Composable
fun OptionButtonsRow(
    title: String,
    subtitle: String,
    optionLabels: List<String>,
    selectedIndex: Int?,     // null → どれも選択されない（＝Custom）
    onSelectIndex: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            optionLabels.forEachIndexed { index, label ->
                PresetChoiceButton(
                    label = label,
                    selected = (selectedIndex == index),
                    onClick = { onSelectIndex(index) }
                )
            }
        }
    }
}


data class PresetOption<P>(
    val value: P,
    val label: String,
)

@Composable
fun <P> PresetOptionRow(
    title: String,
    currentPreset: P?,
    options: List<PresetOption<P>>,
    currentValueDescription: String,
    onPresetSelected: (P) -> Unit,
) {
    val selectedIndex = options.indexOfFirst { it.value == currentPreset }
        .takeIf { it >= 0 }

    val subtitle = buildString {
        append("現在: ")
        append(currentValueDescription)
        append("（")
        append(
            if (currentPreset == null) {
                "カスタム"
            } else {
                val option = options.firstOrNull { it.value == currentPreset }
                if (option != null) {
                    "プリセット: ${option.label}"
                } else {
                    "カスタム"
                }
            }
        )
        append("）")
    }

    val labels = options.map { it.label }

    OptionButtonsRow(
        title = title,
        subtitle = subtitle,
        optionLabels = labels,
        selectedIndex = selectedIndex,
        onSelectIndex = { idx ->
            val option = options.getOrNull(idx) ?: return@OptionButtonsRow
            onPresetSelected(option.value)
        }
    )
}

