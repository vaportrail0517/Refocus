package com.example.refocus.feature.customize

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.refocus.feature.common.overlay.rememberOverlayServiceController
import com.example.refocus.feature.common.overlay.rememberOverlayServiceStatusProvider
import com.example.refocus.feature.common.permissions.rememberPermissionUiState
import com.example.refocus.feature.customize.dialogs.ColorModeDialog
import com.example.refocus.feature.customize.dialogs.FixedColorDialog
import com.example.refocus.feature.customize.dialogs.FontRangeDialog
import com.example.refocus.feature.customize.dialogs.GraceTimeDialog
import com.example.refocus.feature.customize.dialogs.GradientEndColorDialog
import com.example.refocus.feature.customize.dialogs.GradientMiddleColorDialog
import com.example.refocus.feature.customize.dialogs.GradientStartColorDialog
import com.example.refocus.feature.customize.dialogs.GrowthModeDialog
import com.example.refocus.feature.customize.dialogs.PollingIntervalDialog
import com.example.refocus.feature.customize.dialogs.SuggestionCooldownDialog
import com.example.refocus.feature.customize.dialogs.SuggestionForegroundStableDialog
import com.example.refocus.feature.customize.dialogs.SuggestionInteractionLockoutDialog
import com.example.refocus.feature.customize.dialogs.SuggestionTimeoutDialog
import com.example.refocus.feature.customize.dialogs.SuggestionTriggerTimeDialog
import com.example.refocus.feature.customize.dialogs.TimeToMaxDialog
import com.example.refocus.feature.customize.dialogs.TimerTimeModeDialog
import com.example.refocus.feature.customize.dialogs.TimerVisualTimeBasisDialog
import kotlinx.coroutines.launch

private enum class CustomizeTab(val title: String) {
    Basic("基本"),
    Advanced("詳細"),
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CustomizeScreen(
    modifier: Modifier = Modifier,
) {
    val viewModel: CustomizeViewModel = hiltViewModel()

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var activeDialog by remember { mutableStateOf<CustomizeDialogType?>(null) }
    var fontRange by remember(
        uiState.customize.minFontSizeSp,
        uiState.customize.maxFontSizeSp
    ) {
        mutableStateOf(
            uiState.customize.minFontSizeSp..uiState.customize.maxFontSizeSp
        )
    }

    val overlayServiceStatusProvider = rememberOverlayServiceStatusProvider()
    val overlayServiceController = rememberOverlayServiceController()
    var isServiceRunning by remember {
        mutableStateOf(overlayServiceStatusProvider.isRunning())
    }

    val tabs = CustomizeTab.entries
    val pagerState = rememberPagerState(
        initialPage = tabs.indexOf(CustomizeTab.Basic).coerceAtLeast(0),
        pageCount = { tabs.size },
    )
    val scope = rememberCoroutineScope()
    val basicScrollState = rememberScrollState()
    val advancedScrollState = rememberScrollState()

    // 画面復帰（ON_RESUME）で権限状態を再評価し，権限が欠けていれば安全側に倒す．
    rememberPermissionUiState(
        onRefreshed = { latest ->
            isServiceRunning = overlayServiceStatusProvider.isRunning()

            if (!latest.hasCorePermissions) {
                val latestState = viewModel.uiState.value
                // 起動設定 or 実行中サービスが残っていたら OFF に揃える
                if (latestState.customize.overlayEnabled || isServiceRunning) {
                    viewModel.updateOverlayEnabled(false)
                    overlayServiceController.stop(source = "customize_permission_refresh")
                    isServiceRunning = false
                }
                // 自動起動も OFF に揃える
                if (latestState.customize.autoStartOnBoot) {
                    viewModel.updateAutoStartOnBoot(false)
                }
            }
        },
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "カスタマイズ") },
                windowInsets = WindowInsets(0.dp),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TabRow(selectedTabIndex = pagerState.currentPage) {
                    tabs.forEachIndexed { index, tab ->
                        Tab(
                            selected = pagerState.currentPage == index,
                            onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                            text = { Text(tab.title) },
                        )
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    val scrollState = if (tabs[page] == CustomizeTab.Basic) {
                        basicScrollState
                    } else {
                        advancedScrollState
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (tabs[page]) {
                            CustomizeTab.Basic -> {
                                BasicCustomizeContent(
                                    uiState = uiState,
                                    viewModel = viewModel,
                                    onOpenGraceDialog = {
                                        activeDialog = CustomizeDialogType.GraceTime
                                    },
                                    onOpenFontDialog = {
                                        fontRange = uiState.customize.minFontSizeSp..
                                                uiState.customize.maxFontSizeSp
                                        activeDialog = CustomizeDialogType.FontRange
                                    },
                                    onOpenTimeToMaxDialog = {
                                        activeDialog = CustomizeDialogType.TimeToMax
                                    },
                                    onOpenTimerTimeModeDialog = {
                                        activeDialog = CustomizeDialogType.TimerTimeDisplayMode
                                    },
                                    onOpenSuggestionTriggerDialog = {
                                        activeDialog = CustomizeDialogType.SuggestionTriggerTime
                                    },
                                )
                            }

                            CustomizeTab.Advanced -> {
                                AdvancedCustomizeContent(
                                    uiState = uiState,
                                    onOpenTimerVisualTimeBasisDialog = {
                                        activeDialog = CustomizeDialogType.TimerVisualTimeBasis
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
                                    onOpenGrowthModeDialog = {
                                        activeDialog = CustomizeDialogType.GrowthMode
                                    },
                                    onOpenColorModeDialog = {
                                        activeDialog = CustomizeDialogType.ColorMode
                                    },
                                    onOpenFixedColorDialog = {
                                        activeDialog = CustomizeDialogType.FixedColor
                                    },
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
                        }
                    }
                }
            }

            // Dialog は Pager の外で 1 回だけ描画する（隣ページの事前合成で多重表示されないようにする）
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
                            val clampedMin = newRange.start.coerceIn(minFontSpLimit, maxFontSpLimit)
                            val clampedMax = newRange.endInclusive.coerceIn(clampedMin, maxFontSpLimit)
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

                CustomizeDialogType.TimerVisualTimeBasis -> {
                    TimerVisualTimeBasisDialog(
                        current = uiState.customize.timerVisualTimeBasis,
                        onConfirm = { basis ->
                            viewModel.updateTimerVisualTimeBasis(basis)
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
