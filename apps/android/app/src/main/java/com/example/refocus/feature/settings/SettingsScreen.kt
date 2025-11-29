package com.example.refocus.feature.settings

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.example.refocus.system.overlay.service.OverlayService
import com.example.refocus.system.overlay.service.stopOverlayService
import com.example.refocus.system.permissions.PermissionHelper


@Composable
fun SettingsScreen(
    onOpenAppSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val viewModel: SettingsViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = context as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    var usageGranted by remember { mutableStateOf(PermissionHelper.hasUsageAccess(context)) }
    var overlayGranted by remember { mutableStateOf(PermissionHelper.hasOverlayPermission(context)) }
    var activeDialog by remember { mutableStateOf<SettingsDialogType?>(null) }
    var isAdvancedMode by remember { mutableStateOf(false) }
    var fontRange by remember(
        uiState.settings.minFontSizeSp,
        uiState.settings.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.settings.minFontSizeSp..uiState.settings.maxFontSizeSp
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
                    if (latestState.settings.overlayEnabled || isServiceRunning) {
                        viewModel.updateOverlayEnabled(false)
                        context.stopOverlayService()
                        isServiceRunning = false
                    }
                    // 自動起動も OFF に揃える
                    if (latestState.settings.autoStartOnBoot) {
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (!isAdvancedMode) {
            // ================= 基本設定 =================
            BasicSettingsContent(
                uiState = uiState,
                usageGranted = usageGranted,
                overlayGranted = overlayGranted,
                hasCorePermissions = hasCorePermissions,
                isServiceRunning = isServiceRunning,
                onServiceRunningChange = { isServiceRunning = it },
                onOpenAppSelect = onOpenAppSelect,
                onRequireCorePermission = {
                    activeDialog = SettingsDialogType.CorePermissionRequired
                },
                onOpenAdvanced = { isAdvancedMode = true },
                viewModel = viewModel,
                context = context,
                activity = activity,
            )
        } else {
            // ================= 詳細設定 =================
            AdvancedSettingsContent(
                uiState = uiState,
                onBackToBasic = { isAdvancedMode = false },
                onOpenGraceDialog = { activeDialog = SettingsDialogType.GraceTime },
                onOpenPollingDialog = { activeDialog = SettingsDialogType.PollingInterval },
                onOpenFontDialog = {
                    fontRange = uiState.settings.minFontSizeSp..
                            uiState.settings.maxFontSizeSp
                    activeDialog = SettingsDialogType.FontRange
                },
                onOpenTimeToMaxDialog = { activeDialog = SettingsDialogType.TimeToMax },
                onOpenSuggestionTriggerDialog = {
                    activeDialog = SettingsDialogType.SuggestionTriggerTime
                },
                onOpenSuggestionForegroundStableDialog = {
                    activeDialog = SettingsDialogType.SuggestionForegroundStable
                },
                onOpenSuggestionCooldownDialog = {
                    activeDialog = SettingsDialogType.SuggestionCooldown
                },
                onOpenSuggestionTimeoutDialog = {
                    activeDialog = SettingsDialogType.SuggestionTimeout
                },
                onOpenSuggestionInteractionLockoutDialog = {
                    activeDialog = SettingsDialogType.SuggestionInteractionLockout
                },
                onOpenGrowthModeDialog = { activeDialog = SettingsDialogType.GrowthMode },
                onOpenColorModeDialog = { activeDialog = SettingsDialogType.ColorMode },
                onOpenFixedColorDialog = { activeDialog = SettingsDialogType.FixedColor },
                onOpenGradientStartColorDialog = {
                    activeDialog = SettingsDialogType.GradientStartColor
                },
                onOpenGradientMiddleColorDialog = {
                    activeDialog = SettingsDialogType.GradientMiddleColor
                },
                onOpenGradientEndColorDialog = {
                    activeDialog = SettingsDialogType.GradientEndColor
                },
                viewModel = viewModel,
            )
        }

        when (activeDialog) {
            SettingsDialogType.GraceTime -> {
                GraceTimeDialog(
                    currentMillis = uiState.settings.gracePeriodMillis,
                    onConfirm = { newMillis ->
                        viewModel.updateGracePeriodMillis(newMillis)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialogType.PollingInterval -> {
                PollingIntervalDialog(
                    currentMillis = uiState.settings.pollingIntervalMillis,
                    onConfirm = { newMs ->
                        viewModel.updatePollingIntervalMillis(newMs)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialogType.FontRange -> {
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

            SettingsDialogType.TimeToMax -> {
                TimeToMaxDialog(
                    currentMinutes = uiState.settings.timeToMaxMinutes,
                    onConfirm = { minutes ->
                        viewModel.updateTimeToMaxMinutes(minutes)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialogType.CorePermissionRequired -> {
                CorePermissionRequiredDialog(
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialogType.SuggestionFeatureRequired -> {
                SuggestionFeatureRequiredDialog(
                    onDismiss = { activeDialog = null }
                )
            }

            SettingsDialogType.SuggestionTriggerTime -> {
                SuggestionTriggerTimeDialog(
                    currentSeconds = uiState.settings.suggestionTriggerSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionTriggerSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.SuggestionForegroundStable -> {
                SuggestionForegroundStableDialog(
                    currentSeconds = uiState.settings.suggestionForegroundStableSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionForegroundStableSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.SuggestionCooldown -> {
                SuggestionCooldownDialog(
                    currentSeconds = uiState.settings.suggestionCooldownSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionCooldownSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.SuggestionTimeout -> {
                SuggestionTimeoutDialog(
                    currentSeconds = uiState.settings.suggestionTimeoutSeconds,
                    onConfirm = { seconds ->
                        viewModel.updateSuggestionTimeoutSeconds(seconds)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.SuggestionInteractionLockout -> {
                SuggestionInteractionLockoutDialog(
                    currentMillis = uiState.settings.suggestionInteractionLockoutMillis,
                    onConfirm = { millis ->
                        viewModel.updateSuggestionInteractionLockoutMillis(millis)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.GrowthMode -> {
                GrowthModeDialog(
                    current = uiState.settings.growthMode,
                    onConfirm = { mode ->
                        viewModel.updateGrowthMode(mode)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.ColorMode -> {
                ColorModeDialog(
                    current = uiState.settings.colorMode,
                    onConfirm = { mode ->
                        viewModel.updateColorMode(mode)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.FixedColor -> {
                FixedColorDialog(
                    currentColorArgb = uiState.settings.fixedColorArgb,
                    onConfirm = { argb ->
                        viewModel.updateFixedColorArgb(argb)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.GradientStartColor -> {
                GradientStartColorDialog(
                    currentColorArgb = uiState.settings.gradientStartColorArgb,
                    onConfirm = { argb ->
                        viewModel.updateGradientStartColorArgb(argb)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.GradientMiddleColor -> {
                GradientMiddleColorDialog(
                    currentColorArgb = uiState.settings.gradientMiddleColorArgb,
                    onConfirm = { argb ->
                        viewModel.updateGradientMiddleColorArgb(argb)
                        activeDialog = null
                    },
                    onDismiss = { activeDialog = null },
                )
            }

            SettingsDialogType.GradientEndColor -> {
                GradientEndColorDialog(
                    currentColorArgb = uiState.settings.gradientEndColorArgb,
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

