package com.example.refocus.feature.customize.dialogs

import androidx.compose.runtime.Composable

@Composable
fun FixedColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "単色モードの色",
        description = "タイマー背景に使う単色を選びます．",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientStartColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション開始色",
        description = "タイマー利用開始直後に使う色を選びます．",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientMiddleColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション中間色",
        description = "タイマーのフォントサイズが中間のときに使う色を選びます．",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}

@Composable
fun GradientEndColorDialog(
    currentColorArgb: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ColorPickerDialog(
        title = "グラデーション終了色",
        description = "タイマーが最大サイズになったときに使う色を選びます．",
        initialColorArgb = currentColorArgb.takeIf { it != 0 },
        onConfirm = onConfirm,
        onDismiss = onDismiss,
    )
}
