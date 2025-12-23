package com.example.refocus.feature.customize

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.system.overlay.OverlayService
import com.example.refocus.system.overlay.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomizeScreen(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: CustomizeViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var activeDialog by remember { mutableStateOf<CustomizeDialogType?>(null) }
    var isAdvancedMode by remember { mutableStateOf(false) }
    var fontRange by remember(
        uiState.customize.minFontSizeSp,
        uiState.customize.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.customize.minFontSizeSp..uiState.customize.maxFontSizeSp
        )
    }
    var isServiceRunning by remember { mutableStateOf(OverlayService.isRunning) }
    val hasCorePermissions = PermissionHelper.hasAllCorePermissions(context)

    BackHandler(enabled = isAdvancedMode) {
        isAdvancedMode = false
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(isAdvancedMode) {
        scrollState.scrollTo(0)
    }

    // 画面復帰時に権限状態を更新
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                usageGranted = PermissionHelper.hasUsageAccess(context)
                overlayGranted = PermissionHelper.hasOverlayPermission(context)
                isServiceRunning = OverlayService.isRunning
                val hasCorePermissions = usageGranted && overlayGranted
                if (!hasCorePermissions) {
                    val latestState = viewModel.uiState.value
                    // 起動設定 or 実行中サービスが残っていたら OFF に揃える
                    if (latestState.customize.overlayEnabled || isServiceRunning) {
                        viewModel.updateOverlayEnabled(false)
                        context.stopOverlayService()
                        isServiceRunning = false
                    }
                    // 自動起動も OFF に揃える
                    if (latestState.customize.autoStartOnBoot) {
                        viewModel.updateAutoStartOnBoot(false)
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isAdvancedMode) "詳細カスタマイズ" else "カスタマイズ",
                    )
                },
                actions = {
                    if (!isAdvancedMode) {
                        IconButton(onClick = { isAdvancedMode = !isAdvancedMode }) {
                            Icon(
                                imageVector = Icons.Filled.ChevronRight,
                                contentDescription = "詳細設定に切り替え"
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (isAdvancedMode) {
                        IconButton(onClick = { isAdvancedMode = !isAdvancedMode }) {
                            Icon(
                                imageVector = Icons.Filled.ChevronLeft,
                                contentDescription = "基本設定に切り替え"
                            )
                        }
                    }
                },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!isAdvancedMode) {
                // ================= 基本設定 =================
                BasicCustomizeContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenTimerTimeModeDialog = {
                        activeDialog = CustomizeDialogType.TimerTimeDisplayMode
                    },
                    onOpenAdvanced = { isAdvancedMode = true },
                )
            } else {
                // ================= 詳細設定 =================
                AdvancedCustomizeContent(
                    uiState = uiState,
                    onBackToBasic = { isAdvancedMode = false },
                    onOpenGraceDialog = { activeDialog = CustomizeDialogType.GraceTime },
                    onOpenFontDialog = {
                        fontRange = uiState.customize.minFontSizeSp..
                                uiState.customize.maxFontSizeSp
                        activeDialog = CustomizeDialogType.FontRange
                    },
                    onOpenTimeToMaxDialog = { activeDialog = CustomizeDialogType.TimeToMax },
                    onOpenSuggestionTriggerDialog = {
                        activeDialog = CustomizeDialogType.SuggestionTriggerTime
                    },
                    onOpenSuggestionForegroundStableDialog = {
                        activeDialog = CustomizeDialogType.SuggestionForegroundStable
                    },
                    onOpenSuggestionCooldownDialog = {
                        activeDialog = CustomizeDialogType.SuggestionCooldown
                    },
                    onOpenSuggestionTimeoutDialog = {
                        activeDialog = CustomizeDialogType.SuggestionTimeout
                    },
                    onOpenSuggestionInteractionLockoutDialog = {
                        activeDialog = CustomizeDialogType.SuggestionInteractionLockout
                    },
                    onOpenGrowthModeDialog = { activeDialog = CustomizeDialogType.GrowthMode },
                    onOpenColorModeDialog = { activeDialog = CustomizeDialogType.ColorMode },
                    onOpenFixedColorDialog = { activeDialog = CustomizeDialogType.FixedColor },
                    onOpenGradientStartColorDialog = {
                        activeDialog = CustomizeDialogType.GradientStartColor
                    },
                    onOpenGradientMiddleColorDialog = {
                        activeDialog = CustomizeDialogType.GradientMiddleColor
                    },
                    onOpenGradientEndColorDialog = {
                        activeDialog = CustomizeDialogType.GradientEndColor
                    },
                )
            }

            when (activeDialog) {
                CustomizeDialogType.GraceTime -> {
                    GraceTimeDialog(
                        currentMillis = uiState.customize.gracePeriodMillis,
                        onConfirm = { newMillis ->
                            viewModel.updateGracePeriodMillis(newMillis)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null }
                    )
                }

                CustomizeDialogType.PollingInterval -> {
                    PollingIntervalDialog(
                        currentMillis = uiState.customize.pollingIntervalMillis,
                        onConfirm = { newMs ->
                            viewModel.updatePollingIntervalMillis(newMs)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null }
                    )
                }

                CustomizeDialogType.FontRange -> {
                    FontRangeDialog(
                        initialRange = fontRange,
                        onConfirm = { newRange ->
                            val minFontSpLimit = 8f
                            val maxFontSpLimit = 96f
                            val clampedMin =
                                newRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)
                            val clampedMax =
                                newRange.endInclusive.coerceIn(clampedMin, maxFontSpLimit)
                            viewModel.updateMinFontSizeSp(clampedMin)
                            viewModel.updateMaxFontSizeSp(clampedMax)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null }
                    )
                }

                CustomizeDialogType.TimeToMax -> {
                    TimeToMaxDialog(
                        currentMinutes = uiState.customize.timeToMaxMinutes,
                        onConfirm = { minutes ->
                            viewModel.updateTimeToMaxMinutes(minutes)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null }
                    )
                }

                CustomizeDialogType.TimerTimeDisplayMode -> {
                    TimerTimeModeDialog(
                        current = uiState.customize.timerTimeMode,
                        onConfirm = { mode ->
                            viewModel.updateTimerTimeMode(mode)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.SuggestionTriggerTime -> {
                    SuggestionTriggerTimeDialog(
                        currentSeconds = uiState.customize.suggestionTriggerSeconds,
                        onConfirm = { seconds ->
                            viewModel.updateSuggestionTriggerSeconds(seconds)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.SuggestionForegroundStable -> {
                    SuggestionForegroundStableDialog(
                        currentSeconds = uiState.customize.suggestionForegroundStableSeconds,
                        onConfirm = { seconds ->
                            viewModel.updateSuggestionForegroundStableSeconds(seconds)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.SuggestionCooldown -> {
                    SuggestionCooldownDialog(
                        currentSeconds = uiState.customize.suggestionCooldownSeconds,
                        onConfirm = { seconds ->
                            viewModel.updateSuggestionCooldownSeconds(seconds)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.SuggestionTimeout -> {
                    SuggestionTimeoutDialog(
                        currentSeconds = uiState.customize.suggestionTimeoutSeconds,
                        onConfirm = { seconds ->
                            viewModel.updateSuggestionTimeoutSeconds(seconds)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.SuggestionInteractionLockout -> {
                    SuggestionInteractionLockoutDialog(
                        currentMillis = uiState.customize.suggestionInteractionLockoutMillis,
                        onConfirm = { millis ->
                            viewModel.updateSuggestionInteractionLockoutMillis(millis)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.GrowthMode -> {
                    GrowthModeDialog(
                        current = uiState.customize.growthMode,
                        onConfirm = { mode ->
                            viewModel.updateGrowthMode(mode)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.ColorMode -> {
                    ColorModeDialog(
                        current = uiState.customize.colorMode,
                        onConfirm = { mode ->
                            viewModel.updateColorMode(mode)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.FixedColor -> {
                    FixedColorDialog(
                        currentColorArgb = uiState.customize.fixedColorArgb,
                        onConfirm = { argb ->
                            viewModel.updateFixedColorArgb(argb)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.GradientStartColor -> {
                    GradientStartColorDialog(
                        currentColorArgb = uiState.customize.gradientStartColorArgb,
                        onConfirm = { argb ->
                            viewModel.updateGradientStartColorArgb(argb)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.GradientMiddleColor -> {
                    GradientMiddleColorDialog(
                        currentColorArgb = uiState.customize.gradientMiddleColorArgb,
                        onConfirm = { argb ->
                            viewModel.updateGradientMiddleColorArgb(argb)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                CustomizeDialogType.GradientEndColor -> {
                    GradientEndColorDialog(
                        currentColorArgb = uiState.customize.gradientEndColorArgb,
                        onConfirm = { argb ->
                            viewModel.updateGradientEndColorArgb(argb)
                            activeDialog = null
                        },
                        onDismiss = { activeDialog = null },
                    )
                }

                null -> Unit
            }
        }
    }
}
