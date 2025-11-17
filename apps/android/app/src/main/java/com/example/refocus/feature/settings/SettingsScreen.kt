package com.example.refocus.feature.settings

import android.app.Activity
import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.refocus.system.permissions.PermissionHelper
import com.example.refocus.ui.components.SectionCard
import com.example.refocus.ui.components.SectionTitle
import com.example.refocus.ui.components.SettingRow
import com.example.refocus.core.model.OverlayTouchMode
import androidx.compose.material3.Slider

@Composable
fun SettingsScreen(
    onOpenAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModelFactory(app)
    )
    val uiState by viewModel.uiState.collectAsState()
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current

    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var notificationGranted by remember {
        mutableStateOf(
            PermissionHelper.hasNotificationPermission(
                context
            )
        )
    }

    var showGraceDialog by remember { mutableStateOf(false) }
    var graceInput by remember { mutableStateOf("") }
    var showPollingDialog by remember { mutableStateOf(false) }
    var selectedPollingMs by remember {
        mutableStateOf(uiState.overlaySettings.pollingIntervalMillis)
    }
    var showFontRangeDialog by remember { mutableStateOf(false) }
    var fontRange by remember {
        mutableStateOf(
            uiState.overlaySettings.minFontSizeSp..uiState.overlaySettings.maxFontSizeSp
        )
    }
    var showTimeToMaxDialog by remember { mutableStateOf(false) }
    var timeToMaxInput by remember { mutableStateOf("") }

    data class PollingOption(val ms: Long, val label: String, val description: String)

    val pollingOptions = listOf(
        PollingOption(
            ms = 250L,
            label = "250 ms",
            description = "最も素早い反応。バッテリ負荷やや高め。"
        ),
        PollingOption(
            ms = 500L,
            label = "500 ms",
            description = "標準的なバランス。反応と電池のバランスが良い。"
        ),
        PollingOption(
            ms = 1000L,
            label = "1000 ms",
            description = "ややゆっくり。電池を少し節約したい場合。"
        ),
        PollingOption(
            ms = 2000L,
            label = "2000 ms",
            description = "最も省エネ。反応はゆっくりになる。"
        ),
    )

    // 画面復帰時に権限状態を更新
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = PermissionHelper.hasUsageAccess(context)
                overlayGranted = PermissionHelper.hasOverlayPermission(context)
                notificationGranted = PermissionHelper.hasNotificationPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SectionTitle("権限")
        SectionCard {
            PermissionRow(
                title = "使用状況へのアクセス",
                description = "連続使用時間を計測するために必要です。",
                isGranted = usageGranted,
                onClick = {
                    activity?.let { PermissionHelper.openUsageAccessSettings(it) }
                }
            )
            PermissionRow(
                title = "他のアプリの上に表示",
                description = "タイマーを他のアプリの上に表示するために必要です。",
                isGranted = overlayGranted,
                onClick = {
                    activity?.let { PermissionHelper.openOverlaySettings(it) }
                }
            )
            PermissionRow(
                title = "通知",
                description = "やることの提案やお知らせに利用します。",
                isGranted = notificationGranted,
                onClick = {
                    activity?.let { PermissionHelper.openNotificationSettings(it) }
                }
            )
        }

        SectionTitle("アプリ")
        SectionCard {
            SettingRow(
                title = "対象アプリを設定",
                subtitle = "時間を計測したいアプリを選択します。",
                onClick = onOpenAppSelect
            )
        }

        SectionTitle("動作")
        SectionCard {
            SettingRow(
                title = "猶予時間",
                subtitle = "${formatGraceTimeText(uiState.overlaySettings.gracePeriodMillis)}（この時間以内に戻るとセッションを継続します）",
                onClick = {
                    graceInput = formatGraceTimeText(uiState.overlaySettings.gracePeriodMillis)
                    showGraceDialog = true
                }
            )
            val pollingMs = uiState.overlaySettings.pollingIntervalMillis
            SettingRow(
                title = "監視間隔",
                subtitle = "$pollingMs ms（アプリ切り替えの検出頻度）",
                onClick = {
                    selectedPollingMs = pollingMs
                    showPollingDialog = true
                }
            )
            SettingRow(
                title = "フォントサイズ",
                subtitle = "最小 ${uiState.overlaySettings.minFontSizeSp} sp / 最大 ${uiState.overlaySettings.maxFontSizeSp} sp",
                onClick = {
                    fontRange = uiState.overlaySettings.minFontSizeSp..
                            uiState.overlaySettings.maxFontSizeSp
                    showFontRangeDialog = true
                }
            )
            SettingRow(
                title = "最大サイズになるまでの時間",
                subtitle = "${uiState.overlaySettings.timeToMaxMinutes} 分",
                onClick = {
                    timeToMaxInput = uiState.overlaySettings.timeToMaxMinutes.toString()
                    showTimeToMaxDialog = true
                }
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "タイマーの移動",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val enabled = uiState.overlaySettings.touchMode == OverlayTouchMode.Drag
                    Text(
                        text = if (enabled) {
                            "オン：タイマーをドラッグして移動できます"
                        } else {
                            "オフ：タイマーは固定され，タップはタイマーを透過します"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                val enabled = uiState.overlaySettings.touchMode == OverlayTouchMode.Drag
                Switch(
                    checked = enabled,
                    onCheckedChange = { isOn ->
                        val mode = if (isOn) {
                            OverlayTouchMode.Drag
                        } else {
                            OverlayTouchMode.PassThrough
                        }
                        viewModel.updateOverlayTouchMode(mode)
                    }
                )
            }
        }
        if (showGraceDialog) {
            val maxGraceMillis = 10 * 60_000L
            val stepMillis = 30_000L
            val graceMillis = uiState.overlaySettings.gracePeriodMillis
                .coerceIn(0L, maxGraceMillis)
            var sliderValue by remember(graceMillis) {
                mutableStateOf(graceMillis.toFloat())
            }
            val currentLabel = formatGraceTimeText(sliderValue.toLong())
            AlertDialog(
                onDismissRequest = { showGraceDialog = false },
                title = { Text("猶予時間") },
                text = {
                    Column {
                        Text(
                            text = "対象アプリから離れてから、何秒以内に戻れば同じセッションとみなすかを指定します。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "現在: $currentLabel",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "0〜10分 / 30秒刻み",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = sliderValue,
                            onValueChange = { raw ->
                                val steps = (maxGraceMillis / stepMillis).toInt()
                                val index = (raw / stepMillis)
                                    .toInt()
                                    .coerceIn(0, steps)
                                val snapped = index * stepMillis
                                sliderValue = snapped.toFloat()
                            },
                            valueRange = 0f..maxGraceMillis.toFloat(),
                            steps = (maxGraceMillis / stepMillis).toInt() - 1,
                            onValueChangeFinished = {
                                viewModel.updateGracePeriodMillis(sliderValue.toLong())
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val seconds = graceInput.toLongOrNull()
                            if (seconds != null) {
                                viewModel.updateGracePeriodMillis(seconds * 1000L)
                            }
                            showGraceDialog = false
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showGraceDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
        if (showPollingDialog) {
            AlertDialog(
                onDismissRequest = { showPollingDialog = false },
                title = { Text("監視間隔") },
                text = {
                    Column {
                        Text(
                            text = "前面アプリをどれくらいの間隔でチェックするかを選びます。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        pollingOptions.forEach { option ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        selectedPollingMs = option.ms
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedPollingMs == option.ms,
                                    onClick = { selectedPollingMs = option.ms }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = option.label,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = option.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updatePollingIntervalMillis(selectedPollingMs)
                            showPollingDialog = false
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPollingDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
        if (showFontRangeDialog) {
            val minFontSpLimit = 8f
            val maxFontSpLimit = 96f
            AlertDialog(
                onDismissRequest = { showFontRangeDialog = false },
                title = { Text("フォントサイズ") },
                text = {
                    Column {
                        Text(
                            text = "タイマーのフォントサイズ範囲を指定します。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        RangeSlider(
                            value = fontRange,
                            onValueChange = { range ->
                                fontRange = range
                            },
                            valueRange = minFontSpLimit..maxFontSpLimit,
                            steps = (maxFontSpLimit - minFontSpLimit).toInt() - 1, // 1sp刻み
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "最小: ${"%.1f".format(fontRange.start)} sp / 最大: ${
                                "%.1f".format(
                                    fontRange.endInclusive
                                )
                            } sp",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val minSp = fontRange.start
                            val maxSp = fontRange.endInclusive
                            val clampedMin = minSp.coerceIn(minFontSpLimit, maxFontSpLimit)
                            val clampedMax = maxSp.coerceIn(clampedMin, maxFontSpLimit)
                            viewModel.updateMinFontSizeSp(clampedMin)
                            viewModel.updateMaxFontSizeSp(clampedMax)
                            showFontRangeDialog = false
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFontRangeDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
        if (showTimeToMaxDialog) {
            AlertDialog(
                onDismissRequest = { showTimeToMaxDialog = false },
                title = { Text("最大サイズになるまでの時間") },
                text = {
                    Column {
                        Text(
                            text = "フォントが最大サイズになるまでの時間（分）を指定します。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = timeToMaxInput,
                            onValueChange = { timeToMaxInput = it },
                            label = { Text("時間（分）") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number
                            ),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val value = timeToMaxInput.toIntOrNull()
                            if (value != null) {
                                val clamped = value.coerceIn(1, 720)
                                viewModel.updateTimeToMaxMinutes(clamped)
                            }
                            showTimeToMaxDialog = false
                        }
                    ) {
                        Text("保存")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showTimeToMaxDialog = false }) {
                        Text("キャンセル")
                    }
                }
            )
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = isGranted,
            onCheckedChange = null,
            enabled = true,
        )
    }
}

private fun formatGraceTimeText(millis: Long): String {
    if (millis <= 0L) return "なし"
    val totalSeconds = millis / 1000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        minutes > 0 && seconds > 0 ->
            "${minutes}分${seconds}秒"
        minutes > 0 ->
            "${minutes}分"
        else ->
            "${seconds}秒"
    }
}
