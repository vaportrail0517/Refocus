package com.example.refocus.feature.customize

import android.content.pm.ApplicationInfo
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.refocus.core.model.MiniGameKind
import com.example.refocus.core.model.MiniGameOrder
import com.example.refocus.core.model.TimerTimeMode
import com.example.refocus.core.model.TimerTouchMode
import com.example.refocus.core.model.TimerVisualTimeBasis
import com.example.refocus.ui.minigame.catalog.MiniGameRegistry
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SettingRow

@Composable
fun BasicCustomizeContent(
    uiState: CustomizeViewModel.UiState,
    viewModel: CustomizeViewModel,
    onOpenTimerTimeModeDialog: () -> Unit,
    onOpenTimerVisualTimeBasisDialog: () -> Unit,
    onOpenPresetManager: () -> Unit,
    onOpenMiniGameOrderDialog: () -> Unit,
    onDebugPlayMiniGame: (MiniGameKind) -> Unit = {},
) {
    val settings = uiState.customize

    val context = LocalContext.current
    val isDebuggable =
        remember {
            (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        }

    // --- タイマー（プリセット） ---
    SectionCard(title = "タイマー") {
        SettingRow(
            title = "タイマーに表示する時間",
            subtitle =
                when (settings.timerTimeMode) {
                    TimerTimeMode.SessionElapsed -> "現在: セッションの経過時間を表示します。"
                    TimerTimeMode.TodayThisTarget -> "現在: このアプリの今日の累計使用時間を表示します。"
                    TimerTimeMode.TodayAllTargets -> "現在: 全対象アプリの今日の累計使用時間を表示します。"
                },
            onClick = onOpenTimerTimeModeDialog,
        )

        SettingRow(
            title = "色とサイズの変化の基準",
            subtitle =
                when (settings.timerVisualTimeBasis) {
                    TimerVisualTimeBasis.SessionElapsed ->
                        "現在: セッションの経過時間を基準に、色とサイズが変化します。"

                    TimerVisualTimeBasis.FollowDisplayTime ->
                        "現在: タイマーに表示している時間を基準に、色とサイズが変化します。"
                },
            onClick = onOpenTimerVisualTimeBasisDialog,
        )

        val dragEnabled = settings.touchMode == TimerTouchMode.Drag
        SettingRow(
            title = "タイマーのタッチ操作",
            subtitle =
                if (dragEnabled) {
                    "ドラッグで移動：タイマーをドラッグして位置を変えられます。"
                } else {
                    "タップを透過：タイマーは固定され、タップは背面アプリに届きます。"
                },
            trailing = {
                Switch(
                    checked = dragEnabled,
                    onCheckedChange = { isOn ->
                        val mode = if (isOn) TimerTouchMode.Drag else TimerTouchMode.PassThrough
                        viewModel.updateOverlayTouchMode(mode)
                    },
                )
            },
            onClick = {
                val newMode =
                    if (dragEnabled) TimerTouchMode.PassThrough else TimerTouchMode.Drag
                viewModel.updateOverlayTouchMode(newMode)
            },
        )
    }

    // --- 提案（簡易） ---
    SectionCard(title = "提案") {
        val suggestionEnabled = settings.suggestionEnabled
        val restSuggestionEnabled = settings.restSuggestionEnabled

        SettingRow(
            title = "やりたいことの提案",
            subtitle =
                if (suggestionEnabled) {
                    "オン：一定時間経過すると、やりたいことを提案します。"
                } else {
                    "オフ：提案カードを表示しません。"
                },
            trailing = {
                Switch(
                    checked = suggestionEnabled,
                    onCheckedChange = { viewModel.updateSuggestionEnabled(it) },
                )
            },
            onClick = { viewModel.updateSuggestionEnabled(!suggestionEnabled) },
        )

        SettingRow(
            title = "休憩の提案",
            subtitle =
                when {
                    !suggestionEnabled ->
                        "提案が無効になっています。"

                    restSuggestionEnabled ->
                        "オン：やりたいことが未登録のとき、休憩を提案します。"

                    else ->
                        "オフ：休憩を提案しません。"
                },
            trailing = {
                Switch(
                    checked = restSuggestionEnabled,
                    enabled = suggestionEnabled,
                    onCheckedChange = { isOn ->
                        if (!suggestionEnabled) return@Switch
                        viewModel.updateRestSuggestionEnabled(isOn)
                    },
                )
            },
            onClick = {
                if (!suggestionEnabled) return@SettingRow
                viewModel.updateRestSuggestionEnabled(!restSuggestionEnabled)
            },
        )
    }

    // --- ミニゲーム ---
    SectionCard(title = "ミニゲーム") {
        val suggestionEnabled = settings.suggestionEnabled
        val miniGameEnabled = settings.miniGameEnabled

        SettingRow(
            title = "ミニゲーム（提案とセット）",
            subtitle =
                when {
                    !suggestionEnabled ->
                        "提案がオフのため，現在は利用できません．"

                    miniGameEnabled ->
                        "オン：提案の前後にミニゲームを挟みます．"

                    else ->
                        "オフ：ミニゲームを表示しません．"
                },
            trailing = {
                Switch(
                    checked = miniGameEnabled,
                    enabled = suggestionEnabled,
                    onCheckedChange = { isOn ->
                        if (!suggestionEnabled) return@Switch
                        viewModel.updateMiniGameEnabled(isOn)
                    },
                )
            },
            onClick = {
                if (!suggestionEnabled) return@SettingRow
                viewModel.updateMiniGameEnabled(!miniGameEnabled)
            },
        )

        val orderEnabled = suggestionEnabled && miniGameEnabled
        SettingRow(
            title = "提案とミニゲームの順番",
            subtitle =
                when (settings.miniGameOrder) {
                    MiniGameOrder.BeforeSuggestion -> "現在：ミニゲーム → 提案"
                    MiniGameOrder.AfterSuggestion -> "現在：提案 → ミニゲーム"
                } + if (!orderEnabled) "（ミニゲームがオフです）" else "",
            onClick = if (orderEnabled) onOpenMiniGameOrderDialog else null,
        )

        if (isDebuggable) {
            var showMiniGameTestDialog by remember { mutableStateOf(false) }

            SettingRow(
                title = "ミニゲームのテスト",
                subtitle = "実装済みのミニゲームを選択して起動します．",
                onClick = { showMiniGameTestDialog = true },
            )

            if (showMiniGameTestDialog) {
                com.example.refocus.ui.components.SettingsBaseDialog(
                    title = "ミニゲームを選択",
                    confirmLabel = "閉じる",
                    showDismissButton = false,
                    onConfirm = { showMiniGameTestDialog = false },
                    onDismiss = { showMiniGameTestDialog = false },
                ) {
                    MiniGameRegistry.descriptors.forEach { desc ->
                        SettingRow(
                            title = desc.title,
                            subtitle = desc.description,
                            onClick = {
                                showMiniGameTestDialog = false
                                onDebugPlayMiniGame(desc.kind)
                            },
                        )
                    }
                }
            }
        }
    }
}
