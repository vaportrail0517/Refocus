package com.example.refocus.feature.customize.dialogs

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.refocus.ui.components.SettingRow
import com.example.refocus.ui.components.SettingsBaseDialog

@Composable
fun BaseAnimationDialog(
    initialBaseColorAnimEnabled: Boolean,
    initialBaseSizeAnimEnabled: Boolean,
    initialBasePulseEnabled: Boolean,
    onConfirm: (Boolean, Boolean, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var colorEnabled by remember(initialBaseColorAnimEnabled) { mutableStateOf(initialBaseColorAnimEnabled) }
    var sizeEnabled by remember(initialBaseSizeAnimEnabled) { mutableStateOf(initialBaseSizeAnimEnabled) }
    var pulseEnabled by remember(initialBasePulseEnabled) { mutableStateOf(initialBasePulseEnabled) }

    SettingsBaseDialog(
        title = "ベースアニメーション",
        description = "タイマーの基本的な見た目変化（色，サイズ，呼吸）を個別にオン・オフできます．",
        onConfirm = { onConfirm(colorEnabled, sizeEnabled, pulseEnabled) },
        onDismiss = onDismiss,
    ) {
        SettingRow(
            title = "色の変化",
            subtitle =
                if (colorEnabled) {
                    "オン：時間に応じてタイマーの色が変化します．"
                } else {
                    "オフ：タイマーの色は固定になります．"
                },
            trailing = {
                Switch(
                    checked = colorEnabled,
                    onCheckedChange = { colorEnabled = it },
                )
            },
            onClick = { colorEnabled = !colorEnabled },
        )

        SettingRow(
            title = "サイズの変化",
            subtitle =
                if (sizeEnabled) {
                    "オン：時間に応じてタイマーのサイズが変化します．"
                } else {
                    "オフ：タイマーのサイズは固定になります．"
                },
            trailing = {
                Switch(
                    checked = sizeEnabled,
                    onCheckedChange = { sizeEnabled = it },
                )
            },
            onClick = { sizeEnabled = !sizeEnabled },
        )

        SettingRow(
            title = "呼吸",
            subtitle =
                if (pulseEnabled) {
                    "オン：タイマーが穏やかに拡大縮小を繰り返します．"
                } else {
                    "オフ：呼吸アニメーションを表示しません．"
                },
            trailing = {
                Switch(
                    checked = pulseEnabled,
                    onCheckedChange = { pulseEnabled = it },
                )
            },
            onClick = { pulseEnabled = !pulseEnabled },
        )
    }
}
